package com.moments.sicc.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.moments.sicc.repository.RegistroAuditoriaRepository;
import com.moments.sicc.repository.UsuarioInternoRepository;
import java.util.Locale;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@EnabledIfEnvironmentVariable(named = "SICC_POSTGRES_TEST", matches = "true")
@ActiveProfiles("dev")
@SpringBootTest
class PostgresMigrationTest {
    @Autowired
    private Flyway flyway;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private UsuarioInternoRepository usuarios;
    @Autowired
    private RegistroAuditoriaRepository auditoria;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void baselineAplicaEValidaNoPostgresqlReal() throws Exception {
        assertThat(dataSource.getConnection().getMetaData().getDatabaseProductName()).isEqualTo("PostgreSQL");
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("15");
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        assertThat(usuarios.findByLoginIgnoreCase("admin")).hasValueSatisfying(admin -> {
            assertThat(admin.getEmail()).isEqualTo("admin@sicc.test");
            assertThat(admin.getSenhaHash()).startsWith("$2");
            assertThat(admin.isSenhaTemporaria()).isTrue();
            assertThat(admin.getVersaoAcesso()).isZero();
        });
        assertThat(auditoria.findAll())
                .anySatisfy(registro -> {
                    assertThat(registro.getAcao()).isEqualTo("CRIAR_USUARIO");
                    assertThat(registro.getDetalhes()).doesNotContain("Temporaria123!");
                });
    }

    @Test
    void upgradeV3ParaVersaoAtualPadronizaCatalogoPreexistenteNoPostgresqlReal() throws Exception {
        String schema = "sicc_setores_" + UUID.randomUUID().toString().replace("-", "");
        try (var conexao = dataSource.getConnection();
                var comando = conexao.createStatement()) {
            comando.execute("CREATE SCHEMA " + schema);
        }
        try {
            Flyway ateVersaoTres = Flyway.configure()
                    .dataSource(dataSource)
                    .defaultSchema(schema)
                    .schemas(schema)
                    .target("3")
                    .load();
            ateVersaoTres.migrate();
            String tabelaSetores = schema + ".setores";
            CatalogoSetorMigrationFixture.inserirSetorLegado(dataSource, tabelaSetores);

            Flyway atualizado = Flyway.configure()
                    .dataSource(dataSource)
                    .defaultSchema(schema)
                    .schemas(schema)
                    .load();
            atualizado.migrate();
            assertThat(atualizado.info().current().getVersion().getVersion()).isEqualTo("15");

            CatalogoSetorMigrationFixture.verificarIdentidadePadronizadaEUnicidade(
                    dataSource,
                    tabelaSetores
            );
        } finally {
            try (var conexao = dataSource.getConnection();
                    var comando = conexao.createStatement()) {
                comando.execute("DROP SCHEMA " + schema + " CASCADE");
            }
        }
    }

    @Test
    void persistenciaPostgresqlRejeitaCamposObrigatoriosEmBrancoENumeroDuplicado() throws Exception {
        String numero = "PROC-PG-" + UUID.randomUUID().toString().toUpperCase(Locale.ROOT);
        ProcessoAdministrativoMigrationFixture.inserir(dataSource, numero, "DIPAC");
        try {
            assertThatThrownBy(() -> ProcessoAdministrativoMigrationFixture.inserir(
                    dataSource, numero, "Outra origem"))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> ProcessoAdministrativoMigrationFixture.inserir(
                    dataSource, " " + numero.toLowerCase() + " ", "Outra origem"))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> ProcessoAdministrativoMigrationFixture.inserir(
                    dataSource, "   ", "DIPAC"))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> ProcessoAdministrativoMigrationFixture.inserir(
                    dataSource, "PROC-PG-SEM-ORIGEM-" + UUID.randomUUID(), "   "))
                    .isInstanceOf(java.sql.SQLException.class);
        } finally {
            try (var conexao = dataSource.getConnection();
                    var comando = conexao.prepareStatement(
                            "DELETE FROM processos_administrativos WHERE numero = ?")) {
                comando.setString(1, numero);
                comando.executeUpdate();
            }
        }
    }

    @Test
    void postgresqlRejeitaDocumentoAdministrativoForaDeProcesso() {
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

}
