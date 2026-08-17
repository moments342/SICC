package com.moments.sicc.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest
class FlywayMigrationTest {

    @Autowired
    private Flyway flyway;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void migrationsAplicamNoBancoDeTeste() {
        assertThat(dataSource).isNotNull();
        assertThat(flyway.info().applied()).hasSize(15);
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("15");
    }

    @Test
    void persistenciaRejeitaDocumentoAdministrativoForaDeProcesso() {
        Long autorId = jdbc.queryForObject(
                "SELECT id FROM usuarios_internos WHERE login = 'admin'", Long.class);

        assertThatThrownBy(() -> jdbc.update("""
                        INSERT INTO documentos (
                            proprietario_tipo, proprietario_id, categoria, titulo,
                            ativo, criado_por_id, criado_em
                        ) VALUES ('INSTRUMENTO', 999, 'ADMINISTRATIVO', ?,
                            TRUE, ?, CURRENT_TIMESTAMP)
                        """,
                "Documento administrativo inválido",
                autorId))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void migracaoPadronizaIdentidadesPreexistentesEProtegeUnicidade() throws Exception {
        String url = "jdbc:h2:mem:sicc-migracao-setores;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        DataSource bancoIsolado = new DriverManagerDataSource(url, "sa", "");
        Flyway ateVersaoTres = Flyway.configure()
                .dataSource(bancoIsolado)
                .target("3")
                .load();
        ateVersaoTres.migrate();
        CatalogoSetorMigrationFixture.inserirSetorLegado(bancoIsolado, "setores");

        Flyway atualizado = Flyway.configure().dataSource(bancoIsolado).load();
        atualizado.migrate();

        CatalogoSetorMigrationFixture.verificarIdentidadePadronizadaEUnicidade(
                bancoIsolado,
                "setores"
        );
    }

    @Test
    void persistenciaRejeitaCamposObrigatoriosEmBrancoENumeroDuplicado() throws Exception {
        ProcessoAdministrativoMigrationFixture.inserir(
                dataSource, "PROC-PERSISTENCIA-006", "DIPAC");

        assertThatThrownBy(() -> ProcessoAdministrativoMigrationFixture.inserir(
                dataSource, "PROC-PERSISTENCIA-006", "Outra origem"))
                .isInstanceOf(java.sql.SQLException.class);
        assertThatThrownBy(() -> ProcessoAdministrativoMigrationFixture.inserir(
                dataSource, " proc-persistencia-006 ", "Outra origem"))
                .isInstanceOf(java.sql.SQLException.class);
        assertThatThrownBy(() -> ProcessoAdministrativoMigrationFixture.inserir(
                dataSource, "   ", "DIPAC"))
                .isInstanceOf(java.sql.SQLException.class);
        assertThatThrownBy(() -> ProcessoAdministrativoMigrationFixture.inserir(
                dataSource, "PROC-SEM-ORIGEM-006", "   "))
                .isInstanceOf(java.sql.SQLException.class);
    }

    @Test
    void upgradeV6VinculaNotificacaoDeChegadaAoProcessoAdministrativo() throws Exception {
        String url = "jdbc:h2:mem:sicc-migracao-notificacoes;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        DataSource bancoIsolado = new DriverManagerDataSource(url, "sa", "");
        Flyway ateVersaoSeis = Flyway.configure()
                .dataSource(bancoIsolado)
                .target("6")
                .load();
        ateVersaoSeis.migrate();
        NotificacaoChegadaMigrationFixture.inserirLegado(bancoIsolado);

        Flyway atualizado = Flyway.configure().dataSource(bancoIsolado).load();
        atualizado.migrate();

        assertThat(atualizado.info().current().getVersion().getVersion()).isEqualTo("15");
        NotificacaoChegadaMigrationFixture.verificarVinculo(bancoIsolado);
    }

    @Test
    void upgradeV10VinculaDocumentoAssinadoAoInstrumentoContratual() throws Exception {
        String url = "jdbc:h2:mem:sicc-migracao-instrumentos;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        DataSource bancoIsolado = new DriverManagerDataSource(url, "sa", "");
        Flyway ateVersaoDez = Flyway.configure()
                .dataSource(bancoIsolado)
                .target("10")
                .load();
        ateVersaoDez.migrate();
        InstrumentoContratualMigrationFixture.inserirLegado(bancoIsolado);

        Flyway atualizado = Flyway.configure().dataSource(bancoIsolado).load();
        atualizado.migrate();

        assertThat(atualizado.info().current().getVersion().getVersion()).isEqualTo("15");
        InstrumentoContratualMigrationFixture.verificarBackfill(bancoIsolado);
    }

    @Test
    void persistenciaProtegeCatalogoCamposEDocumentoDaFormalizacao() throws Exception {
        InstrumentoContratualMigrationFixture.prepararNovoInstrumento(
                dataSource, 710, 810, 910, "PROC-PERSIST-TIPO-010");
        assertThatThrownBy(() -> InstrumentoContratualMigrationFixture.inserirInstrumentoAtual(
                dataSource, 910, 710, 810L, "TED", "TED-010", "Objeto"))
                .isInstanceOf(java.sql.SQLException.class);

        InstrumentoContratualMigrationFixture.prepararNovoInstrumento(
                dataSource, 711, 811, 911, "PROC-PERSIST-CAMPO-010");
        assertThatThrownBy(() -> InstrumentoContratualMigrationFixture.inserirInstrumentoAtual(
                dataSource, 911, 711, 811L, "CONVENIO", "   ", "Objeto"))
                .isInstanceOf(java.sql.SQLException.class);

        InstrumentoContratualMigrationFixture.prepararNovoInstrumento(
                dataSource, 712, 812, 912, "PROC-PERSIST-DOC-010");
        assertThatThrownBy(() -> InstrumentoContratualMigrationFixture.inserirInstrumentoAtual(
                dataSource, 912, 712, null, "CONVENIO", "CV-SEM-DOC-010", "Objeto"))
                .isInstanceOf(java.sql.SQLException.class);
    }

    @Test
    void persistenciaNaoArmazenaDatasIniciaisDoContratoOuTed() {
        Integer colunasIniciais = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE LOWER(table_name) = 'instrumentos_contratuais'
                  AND LOWER(column_name) IN (
                      'vigencia_contratual_inicial', 'vigencia_ted_inicial'
                  )
                """, Integer.class);

        assertThat(colunasIniciais).isZero();
    }

    @Test
    void persistenciaProtegeCatalogosEFormaDoRascunhoDeTermoAditivo() throws Exception {
        InstrumentoContratualMigrationFixture.prepararNovoInstrumento(
                dataSource, 720, 820, 920, "PROC-PERSIST-TA-012");
        InstrumentoContratualMigrationFixture.inserirInstrumentoAtual(
                dataSource, 920, 720, 820L, "CONVENIO", "CV-TA-012", "Objeto");
        jdbc.update("""
                INSERT INTO alteracoes_contratuais (
                    id, instrumento_id, tipo, estado, numero_oficial,
                    operacao, criado_em
                ) VALUES (1020, 920, 'TERMO_ADITIVO', 'RASCUNHO',
                    'TA-01/2026', 'ORIGINAL', CURRENT_TIMESTAMP)
                """);

        assertThatThrownBy(() -> jdbc.update("""
                        INSERT INTO alteracoes_campos (
                            alteracao_id, campo, valor_anterior, valor_novo
                        ) VALUES (1020, 'NUMERO', 'CV-TA-012', 'CV-OUTRO')
                        """))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("""
                        INSERT INTO alteracoes_contratuais (
                            instrumento_id, tipo, estado, numero_oficial,
                            operacao, criado_em
                        ) VALUES (920, 'TERMO_ADITIVO', 'RASCUNHO',
                            '   ', 'ORIGINAL', CURRENT_TIMESTAMP)
                        """))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void persistenciaProtegeOrdemEUnicidadeDoDocumentoOficialDaAlteracao() throws Exception {
        InstrumentoContratualMigrationFixture.prepararNovoInstrumento(
                dataSource, 721, 821, 921, "PROC-PERSIST-EFETIVAR-013");
        InstrumentoContratualMigrationFixture.inserirInstrumentoAtual(
                dataSource, 921, 721, 821L, "CONVENIO", "CV-EFETIVAR-013", "Objeto");
        jdbc.update("""
                INSERT INTO documentos (
                    id, proprietario_tipo, proprietario_id, categoria, titulo,
                    ativo, criado_por_id, criado_em
                ) VALUES
                    (822, 'TERMO_ADITIVO', 1021, 'ASSINADO', 'Termo 1',
                        TRUE, 101, CURRENT_TIMESTAMP),
                    (823, 'TERMO_ADITIVO', 1022, 'ASSINADO', 'Termo 2',
                        TRUE, 101, CURRENT_TIMESTAMP)
                """);
        jdbc.update("""
                INSERT INTO alteracoes_contratuais (
                    id, instrumento_id, tipo, estado, numero_oficial,
                    ordem_oficial, data_efetivacao, operacao,
                    documento_assinado_id, criado_em
                ) VALUES (
                    1021, 921, 'TERMO_ADITIVO', 'EFETIVADA', 'TA-01/2026',
                    1, DATE '2026-08-07', 'ORIGINAL', 822, CURRENT_TIMESTAMP
                )
                """);

        assertThatThrownBy(() -> jdbc.update("""
                        INSERT INTO alteracoes_contratuais (
                            id, instrumento_id, tipo, estado, numero_oficial,
                            ordem_oficial, data_efetivacao, operacao,
                            documento_assinado_id, criado_em
                        ) VALUES (
                            1022, 921, 'TERMO_ADITIVO', 'EFETIVADA', 'TA-02/2026',
                            1, DATE '2026-08-07', 'ORIGINAL', 823, CURRENT_TIMESTAMP
                        )
                        """))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("""
                        INSERT INTO alteracoes_contratuais (
                            instrumento_id, tipo, estado, numero_oficial,
                            ordem_oficial, data_efetivacao, operacao,
                            documento_assinado_id, criado_em
                        ) VALUES (
                            921, 'TERMO_ADITIVO', 'EFETIVADA', 'TA-03/2026',
                            2, DATE '2026-08-08', 'ORIGINAL', 822, CURRENT_TIMESTAMP
                        )
                        """))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void upgradeV13PreservaEstadoInicialHistoricoEValidaFormaDaReferencia() throws Exception {
        String url = "jdbc:h2:mem:sicc-migracao-reconstrucao;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        DataSource bancoIsolado = new DriverManagerDataSource(url, "sa", "");
        Flyway ateVersaoTreze = Flyway.configure()
                .dataSource(bancoIsolado)
                .target("13")
                .load();
        ateVersaoTreze.migrate();
        InstrumentoContratualMigrationFixture.prepararNovoInstrumento(
                bancoIsolado, 730, 830, 930, "PROC-PERSIST-RECONSTRUCAO-015");
        InstrumentoContratualMigrationFixture.inserirInstrumentoAtual(
                bancoIsolado, 930, 730, 830L, "CONVENIO", "CV-RECONSTRUCAO-015", "Objeto");
        JdbcTemplate isolado = new JdbcTemplate(bancoIsolado);
        isolado.update("UPDATE instrumentos_contratuais SET valor_atual = 175000.00 WHERE id = 930");
        isolado.update("""
                INSERT INTO alteracoes_contratuais (
                    id, instrumento_id, tipo, estado, numero_oficial,
                    operacao, criado_em
                ) VALUES (1030, 930, 'TERMO_ADITIVO', 'RASCUNHO',
                    'TA-RASCUNHO-ANTIGO-015/2026', 'ORIGINAL', TIMESTAMP '2026-07-01 10:00:00')
                """);
        isolado.update("""
                INSERT INTO alteracoes_campos (
                    alteracao_id, campo, valor_anterior, valor_novo
                ) VALUES (1030, 'VALOR_ATUAL', '175000.00', '180000.00')
                """);
        isolado.update("""
                INSERT INTO documentos (
                    id, proprietario_tipo, proprietario_id, categoria, titulo,
                    ativo, criado_por_id, criado_em
                ) VALUES (831, 'TERMO_ADITIVO', 1031, 'ASSINADO', 'Termo efetivado',
                    TRUE, 101, TIMESTAMP '2026-08-02 09:00:00')
                """);
        isolado.update("""
                INSERT INTO alteracoes_contratuais (
                    id, instrumento_id, tipo, estado, numero_oficial,
                    ordem_oficial, data_efetivacao, operacao,
                    documento_assinado_id, criado_em
                ) VALUES (1031, 930, 'TERMO_ADITIVO', 'EFETIVADA',
                    'TA-EFETIVADO-015/2026', 1, DATE '2026-08-02', 'ORIGINAL',
                    831, TIMESTAMP '2026-08-01 10:00:00')
                """);
        isolado.update("""
                INSERT INTO alteracoes_campos (
                    alteracao_id, campo, valor_anterior, valor_novo
                ) VALUES (1031, 'VALOR_ATUAL', '150000.00', '175000.00')
                """);
        isolado.update("""
                INSERT INTO registros_auditoria (
                    usuario_id, acao, entidade, entidade_id, sucesso, criado_em
                ) VALUES (101, 'EFETIVAR_ALTERACAO', 'ALTERACAO_CONTRATUAL',
                    1031, TRUE, TIMESTAMP '2026-08-02 10:00:00')
                """);

        Flyway atualizado = Flyway.configure().dataSource(bancoIsolado).load();
        atualizado.migrate();

        assertThat(isolado.queryForObject("""
                SELECT valor_atual FROM instrumentos_estados_iniciais
                WHERE instrumento_id = 930
                """, java.math.BigDecimal.class))
                .isEqualByComparingTo("150000.00");
        assertThatThrownBy(() -> isolado.update("""
                        INSERT INTO alteracoes_contratuais (
                            instrumento_id, tipo, estado, numero_oficial,
                            operacao, referencia_id, criado_em
                        ) VALUES (930, 'TERMO_ADITIVO', 'RASCUNHO',
                            'TA-REFERENCIA-INVALIDA-015', 'ORIGINAL', 1030, CURRENT_TIMESTAMP)
                        """))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void upgradeV14PreservaRelatorioLegadoEProtegeNovosMetadados() {
        String url = "jdbc:h2:mem:sicc-migracao-relatorios;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        DataSource bancoIsolado = new DriverManagerDataSource(url, "sa", "");
        Flyway ateVersaoQuatorze = Flyway.configure()
                .dataSource(bancoIsolado)
                .target("14")
                .load();
        ateVersaoQuatorze.migrate();
        JdbcTemplate isolado = new JdbcTemplate(bancoIsolado);
        isolado.update("""
                INSERT INTO usuarios_internos (
                    id, nome, email, login, senha_hash, perfil, ativo,
                    senha_temporaria, criado_em, versao_acesso
                ) VALUES (1801, 'Autor legado', 'legado-018@sicc.test', 'legado-018',
                    'hash-legado', 'OPERADOR_DIPAC', TRUE, FALSE, CURRENT_TIMESTAMP, 0)
                """);
        isolado.update("""
                INSERT INTO relatorios_gerados (
                    id, tipo, formato, filtros, chave_armazenamento,
                    criado_por_id, criado_em
                ) VALUES (1818, 'CONSOLIDADO', 'CSV', '{}',
                    'relatorios/legado-018', 1801, CURRENT_TIMESTAMP)
                """);

        Flyway atualizado = Flyway.configure().dataSource(bancoIsolado).load();
        atualizado.migrate();

        assertThat(atualizado.info().current().getVersion().getVersion()).isEqualTo("15");
        assertThat(isolado.queryForObject("""
                SELECT chave_armazenamento FROM relatorios_gerados WHERE id = 1818
                """, String.class)).isEqualTo("relatorios/legado-018");
        assertThat(isolado.queryForObject("""
                SELECT COUNT(*) FROM relatorios_gerados
                WHERE id = 1818 AND checksum_sha256 IS NULL AND tamanho_bytes IS NULL
                """, Integer.class)).isOne();
        assertThatThrownBy(() -> isolado.update("""
                INSERT INTO relatorios_gerados (
                    tipo, formato, filtros, chave_armazenamento, checksum_sha256,
                    tamanho_bytes, criado_por_id, criado_em
                ) VALUES ('CONSOLIDADO', 'CSV', '{}', 'relatorios/novo-018',
                    'checksum-invalido', 10, 1801, CURRENT_TIMESTAMP)
                """))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> isolado.update("""
                INSERT INTO relatorios_gerados (
                    tipo, formato, filtros, chave_armazenamento,
                    criado_por_id, criado_em
                ) VALUES ('CONSOLIDADO', 'CSV', '{}', 'relatorios/legado-018',
                    1801, CURRENT_TIMESTAMP)
                """))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
