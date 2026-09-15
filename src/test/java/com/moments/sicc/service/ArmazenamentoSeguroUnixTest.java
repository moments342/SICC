package com.moments.sicc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moments.sicc.shared.exception.ArmazenamentoException;
import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ArmazenamentoSeguroUnixTest {
    private static final int EINTR = 4;
    private static final ConfiguracaoUnix LINUX =
            new ConfiguracaoUnix(
                    0, 1, 0x40, 0x80, 0x10000, 0x20000, 0x80000,
                    1030, 2, 17, 20, 40);

    @Test
    void criaRaizPrefixosEArquivoSomentePorDescritoresComPermissoesPrivadas()
            throws Exception {
        SistemaArquivosFalso chamadas = new SistemaArquivosFalso();
        ArmazenamentoSeguroUnix.Sessao sessao = ArmazenamentoSeguroUnix.fixar(
                "/", List.of("srv", "sicc"), true, chamadas, LINUX);

        sessao.armazenar(
                Path.of("documentos", "1", "arquivo"),
                new byte[] {1, 2, 3});

        assertThat(chamadas.modosDiretoriosCriados)
                .containsOnly(0700)
                .hasSize(4);
        assertThat(chamadas.modosArquivosCriados)
                .containsExactly(0600);
        assertThat(chamadas.comandosDuplicacao).containsExactly(1030);
        assertThat(chamadas.caminhosAbertos).containsExactly("/");
        assertThat(chamadas.nomesRelativos)
                .allMatch(nome -> !nome.contains("/")
                        && !nome.contains("\\")
                        && !nome.equals(".."));

        try (InputStream conteudo = sessao.carregar(
                Path.of("documentos", "1", "arquivo")).conteudo()) {
            assertThat(chamadas.quantidadeLeituras).isZero();
            assertThat(conteudo.readAllBytes()).containsExactly(1, 2, 3);
        }
        sessao.close();
        assertThat(chamadas.quantidadeDescritoresAbertos()).isZero();
    }

    @Test
    void repeteLeituraEGravacaoQuandoLibcRetornaEintr() throws Exception {
        SistemaArquivosFalso chamadas = new SistemaArquivosFalso();
        chamadas.interromperPrimeiraEscrita = true;
        ArmazenamentoSeguroUnix.Sessao sessao = ArmazenamentoSeguroUnix.fixar(
                "/", List.of("srv", "sicc"), true, chamadas, LINUX);

        sessao.armazenar(Path.of("arquivo"), new byte[] {4, 5, 6});
        chamadas.interromperPrimeiraLeitura = true;

        try (InputStream conteudo = sessao.carregar(Path.of("arquivo")).conteudo()) {
            assertThat(conteudo.readAllBytes()).containsExactly(4, 5, 6);
        }
        sessao.close();
    }

    @Test
    void publicacaoNuncaSubstituiDestinoPreexistente() throws Exception {
        SistemaArquivosFalso chamadas = new SistemaArquivosFalso();
        ArmazenamentoSeguroUnix.Sessao primeiraSessao = ArmazenamentoSeguroUnix.fixar(
                "/", List.of("srv", "sicc"), true, chamadas, LINUX);
        primeiraSessao.armazenar(Path.of("arquivo"), new byte[] {1, 2, 3});
        primeiraSessao.close();
        ArmazenamentoSeguroUnix.Sessao segundaSessao = ArmazenamentoSeguroUnix.fixar(
                "/", List.of("srv", "sicc"), false, chamadas, LINUX);

        assertThatThrownBy(() -> segundaSessao.armazenar(
                Path.of("arquivo"), new byte[] {9, 8, 7}))
                .isInstanceOf(FileAlreadyExistsException.class);
        try (InputStream conteudo = segundaSessao.carregar(Path.of("arquivo")).conteudo()) {
            assertThat(conteudo.readAllBytes()).containsExactly(1, 2, 3);
        }
        segundaSessao.close();
        assertThat(chamadas.quantidadeDescritoresAbertos()).isZero();
    }

    @Test
    void falhaAoFecharAncestralDuranteFixacaoNaoVazaAPropriaRaizFixada() {
        SistemaArquivosFalso chamadas = new SistemaArquivosFalso();
        chamadas.falharProximoFechamento = true;

        assertThatThrownBy(() -> ArmazenamentoSeguroUnix.fixar(
                "/", List.of("srv", "sicc"), true, chamadas, LINUX))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("fechar descritor");
        assertThat(chamadas.quantidadeDescritoresAbertos()).isZero();
    }

    @Test
    void detectaTrocaDaRaizECompensaPeloDescritorOriginal() throws Exception {
        SistemaArquivosFalso chamadas = new SistemaArquivosFalso();
        ArmazenamentoSeguroUnix.Sessao sessao = ArmazenamentoSeguroUnix.fixar(
                "/", List.of("srv", "sicc"), true, chamadas, LINUX);
        sessao.armazenar(Path.of("arquivo"), new byte[] {1, 2, 3});
        No raizOriginal = chamadas.substituirDiretorio("srv/sicc");

        assertThatThrownBy(sessao::validarVinculo)
                .isInstanceOf(ArmazenamentoException.class)
                .hasMessage("Raiz de armazenamento inválida.");
        sessao.remover(Path.of("arquivo"));
        sessao.confirmar();
        sessao.close();

        assertThat(raizOriginal.filhos).doesNotContainKey("arquivo");
        assertThat(chamadas.existe("srv/sicc/arquivo")).isFalse();
        assertThat(chamadas.quantidadeDescritoresAbertos()).isZero();
    }

    @Test
    void trocaDoPaiImpedeCommitECompensaSomenteNoDiretorioOriginal() throws Exception {
        SistemaArquivosFalso chamadas = new SistemaArquivosFalso();
        var sessao = ArmazenamentoSeguroUnix.fixar(
                "/", List.of("srv", "sicc"), true, chamadas, LINUX);
        Path chave = Path.of("documentos", "1", "arquivo");
        sessao.armazenar(chave, new byte[] {1, 2, 3});
        No original = chamadas.substituirDiretorio("srv/sicc/documentos/1");
        No substituto = new No(false, 0600);
        chamadas.no("srv/sicc/documentos/1").filhos.put("arquivo", substituto);

        assertThatThrownBy(sessao::validarVinculo)
                .isInstanceOf(ArmazenamentoException.class);
        sessao.remover(chave);
        sessao.confirmar();
        sessao.close();

        assertThat(original.filhos).doesNotContainKey("arquivo");
        assertThat(chamadas.no("srv/sicc/documentos/1/arquivo")).isSameAs(substituto);
        assertThat(chamadas.quantidadeDescritoresAbertos()).isZero();
    }

    @Test
    void folhaSubstituidaNaoPodeSerConfirmadaNemRemovidaComoCompensacao() throws Exception {
        SistemaArquivosFalso chamadas = new SistemaArquivosFalso();
        var sessao = ArmazenamentoSeguroUnix.fixar(
                "/", List.of("srv", "sicc"), true, chamadas, LINUX);
        Path chave = Path.of("documentos", "1", "arquivo");
        sessao.armazenar(chave, new byte[] {1, 2, 3});
        No substituto = new No(false, 0600);
        chamadas.no("srv/sicc/documentos/1").filhos.put("arquivo", substituto);

        assertThatThrownBy(sessao::validarVinculo)
                .isInstanceOf(ArmazenamentoException.class);
        assertThatThrownBy(() -> sessao.remover(chave))
                .isInstanceOf(ArmazenamentoException.class);
        sessao.close();

        assertThat(chamadas.no("srv/sicc/documentos/1/arquivo")).isSameAs(substituto);
        assertThat(chamadas.quantidadeDescritoresAbertos()).isZero();
    }

    @Test
    void falhaAoFecharDiretorioDepoisDaPublicacaoCompensaArquivo() throws Exception {
        SistemaArquivosFalso chamadas = new SistemaArquivosFalso();
        ArmazenamentoSeguroUnix.Sessao sessao = ArmazenamentoSeguroUnix.fixar(
                "/", List.of("srv", "sicc"), true, chamadas, LINUX);
        chamadas.falharDiretorioDepoisDaPublicacao = true;

        assertThatThrownBy(() -> sessao.armazenar(
                Path.of("documentos", "1", "arquivo"),
                new byte[] {1, 2, 3}))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("fechar descritor");
        assertThat(chamadas.existe("srv/sicc/documentos/1/arquivo")).isFalse();
        sessao.close();
    }

    @Test
    void falhaAoFecharRaizDepoisDaPublicacaoCompensaArquivoPelaReserva() throws Exception {
        SistemaArquivosFalso chamadas = new SistemaArquivosFalso();
        ArmazenamentoSeguroUnix.Sessao sessao = ArmazenamentoSeguroUnix.fixar(
                "/", List.of("srv", "sicc"), true, chamadas, LINUX);
        sessao.armazenar(Path.of("arquivo"), new byte[] {1, 2, 3});
        chamadas.falharAoFechar(chamadas.descritorPrincipalDaRaiz("srv/sicc"));

        assertThatThrownBy(sessao::close)
                .isInstanceOf(IOException.class)
                .hasMessageContaining("fechar raiz");
        assertThat(chamadas.existe("srv/sicc/arquivo")).isFalse();
        assertThat(chamadas.quantidadeDescritoresAbertos()).isZero();
    }

    @Test
    void falhaAoLiberarReservaDepoisDoCommitNaoTransformaSucessoEmOrfao() throws Exception {
        SistemaArquivosFalso chamadas = new SistemaArquivosFalso();
        ArmazenamentoSeguroUnix.Sessao sessao = ArmazenamentoSeguroUnix.fixar(
                "/", List.of("srv", "sicc"), true, chamadas, LINUX);
        sessao.armazenar(Path.of("arquivo"), new byte[] {1, 2, 3});
        chamadas.falharAoFechar(chamadas.descritorReservaDaRaiz("srv/sicc"));

        assertThatCode(sessao::close).doesNotThrowAnyException();
        assertThat(chamadas.existe("srv/sicc/arquivo")).isTrue();
        assertThat(chamadas.quantidadeDescritoresAbertos()).isZero();
    }

    @Test
    void falhaFechadaQuandoArquiteturaUnixNaoTemConstantesValidadas() {
        assertThatThrownBy(() -> ConfiguracaoUnix.para(
                "Linux", "mips64el"))
                .isInstanceOf(IOException.class)
                .hasMessage("O sistema Unix não possui adaptador seguro de armazenamento.");
    }

    @Test
    void interpretaLayoutsNativosValidadosSemDependerDoSistemaDoTeste() {
        try (Memory linux = new Memory(256); Memory mac = new Memory(144)) {
            linux.clear();
            linux.setLong(32, 1234L);
            linux.setLong(40, 5678L);
            linux.setInt(136, 9);
            linux.setInt(140, 10);
            mac.clear();
            mac.setInt(0, 11);
            mac.setLong(8, 4321L);
            mac.setLong(96, 8765L);

            assertThat(ChamadasNativasUnix.interpretarAtributosLinux(linux))
                    .isEqualTo(new ArmazenamentoSeguroUnix.AtributosDescritor(
                            (9L << 32) | 10L, 1234L, 5678L));
            assertThat(ChamadasNativasUnix.interpretarAtributosMac(mac))
                    .isEqualTo(new ArmazenamentoSeguroUnix.AtributosDescritor(
                            11L, 4321L, 8765L));
            assertThat(ConfiguracaoUnix.para(
                    "Mac OS X", "aarch64").plataforma())
                    .isEqualTo(ConfiguracaoUnix.Plataforma.MACOS);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void leituraInvalidaNaoAvancaDescritorAntesDeLancarExcecao() throws Exception {
        SistemaArquivosFalso chamadas = new SistemaArquivosFalso();
        ArmazenamentoSeguroUnix.Sessao sessao = ArmazenamentoSeguroUnix.fixar(
                "/", List.of("srv", "sicc"), true, chamadas, LINUX);
        sessao.armazenar(Path.of("arquivo"), new byte[] {1, 2, 3});

        try (InputStream conteudo = sessao.carregar(Path.of("arquivo")).conteudo()) {
            assertThatThrownBy(() -> conteudo.read((byte[]) null, 0, 1))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> conteudo.read(new byte[1], 1, 1))
                    .isInstanceOf(IndexOutOfBoundsException.class);
            assertThatThrownBy(() -> conteudo.read(new byte[1], 0, -1))
                    .isInstanceOf(IndexOutOfBoundsException.class);
            assertThat(chamadas.quantidadeLeituras).isZero();
            assertThat(conteudo.readAllBytes()).containsExactly(1, 2, 3);
        }
        sessao.close();
    }

    private static final class SistemaArquivosFalso
            implements ArmazenamentoSeguroUnix.ChamadasUnix {
        private final No raiz = new No(true, 0755);
        private final Map<Integer, Handle> handles = new LinkedHashMap<>();
        private final List<String> caminhosAbertos = new ArrayList<>();
        private final List<String> nomesRelativos = new ArrayList<>();
        private final List<Integer> modosDiretoriosCriados = new ArrayList<>();
        private final List<Integer> modosArquivosCriados = new ArrayList<>();
        private final List<Integer> comandosDuplicacao = new ArrayList<>();
        private final Map<Integer, Boolean> fechamentosComFalha = new HashMap<>();
        private int proximoDescritor = 10;
        private int erro;
        private int quantidadeLeituras;
        private boolean interromperPrimeiraEscrita;
        private boolean interromperPrimeiraLeitura;
        private boolean falharProximoFechamento;
        private boolean falharDiretorioDepoisDaPublicacao;
        private boolean publicado;

        @Override
        public int open(String caminho, int flags, int modo) {
            caminhosAbertos.add(caminho);
            if (!"/".equals(caminho)) return falhar(LINUX.enoent());
            return abrir(raiz);
        }

        @Override
        public int openat(int diretorio, String nome, int flags, int modo) {
            nomesRelativos.add(nome);
            Handle pai = handles.get(diretorio);
            if (pai == null || !pai.no.diretorio) return falhar(LINUX.enotdir());
            No existente = pai.no.filhos.get(nome);
            if ((flags & LINUX.oCreat()) != 0) {
                if (existente != null) return falhar(LINUX.eexist());
                No arquivo = new No(false, modo);
                pai.no.filhos.put(nome, arquivo);
                modosArquivosCriados.add(modo);
                return abrir(arquivo);
            }
            if (existente == null) return falhar(LINUX.enoent());
            if ((flags & LINUX.oDirectory()) != 0 && !existente.diretorio) {
                return falhar(LINUX.enotdir());
            }
            return abrir(existente);
        }

        @Override
        public int mkdirat(int diretorio, String nome, int modo) {
            nomesRelativos.add(nome);
            Handle pai = handles.get(diretorio);
            if (pai == null || !pai.no.diretorio) return falhar(LINUX.enotdir());
            if (pai.no.filhos.containsKey(nome)) return falhar(LINUX.eexist());
            pai.no.filhos.put(nome, new No(true, modo));
            modosDiretoriosCriados.add(modo);
            return 0;
        }

        @Override
        public int publicarSemSubstituir(
                int diretorioOrigem,
                String origem,
                int diretorioDestino,
                String destino,
                ConfiguracaoUnix configuracao) {
            nomesRelativos.add(origem);
            nomesRelativos.add(destino);
            Handle paiOrigem = handles.get(diretorioOrigem);
            Handle paiDestino = handles.get(diretorioDestino);
            if (paiOrigem == null || paiDestino == null) return falhar(LINUX.enotdir());
            if (paiDestino.no.filhos.containsKey(destino)) return falhar(LINUX.eexist());
            No arquivo = paiOrigem.no.filhos.remove(origem);
            if (arquivo == null) return falhar(LINUX.enoent());
            paiDestino.no.filhos.put(destino, arquivo);
            publicado = true;
            return 0;
        }

        @Override
        public int unlinkat(int diretorio, String nome, int flags) {
            nomesRelativos.add(nome);
            Handle pai = handles.get(diretorio);
            if (pai == null || !pai.no.diretorio) return falhar(LINUX.enotdir());
            if (pai.no.filhos.remove(nome) == null) return falhar(LINUX.enoent());
            return 0;
        }

        @Override
        public int duplicarCloexec(int descritor, int comando) {
            Handle original = handles.get(descritor);
            if (original == null) return falhar(9);
            comandosDuplicacao.add(comando);
            return abrir(original.no);
        }

        @Override
        public long read(int descritor, Pointer destino, long quantidade) {
            quantidadeLeituras++;
            if (interromperPrimeiraLeitura) {
                interromperPrimeiraLeitura = false;
                erro = EINTR;
                return -1;
            }
            Handle handle = handles.get(descritor);
            if (handle == null || handle.no.diretorio) return falharLongo(9);
            int restante = handle.no.conteudo.length - handle.posicao;
            if (restante == 0) return 0;
            int lidos = Math.min(restante, Math.toIntExact(quantidade));
            destino.write(0, handle.no.conteudo, handle.posicao, lidos);
            handle.posicao += lidos;
            return lidos;
        }

        @Override
        public long write(int descritor, Pointer origem, long quantidade) {
            if (interromperPrimeiraEscrita) {
                interromperPrimeiraEscrita = false;
                erro = EINTR;
                return -1;
            }
            Handle handle = handles.get(descritor);
            if (handle == null || handle.no.diretorio) return falharLongo(9);
            byte[] novos = origem.getByteArray(0, Math.toIntExact(quantidade));
            int fim = handle.posicao + novos.length;
            handle.no.conteudo = Arrays.copyOf(handle.no.conteudo, fim);
            System.arraycopy(novos, 0, handle.no.conteudo, handle.posicao, novos.length);
            handle.posicao = fim;
            return novos.length;
        }

        @Override
        public int close(int descritor) {
            Handle removido = handles.remove(descritor);
            boolean falhar = Boolean.TRUE.equals(fechamentosComFalha.remove(descritor));
            if (falharProximoFechamento) {
                falharProximoFechamento = false;
                falhar = true;
            }
            if (falharDiretorioDepoisDaPublicacao
                    && publicado
                    && removido != null
                    && removido.no.diretorio
                    && removido.no != no("srv/sicc")) {
                falharDiretorioDepoisDaPublicacao = false;
                falhar = true;
            }
            if (falhar) return falhar(5);
            return removido == null ? falhar(9) : 0;
        }

        @Override
        public ArmazenamentoSeguroUnix.AtributosDescritor atributos(
                int descritor,
                ConfiguracaoUnix configuracao) throws IOException {
            Handle handle = handles.get(descritor);
            if (handle == null) throw new IOException("descritor inválido");
            return new ArmazenamentoSeguroUnix.AtributosDescritor(
                    1,
                    handle.no.inode,
                    handle.no.diretorio ? 0 : handle.no.conteudo.length);
        }

        @Override
        public int erro() {
            return erro;
        }

        private int abrir(No no) {
            int descritor = proximoDescritor++;
            handles.put(descritor, new Handle(no));
            return descritor;
        }

        private int falhar(int codigo) {
            erro = codigo;
            return -1;
        }

        private long falharLongo(int codigo) {
            erro = codigo;
            return -1;
        }

        private void falharAoFechar(int descritor) {
            fechamentosComFalha.put(descritor, true);
        }

        private int quantidadeDescritoresAbertos() {
            return handles.size();
        }

        private int descritorPrincipalDaRaiz(String caminho) {
            No alvo = no(caminho);
            return handles.entrySet().stream()
                    .filter(entry -> entry.getValue().no == alvo)
                    .mapToInt(Map.Entry::getKey)
                    .min()
                    .orElseThrow();
        }

        private int descritorReservaDaRaiz(String caminho) {
            No alvo = no(caminho);
            return handles.entrySet().stream()
                    .filter(entry -> entry.getValue().no == alvo)
                    .mapToInt(Map.Entry::getKey)
                    .max()
                    .orElseThrow();
        }

        private boolean existe(String caminho) {
            return no(caminho) != null;
        }

        private No substituirDiretorio(String caminho) {
            int separador = caminho.lastIndexOf('/');
            No pai = no(caminho.substring(0, separador));
            String nome = caminho.substring(separador + 1);
            No anterior = pai.filhos.put(nome, new No(true, 0700));
            if (anterior == null) throw new IllegalStateException("diretório ausente");
            return anterior;
        }

        private No no(String caminho) {
            No atual = raiz;
            for (String nome : caminho.split("/")) {
                if (nome.isEmpty()) continue;
                atual = atual.filhos.get(nome);
                if (atual == null) return null;
            }
            return atual;
        }
    }

    private static final class Handle {
        private final No no;
        private int posicao;

        private Handle(No no) {
            this.no = no;
        }
    }

    private static final class No {
        private static long proximoInode;

        private final boolean diretorio;
        private final long inode = ++proximoInode;
        @SuppressWarnings("unused")
        private final int modo;
        private final Map<String, No> filhos = new LinkedHashMap<>();
        private byte[] conteudo = new byte[0];

        private No(boolean diretorio, int modo) {
            this.diretorio = diretorio;
            this.modo = modo;
        }
    }
}
