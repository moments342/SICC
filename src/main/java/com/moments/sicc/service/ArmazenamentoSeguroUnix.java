package com.moments.sicc.service;

import com.moments.sicc.shared.exception.ArmazenamentoException;
import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

final class ArmazenamentoSeguroUnix {
    private static final System.Logger LOG = System.getLogger(
            ArmazenamentoSeguroUnix.class.getName());
    private static final int ERRO_CHAMADA_INTERROMPIDA = 4;
    private static final int MODO_DIRETORIO_PRIVADO = 0700;
    private static final int MODO_ARQUIVO_PRIVADO = 0600;

    private ArmazenamentoSeguroUnix() {
    }

    static Sessao fixar(Path raiz, boolean criarSeAusente) throws IOException {
        ConfiguracaoUnix configuracao = ConfiguracaoUnix.atual();
        return fixar(
                raiz.getRoot().toString(),
                nomes(raiz),
                criarSeAusente,
                new ChamadasNativasUnix(),
                configuracao);
    }

    static Sessao fixar(
            String raizDoSistema,
            List<String> componentes,
            boolean criarSeAusente,
            ChamadasUnix chamadas,
            ConfiguracaoUnix configuracao) throws IOException {
        int raizFixada = abrirRaizFixada(
                raizDoSistema, componentes, criarSeAusente, chamadas, configuracao);
        try {
            AtributosDescritor identidade = chamadas.atributos(
                    raizFixada, configuracao);
            return new SessaoNativa(
                    raizFixada,
                    chamadas,
                    configuracao,
                    raizDoSistema,
                    componentes,
                    identidade);
        } catch (IOException | RuntimeException e) {
            fecharUm(raizFixada, chamadas, e);
            throw e;
        }
    }

    private static int abrirRaizFixada(
            String raizDoSistema,
            List<String> componentes,
            boolean criarSeAusente,
            ChamadasUnix chamadas,
            ConfiguracaoUnix configuracao) throws IOException {
        List<Integer> descritores = new ArrayList<>();
        try {
            int atual = chamadas.open(
                    raizDoSistema, configuracao.flagsDiretorio(), 0);
            if (atual < 0) throw erro("abrir raiz do sistema", chamadas.erro(), null);
            descritores.add(atual);
            for (String componente : componentes) {
                atual = abrirDiretorio(
                        chamadas,
                        configuracao,
                        atual,
                        componente,
                        criarSeAusente,
                        raizInvalida());
                descritores.add(atual);
            }
            int raizFixada = descritores.remove(descritores.size() - 1);
            try {
                fecharTodos(descritores, chamadas, null);
            } catch (IOException | RuntimeException e) {
                fecharUm(raizFixada, chamadas, e);
                throw e;
            }
            return raizFixada;
        } catch (IOException | RuntimeException e) {
            fecharTodos(descritores, chamadas, e);
            throw e;
        }
    }

    private static List<String> nomes(Path caminho) {
        List<String> nomes = new ArrayList<>();
        for (Path nome : caminho) nomes.add(nome.toString());
        return nomes;
    }

    private static int abrirDiretorio(
            ChamadasUnix chamadas,
            ConfiguracaoUnix configuracao,
            int pai,
            String nome,
            boolean criar,
            ArmazenamentoException invalida) throws IOException {
        int descritor = chamadas.openat(pai, nome, configuracao.flagsDiretorio(), 0);
        if (descritor >= 0) return descritor;
        int erro = chamadas.erro();
        if (erro == configuracao.enoent() && criar) {
            int resultado = chamadas.mkdirat(pai, nome, MODO_DIRETORIO_PRIVADO);
            if (resultado != 0) {
                int erroCriacao = chamadas.erro();
                if (erroCriacao != configuracao.eexist()) {
                    throw erro("criar diretório " + nome, erroCriacao, nome);
                }
            }
            descritor = chamadas.openat(pai, nome, configuracao.flagsDiretorio(), 0);
            if (descritor >= 0) return descritor;
            erro = chamadas.erro();
        }
        if (erro == configuracao.enoent()) throw new NoSuchFileException(nome);
        if (erro == configuracao.eloop() || erro == configuracao.enotdir()) throw invalida;
        throw erro("abrir diretório " + nome, erro, nome);
    }

    static IOException erro(String operacao, int codigo, String caminho) {
        String alvo = caminho == null ? "" : " " + caminho;
        return new IOException(
                "Falha nativa ao " + operacao + alvo + " (errno " + codigo + ").");
    }

