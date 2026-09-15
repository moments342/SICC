ALTER TABLE documentos
    DROP CONSTRAINT IF EXISTS ck_documento_administrativo_processo;

ALTER TABLE instrumentos_contratuais
    ADD COLUMN documento_assinado_versao_id BIGINT;

UPDATE instrumentos_contratuais instrumento
SET documento_assinado_versao_id = COALESCE(
    (
        SELECT versao.id
        FROM versoes_documento versao
        WHERE versao.documento_id = instrumento.documento_assinado_id
          AND versao.criado_em < (
              SELECT MIN(auditoria.criado_em)
              FROM registros_auditoria auditoria
              WHERE auditoria.acao = 'FORMALIZAR_INSTRUMENTO'
                AND auditoria.entidade = 'INSTRUMENTO_CONTRATUAL'
                AND auditoria.entidade_id = instrumento.id
                AND auditoria.sucesso = TRUE
          )
          AND NOT EXISTS (
              SELECT 1
              FROM versoes_documento versao_simultanea
              WHERE versao_simultanea.documento_id = instrumento.documento_assinado_id
                AND versao_simultanea.criado_em = (
                    SELECT MIN(auditoria.criado_em)
                    FROM registros_auditoria auditoria
                    WHERE auditoria.acao = 'FORMALIZAR_INSTRUMENTO'
                      AND auditoria.entidade = 'INSTRUMENTO_CONTRATUAL'
                      AND auditoria.entidade_id = instrumento.id
                      AND auditoria.sucesso = TRUE
                )
          )
        ORDER BY versao.versao DESC, versao.criado_em DESC, versao.id DESC
        FETCH FIRST 1 ROW ONLY
    ),
    (
        SELECT MIN(versao.id)
        FROM versoes_documento versao
        WHERE versao.documento_id = instrumento.documento_assinado_id
        HAVING COUNT(*) = 1
    )
);

ALTER TABLE instrumentos_contratuais
    ALTER COLUMN documento_assinado_versao_id SET NOT NULL;

ALTER TABLE versoes_documento
    ADD CONSTRAINT uk_versao_documento_id_documento
        UNIQUE (id, documento_id);

ALTER TABLE instrumentos_contratuais
    ADD CONSTRAINT uk_instrumento_documento_assinado_versao
        UNIQUE (documento_assinado_versao_id);

ALTER TABLE instrumentos_contratuais
    ADD CONSTRAINT fk_instrumento_documento_assinado_versao
        FOREIGN KEY (documento_assinado_versao_id, documento_assinado_id)
        REFERENCES versoes_documento (id, documento_id);
