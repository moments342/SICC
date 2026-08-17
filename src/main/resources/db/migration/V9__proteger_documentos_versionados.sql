ALTER TABLE documentos
    ADD CONSTRAINT ck_documento_proprietario_tipo
        CHECK (proprietario_tipo IN ('PROCESSO', 'INSTRUMENTO', 'TERMO_ADITIVO', 'APOSTILAMENTO'));

ALTER TABLE documentos
    ADD CONSTRAINT ck_documento_categoria
        CHECK (categoria IN ('ADMINISTRATIVO', 'ASSINADO'));

ALTER TABLE documentos
    ADD CONSTRAINT ck_documento_titulo_preenchido
        CHECK (CHAR_LENGTH(TRIM(titulo)) > 0);

ALTER TABLE versoes_documento
    ADD CONSTRAINT ck_versao_documento_numero
        CHECK (versao > 0);

ALTER TABLE versoes_documento
    ADD CONSTRAINT ck_versao_documento_tamanho
        CHECK (tamanho > 0 AND tamanho <= 20971520);

ALTER TABLE versoes_documento
    ADD CONSTRAINT ck_versao_documento_checksum
        CHECK (CHAR_LENGTH(checksum_sha256) = 64 AND checksum_sha256 = LOWER(checksum_sha256));

ALTER TABLE versoes_documento
    ADD CONSTRAINT ck_versao_documento_chave_preenchida
        CHECK (CHAR_LENGTH(TRIM(chave_armazenamento)) > 0);

ALTER TABLE versoes_documento
    ADD CONSTRAINT ck_versao_documento_tipo_mime
        CHECK (tipo_mime IN (
            'application/pdf',
            'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
            'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
            'text/csv'
        ));

CREATE INDEX idx_documentos_proprietario_ativo
    ON documentos (proprietario_tipo, proprietario_id, ativo);

CREATE INDEX idx_versoes_documento_ordem
    ON versoes_documento (documento_id, versao DESC);