    private static ArmazenamentoException raizInvalida() {
        return new ArmazenamentoException("Raiz de armazenamento inválida.");
    }

    private static ArmazenamentoException chaveInvalida() {
        return new ArmazenamentoException("Chave de armazenamento inválida.");
    }

    private static void fecharTodos(
            List<Integer> descritores,
            ChamadasUnix chamadas,
            Throwable falha) throws IOException {
        IOException falhaFechamento = null;
        for (int indice = descritores.size() - 1; indice >= 0; indice--) {
            int descritor = descritores.get(indice);
            if (chamadas.close(descritor) != 0) {
                IOException atual = erro("fechar descritor", chamadas.erro(), null);
                if (falha != null) falha.addSuppressed(atual);
                else if (falhaFechamento == null) falhaFechamento = atual;
                else falhaFechamento.addSuppressed(atual);
            }
        }
        descritores.clear();
        if (falha == null && falhaFechamento != null) throw falhaFechamento;
    }

    private static void fecharUm(
            int descritor,
            ChamadasUnix chamadas,
            Throwable falha) {
        if (chamadas.close(descritor) != 0) {
            falha.addSuppressed(erro("fechar descritor", chamadas.erro(), null));
        }
    }

    interface Sessao extends Closeable {
        void armazenar(Path relativo, byte[] conteudo) throws IOException;

        RaizArmazenamentoSegura.Leitura carregar(Path relativo) throws IOException;

        void remover(Path relativo) throws IOException;

        void validarVinculo() throws IOException;

        void confirmar();
    }

    interface ChamadasUnix {
        int open(String caminho, int flags, int modo);

        int openat(int diretorio, String nome, int flags, int modo);

        int mkdirat(int diretorio, String nome, int modo);

        int publicarSemSubstituir(
                int diretorioOrigem,
                String origem,
                int diretorioDestino,
                String destino,
                ConfiguracaoUnix configuracao);

        int unlinkat(int diretorio, String nome, int flags);

        int duplicarCloexec(int descritor, int comando);

        long read(int descritor, Pointer destino, long quantidade);

        long write(int descritor, Pointer origem, long quantidade);

        int close(int descritor);

        AtributosDescritor atributos(int descritor, ConfiguracaoUnix configuracao)
                throws IOException;

        int erro();
    }

    record AtributosDescritor(long dispositivo, long inode, long tamanho) {
        AtributosDescritor {
            if (tamanho < 0) throw new IllegalArgumentException("Tamanho inválido.");
        }

        IdentidadeDescritor identidade() {
            return new IdentidadeDescritor(dispositivo, inode);
        }
    }

    record IdentidadeDescritor(long dispositivo, long inode) {
    }

    private static final class SessaoNativa implements Sessao {
        private int raiz;
        private int paiReserva = -1;
        private final ChamadasUnix chamadas;
        private final ConfiguracaoUnix configuracao;
        private final String raizDoSistema;
        private final List<String> componentesRaiz;
        private final IdentidadeDescritor identidadeRaiz;
        private Path arquivoCriado;
        private IdentidadeDescritor identidadeArquivo;

        private SessaoNativa(
                int raiz,
                ChamadasUnix chamadas,
                ConfiguracaoUnix configuracao,
                String raizDoSistema,
                List<String> componentesRaiz,
                AtributosDescritor atributosRaiz) {
            this.raiz = raiz;
            this.chamadas = chamadas;
            this.configuracao = configuracao;
            this.raizDoSistema = raizDoSistema;
            this.componentesRaiz = List.copyOf(componentesRaiz);
            this.identidadeRaiz = atributosRaiz.identidade();
        }

