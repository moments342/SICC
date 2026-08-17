package com.moments.sicc.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moments.sicc.repository.DocumentoRepository;
import com.moments.sicc.repository.RegistroAuditoriaRepository;
import com.moments.sicc.repository.VersaoDocumentoRepository;
import com.moments.sicc.service.ArmazenamentoArquivo;
import com.moments.sicc.shared.exception.ArmazenamentoException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;

@ActiveProfiles("test")
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:sicc-documentos-storage-failure;MODE=PostgreSQL")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ArmazenamentoDocumentoApiContractTest extends DocumentoApiContractTestSupport {

    @Autowired
    private DocumentoRepository documentos;
    @Autowired
    private VersaoDocumentoRepository versoes;
    @Autowired
    private RegistroAuditoriaRepository auditoria;

    @MockitoBean
    private ArmazenamentoArquivo storage;

    @Test
    void falhaDeArmazenamentoNaoDeixaMetadadosParciaisEAuditaATentativa() throws Exception {
        String token = tokenAdministradorPermanente();
        long processoId = criarProcesso(token);
        when(storage.armazenar(any(byte[].class), anyString()))
                .thenThrow(new ArmazenamentoException("detalhe interno do disco"));

        MockMultipartFile arquivo = new MockMultipartFile(
                "arquivo",
                "administrativo.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                "%PDF-1.4\n%%EOF".getBytes(StandardCharsets.US_ASCII));
        mockMvc.perform(multipart("/api/v1/documentos")
                        .file(arquivo)
                        .param("proprietarioTipo", "PROCESSO")
                        .param("proprietarioId", Long.toString(processoId))
                        .param("categoria", "ADMINISTRATIVO")
                        .param("titulo", "Documento administrativo")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.mensagem")
                        .value("O armazenamento de arquivos está indisponível."));

        assertThat(documentos.findAll()).isEmpty();
        assertThat(versoes.findAll()).isEmpty();
        assertThat(auditoria.findAll().stream()
                .filter(registro -> "CRIAR_DOCUMENTO".equals(registro.getAcao())
                        && !registro.isSucesso())
                .toList())
                .singleElement()
                .satisfies(registro -> {
                    assertThat(registro.getEntidade()).isEqualTo("DOCUMENTO");
                    assertThat(registro.getDetalhes())
                            .doesNotContain("detalhe interno do disco")
                            .contains("armazenamento");
                });
    }

    @Test
    void falhaAoCarregarArquivoRetornaIndisponibilidadeEAuditaDownload() throws Exception {
        String token = tokenAdministradorPermanente();
        long processoId = criarProcesso(token);
        when(storage.armazenar(any(byte[].class), anyString()))
                .thenReturn("documentos/versao-inicial");
        long documentoId = criarDocumento(token, processoId);
        when(storage.carregar(anyString()))
                .thenThrow(new ArmazenamentoException("detalhe interno da leitura"));

        mockMvc.perform(get(
                                "/api/v1/documentos/{id}/versoes/{versao}/arquivo",
                                documentoId,
                                1)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.mensagem")
                        .value("O armazenamento de arquivos está indisponível."));

        assertThat(auditoria.findByEntidadeAndEntidadeIdOrderByCriadoEmDesc(
                "DOCUMENTO", documentoId))
                .filteredOn(registro -> "DOWNLOAD_DOCUMENTO".equals(registro.getAcao()))
                .singleElement()
                .satisfies(registro -> {
                    assertThat(registro.isSucesso()).isFalse();
                    assertThat(registro.getDetalhes())
                            .doesNotContain("detalhe interno da leitura")
                            .contains("armazenamento");
                });
    }

    @Test
    void atualizacoesConcorrentesRecebemNumerosDeVersaoDistintos() throws Exception {
        String token = tokenAdministradorPermanente();
        long processoId = criarProcesso(token);
        when(storage.armazenar(any(byte[].class), anyString()))
                .thenReturn("documentos/versao-inicial");
        long documentoId = criarDocumento(token, processoId);

        CountDownLatch primeiraVersaoArmazenando = new CountDownLatch(1);
        CountDownLatch liberarPrimeiraVersao = new CountDownLatch(1);
        AtomicInteger chamada = new AtomicInteger();
        when(storage.armazenar(any(byte[].class), anyString())).thenAnswer(invocation -> {
            int numeroChamada = chamada.incrementAndGet();
            if (numeroChamada == 1) {
                primeiraVersaoArmazenando.countDown();
                if (!liberarPrimeiraVersao.await(5, TimeUnit.SECONDS)) {
                    throw new ArmazenamentoException("timeout controlado do teste");
                }
            }
            return "documentos/versao-concorrente-" + numeroChamada;
        });

        try (var executor = Executors.newFixedThreadPool(2)) {
            var primeira = executor.submit(() -> statusNovaVersao(
                    token, documentoId, "primeira atualização"));
            assertThat(primeiraVersaoArmazenando.await(5, TimeUnit.SECONDS)).isTrue();
            var segunda = executor.submit(() -> statusNovaVersao(
                    token, documentoId, "segunda atualização"));

            Thread.sleep(250);
            liberarPrimeiraVersao.countDown();

            assertThat(List.of(primeira.get(), segunda.get()))
                    .containsExactlyInAnyOrder(201, 201);
        } finally {
            liberarPrimeiraVersao.countDown();
        }

        mockMvc.perform(get("/api/v1/documentos")
                        .queryParam("proprietarioTipo", "PROCESSO")
                        .queryParam("proprietarioId", Long.toString(processoId))
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].versoes[0].versao").value(3))
                .andExpect(jsonPath("$[0].versoes[1].versao").value(2))
                .andExpect(jsonPath("$[0].versoes[2].versao").value(1));
    }

    private long criarDocumento(String token, long processoId) throws Exception {
        MockMultipartFile arquivo = pdf(
                "inicial.pdf", "versão inicial");
        MvcResult resultado = mockMvc.perform(multipart("/api/v1/documentos")
                        .file(arquivo)
                        .param("proprietarioTipo", "PROCESSO")
                        .param("proprietarioId", Long.toString(processoId))
                        .param("categoria", "ADMINISTRATIVO")
                        .param("titulo", "Documento concorrente")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isCreated())
                .andReturn();
        return json(resultado).get("id").asLong();
    }

    private int statusNovaVersao(
            String token, long documentoId, String conteudo) {
        try {
            return mockMvc.perform(multipart("/api/v1/documentos/{id}/versoes", documentoId)
                            .file(pdf("atualizacao.pdf", conteudo))
                            .header("Authorization", bearer(token)))
                    .andReturn()
                    .getResponse()
                    .getStatus();
        } catch (Exception e) {
            return 500;
        }
    }

    private MockMultipartFile pdf(String nome, String conteudo) {
        return new MockMultipartFile(
                "arquivo",
                nome,
                MediaType.APPLICATION_PDF_VALUE,
                ("%PDF-1.4\n" + conteudo + "\n%%EOF").getBytes(StandardCharsets.UTF_8));
    }

}
