package com.moments.sicc.service;

import com.moments.sicc.shared.exception.ArmazenamentoException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

@Service
public class ArmazenamentoLocal implements ArmazenamentoArquivoTransacional {
    private final RaizArmazenamentoSegura root;

    @Autowired
    public ArmazenamentoLocal(@Value("${sicc.storage.directory}") String directory) {
        this.root = new RaizArmazenamentoSegura(Path.of(directory));
    }

    ArmazenamentoLocal(RaizArmazenamentoSegura root) {
        this.root = root;
    }

    @Override
    public String armazenar(byte[] content, String prefix) {
        String key = prefix + "/" + UUID.randomUUID();
        Path destination = resolve(key);
        try (RaizArmazenamentoSegura.Fixacao raizFixada = root.fixar(true)) {
            raizFixada.armazenar(destination, content);
            try {
                raizFixada.validarVinculo();
            } catch (IOException | RuntimeException e) {
                compensarAntesDeFechar(raizFixada, destination, e);
                throw e;
            }
            return key;
        } catch (IOException e) {
            throw new ArmazenamentoException("Não foi possível armazenar o arquivo.", e);
        }
    }

    @Override
    public Gravacao prepararArmazenamento(byte[] content, String prefix) {
        String key = prefix + "/" + UUID.randomUUID();
        Path destination = resolve(key);
        RaizArmazenamentoSegura.Fixacao raizFixada = null;
        try {
            raizFixada = root.fixar(true);
            raizFixada.armazenar(destination, content);
            return new GravacaoLocal(key, destination, raizFixada);
        } catch (IOException e) {
            fecharSuprimindo(raizFixada, e);
            throw new ArmazenamentoException("Não foi possível armazenar o arquivo.", e);
        } catch (RuntimeException e) {
            fecharSuprimindo(raizFixada, e);
            throw e;
        }
    }

    @Override
    public Resource carregar(String key) {
        Path file = resolve(key);
        try (RaizArmazenamentoSegura.Fixacao raizFixada = root.fixar(false)) {
            RaizArmazenamentoSegura.Leitura leitura = raizFixada.carregar(file);
            try (InputStream ignorado = leitura.conteudo()) {
                return new ArquivoResource(file, leitura.tamanho());
            }
        } catch (NoSuchFileException e) {
            throw new ArmazenamentoException("Arquivo não encontrado.");
        } catch (IOException e) {
            throw new ArmazenamentoException("Não foi possível carregar o arquivo.", e);
        }
    }

    @Override
    public void remover(String key) {
        Path file = resolve(key);
        try (RaizArmazenamentoSegura.Fixacao raizFixada = root.fixar(false)) {
            raizFixada.remover(file);
        } catch (NoSuchFileException e) {
            // A remoção compensatória é idempotente, inclusive sem o diretório pai.
        } catch (IOException e) {
            throw new ArmazenamentoException("Não foi possível remover o arquivo.", e);
        }
    }

    private Path resolve(String key) {
        Path path = root.caminho().resolve(key).normalize();
        if (!path.startsWith(root.caminho()) || path.equals(root.caminho())) {
            throw new ArmazenamentoException("Chave de armazenamento inválida.");
        }
        return path;
    }

    private static void fecharSuprimindo(AutoCloseable closeable, Throwable falha) {
        if (closeable == null) return;
        try {
            closeable.close();
        } catch (Exception e) {
            falha.addSuppressed(e);
        }
    }

    private static void compensarAntesDeFechar(
            RaizArmazenamentoSegura.Fixacao raizFixada,
            Path arquivo,
            Throwable falha) {
        try {
            raizFixada.remover(arquivo);
            raizFixada.confirmar();
        } catch (NoSuchFileException ignored) {
            raizFixada.confirmar();
        } catch (IOException | RuntimeException e) {
            falha.addSuppressed(e);
        }
    }

    private final class ArquivoResource extends AbstractResource {
        private final Path arquivo;
        private final long tamanho;