        @Override
        public void armazenar(Path relativo, byte[] conteudo) throws IOException {
            Path seguro = validarRelativo(relativo);
            Throwable falha = null;
            try (DiretorioAberto pai = abrirPai(seguro, true)) {
                String temporario = ".upload-" + UUID.randomUUID() + ".tmp";
                boolean movido = false;
                Throwable falhaTemporario = null;
                try {
                    identidadeArquivo = escreverNovo(pai.descritor(), temporario, conteudo);
                    int publicacao;
                    try {
                        publicacao = chamadas.publicarSemSubstituir(
                                pai.descritor(), temporario,
                                pai.descritor(), seguro.getFileName().toString(),
                                configuracao);
                    } catch (LinkageError e) {
                        throw new IOException(
                                "A publicação atômica sem substituição está indisponível.", e);
                    }
                    if (publicacao != 0) {
                        int codigo = chamadas.erro();
                        if (codigo == configuracao.eexist()) {
                            throw new java.nio.file.FileAlreadyExistsException(
                                    seguro.toString());
                        }
                        throw erro("publicar arquivo", codigo, temporario);
                    }
                    arquivoCriado = seguro;
                    garantirReserva(pai.descritor());
                    movido = true;
                } catch (IOException | RuntimeException e) {
                    falhaTemporario = e;
                    throw e;
                } finally {
                    if (!movido) {
                        desvincularSeExistir(
                                pai.descritor(), temporario, falhaTemporario);
                    }
                }
            } catch (IOException | RuntimeException e) {
                falha = e;
                compensarArquivoCriado(e, raiz);
                throw e;
            } finally {
                if (falha != null && paiReserva >= 0) fecharReservaSuprimindo(falha);
            }
        }

        @Override
        public RaizArmazenamentoSegura.Leitura carregar(Path relativo) throws IOException {
            Path seguro = validarRelativo(relativo);
            InputStream conteudo = null;
            try (DiretorioAberto pai = abrirPai(seguro, false)) {
                int descritor = chamadas.openat(
                        pai.descritor(),
                        seguro.getFileName().toString(),
                        configuracao.flagsLeituraArquivo(),
                        0);
                if (descritor < 0) {
                    int erro = chamadas.erro();
                    if (erro == configuracao.enoent()) {
                        throw new NoSuchFileException(seguro.toString());
                    }
                    if (erro == configuracao.eloop() || erro == configuracao.enotdir()) {
                        throw chaveInvalida();
                    }
                    throw erro("abrir arquivo", erro, seguro.toString());
                }
                long tamanho;
                try {
                    tamanho = chamadas.atributos(descritor, configuracao).tamanho();
                } catch (IOException | RuntimeException e) {
                    fecharUm(descritor, chamadas, e);
                    throw e;
                }
                conteudo = new InputStreamDescritor(descritor, chamadas);
                return new RaizArmazenamentoSegura.Leitura(conteudo, tamanho);
            } catch (IOException | RuntimeException e) {
                fecharStreamSuprimindo(conteudo, e);
                throw e;
            }
        }

        @Override
        public void remover(Path relativo) throws IOException {
            Path seguro = validarRelativo(relativo);
            if (seguro.equals(arquivoCriado) && paiReserva >= 0) {
                removerArquivoCriado(paiReserva);
            } else {
                removerRelativo(raiz, seguro);
            }
        }

        @Override
        public void validarVinculo() throws IOException {
            if (raiz < 0) throw new IOException("A raiz fixada já foi fechada.");
            int atual = abrirRaizFixada(
                    raizDoSistema,
                    componentesRaiz,
                    false,
                    chamadas,
                    configuracao);
            Throwable falha = null;
            try {
                IdentidadeDescritor identidadeAtual = chamadas
                        .atributos(atual, configuracao)
                        .identidade();
                if (!identidadeRaiz.equals(identidadeAtual)) throw raizInvalida();
                if (arquivoCriado != null) {
                    try (DiretorioAberto paiAtual = abrirPai(atual, arquivoCriado, false)) {
                        if (!chamadas.atributos(paiAtual.descritor(), configuracao).identidade()
                                .equals(chamadas.atributos(paiReserva, configuracao).identidade())) {
                            throw chaveInvalida();
                        }
                        validarIdentidadeArquivo(paiAtual.descritor());
                    }
                }
            } catch (IOException | RuntimeException e) {
                falha = e;
                throw e;
            } finally {
                if (falha == null) {
                    if (chamadas.close(atual) != 0) {
                        throw erro("fechar raiz revalidada", chamadas.erro(), null);
                    }
                } else {
                    fecharUm(atual, chamadas, falha);
                }
            }
        }

        @Override
        public void confirmar() {
            arquivoCriado = null;
        }

