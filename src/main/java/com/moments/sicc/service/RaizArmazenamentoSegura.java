package com.moments.sicc.service;

import com.moments.sicc.shared.exception.ArmazenamentoException;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

final class RaizArmazenamentoSegura {
    private final Path caminho;
    private final FixadorCaminho fixadorCaminho;

    RaizArmazenamentoSegura(Path caminho) {
        this(caminho, ExclusaoSeguraWindows::fixarRaiz);
    }

    RaizArmazenamentoSegura(Path caminho, FixadorCaminho fixadorCaminho) {
        this.caminho = caminho.toAbsolutePath().normalize();
        this.fixadorCaminho = fixadorCaminho;
    }

    Path caminho() {
        return caminho;
    }

    Fixacao fixar(boolean criarSeAusente) throws IOException {
        if (ehWindowsPadrao()) {
            try {
                return new FixacaoPorCaminho(
                        fixadorCaminho.fixar(caminho, criarSeAusente));
            } catch (LinkageError e) {
                throw new IOException("A validação segura do Windows está indisponível.", e);
            }
        }
        if (ehUnixPadrao()) {
            try {
                return new FixacaoUnixNativa(
                        ArmazenamentoSeguroUnix.fixar(caminho, criarSeAusente));
            } catch (LinkageError e) {
                throw new IOException("A fixação segura do Unix está indisponível.", e);
            }
        }
        throw new IOException("O provedor de arquivos não possui adaptador seguro de armazenamento.");
    }

    private boolean ehWindowsPadrao() {
        return caminho.getFileSystem().equals(FileSystems.getDefault())
                && "\\".equals(caminho.getFileSystem().getSeparator());
    }

    private boolean ehUnixPadrao() {
        return caminho.getFileSystem().equals(FileSystems.getDefault())
                && "/".equals(caminho.getFileSystem().getSeparator());
    }

    private Path relativizar(Path arquivo) {
        Path normalizado = arquivo.toAbsolutePath().normalize();
        if (normalizado.equals(caminho) || !normalizado.startsWith(caminho)) {
            throw chaveInvalida();
        }
        Path relativo = caminho.relativize(normalizado);
        if (relativo.getNameCount() == 0) throw chaveInvalida();
        return relativo;
    }

