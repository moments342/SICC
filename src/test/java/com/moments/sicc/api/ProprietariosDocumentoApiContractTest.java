package com.moments.sicc.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static com.moments.sicc.support.ArquivoDocumentoTeste.pdfValido;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.List;
import java.time.LocalDate;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:sicc-proprietarios;MODE=PostgreSQL")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ProprietariosDocumentoApiContractTest extends ApiContractTestSupport {
    @Autowired
    private Clock clock;

    @Test
    void consultaResolveOsQuatroTiposEIncluiHistoricoInativoSomenteQuandoSolicitado() throws Exception {
        String token = tokenAdministradorPermanente();
        long processo = json(mockMvc.perform(post("/api/v1/processos")
                        .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"numero\":\"PA-HISTORICO\",\"origem\":\"DIPAC\"}"))
                .andExpect(status().isCreated()).andReturn()).get("id").asLong();
        long documento = json(mockMvc.perform(multipart("/api/v1/documentos")
                        .file(new MockMultipartFile("arquivo", "assinado.pdf", "application/pdf", pdfValido()))
                        .param("proprietarioTipo", "PROCESSO").param("proprietarioId", String.valueOf(processo))
                        .param("categoria", "ASSINADO").param("titulo", "Instrumento assinado")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isCreated()).andReturn()).get("id").asLong();
        long instrumento = json(mockMvc.perform(post("/api/v1/processos/{id}/instrumento", processo)
                        .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.ofEntries(
                                Map.entry("numero", "CV-HISTORICO"), Map.entry("tipo", "CONVENIO"),
                                Map.entry("objeto", "Cooperação"), Map.entry("natureza", "Administrativa"),
                                Map.entry("coordenador", "Maria"), Map.entry("participes", List.of("UFGD")),
                                Map.entry("valorAtual", 100), Map.entry("vigenciaContratualFinal", "2099-12-31"),
                                Map.entry("dataFormalizacao", LocalDate.now(clock).toString()),
                                Map.entry("documentoAssinadoId", documento)))))
                .andExpect(status().isCreated()).andReturn()).get("id").asLong();
        for (String tipo : List.of("TERMO_ADITIVO", "APOSTILAMENTO")) {
            mockMvc.perform(post("/api/v1/alteracoes")
                            .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "instrumentoId", instrumento, "tipo", tipo, "numeroOficial", tipo + "-HISTORICO",
                                    "operacao", "ORIGINAL", "mudancas", List.of(Map.of(
                                            "campo", "COORDENADOR", "valorAnterior", "Maria", "valorNovo", "Ana"))))))
                    .andExpect(status().isCreated());
        }
        mockMvc.perform(delete("/api/v1/processos/{id}", processo).header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        for (String tipo : List.of("PROCESSO", "INSTRUMENTO", "TERMO_ADITIVO", "APOSTILAMENTO")) {
            mockMvc.perform(get("/api/v1/documentos/proprietarios").param("tipo", tipo)
                            .header("Authorization", bearer(token)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
            mockMvc.perform(get("/api/v1/documentos/proprietarios").param("tipo", tipo)
                            .param("busca", "cv-historico").param("incluirInativos", "true")
                            .header("Authorization", bearer(token)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].numeroProcesso").value("PA-HISTORICO"))
                    .andExpect(jsonPath("$.content[0].numeroInstrumento").value("CV-HISTORICO"))
                    .andExpect(jsonPath("$.content[0].processoAtivo").value(false))
                    .andExpect(jsonPath("$.content[0].statusProcesso").value("EM_VIGENCIA"))
                    .andExpect(jsonPath("$.content[0].numero").value(switch (tipo) {
                        case "PROCESSO" -> "PA-HISTORICO";
                        case "INSTRUMENTO" -> "CV-HISTORICO";
                        default -> tipo + "-HISTORICO";
                    }));
        }
    }

    @Test
    void consultaInternaBuscaProprietariosSemCarregarTodoOCatalogo() throws Exception {
        String token = tokenAdministradorPermanente();
        for (String numero : new String[] {"PA-B", "PA-A", "PA-%"}) {
            mockMvc.perform(post("/api/v1/processos")
                            .header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("numero", numero, "origem", "DIPAC"))))
                    .andExpect(status().isCreated());
        }
        mockMvc.perform(get("/api/v1/documentos/proprietarios")
                        .param("tipo", "PROCESSO").param("busca", "pa-").param("size", "1").param("page", "1")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].numero").value("PA-A"))
                .andExpect(jsonPath("$.content[0].origem").value("DIPAC"))
                .andExpect(jsonPath("$.content[0].processoAtivo").value(true))
                .andExpect(jsonPath("$.content[0].statusProcesso").value("EM_FORMALIZACAO"));
        mockMvc.perform(get("/api/v1/documentos/proprietarios")
                        .param("tipo", "PROCESSO").param("busca", "%")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].numero").value("PA-%"));
        mockMvc.perform(get("/api/v1/documentos/proprietarios").param("tipo", "PROCESSO"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/documentos/proprietarios").param("tipo", "PROCESSO").param("size", "101")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isUnprocessableEntity());
    }
}