        private IdentidadeDescritor escreverNovo(int pai, String nome, byte[] conteudo) throws IOException {
            int descritor = chamadas.openat(
                    pai, nome, configuracao.flagsCriacaoArquivo(), MODO_ARQUIVO_PRIVADO);
            if (descritor < 0) throw erro("criar arquivo", chamadas.erro(), nome);
            Throwable falha = null;
            try (Memory memoria = new Memory(Math.max(1, conteudo.length))) {
                if (conteudo.length > 0) memoria.write(0, conteudo, 0, conteudo.length);
                long escritos = 0;
                while (escritos < conteudo.length) {
                    long atual = chamadas.write(
                            descritor,
                            memoria.share(escritos),
                            conteudo.length - escritos);
                    if (atual < 0) {
                        int codigo = chamadas.erro();
                        if (codigo == ERRO_CHAMADA_INTERROMPIDA) continue;
                        throw erro("gravar arquivo", codigo, nome);
                    }
                    if (atual == 0) throw new IOException("A gravação nativa não progrediu.");
                    escritos += atual;
                }
                return chamadas.atributos(descritor, configuracao).identidade();
            } catch (IOException | RuntimeException e) {
                falha = e;
                throw e;
            } finally {
                fecharDescritor(descritor, falha);
            }
        }

        private DiretorioAberto abrirPai(Path relativo, boolean criar) throws IOException {
            return abrirPai(raiz, relativo, criar);
        }

        private void removerRelativo(int raizOperacao, Path relativo) throws IOException {
            try (DiretorioAberto pai = abrirPai(raizOperacao, relativo, false)) {
                int resultado = chamadas.unlinkat(
                        pai.descritor(), relativo.getFileName().toString(), 0);
                if (resultado != 0) {
                    int erro = chamadas.erro();
                    if (erro != configuracao.enoent()) {
                        throw erro("remover arquivo", erro, relativo.toString());
                    }
                }
            }
        }

        private DiretorioAberto abrirPai(
                int raizOperacao,
                Path relativo, boolean criar) throws IOException {
            List<Integer> abertos = new ArrayList<>();
            int atual = raizOperacao;
            try {
                for (int indice = 0; indice < relativo.getNameCount() - 1; indice++) {
                    atual = abrirDiretorio(
                            chamadas,
                            configuracao,
                            atual,
                            relativo.getName(indice).toString(),
                            criar,
                            chaveInvalida());
                    abertos.add(atual);
                }
                return new DiretorioAberto(atual, abertos);
            } catch (IOException | RuntimeException e) {
                fecharTodos(abertos, chamadas, e);
                throw e;
            }
        }

        private void garantirReserva(int pai) throws IOException {
            if (paiReserva >= 0) return;
            paiReserva = chamadas.duplicarCloexec(
                    pai, configuracao.fDupfdCloexec());
            if (paiReserva < 0) {
                IOException falha = erro("duplicar pai", chamadas.erro(), null);
                compensarArquivoCriado(falha, pai);
                throw falha;
            }
        }

        private void compensarArquivoCriado(Throwable falha, int raizOperacao) {
            if (arquivoCriado == null || raizOperacao < 0) return;
            try {
                removerArquivoCriado(paiReserva >= 0 ? paiReserva : raizOperacao);
                arquivoCriado = null;
            } catch (NoSuchFileException ignored) {
                arquivoCriado = null;
            } catch (IOException | RuntimeException e) {
                falha.addSuppressed(e);
            }
        }

        private void validarIdentidadeArquivo(int pai) throws IOException {
            int arquivo = chamadas.openat(pai, arquivoCriado.getFileName().toString(),
                    configuracao.flagsLeituraArquivo(), 0);
            if (arquivo < 0) {
                if (chamadas.erro() == configuracao.enoent()) {
                    throw new NoSuchFileException(arquivoCriado.toString());
                }
                throw chaveInvalida();
            }
            Throwable falha = null;
            try {
                if (!identidadeArquivo.equals(chamadas.atributos(arquivo, configuracao).identidade())) {
                    throw chaveInvalida();
                }
            } catch (IOException | RuntimeException e) {
                falha = e;
                throw e;
            } finally {
                fecharDescritor(arquivo, falha);
            }
        }

        private void removerArquivoCriado(int pai) throws IOException {
            validarIdentidadeArquivo(pai);
            if (chamadas.unlinkat(pai, arquivoCriado.getFileName().toString(), 0) != 0
                    && chamadas.erro() != configuracao.enoent()) {
                throw erro("compensar arquivo", chamadas.erro(), arquivoCriado.toString());
            }
        }

