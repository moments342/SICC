package com.moments.sicc.integration;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;

final class NotificacaoChegadaMigrationFixture {
    private NotificacaoChegadaMigrationFixture() {}

    static void inserirLegado(DataSource banco) throws Exception {
        try (var conexao = banco.getConnection();
                var comando = conexao.createStatement()) {
            comando.executeUpdate("""
                    INSERT INTO usuarios_internos
                        (id, nome, email, login, senha_hash, perfil, ativo,
                         senha_temporaria, criado_em, versao_acesso)
                    VALUES
                        (101, 'Autor', 'autor-migracao@sicc.test', 'autor-migracao',
                         'hash', 'OPERADOR_DIPAC', TRUE, FALSE, CURRENT_TIMESTAMP, 0),
                        (102, 'Destinatario', 'destinatario-migracao@sicc.test',
                         'destinatario-migracao', 'hash', 'OPERADOR_DIPAC',
                         TRUE, FALSE, CURRENT_TIMESTAMP, 0)
                    """);
            comando.executeUpdate("""
                    INSERT INTO setores
                        (id, sigla, nome, ativo, sigla_normalizada, nome_normalizado)
                    VALUES
                        (201, 'DIPAC', 'Divisao de Parcerias e Convenios', TRUE,
                         'dipac', 'divisao de parcerias e convenios')
                    """);
            comando.executeUpdate("""
                    INSERT INTO processos_administrativos
                        (id, numero, origem, status, data_cadastro, ativo)
                    VALUES
                        (301, 'PROC-MIG-NOT-008', 'DIPAC', 'EM_FORMALIZACAO',
                         DATE '2026-07-30', TRUE)
                    """);
            comando.executeUpdate("""
                    INSERT INTO movimentacoes
                        (id, contexto_tipo, contexto_id, data_movimentacao,
                         sequencia_diaria, setor_destino_id, autor_id, inserido_em)
                    VALUES
                        (401, 'FORMALIZACAO', 301, DATE '2026-07-30',
                         1, 201, 101, CURRENT_TIMESTAMP)
                    """);
            comando.executeUpdate("""
                    INSERT INTO notificacoes
                        (destinatario_id, tipo, chave_idempotencia, mensagem, lida, criada_em)
                    VALUES
                        (102, 'CHEGADA_TRAMITACAO', 'CHEGADA:401:102',
                         'Chegada legada', FALSE, CURRENT_TIMESTAMP)
                    """);
        }
    }

    static void verificarVinculo(DataSource banco) throws Exception {
        try (var conexao = banco.getConnection();
                var consulta = conexao.prepareStatement("""
                        SELECT processo_id
                        FROM notificacoes
                        WHERE chave_idempotencia = 'CHEGADA:401:102'
                        """);
                var resultado = consulta.executeQuery()) {
            assertThat(resultado.next()).isTrue();
            assertThat(resultado.getLong("processo_id")).isEqualTo(301L);
            assertThat(resultado.wasNull()).isFalse();
        }
    }
}
