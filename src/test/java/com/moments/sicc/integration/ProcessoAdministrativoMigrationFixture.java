package com.moments.sicc.integration;

import java.sql.Date;
import java.time.LocalDate;
import javax.sql.DataSource;

final class ProcessoAdministrativoMigrationFixture {
    private ProcessoAdministrativoMigrationFixture() {}

    static void inserir(DataSource banco, String numero, String origem) throws Exception {
        try (var conexao = banco.getConnection();
                var comando = conexao.prepareStatement("""
                        INSERT INTO processos_administrativos
                            (numero, origem, status, data_cadastro, ativo)
                        VALUES (?, ?, 'EM_FORMALIZACAO', ?, TRUE)
                        """)) {
            comando.setString(1, numero);
            comando.setString(2, origem);
            comando.setDate(3, Date.valueOf(LocalDate.of(2026, 7, 30)));
            comando.executeUpdate();
        }
    }
}