        private void desvincularSeExistir(int pai, String nome, Throwable falha)
                throws IOException {
            if (chamadas.unlinkat(pai, nome, 0) == 0) return;
            int codigo = chamadas.erro();
            if (codigo == configuracao.enoent()) return;
            IOException erro = erro("limpar temporário", codigo, nome);
            if (falha != null) falha.addSuppressed(erro);
            else throw erro;
        }

        private void fecharDescritor(int descritor, Throwable falha) throws IOException {
            if (chamadas.close(descritor) == 0) return;
            IOException erro = erro("fechar arquivo", chamadas.erro(), null);
            if (falha != null) falha.addSuppressed(erro);
            else throw erro;
        }

        private void fecharReservaSuprimindo(Throwable falha) {
            if (paiReserva < 0) return;
            if (chamadas.close(paiReserva) != 0) {
                falha.addSuppressed(erro("fechar pai retido", chamadas.erro(), null));
            }
            paiReserva = -1;
        }

        @Override
        public void close() throws IOException {
            if (raiz < 0) return;
            int principal = raiz;
            raiz = -1;
            if (chamadas.close(principal) != 0) {
                IOException falha = erro("fechar raiz", chamadas.erro(), null);
                int raizCompensacao = paiReserva >= 0 ? paiReserva : principal;
                compensarArquivoCriado(falha, raizCompensacao);
                fecharReservaSuprimindo(falha);
                throw falha;
            }
            arquivoCriado = null;
            fecharReservaAposCommit();
        }

        private void fecharReservaAposCommit() {
            if (paiReserva < 0) return;
            int reserva = paiReserva;
            paiReserva = -1;
            if (chamadas.close(reserva) != 0) {
                // O fechamento do descritor principal é o ponto de commit. A reserva
                // existe apenas para compensar uma falha anterior e não pode converter
                // uma gravação confirmada em erro sem deixar o chamador sem a chave.
                LOG.log(
                        System.Logger.Level.WARNING,
                        "Falha ao liberar descritor Unix de recuperação após commit (errno {0}).",
                        chamadas.erro());
            }
        }

        private Path validarRelativo(Path relativo) {
            Path normalizado = relativo.normalize();
            if (normalizado.isAbsolute()
                    || normalizado.getNameCount() == 0
                    || normalizado.startsWith("..")) {
                throw chaveInvalida();
            }
            return normalizado;
        }

        private final class DiretorioAberto implements Closeable {
            private final int descritor;
            private final List<Integer> abertos;

            private DiretorioAberto(int descritor, List<Integer> abertos) {
                this.descritor = descritor;
                this.abertos = List.copyOf(abertos);
            }

            private int descritor() {
                return descritor;
            }

            @Override
            public void close() throws IOException {
                fecharTodos(new ArrayList<>(abertos), chamadas, null);
            }
        }
    }

    private static final class InputStreamDescritor extends InputStream {
        private int descritor;
        private final ChamadasUnix chamadas;

        private InputStreamDescritor(int descritor, ChamadasUnix chamadas) {
            this.descritor = descritor;
            this.chamadas = chamadas;
        }

        @Override
        public int read() throws IOException {
            byte[] unico = new byte[1];
            int lidos = read(unico, 0, 1);
            return lidos < 0 ? -1 : Byte.toUnsignedInt(unico[0]);
        }

        @Override
        public int read(byte[] destino, int inicio, int quantidade) throws IOException {
            Objects.checkFromIndexSize(inicio, quantidade, destino.length);
            if (quantidade == 0) return 0;
            if (descritor < 0) throw new IOException("Stream fechado.");
            try (Memory memoria = new Memory(quantidade)) {
                long lidos;
                do {
                    lidos = chamadas.read(descritor, memoria, quantidade);
                    if (lidos < 0 && chamadas.erro() != ERRO_CHAMADA_INTERROMPIDA) {
                        throw erro("ler arquivo", chamadas.erro(), null);
                    }
                } while (lidos < 0);
                if (lidos == 0) return -1;
                memoria.read(0, destino, inicio, (int) lidos);
                return (int) lidos;
            }
        }

        @Override
        public void close() throws IOException {
            if (descritor < 0) return;
            int atual = descritor;
            descritor = -1;
            if (chamadas.close(atual) != 0) {
                throw erro("fechar arquivo de leitura", chamadas.erro(), null);
            }
        }
    }

    private static void fecharStreamSuprimindo(InputStream stream, Throwable falha) {
        if (stream == null) return;
        try {
            stream.close();
        } catch (IOException e) {
            falha.addSuppressed(e);
        }
    }
}
