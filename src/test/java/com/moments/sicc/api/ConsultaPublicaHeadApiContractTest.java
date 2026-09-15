package com.moments.sicc.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:sicc-public-head;MODE=PostgreSQL")
@AutoConfigureMockMvc
class ConsultaPublicaHeadApiContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void consultaPublicaAceitaHeadSemAutenticacao() throws Exception {
        mockMvc.perform(head("/api/v1/public/processos"))
                .andExpect(status().isOk());
    }
}
