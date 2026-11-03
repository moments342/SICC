package com.moments.sicc.service;

import static com.moments.sicc.domain.Enums.ContextoTramitacao.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moments.sicc.domain.Enums.ContextoTramitacao;
import com.moments.sicc.domain.Movimentacao;
import com.moments.sicc.domain.ProcessoAdministrativo;
import com.moments.sicc.service.LeituraTramitacao.Contexto;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LeituraTramitacaoTest {
    private static final LocalDate REFERENCIA = LocalDate.of(2026, 10, 25);
    private static final long P1 = 710001, P2 = 710002, VAZIO = 710003;
    private static final long A = 700001, B = 700002;
    @Autowired private LeituraTramitacao leitura;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager em;
    @Autowired private EntityManagerFactory emf;
    private long autor;
    private long proximoMovimento = 900001;

    @BeforeEach
    void preparar() {
        autor = jdbc.queryForObject("select id from usuarios_internos where login = 'admin'", Long.class);
        jdbc.update("""
                insert into setores (id, sigla, sigla_normalizada, nome, nome_normalizado, ativo)
                values (?, 'LEIT-A', 'leit-a', 'Setor A', 'setor a', true)
                """, A);
        jdbc.update("""
                insert into setores (id, sigla, sigla_normalizada, nome, nome_normalizado, ativo)
                values (?, 'LEIT-B', 'leit-b', 'Setor B', 'setor b', true)
                """, B);
        processo(P1); processo(P2); processo(VAZIO);
    }

    @AfterEach
    void encerrarMedicao() {
        estatisticas().setStatisticsEnabled(false);
    }

    @Test
    void ordenaPorDataESequenciaSemReiniciarPermanenciaNoMesmoSetor() {
        long ultimo = movimento(FORMALIZACAO, P1, 8, 2, B);
        long chegada = movimento(FORMALIZACAO, P1, 10, 1, A);
        long repeticao = movimento(FORMALIZACAO, P1, 8, 1, A);
        var historico = leitura.historico(new Contexto(FORMALIZACAO, P1), REFERENCIA);
        assertThat(historico.movimentacoes()).extracting(LeituraTramitacao.MovimentoLido::id)
                .containsExactly(chegada, repeticao, ultimo);
        assertThat(historico.setorAtual().id()).isEqualTo(B);
        assertThat(historico.permanencias()).extracting(LeituraTramitacao.PermanenciaLida::diasCorridos)
                .containsExactly(2L, 8L);
        assertThat(historico.permanencias().getFirst().aberta()).isFalse();
        assertThat(historico.permanencias().getLast().aberta()).isTrue();
        assertThat(historico.movimentacoes().get(1).permanenciaIniciada()).isNull();
        assertThat(leitura.historicos(List.of(P1), Set.of(FORMALIZACAO), REFERENCIA))
                .containsExactly(historico);
        var outraReferencia = leitura.historico(new Contexto(FORMALIZACAO, P1), REFERENCIA.plusDays(3));
        assertThat(outraReferencia.permanencias().getLast().diasCorridos()).isEqualTo(11);
    }

    @Test
    void isolaContextosComMesmoIdEResolveAlteracoesSomenteDosProcessosSelecionados() {
        instrumento(P1); instrumento(P2);
        alteracao(P1, P1, "TERMO_ADITIVO");
        alteracao(P2, P1, "APOSTILAMENTO");
        alteracao(720001, P2, "TERMO_ADITIVO");
        movimento(FORMALIZACAO, P1, 10, 1, A);
        movimento(TERMO_ADITIVO, P1, 6, 1, B);
        movimento(APOSTILAMENTO, P2, 4, 1, A);
        movimento(FORMALIZACAO, P2, 10, 1, B);
        movimento(TERMO_ADITIVO, 720001, 5, 1, B);
        medir();
        var historicos = leitura.historicos(List.of(P1, P1), EnumSet.allOf(ContextoTramitacao.class), REFERENCIA);
        assertThat(historicos).extracting(LeituraTramitacao.HistoricoLido::contexto).containsExactly(
                new Contexto(FORMALIZACAO, P1), new Contexto(TERMO_ADITIVO, P1),
                new Contexto(APOSTILAMENTO, P2));
        assertThat(historicos).extracting(LeituraTramitacao.HistoricoLido::processoId).containsOnly(P1);
        assertThat(historicos).allSatisfy(h -> assertThat(h.movimentacoes()).hasSize(1));
        assertThat(estatisticas().getEntityStatistics(ProcessoAdministrativo.class.getName()).getLoadCount()).isZero();
        assertThat(estatisticas().getEntityStatistics(Movimentacao.class.getName()).getLoadCount()).isEqualTo(3);
        assertThat(leitura.historicos(List.of(P1), Set.of(TERMO_ADITIVO), REFERENCIA))
                .containsExactly(historicos.get(1));
        assertThat(leitura.historico(new Contexto(TERMO_ADITIVO, P1), REFERENCIA))
                .isEqualTo(historicos.get(1));
    }

    @Test
    void resultadosSaoImutaveisEDestacadosDaPersistenciaSemCacheNoModulo() {
        movimento(FORMALIZACAO, P1, 10, 1, A);
        var setores = leitura.setoresAtuais(List.of(P1));
        var historicos = leitura.historicos(List.of(P1), Set.of(FORMALIZACAO), REFERENCIA);
        em.clear();
        var estatisticas = medir();
        assertThat(historicos.getFirst().movimentacoes().getFirst().autor().nome()).isNotBlank();
        assertThat(historicos.getFirst().permanencias().getFirst().setor().sigla()).isEqualTo("LEIT-A");
        assertThat(estatisticas.getPrepareStatementCount()).isZero();
        assertThatThrownBy(() -> setores.clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> historicos.clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> historicos.getFirst().movimentacoes().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> historicos.getFirst().permanencias().clear()).isInstanceOf(UnsupportedOperationException.class);
        jdbc.update("update setores set sigla = 'LEIT-ATUALIZADO', sigla_normalizada = 'leit-atualizado' where id = ?", A);
        em.clear();
        assertThat(leitura.setoresAtuais(List.of(P1)).get(P1).sigla()).isEqualTo("LEIT-ATUALIZADO");
        assertThat(setores.get(P1).sigla()).isEqualTo("LEIT-A");
    }

    @Test
    void conjuntoVazioNaoConsultaEHistoricoSemMovimentosContinuaVazio() {
        var estatisticas = medir();
        assertThat(leitura.setoresAtuais(List.of())).isEmpty();
        assertThat(leitura.historicos(List.of(), Set.of(FORMALIZACAO), REFERENCIA)).isEmpty();
        assertThat(leitura.historicos(List.of(P1), Set.of(), REFERENCIA)).isEmpty();
        assertThat(estatisticas.getPrepareStatementCount()).isZero();
        var historico = leitura.historico(new Contexto(FORMALIZACAO, VAZIO), REFERENCIA);
        assertThat(historico.movimentacoes()).isEmpty();
        assertThat(historico.permanencias()).isEmpty();
        assertThat(historico.setorAtual()).isNull();
        assertThat(leitura.historico(new Contexto(TERMO_ADITIVO, -1L), REFERENCIA).movimentacoes()).isEmpty();
    }

    @Test
    void quantidadeDeConsultasNaoCrescePorProcessoOuMovimentacao() {
        List<Long> selecionados = new ArrayList<>();
        for (long id = 800001; id <= 800060; id++) {
            processo(id); selecionados.add(id);
            instrumento(id);
            alteracao(id + 1000000, id, "TERMO_ADITIVO");
            alteracao(id + 2000000, id, "APOSTILAMENTO");
            movimento(FORMALIZACAO, id, 10, 1, A);
            movimento(FORMALIZACAO, id, 5, 1, B);
            movimento(TERMO_ADITIVO, id + 1000000, 4, 1, A);
            movimento(APOSTILAMENTO, id + 2000000, 3, 1, B);
        }
        movimento(FORMALIZACAO, P2, 1, 1, A);
        var estatisticas = medir();
        Set<ContextoTramitacao> tipos = EnumSet.allOf(ContextoTramitacao.class);
        leitura.historicos(List.of(selecionados.getFirst()), tipos, REFERENCIA);
        long pequenas = estatisticas.getPrepareStatementCount();
        medir();
        var historicos = leitura.historicos(selecionados, tipos, REFERENCIA);
        long grandes = estatisticas.getPrepareStatementCount();
        assertThat(historicos).hasSize(180);
        assertThat(historicos.stream().mapToInt(h -> h.movimentacoes().size()).sum()).isEqualTo(240);
        assertThat(grandes).isEqualTo(pequenas).isEqualTo(4);
        assertThat(estatisticas.getEntityStatistics(ProcessoAdministrativo.class.getName()).getLoadCount()).isZero();
        assertThat(estatisticas.getEntityStatistics(Movimentacao.class.getName()).getLoadCount()).isEqualTo(240);
        medir();
        assertThat(leitura.historicos(selecionados, Set.of(FORMALIZACAO), REFERENCIA)).hasSize(60);
        assertThat(estatisticas.getPrepareStatementCount()).isEqualTo(1);
        System.out.printf("LEITURA_TRAMITACAO: tres contextos: 1 processo=%d consultas; 60 processos/240 movimentos=%d consultas; somente Formalizacao=1 consulta%n", pequenas, grandes);
    }

    @Test
    void setorAtualTemCaminhoLeveEListasGrandesSaoDivididasEmLotes() {
        List<Long> selecionados = new ArrayList<>();
        for (long id = 810001; id <= 810501; id++) {
            processo(id); selecionados.add(id);
            movimento(FORMALIZACAO, id, 10, 1, A);
            movimento(FORMALIZACAO, id, 5, 1, B);
        }
        var estatisticas = medir();
        var setores = leitura.setoresAtuais(selecionados);
        assertThat(setores).hasSize(501);
        assertThat(setores.values()).allSatisfy(setor -> assertThat(setor.id()).isEqualTo(B));
        assertThat(estatisticas.getPrepareStatementCount()).isEqualTo(2);
        assertThat(estatisticas.getEntityStatistics(Movimentacao.class.getName()).getLoadCount()).isEqualTo(501);
        medir();
        assertThat(leitura.historicos(selecionados, Set.of(FORMALIZACAO), REFERENCIA)).hasSize(501);
        assertThat(estatisticas.getPrepareStatementCount()).isEqualTo(2);
        System.out.println("LEITURA_TRAMITACAO: 501 processos=2 consultas por operacao em lotes; Setor Atual carrega 501 ultimos movimentos");
    }

    private Statistics estatisticas() {
        return emf.unwrap(SessionFactory.class).getStatistics();
    }

    private Statistics medir() {
        em.flush(); em.clear();
        Statistics estatisticas = estatisticas();
        estatisticas.setStatisticsEnabled(true); estatisticas.clear();
        return estatisticas;
    }

    private void processo(long id) {
        jdbc.update("""
                insert into processos_administrativos (id, numero, origem, status, data_cadastro, ativo)
                values (?, ?, 'LEITURA', 'EM_FORMALIZACAO', ?, true)
                """, id, "PROC-LEIT-" + id, REFERENCIA.minusDays(30));
    }

    private long movimento(ContextoTramitacao tipo, long contextoId, int diasAntes, int sequencia, long setor) {
        long id = proximoMovimento++;
        jdbc.update("""
                insert into movimentacoes (id, contexto_tipo, contexto_id, data_movimentacao,
                    sequencia_diaria, setor_destino_id, autor_id, observacao, inserido_em)
                values (?, ?, ?, ?, ?, ?, ?, ?, current_timestamp)
                """, id, tipo.name(), contextoId, REFERENCIA.minusDays(diasAntes), sequencia,
                setor, autor, "Movimento " + id);
        return id;
    }

    private void instrumento(long processoId) {
        jdbc.update("""
                insert into documentos (id, proprietario_tipo, proprietario_id, categoria,
                    titulo, ativo, criado_por_id, criado_em)
                values (?, 'INSTRUMENTO', ?, 'ASSINADO', 'Documento de leitura', true, ?, current_timestamp)
                """, processoId, processoId, autor);
        jdbc.update("""
                insert into versoes_documento (id, documento_id, versao, nome_arquivo, tipo_mime,
                    tamanho, checksum_sha256, chave_armazenamento, criado_em, criado_por_id)
                values (?, ?, 1, 'leitura.pdf', 'application/pdf', 10, ?, ?, current_timestamp, ?)
                """, processoId, processoId, "a".repeat(64), "leitura/" + processoId, autor);
        jdbc.update("""
                insert into instrumentos_contratuais (id, processo_id, numero, tipo, objeto,
                    natureza, coordenador, participes, valor_atual, vigencia_contratual_final,
                    data_formalizacao, documento_assinado_id, documento_assinado_versao_id)
                values (?, ?, ?, 'CONVENIO', 'Objeto', 'Natureza', 'Coordenador', 'UFGD',
                    100, ?, ?, ?, ?)
                """, processoId, processoId, "CV-LEIT-" + processoId, REFERENCIA.plusDays(30),
                REFERENCIA.minusDays(20), processoId, processoId);
    }

    private void alteracao(long id, long instrumentoId, String tipo) {
        jdbc.update("""
                insert into alteracoes_contratuais (id, instrumento_id, tipo, estado, numero_oficial,
                    operacao, criado_em) values (?, ?, ?, 'RASCUNHO', ?, 'ORIGINAL', current_timestamp)
                """, id, instrumentoId, tipo, "ALT-LEIT-" + id);
    }
}
