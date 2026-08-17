package com.moments.sicc.integration;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;

final class InstrumentoContratualMigrationFixture {
    private InstrumentoContratualMigrationFixture() {}

    static void inserirLegado(DataSource banco) throws Exception {
        try (var conexao = banco.getConnection();
                var comando = conexao.createStatement()) {
            comando.executeUpdate("""
                    INSERT INTO usuarios_internos
                        (id, nome, email, login, senha_hash, perfil, ativo,
                         senha_temporaria, criado_em, versao_acesso)
                    VALUES
                        (101, 'Autor', 'autor-formalizacao@sicc.test',
                         'autor-formalizacao', 'hash', 'OPERADOR_DIPAC', TRUE,
                         FALSE, CURRENT_TIMESTAMP, 0)
                    """);
            inserirProcesso(conexao, 301, "PROC-MIG-FORMAL-010");
            inserirDocumento(conexao, 401, 601);
            comando.executeUpdate("""
                    INSERT INTO versoes_documento
                        (id, documento_id, versao, nome_arquivo, tipo_mime, tamanho,
                         checksum_sha256, chave_armazenamento, criado_em, criado_por_id)
                    VALUES
                        (501, 401, 1, 'instrumento.pdf', 'application/pdf', 18,
                         REPEAT('a', 64), 'documentos/401/v1.pdf', CURRENT_TIMESTAMP, 101)
                    """);
            inserirInstrumento(
                    conexao, 601, 301, null, "CONVENIO", "CV-MIG-010", "Objeto");
        }
    }

    static void verificarBackfill(DataSource banco) throws Exception {
        try (var conexao = banco.getConnection();
                var consulta = conexao.prepareStatement("""
                        SELECT documento_assinado_id
                        FROM instrumentos_contratuais
                        WHERE id = 601
                        """);
                var resultado = consulta.executeQuery()) {
            assertThat(resultado.next()).isTrue();
            assertThat(resultado.getLong("documento_assinado_id")).isEqualTo(401L);
            assertThat(resultado.wasNull()).isFalse();
        }
    }

    static void prepararNovoInstrumento(
            DataSource banco,
            long processoId,
            long documentoId,
            long instrumentoId,
            String numeroProcesso) throws Exception {
        try (var conexao = banco.getConnection()) {
            garantirAutor(conexao);
            inserirProcesso(conexao, processoId, numeroProcesso);
            inserirDocumento(conexao, documentoId, instrumentoId);
        }
    }

    static void inserirInstrumentoAtual(
            DataSource banco,
            long instrumentoId,
            long processoId,
            Long documentoId,
            String tipo,
            String numero,
            String objeto) throws Exception {
        try (var conexao = banco.getConnection()) {
            inserirInstrumento(
                    conexao, instrumentoId, processoId, documentoId, tipo, numero, objeto);
        }
    }

    private static void inserirProcesso(
            java.sql.Connection conexao,
            long processoId,
            String numero) throws Exception {
        try (var comando = conexao.prepareStatement("""
                INSERT INTO processos_administrativos
                    (id, numero, origem, status, data_cadastro, ativo)
                VALUES (?, ?, 'DIPAC', 'EM_FORMALIZACAO', DATE '2026-08-01', TRUE)
                """)) {
            comando.setLong(1, processoId);
            comando.setString(2, numero);
            comando.executeUpdate();
        }
    }

    private static void garantirAutor(java.sql.Connection conexao) throws Exception {
        try (var consulta = conexao.prepareStatement(
                "SELECT COUNT(*) FROM usuarios_internos WHERE id = 101");
                var resultado = consulta.executeQuery()) {
            resultado.next();
            if (resultado.getInt(1) > 0) return;
        }
        try (var comando = conexao.createStatement()) {
            comando.executeUpdate("""
                    INSERT INTO usuarios_internos
                        (id, nome, email, login, senha_hash, perfil, ativo,
                         senha_temporaria, criado_em, versao_acesso)
                    VALUES
                        (101, 'Autor da fixture', 'autor-fixture@sicc.test',
                         'autor-fixture', 'hash', 'OPERADOR_DIPAC', TRUE,
                         FALSE, CURRENT_TIMESTAMP, 0)
                    """);
        }
    }

    private static void inserirDocumento(
            java.sql.Connection conexao,
            long documentoId,
            long instrumentoId) throws Exception {
        try (var comando = conexao.prepareStatement("""
                INSERT INTO documentos
                    (id, proprietario_tipo, proprietario_id, categoria, titulo,
                     ativo, criado_por_id, criado_em)
                VALUES (?, 'INSTRUMENTO', ?, 'ASSINADO', 'Instrumento assinado',
                    TRUE, 101, CURRENT_TIMESTAMP)
                """)) {
            comando.setLong(1, documentoId);
            comando.setLong(2, instrumentoId);
            comando.executeUpdate();
        }
    }

    private static void inserirInstrumento(
            java.sql.Connection conexao,
            long instrumentoId,
            long processoId,
            Long documentoId,
            String tipo,
            String numero,
            String objeto) throws Exception {
        String colunasDocumento = documentoId == null ? "" : ", documento_assinado_id";
        String valorDocumento = documentoId == null ? "" : ", ?";
        try (var comando = conexao.prepareStatement("""
                INSERT INTO instrumentos_contratuais
                    (id, processo_id, numero, tipo, objeto, descricao, natureza,
                     coordenador, participes, valor_atual, vigencia_contratual_final,
                     vigencia_ted_final, data_formalizacao%s)
                VALUES (?, ?, ?, ?, ?, 'Descricao', 'Administrativa', 'Maria Silva',
                    'UFGD', 1000.00, DATE '2027-08-01', DATE '2027-04-30',
                    DATE '2026-08-01'%s)
                """.formatted(colunasDocumento, valorDocumento))) {
            comando.setLong(1, instrumentoId);
            comando.setLong(2, processoId);
            comando.setString(3, numero);
            comando.setString(4, tipo);
            comando.setString(5, objeto);
            if (documentoId != null) comando.setLong(6, documentoId);
            comando.executeUpdate();
        }
    }
}
