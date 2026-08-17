package com.moments.sicc.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import javax.sql.DataSource;

final class CatalogoSetorMigrationFixture {

    private CatalogoSetorMigrationFixture() {
    }

    static void inserirSetorLegado(DataSource dataSource, String tabelaSetores) throws SQLException {
        try (var conexao = dataSource.getConnection();
                var comando = conexao.prepareStatement("""
                        INSERT INTO %s (sigla, nome, ativo)
                        VALUES (?, ?, true)
                        """.formatted(tabelaSetores))) {
            comando.setString(1, "\t dipac \n");
            comando.setString(2, "\r Divisão   de  Parcerias \t");
            comando.executeUpdate();
        }
    }

    static void verificarIdentidadePadronizadaEUnicidade(
            DataSource dataSource,
            String tabelaSetores
    ) throws SQLException {
        try (var conexao = dataSource.getConnection();
                var consulta = conexao.createStatement();
                var resultado = consulta.executeQuery("""
                        SELECT sigla, nome, sigla_normalizada, nome_normalizado
                        FROM %s
                        """.formatted(tabelaSetores))) {
            assertThat(resultado.next()).isTrue();
            assertThat(resultado.getString("sigla")).isEqualTo("DIPAC");
            assertThat(resultado.getString("nome")).isEqualTo("Divisão de Parcerias");
            assertThat(resultado.getString("sigla_normalizada")).isEqualTo("dipac");
            assertThat(resultado.getString("nome_normalizado")).isEqualTo("divisão de parcerias");
        }
        try (var conexao = dataSource.getConnection();
                var duplicado = conexao.prepareStatement("""
                        INSERT INTO %s (
                            sigla, sigla_normalizada, nome, nome_normalizado, ativo
                        ) VALUES ('OUTRA', 'outra', 'Outro nome', 'divisão de parcerias', true)
                        """.formatted(tabelaSetores))) {
            assertThatThrownBy(duplicado::executeUpdate)
                    .isInstanceOf(SQLException.class);
        }
    }
}
