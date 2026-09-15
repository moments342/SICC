package com.moments.sicc.service;

import static com.moments.sicc.domain.Enums.CampoInstrumento.COORDENADOR;
import static com.moments.sicc.domain.Enums.CampoInstrumento.DESCRICAO;
import static com.moments.sicc.domain.Enums.CampoInstrumento.VALOR_ATUAL;
import static com.moments.sicc.domain.Enums.StatusProcesso.CONCLUIDO;
import static org.assertj.core.api.Assertions.assertThat;

import com.moments.sicc.domain.AlteracaoContratual;
import com.moments.sicc.domain.Documento;
import com.moments.sicc.domain.InstrumentoContratual;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@ActiveProfiles("test")
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:sicc-estado-atual;MODE=PostgreSQL")
@Import(EstadoAtualInstrumentoTest.RelogioFixoConfig.class)
@Transactional
class EstadoAtualInstrumentoTest {
    private static final long INSTRUMENTO_ID = 100;

    @Autowired private EstadoAtualInstrumento modulo;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbc;
    private long administradorId;

    @BeforeEach
    void criarInstrumentoFormalizado() {
        administradorId = jdbc.queryForObject(
                "select id from usuarios_internos where login = 'admin'", Long.class);
        jdbc.update("""
                insert into processos_administrativos
                    (id, numero, origem, status, data_cadastro, ativo)
                values (?, ?, 'DIPAC', 'EM_VIGENCIA', ?, true)
                """, INSTRUMENTO_ID, "PROC-ESTADO-" + System.nanoTime(), data(1));
        inserirDocumento(900, "INSTRUMENTO", INSTRUMENTO_ID);
        jdbc.update("""
                insert into versoes_documento
                    (id, documento_id, versao, nome_arquivo, tipo_mime, tamanho,
                     checksum_sha256, chave_armazenamento, criado_em, criado_por_id)
                values (900, 900, 1, 'instrumento.pdf', 'application/pdf', 10,
                        ?, ?, current_timestamp, ?)
                """, "a".repeat(64), "estado/instrumento-" + System.nanoTime(), administradorId);
        jdbc.update("""
                insert into instrumentos_contratuais
                    (id, processo_id, numero, tipo, objeto, descricao, natureza,
                     coordenador, participes, valor_atual, vigencia_contratual_final,
                     data_formalizacao, documento_assinado_id, documento_assinado_versao_id)
                values (?, ?, 'CV-ESTADO/2026', 'CONVENIO', 'Objeto inicial',
                        'Descrição inicial', 'Natureza inicial', 'Coordenação inicial',
                        'UFGD', 100.00, ?, ?, 900, 900)
                """, INSTRUMENTO_ID, INSTRUMENTO_ID, data(3), data(1));
        jdbc.update("""
                insert into instrumentos_estados_iniciais
                    (instrumento_id, objeto, descricao, natureza, coordenador,
                     participes, valor_atual, vigencia_contratual_final)
                values (?, 'Objeto inicial', 'Descrição inicial', 'Natureza inicial',
                        'Coordenação inicial', 'UFGD', 100.00, ?)
                """, INSTRUMENTO_ID, data(3));
    }

    @Test
    void umaAvaliacaoOrdenaCronologiaAplicaPrecedenciaEExplicaCancelamento() {
        inserirAlteracao(10, "ORIGINAL", null, data(1), 2, true);
        inserirAlteracao(11, "ORIGINAL", null, data(1), 1, true);
        inserirAlteracao(12, "RETIFICACAO", 10L, data(2), 1, true);
        inserirAlteracao(13, "CANCELAMENTO", 12L, data(3), 1, true);
        inserirAlteracao(14, "ORIGINAL", null, data(3), 2, true);
        inserirMudanca(10, "VALOR_ATUAL", "100.00", "120.00");
        inserirMudanca(10, "COORDENADOR", "Coordenação inicial", "Coordenação A");
        inserirMudanca(10, "DESCRICAO", "Descrição inicial", null);
        inserirMudanca(11, "VALOR_ATUAL", "100.00", "110.00");
        inserirMudanca(12, "VALOR_ATUAL", "120.00", "130.00");
        inserirMudanca(12, "COORDENADOR", "Coordenação A", "Coordenação B");
        inserirMudanca(14, "VALOR_ATUAL", "120.00", "140.00");
        jdbc.update("""
                update instrumentos_contratuais
                set valor_atual = 140.00, coordenador = 'Coordenação A', descricao = null
                where id = ?
                """, INSTRUMENTO_ID);
        entityManager.clear();

        InstrumentoContratual instrumento = entityManager.find(
                InstrumentoContratual.class, INSTRUMENTO_ID);
        EstadoAtualInstrumento.Avaliacao avaliacao = modulo.avaliar(instrumento);
        EstadoAtualInstrumento.Estado estado = avaliacao.estadoAtual();
        AlteracaoContratual original = entityManager.find(AlteracaoContratual.class, 10L);
        List<EstadoAtualInstrumento.RegistroCadeia> cadeia = avaliacao.cadeia(original);

        assertThat(avaliacao.alteracoes()).extracting(AlteracaoContratual::getId)
                .containsExactly(11L, 10L, 12L, 13L, 14L);
        assertThat(estado.valores())
                .containsEntry(VALOR_ATUAL, "140.00")
                .containsEntry(COORDENADOR, "Coordenação A")
                .containsEntry(DESCRICAO, null);
        assertThat(estado.precedenciaPorCampo().get(VALOR_ATUAL).alteracaoId()).isEqualTo(14L);
        assertThat(estado.precedenciaPorCampo().get(COORDENADOR).alteracaoId()).isEqualTo(10L);
        assertThat(estado.statusProcesso()).isEqualTo(CONCLUIDO);
        assertThat(cadeia.get(2).valoresProduzidos())
                .containsEntry(VALOR_ATUAL, "120.00")
                .containsEntry(COORDENADOR, "Coordenação A");
        assertThat(avaliacao.estadoAtual()).isSameAs(estado);
        assertThat(avaliacao.cadeia(original)).isSameAs(cadeia);
    }