    private void moverAtomico(Path origem, Path destino) throws IOException {
        try {
            Files.move(origem, destino, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(origem, destino);
        }
    }

    private void apagarTemporario(Path temporario, Throwable falha) throws IOException {
        if (temporario == null) return;
        try {
            Files.deleteIfExists(temporario);
        } catch (IOException e) {
            if (falha != null) falha.addSuppressed(e);
            else throw e;
        }
    }

    private void fecharSuprimindo(AutoCloseable closeable, Throwable falha) {
        try {
            closeable.close();
        } catch (Exception e) {
            falha.addSuppressed(e);
        }
    }

    private ArmazenamentoException chaveInvalida() {
        return new ArmazenamentoException("Chave de armazenamento inválida.");
    }

    interface Fixacao extends AutoCloseable {
        void armazenar(Path arquivo, byte[] conteudo) throws IOException;

        Leitura carregar(Path arquivo) throws IOException;

        void remover(Path arquivo) throws IOException;

        void validarVinculo() throws IOException;

        void confirmar();

        @Override
        void close() throws IOException;
    }

    record Leitura(InputStream conteudo, long tamanho) {
        Leitura {
            Objects.requireNonNull(conteudo);
            if (tamanho < 0) throw new IllegalArgumentException("Tamanho inválido.");
        }
    }

    private final class FixacaoPorCaminho implements Fixacao {
        private final Closeable pinagem;
        private Path arquivoCriado;
        private Closeable paisFixados;
        private ExclusaoSeguraWindows.ArquivoFixado arquivoFixado;

        private FixacaoPorCaminho(Closeable pinagem) {
            this.pinagem = pinagem;
        }

        @Override
        public void armazenar(Path arquivo, byte[] conteudo) throws IOException {
            relativizar(arquivo);
            Path temporario = null;
            Throwable falha = null;
            try {
                paisFixados = fixadorCaminho.fixar(arquivo.getParent(), true);
                temporario = arquivo.getParent().resolve(
                        ".upload-" + UUID.randomUUID() + ".tmp");
                escreverArquivoNovoSemSeguirLink(temporario, conteudo);
                moverAtomico(temporario, arquivo);
                arquivoCriado = arquivo;
                arquivoFixado = ExclusaoSeguraWindows.fixarArquivo(caminho, arquivo);
                temporario = null;
            } catch (IOException | RuntimeException e) {
                falha = e;
                compensarArquivoCriado(e);
                throw e;
            } finally {
                apagarTemporario(temporario, falha);
            }
        }

        @Override
        public Leitura carregar(Path arquivo) throws IOException {
            relativizar(arquivo);
            InputStream conteudo = null;
            try (Closeable paisFixados = fixadorCaminho.fixar(
                    arquivo.getParent(), false)) {
                Set<OpenOption> opcoes = Set.of(
                        StandardOpenOption.READ,
                        LinkOption.NOFOLLOW_LINKS);
                SeekableByteChannel canal = Files.newByteChannel(arquivo, opcoes);
                conteudo = Channels.newInputStream(canal);
                return new Leitura(conteudo, canal.size());
            } catch (IOException | RuntimeException e) {
                fecharStreamSuprimindo(conteudo, e);
                throw e;
            }
        }

        @Override
        public void remover(Path arquivo) throws IOException {
            relativizar(arquivo);
            if (arquivo.equals(arquivoCriado) && arquivoFixado != null) {
                arquivoFixado.remover();
            } else {
                ExclusaoSeguraArquivo.remover(caminho, arquivo);
            }
        }

        @Override
        public void validarVinculo() throws IOException {
            // O handle original do Windows é aberto sem FILE_SHARE_DELETE e mantém
            // cada componente da raiz imóvel. A nova abertura também revalida os
            // atributos sem seguir reparse points.
            try (Closeable ignorado = fixadorCaminho.fixar(caminho, false)) {
                // A validação acontece durante a abertura.
            }
        }

        @Override
        public void confirmar() {
            arquivoCriado = null;
        }

        @Override
        public void close() throws IOException {
            Throwable falha = null;
            // A folha permanece fixada para compensar falhas ao fechar os diretórios.
            try { pinagem.close(); } catch (IOException | RuntimeException e) {
                falha = e;
            }
            try {
                if (paisFixados != null) paisFixados.close();
            } catch (IOException | RuntimeException e) {
                if (falha == null) falha = e;
                else falha.addSuppressed(e);
            }
            if (falha != null) compensarArquivoCriado(falha);
            if (arquivoFixado != null) {
                try { arquivoFixado.close(); } catch (IOException e) {
                    if (falha == null) falha = e;
                    else falha.addSuppressed(e);
                }
            }
            arquivoFixado = null;
            paisFixados = null;
            if (falha instanceof RuntimeException e) throw e;
            if (falha != null) throw new IOException("Falha ao liberar armazenamento fixado.", falha);
        }

        private void compensarArquivoCriado(Throwable falha) {
            if (arquivoCriado == null) return;
            try {
                if (arquivoFixado != null) arquivoFixado.remover();
                else ExclusaoSeguraArquivo.remover(caminho, arquivoCriado);
                arquivoCriado = null;
            } catch (NoSuchFileException ignored) {
                arquivoCriado = null;
            } catch (IOException | RuntimeException e) {
                falha.addSuppressed(e);
            }
        }
    }

    static void escreverArquivoNovoSemSeguirLink(
            Path arquivo,
            byte[] conteudo) throws IOException {
        Set<OpenOption> opcoes = Set.of(
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE,
                LinkOption.NOFOLLOW_LINKS);
        try (SeekableByteChannel canal = Files.newByteChannel(arquivo, opcoes)) {
            ByteBuffer restante = ByteBuffer.wrap(conteudo);
            while (restante.hasRemaining()) canal.write(restante);
        }
    }

    @FunctionalInterface
    interface FixadorCaminho {
        Closeable fixar(Path caminho, boolean criarSeAusente) throws IOException;
    }

    private final class FixacaoUnixNativa implements Fixacao {
        private final ArmazenamentoSeguroUnix.Sessao sessao;

        private FixacaoUnixNativa(ArmazenamentoSeguroUnix.Sessao sessao) {
            this.sessao = sessao;
        }

        @Override
        public void armazenar(Path arquivo, byte[] conteudo) throws IOException {
            sessao.armazenar(relativizar(arquivo), conteudo);
        }

        @Override
        public Leitura carregar(Path arquivo) throws IOException {
            return sessao.carregar(relativizar(arquivo));
        }

        @Override
        public void remover(Path arquivo) throws IOException {
            sessao.remover(relativizar(arquivo));
        }

        @Override
        public void validarVinculo() throws IOException {
            sessao.validarVinculo();
        }

        @Override
        public void confirmar() {
            sessao.confirmar();
        }

        @Override
        public void close() throws IOException {
            sessao.close();
        }
    }

    private static void fecharStreamSuprimindo(InputStream stream, Throwable falha) {
        if (stream == null) return;
        try {
            stream.close();
        } catch (IOException | RuntimeException e) {
            falha.addSuppressed(e);
        }
    }
}
