package com.moments.sicc.api;

import static com.moments.sicc.support.ArquivoDocumentoTeste.pdfValido;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.moments.sicc.consulta.ConsultaInstrumentosAlteracao;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import java.util.Map;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:sicc-instrumentos-alteracao;MODE=PostgreSQL")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class InstrumentosAlteracaoApiContractTest extends ApiContractTestSupport {
    @Autowired ConsultaInstrumentosAlteracao consulta;
    @Autowired EntityManagerFactory entityManagerFactory;
    private static final String URL = "/api/v1/alteracoes/instrumentos";

    @Test
    void paginaBuscaEProjetaSomenteInstrumentosDeProcessosAtivosInclusiveConcluidos() throws Exception {
        String token = tokenAdministradorPermanente();
        long concluido = instrumento(token, "PA-C", "CV-B", "Pesquisa", "2020-01-01", false);
        instrumento(token, "PA-A", "CV-A", "Extensão", "2099-12-31", false);
        instrumento(token, "PA-ESPECIAL", "CV-%_\\", "Extensão", "2099-12-31", false);
        instrumento(token, "PA-INATIVO", "CV-INATIVO", "Extensão", "2099-12-31", true);
        processo(token, "PA-SEM-INSTRUMENTO", "Extensão");
        var pagina = json(mockMvc.perform(get(URL).header("Authorization", bearer(token))
                        .param("page", "2").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(concluido)).andReturn());
        assertThat(pagina.get("content").get(0).properties()).extracting(Map.Entry::getKey)
                .containsExactlyInAnyOrder("id", "numero", "tipo", "numeroProcesso");
        for (String termo : List.of(" cv-b ", "pa-c", " PESQUISA ", "%", "_", "\\")) {
            mockMvc.perform(get(URL).header("Authorization", bearer(token)).param("busca", termo))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        }
        mockMvc.perform(get(URL).header("Authorization", bearer(token)).param("busca", "inexistente"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(0));
        mockMvc.perform(get(URL).header("Authorization", bearer(token)).param("page", "99"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content.length()").value(0));
        var detalhe = json(mockMvc.perform(get(URL + "/{id}", concluido).header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.participes[0]").value("UFGD"))
                .andExpect(jsonPath("$.vigenciaContratualFinal").value("2020-01-01")).andReturn());
        assertThat(detalhe.properties()).extracting(Map.Entry::getKey).containsExactlyInAnyOrder(
                "id", "numero", "tipo", "numeroProcesso", "objeto", "descricao", "natureza", "coordenador",
                "participes", "valorAtual", "vigenciaContratualFinal", "vigenciaTedFinal");
        var stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);
        try {
            stats.clear();
            consulta.consultar("CV", 0, 1);
            assertThat(stats.getPrepareStatementCount()).isEqualTo(2);
            assertThat(stats.getEntityLoadCount()).isZero();
            stats.clear();
            consulta.buscar(concluido);
            assertThat(stats.getPrepareStatementCount()).isEqualTo(1);
            assertThat(stats.getEntityLoadCount()).isZero();
        } finally { stats.setStatisticsEnabled(false); }
    }

    @Test
    void preservaAcessoInternoLimitesERecusaSelecaoInativaOuInexistente() throws Exception {
        String temporario = tokenDoLogin("admin", "Temporaria123!", true);
        mockMvc.perform(get(URL).header("Authorization", bearer(temporario))).andExpect(status().isForbidden());
        String token = tokenAdministradorPermanente();
        long inativo = instrumento(token, "PA-INATIVO", "CV-INATIVO", "DIPAC", "2099-12-31", true);
        for (String path : List.of(URL, URL + "/" + inativo)) {
            mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
        for (String id : List.of(String.valueOf(inativo), "999999")) {
            mockMvc.perform(get(URL + "/" + id).header("Authorization", bearer(token)))
                    .andExpect(status().isNotFound());
        }
        for (Map<String, String> params : List.of(Map.of("page", "-1"), Map.of("size", "0"),
                Map.of("size", "101"), Map.of("page", "2147483647", "size", "100"))) {
            var req = get(URL).header("Authorization", bearer(token)); params.forEach(req::param);
            mockMvc.perform(req).andExpect(status().isUnprocessableEntity());
        }
        long ativo = instrumento(token, "PA-ATIVO", "CV-ATIVO", "DIPAC", "2099-12-31", false);
        mockMvc.perform(post("/api/v1/admin/usuarios").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of(
                        "nome", "Operador", "email", "op@f13.test", "login", "operador-f13",
                        "senhaTemporaria", "Temporaria123!", "perfil", "OPERADOR_DIPAC"))))
                .andExpect(status().isCreated());
        String opTemp = tokenDoLogin("operador-f13", "Temporaria123!", true);
        mockMvc.perform(post("/api/v1/auth/senha").header("Authorization", bearer(opTemp))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"senhaAtual\":\"Temporaria123!\",\"novaSenha\":\"Permanente123!\"}"))
                .andExpect(status().isNoContent());
        String op = tokenDoLogin("operador-f13", "Permanente123!", false);
        mockMvc.perform(get(URL).header("Authorization", bearer(op)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get(URL + "/{id}", ativo).header("Authorization", bearer(op)))
                .andExpect(status().isOk());
    }

    private long processo(String token, String numero, String origem) throws Exception {
        return json(mockMvc.perform(post("/api/v1/processos").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(
                        Map.of("numero", numero, "origem", origem))))
                .andExpect(status().isCreated()).andReturn()).get("id").asLong();
    }

    private long instrumento(String token, String numeroProcesso, String numero, String origem,
            String fim, boolean inativo) throws Exception {
        long p = processo(token, numeroProcesso, origem);
        long d = json(mockMvc.perform(multipart("/api/v1/documentos")
                .file(new MockMultipartFile("arquivo", "assinado.pdf", "application/pdf", pdfValido()))
                .param("proprietarioTipo", "PROCESSO").param("proprietarioId", String.valueOf(p))
                .param("categoria", "ASSINADO").param("titulo", "Instrumento assinado")
                .header("Authorization", bearer(token))).andExpect(status().isCreated()).andReturn()).get("id").asLong();
        long i = json(mockMvc.perform(post("/api/v1/processos/{id}/instrumento", p)
                .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.ofEntries(Map.entry("numero", numero),
                        Map.entry("tipo", "CONVENIO"), Map.entry("objeto", "Cooperação"),
                        Map.entry("natureza", "Acadêmica"), Map.entry("coordenador", "Maria"),
                        Map.entry("participes", List.of("UFGD")), Map.entry("valorAtual", 100),
                        Map.entry("vigenciaContratualFinal", fim), Map.entry("dataFormalizacao", "2019-01-01"),
                        Map.entry("documentoAssinadoId", d)))))
                .andExpect(status().isCreated()).andReturn()).get("id").asLong();
        if (inativo) mockMvc.perform(delete("/api/v1/processos/{id}", p).header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        return i;
    }
}
