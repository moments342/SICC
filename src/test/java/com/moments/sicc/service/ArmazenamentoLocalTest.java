package com.moments.sicc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import com.moments.sicc.shared.exception.ArmazenamentoException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class ArmazenamentoLocalTest {

    @TempDir
    private Path temporario;

    @Test
    void remocaoEIdempotente() throws Exception {
        ArmazenamentoLocal storage = new ArmazenamentoLocal(
                temporario.resolve("storage").toString());
        String chave = storage.armazenar(new byte[] {1, 2, 3}, "documentos/1");
        assertThat(storage.carregar(chave).getContentAsByteArray())
                .containsExactly(1, 2, 3);

        storage.remover(chave);
        storage.remover(chave);

        assertThatThrownBy(() -> storage.carregar(chave))
                .isInstanceOf(ArmazenamentoException.class)
                .hasMessage("Arquivo não encontrado.");
    }

    @Test
    void remocaoEIdempotenteQuandoDiretorioPaiNaoExiste() throws Exception {
        Path raiz = temporario.resolve("storage");
        Files.createDirectories(raiz);
        ArmazenamentoLocal storage = new ArmazenamentoLocal(raiz.toString());

        storage.remover("documentos/1/arquivo-inexistente");

        assertThat(raiz).isDirectory();
    }

    @Test
    void remocaoRejeitaChaveForaDaRaiz() throws Exception {
        Path externo = temporario.resolve("fora.txt");
        Files.writeString(externo, "preservar");
        ArmazenamentoLocal storage = new ArmazenamentoLocal(
                temporario.resolve("storage").toString());

        assertThatThrownBy(() -> storage.remover("../fora.txt"))
                .isInstanceOf(ArmazenamentoException.class)
                .hasMessage("Chave de armazenamento inválida.");
        assertThat(externo).exists();
    }

    @Test
    void remocaoRejeitaChaveQueRepresentaAPropriaRaiz() {
        ArmazenamentoLocal storage = new ArmazenamentoLocal(
                temporario.resolve("storage").toString());

        assertThatThrownBy(() -> storage.remover("."))
                .isInstanceOf(ArmazenamentoException.class)
                .hasMessage("Chave de armazenamento inválida.");
    }

    @Test
    void remocaoNaoSegueLinkInternoParaArquivoForaDaRaiz() throws Exception {
        Path raiz = temporario.resolve("storage");
        Path diretorioExterno = temporario.resolve("externo");
        Files.createDirectories(raiz);
        Files.createDirectories(diretorioExterno);
        Path arquivoExterno = Files.writeString(
                diretorioExterno.resolve("preservar.txt"), "preservar");
        try {
            Files.createSymbolicLink(raiz.resolve("atalho"), diretorioExterno);
        } catch (UnsupportedOperationException | IOException e) {
            Assumptions.assumeTrue(false,
                    "O sistema de arquivos não permite criar link simbólico: " + e.getMessage());
        }
        ArmazenamentoLocal storage = new ArmazenamentoLocal(raiz.toString());

        assertThatThrownBy(() -> storage.remover("atalho/preservar.txt"))
                .isInstanceOf(ArmazenamentoException.class)
                .hasMessage("Chave de armazenamento inválida.");
        assertThat(arquivoExterno).exists();
    }

    @Test
    void armazenamentoNaoSegueRaizConfiguradaComoLinkParaDiretorioExterno() throws Exception {
        Path raiz = temporario.resolve("storage-gravacao-link");
        Path diretorioExterno = temporario.resolve("externo-gravacao-link");
        Files.createDirectories(diretorioExterno);
        try {
            Files.createSymbolicLink(raiz, diretorioExterno);
        } catch (UnsupportedOperationException | IOException e) {
            Assumptions.assumeTrue(false,
                    "O sistema de arquivos não permite criar link simbólico: " + e.getMessage());
        }
        ArmazenamentoLocal storage = new ArmazenamentoLocal(raiz.toString());

        assertThatThrownBy(() -> storage.armazenar(
                new byte[] {1, 2, 3}, "documentos/1"))
                .isInstanceOf(ArmazenamentoException.class)
                .hasMessage("Raiz de armazenamento inválida.");
        assertThat(diretorioExterno).isEmptyDirectory();
    }

    @Test
    void carregamentoNaoSegueRaizConfiguradaComoLinkParaArquivoExterno() throws Exception {
        Path raiz = temporario.resolve("storage-leitura-link");
        Path diretorioExterno = temporario.resolve("externo-leitura-link");
        Path arquivoExterno = Files.createDirectories(
                diretorioExterno.resolve("documentos/1"))
                .resolve("preservar.txt");
        Files.writeString(arquivoExterno, "segredo externo");
        try {
            Files.createSymbolicLink(raiz, diretorioExterno);
        } catch (UnsupportedOperationException | IOException e) {
            Assumptions.assumeTrue(false,
                    "O sistema de arquivos não permite criar link simbólico: " + e.getMessage());
        }
        ArmazenamentoLocal storage = new ArmazenamentoLocal(raiz.toString());

        assertThatThrownBy(() -> storage.carregar("documentos/1/preservar.txt"))
                .isInstanceOf(ArmazenamentoException.class)
                .hasMessage("Raiz de armazenamento inválida.");
        assertThat(arquivoExterno).hasContent("segredo externo");
    }

    @Test
    void remocaoRejeitaRaizConfiguradaComoLinkParaDiretorioExterno() throws Exception {
        Path raiz = temporario.resolve("storage");
        Path diretorioExterno = temporario.resolve("externo-raiz");
        Files.createDirectories(diretorioExterno);
        Path arquivoExterno = Files.writeString(
                diretorioExterno.resolve("preservar.txt"), "preservar");
        try {
            Files.createSymbolicLink(raiz, diretorioExterno);
        } catch (UnsupportedOperationException | IOException e) {
            Assumptions.assumeTrue(false,
                    "O sistema de arquivos não permite criar link simbólico: " + e.getMessage());
        }
        ArmazenamentoLocal storage = new ArmazenamentoLocal(raiz.toString());

        assertThatThrownBy(() -> storage.remover("preservar.txt"))
                .isInstanceOf(ArmazenamentoException.class)
                .hasMessage("Raiz de armazenamento inválida.");
        assertThat(arquivoExterno).exists();
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void remocaoRejeitaRaizConfiguradaComoJuncaoParaDiretorioExterno() throws Exception {
        Path raiz = temporario.resolve("storage-junction");
        Path diretorioExterno = temporario.resolve("externo-junction");
        Files.createDirectories(diretorioExterno);
        Path arquivoExterno = Files.writeString(
                diretorioExterno.resolve("preservar.txt"), "preservar");
        criarJuncaoWindows(raiz, diretorioExterno);
        ArmazenamentoLocal storage = new ArmazenamentoLocal(raiz.toString());

        assertThatThrownBy(() -> storage.remover("preservar.txt"))
                .isInstanceOf(ArmazenamentoException.class)
                .hasMessage("Raiz de armazenamento inválida.");
        assertThat(arquivoExterno).exists();
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void armazenamentoNaoSegueRaizConfiguradaComoJuncaoParaDiretorioExterno() throws Exception {
        Path raiz = temporario.resolve("storage-gravacao-junction");
        Path diretorioExterno = temporario.resolve("externo-gravacao-junction");
        Files.createDirectories(diretorioExterno);
        criarJuncaoWindows(raiz, diretorioExterno);
        ArmazenamentoLocal storage = new ArmazenamentoLocal(raiz.toString());

        assertThatThrownBy(() -> storage.armazenar(
                new byte[] {1, 2, 3}, "documentos/1"))
                .isInstanceOf(ArmazenamentoException.class)
                .hasMessage("Raiz de armazenamento inválida.");
        assertThat(diretorioExterno).isEmptyDirectory();
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void carregamentoNaoSegueRaizConfiguradaComoJuncaoParaArquivoExterno() throws Exception {
        Path raiz = temporario.resolve("storage-leitura-junction");
        Path diretorioExterno = temporario.resolve("externo-leitura-junction");
        Path arquivoExterno = Files.createDirectories(
                diretorioExterno.resolve("documentos/1"))
                .resolve("preservar.txt");
        Files.writeString(arquivoExterno, "segredo externo");
        criarJuncaoWindows(raiz, diretorioExterno);
        ArmazenamentoLocal storage = new ArmazenamentoLocal(raiz.toString());

        assertThatThrownBy(() -> storage.carregar("documentos/1/preservar.txt"))
                .isInstanceOf(ArmazenamentoException.class)
                .hasMessage("Raiz de armazenamento inválida.");
        assertThat(arquivoExterno).hasContent("segredo externo");
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void gravacaoTemporariaNaoSegueLinkColocadoAntesDaAbertura() throws Exception {
        Path externo = Files.writeString(
                temporario.resolve("externo-temporario.txt"), "preservar");
        Path temporarioMalicioso = temporario.resolve(".upload-controlado.tmp");
        try {
            Files.createSymbolicLink(temporarioMalicioso, externo);
        } catch (UnsupportedOperationException | IOException e) {
            Assumptions.assumeTrue(false,
                    "O sistema de arquivos não permite criar link simbólico: " + e.getMessage());
        }

        assertThatThrownBy(() -> RaizArmazenamentoSegura
                .escreverArquivoNovoSemSeguirLink(
                        temporarioMalicioso, new byte[] {1, 2, 3}))
                .isInstanceOf(FileAlreadyExistsException.class);
        assertThat(externo).hasContent("preservar");
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void falhaAoFecharRaizCompensaArquivoAntesDePropagar() throws Exception {
        Path raiz = temporario.resolve("storage-fechamento").toAbsolutePath().normalize();
        Files.createDirectories(raiz.resolve("documentos/1"));
        RaizArmazenamentoSegura raizSegura = new RaizArmazenamentoSegura(
                raiz,
                (caminho, criarSeAusente) -> caminho.equals(raiz)
                        ? () -> { throw new IOException("falha controlada no fechamento"); }
                        : () -> { });
        ArmazenamentoLocal storage = new ArmazenamentoLocal(raizSegura);

        assertThatThrownBy(() -> storage.armazenar(
                new byte[] {1, 2, 3}, "documentos/1"))
                .isInstanceOf(ArmazenamentoException.class)
                .hasMessage("Não foi possível armazenar o arquivo.")
                .hasRootCauseMessage("falha controlada no fechamento");
        try (var arquivos = Files.walk(raiz)) {
            assertThat(arquivos.filter(Files::isRegularFile).toList()).isEmpty();
        }
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void mudancaDaRaizDetectadaAntesDoRetornoCompensaArquivo() throws Exception {
        Path raiz = temporario.resolve("storage-validacao").toAbsolutePath().normalize();
        Files.createDirectories(raiz.resolve("documentos/1"));
        AtomicInteger fixacoesDaRaiz = new AtomicInteger();
        RaizArmazenamentoSegura raizSegura = new RaizArmazenamentoSegura(
                raiz,
                (caminho, criarSeAusente) -> {
                    if (caminho.equals(raiz) && fixacoesDaRaiz.incrementAndGet() > 1) {
                        throw new IOException("raiz trocada durante a gravação");
                    }
                    return () -> { };
                });
        ArmazenamentoLocal storage = new ArmazenamentoLocal(raizSegura);

        assertThatThrownBy(() -> storage.armazenar(
                new byte[] {1, 2, 3}, "documentos/1"))
                .isInstanceOf(ArmazenamentoException.class)
                .hasMessage("Não foi possível armazenar o arquivo.")
                .hasRootCauseMessage("raiz trocada durante a gravação");
        try (var arquivos = Files.walk(raiz)) {
            assertThat(arquivos.filter(Files::isRegularFile).toList()).isEmpty();
        }
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void transacaoImpedeSubstituirPaiEArquivoAteRollback() throws Exception {
        Path raiz = temporario.resolve("storage-pais-retidos");
        ArmazenamentoLocal storage = new ArmazenamentoLocal(raiz.toString());
        var gravacao = storage.prepararArmazenamento(new byte[] {1, 2, 3}, "documentos/1");
        Path arquivo = raiz.resolve(gravacao.chave());
        assertThatThrownBy(() -> Files.move(arquivo.getParent(), raiz.resolve("outro-pai")))
                .isInstanceOf(IOException.class);
        assertThatThrownBy(() -> Files.delete(arquivo)).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> Files.write(arquivo, new byte[] {9})).isInstanceOf(IOException.class);
        gravacao.validarParaCommit();
        gravacao.reverter();
        assertThat(arquivo).doesNotExist();
        Files.move(arquivo.getParent(), raiz.resolve("outro-pai"));
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void gravacaoTransacionalMantemFixacaoAteConfirmar() throws Exception {
        Path raiz = temporario.resolve("storage-transacao-commit")
                .toAbsolutePath().normalize();
        Files.createDirectories(raiz.resolve("documentos/1"));
        AtomicInteger fixacoesAtivas = new AtomicInteger();
        RaizArmazenamentoSegura raizSegura = new RaizArmazenamentoSegura(
                raiz,
                (caminho, criarSeAusente) -> {
                    if (!caminho.equals(raiz)) return () -> { };
                    fixacoesAtivas.incrementAndGet();
                    return fixacoesAtivas::decrementAndGet;
                });
        ArmazenamentoLocal storage = new ArmazenamentoLocal(raizSegura);

        ArmazenamentoArquivoTransacional.Gravacao gravacao =
                storage.prepararArmazenamento(
                        new byte[] {1, 2, 3}, "documentos/1");

        assertThat(fixacoesAtivas).hasValue(1);
        assertThat(raiz.resolve(gravacao.chave())).exists();
        gravacao.validarParaCommit();
        assertThat(fixacoesAtivas).hasValue(1);
        gravacao.confirmar();
        assertThat(fixacoesAtivas).hasValue(0);
        assertThat(raiz.resolve(gravacao.chave())).exists();
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void gravacaoTransacionalReverteAntesDeLiberarFixacao() throws Exception {
        Path raiz = temporario.resolve("storage-transacao-rollback")
                .toAbsolutePath().normalize();
        Files.createDirectories(raiz.resolve("documentos/1"));
        AtomicInteger fixacoesAtivas = new AtomicInteger();
        RaizArmazenamentoSegura raizSegura = new RaizArmazenamentoSegura(
                raiz,
                (caminho, criarSeAusente) -> {
                    if (!caminho.equals(raiz)) return () -> { };
                    fixacoesAtivas.incrementAndGet();
                    return fixacoesAtivas::decrementAndGet;
                });
        ArmazenamentoLocal storage = new ArmazenamentoLocal(raizSegura);
        ArmazenamentoArquivoTransacional.Gravacao gravacao =
                storage.prepararArmazenamento(
                        new byte[] {1, 2, 3}, "documentos/1");

        gravacao.reverter();

        assertThat(fixacoesAtivas).hasValue(0);
        assertThat(raiz.resolve(gravacao.chave())).doesNotExist();
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void falhaAoLiberarFixacaoDepoisDoCommitNaoApagaArquivoConfirmado() throws Exception {
        Path raiz = temporario.resolve("storage-transacao-fechamento")
                .toAbsolutePath().normalize();
        Files.createDirectories(raiz.resolve("documentos/1"));
        RaizArmazenamentoSegura raizSegura = new RaizArmazenamentoSegura(
                raiz,
                (caminho, criarSeAusente) -> caminho.equals(raiz)
                        ? () -> { throw new IOException("falha ao liberar raiz confirmada"); }
                        : () -> { });
        ArmazenamentoLocal storage = new ArmazenamentoLocal(raizSegura);
        ArmazenamentoArquivoTransacional.Gravacao gravacao =
                storage.prepararArmazenamento(
                        new byte[] {1, 2, 3}, "documentos/1");

        assertThatThrownBy(gravacao::confirmar)
                .isInstanceOf(ArmazenamentoException.class)
                .hasRootCauseMessage("falha ao liberar raiz confirmada");
        assertThat(raiz.resolve(gravacao.chave())).exists();
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void recursoDeLeituraNaoMantemHandleAbertoAntesDoConsumo() throws Exception {
        Path raiz = temporario.resolve("storage-stream").toAbsolutePath().normalize();
        Path arquivo = Files.createDirectories(raiz.resolve("documentos/1"))
                .resolve("arquivo");
        Files.write(arquivo, new byte[] {1, 2, 3});
        AtomicBoolean raizFechada = new AtomicBoolean();
        RaizArmazenamentoSegura raizSegura = new RaizArmazenamentoSegura(
                raiz,
                (caminho, criarSeAusente) -> caminho.equals(raiz)
                        ? () -> raizFechada.set(true)
                        : () -> { });
        ArmazenamentoLocal storage = new ArmazenamentoLocal(raizSegura);

        var recurso = storage.carregar("documentos/1/arquivo");

        assertThat(recurso.isOpen()).isFalse();
        assertThat(raizFechada).isTrue();
        raizFechada.set(false);
        assertThat(recurso.contentLength()).isEqualTo(3);
        assertThat(raizFechada).isFalse();
        try (InputStream conteudo = recurso.getInputStream()) {
            assertThat(conteudo.readAllBytes()).containsExactly(1, 2, 3);
            assertThat(raizFechada).isFalse();
        }
        assertThat(raizFechada).isTrue();
    }

    @Test
    void recursoLazySuportaRequisicaoHttpRange() throws Exception {
        ArmazenamentoLocal storage = new ArmazenamentoLocal(
                temporario.resolve("storage-range").toString());
        String chave = storage.armazenar(
                "0123456789".getBytes(StandardCharsets.UTF_8), "documentos/1");
        Resource recurso = storage.carregar(chave);
        MockMvc mvc = standaloneSetup(new ControladorArquivo(recurso)).build();

        mvc.perform(get("/arquivo").header(HttpHeaders.RANGE, "bytes=2-5"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 2-5/10"))
                .andExpect(content().bytes("2345".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void remocaoNaoSegueJuncaoIntermediariaParaArquivoExterno() throws Exception {
        Path raiz = temporario.resolve("storage-junction-interna");
        Path diretorioExterno = temporario.resolve("externo-junction-interna");
        Files.createDirectories(raiz);
        Files.createDirectories(diretorioExterno);
        Path arquivoExterno = Files.writeString(
                diretorioExterno.resolve("preservar.txt"), "preservar");
        criarJuncaoWindows(raiz.resolve("atalho"), diretorioExterno);
        ArmazenamentoLocal storage = new ArmazenamentoLocal(raiz.toString());

        assertThatThrownBy(() -> storage.remover("atalho/preservar.txt"))
                .isInstanceOf(ArmazenamentoException.class)
                .hasMessage("Chave de armazenamento inválida.");
        assertThat(arquivoExterno).exists();
    }

    private void criarJuncaoWindows(Path juncao, Path destino) throws Exception {
        Process processo = new ProcessBuilder(
                "cmd.exe", "/c", "mklink", "/J", juncao.toString(), destino.toString())
                .redirectErrorStream(true)
                .start();
        String saida = new String(processo.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int status = processo.waitFor();
        Assumptions.assumeTrue(status == 0,
                "O sistema de arquivos não permite criar junction: " + saida.trim());
    }

    @RestController
    private static final class ControladorArquivo {
        private final Resource recurso;

        private ControladorArquivo(Resource recurso) {
            this.recurso = recurso;
        }

        @GetMapping("/arquivo")
        public ResponseEntity<Resource> arquivo() {
            return ResponseEntity.ok(recurso);
        }
    }
}