    @Test
    void efetivacaoCoesaFixaValorAnteriorERecompoeSemPerderTermoPosterior() {
        inserirAlteracao(20, "ORIGINAL", null, data(3), 2, true);
        inserirMudanca(20, "VALOR_ATUAL", "100.00", "140.00");
        inserirAlteracao(21, "ORIGINAL", null, null, null, false);
        inserirMudanca(21, "VALOR_ATUAL", "140.00", "120.00");
        inserirDocumento(921, "TERMO_ADITIVO", 21);
        jdbc.update("update instrumentos_contratuais set valor_atual = 140.00 where id = ?",
                INSTRUMENTO_ID);
        entityManager.clear();

        InstrumentoContratual instrumento = entityManager.find(
                InstrumentoContratual.class, INSTRUMENTO_ID);
        AlteracaoContratual retroativa = entityManager.find(AlteracaoContratual.class, 21L);
        Documento documento = entityManager.find(Documento.class, 921L);
        EstadoAtualInstrumento.Avaliacao avaliacao = modulo.avaliar(instrumento);

        avaliacao.efetivar(retroativa, null, data(2), 1, documento);
        entityManager.flush();

        assertThat(retroativa.getDataEfetivacao()).isEqualTo(data(2));
        assertThat(retroativa.getOrdemOficial()).isEqualTo(1);
        assertThat(retroativa.getDocumentoAssinado()).isEqualTo(documento);
        assertThat(avaliacao.mudancas(retroativa).getFirst().getValorAnterior()).isEqualTo("100.00");
        assertThat(avaliacao.estadoAtual().valores()).containsEntry(VALOR_ATUAL, "140.00");
        assertThat(instrumento.getValorAtual().toPlainString()).isEqualTo("140.00");
        assertThat(instrumento.getProcesso().getStatus()).isEqualTo(CONCLUIDO);
    }

    private void inserirAlteracao(long id, String operacao, Long referenciaId,
            LocalDate dataEfetivacao, Integer ordemOficial, boolean efetivada) {
        Long documentoId = null;
        if (efetivada) {
            documentoId = 1000 + id;
            inserirDocumento(documentoId, "TERMO_ADITIVO", id);
        }
        jdbc.update("""
                insert into alteracoes_contratuais
                    (id, instrumento_id, tipo, estado, numero_oficial, ordem_oficial,
                     data_efetivacao, referencia_id, operacao, documento_assinado_id, criado_em)
                values (?, ?, 'TERMO_ADITIVO', ?, ?, ?, ?, ?, ?, ?, current_timestamp)
                """, id, INSTRUMENTO_ID, efetivada ? "EFETIVADA" : "RASCUNHO",
                "TA-" + id, ordemOficial, dataEfetivacao, referenciaId, operacao, documentoId);
    }

    private void inserirMudanca(long alteracaoId, String campo,
            String valorAnterior, String valorNovo) {
        jdbc.update("""
                insert into alteracoes_campos
                    (alteracao_id, campo, valor_anterior, valor_novo)
                values (?, ?, ?, ?)
                """, alteracaoId, campo, valorAnterior, valorNovo);
    }

    private void inserirDocumento(long id, String proprietarioTipo, long proprietarioId) {
        jdbc.update("""
                insert into documentos
                    (id, proprietario_tipo, proprietario_id, categoria, titulo,
                     ativo, criado_por_id, criado_em)
                values (?, ?, ?, 'ASSINADO', ?, true, ?, current_timestamp)
                """, id, proprietarioTipo, proprietarioId, "Documento " + id, administradorId);
    }

    private LocalDate data(int dia) {
        return LocalDate.of(2026, 8, dia);
    }

    @TestConfiguration
    static class RelogioFixoConfig {
        @Bean
        @Primary
        Clock relogioFixo() {
            return Clock.fixed(Instant.parse("2026-08-04T12:00:00Z"), ZoneOffset.UTC);
        }
    }
}