        private ArquivoResource(Path arquivo, long tamanho) {
            this.arquivo = arquivo;
            this.tamanho = tamanho;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            RaizArmazenamentoSegura.Fixacao raizFixada = null;
            InputStream conteudo = null;
            try {
                raizFixada = root.fixar(false);
                RaizArmazenamentoSegura.Leitura leitura = raizFixada.carregar(arquivo);
                conteudo = leitura.conteudo();
                return new InputStreamFixado(conteudo, raizFixada);
            } catch (IOException | RuntimeException e) {
                fecharSuprimindo(conteudo, e);
                fecharSuprimindo(raizFixada, e);
                throw e;
            }
        }

        @Override
        public long contentLength() {
            return tamanho;
        }

        @Override
        public String getFilename() {
            return arquivo.getFileName().toString();
        }

        @Override
        public String getDescription() {
            return "arquivo armazenado " + getFilename();
        }
    }

    private static final class GravacaoLocal implements Gravacao {
        private final String chave;
        private final Path arquivo;
        private RaizArmazenamentoSegura.Fixacao raizFixada;

        private GravacaoLocal(
                String chave,
                Path arquivo,
                RaizArmazenamentoSegura.Fixacao raizFixada) {
            this.chave = chave;
            this.arquivo = arquivo;
            this.raizFixada = raizFixada;
        }

        @Override
        public String chave() {
            return chave;
        }

        @Override
        public synchronized void validarParaCommit() {
            try {
                exigirAberta().validarVinculo();
            } catch (IOException e) {
                throw new ArmazenamentoException(
                        "A raiz de armazenamento mudou durante a transação.", e);
            }
        }

        @Override
        public synchronized void confirmar() {
            RaizArmazenamentoSegura.Fixacao fixacao = exigirAberta();
            raizFixada = null;
            fixacao.confirmar();
            try {
                fixacao.close();
            } catch (IOException e) {
                throw new ArmazenamentoException(
                        "Não foi possível liberar a raiz de armazenamento.", e);
            }
        }

        @Override
        public synchronized void reverter() {
            RaizArmazenamentoSegura.Fixacao fixacao = exigirAberta();
            raizFixada = null;
            Throwable falha = null;
            try {
                fixacao.remover(arquivo);
                fixacao.confirmar();
            } catch (NoSuchFileException ignored) {
                fixacao.confirmar();
            } catch (IOException | RuntimeException e) {
                falha = e;
            }
            try {
                fixacao.close();
            } catch (IOException | RuntimeException e) {
                if (falha == null) falha = e;
                else falha.addSuppressed(e);
            }
            if (falha instanceof RuntimeException e) throw e;
            if (falha != null) {
                throw new ArmazenamentoException(
                        "Não foi possível compensar o arquivo.", falha);
            }
        }

        private RaizArmazenamentoSegura.Fixacao exigirAberta() {
            if (raizFixada == null) {
                throw new IllegalStateException("A gravação já foi concluída.");
            }
            return raizFixada;
        }
    }

    private static final class InputStreamFixado extends FilterInputStream {
        private RaizArmazenamentoSegura.Fixacao raizFixada;

        private InputStreamFixado(
                InputStream conteudo,
                RaizArmazenamentoSegura.Fixacao raizFixada) {
            super(conteudo);
            this.raizFixada = raizFixada;
        }

        @Override
        public void close() throws IOException {
            if (raizFixada == null) return;
            Throwable falha = null;
            try {
                super.close();
            } catch (Throwable e) {
                falha = e;
            }
            try {
                raizFixada.close();
            } catch (Throwable e) {
                if (falha == null) falha = e;
                else falha.addSuppressed(e);
            } finally {
                raizFixada = null;
            }
            if (falha instanceof IOException e) throw e;
            if (falha instanceof RuntimeException e) throw e;
            if (falha instanceof Error e) throw e;
            if (falha != null) throw new IOException(falha);
        }
    }

}
