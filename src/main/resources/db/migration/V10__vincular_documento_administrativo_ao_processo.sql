ALTER TABLE documentos
    ADD CONSTRAINT ck_documento_administrativo_processo
        CHECK (categoria <> 'ADMINISTRATIVO' OR proprietario_tipo = 'PROCESSO');
