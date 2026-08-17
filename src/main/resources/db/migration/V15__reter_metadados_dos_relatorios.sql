ALTER TABLE relatorios_gerados
    ADD COLUMN checksum_sha256 VARCHAR(64);

ALTER TABLE relatorios_gerados
    ADD COLUMN tamanho_bytes BIGINT;

ALTER TABLE relatorios_gerados ADD CONSTRAINT ck_relatorio_checksum_sha256
    CHECK (checksum_sha256 IS NULL OR LENGTH(checksum_sha256) = 64);

ALTER TABLE relatorios_gerados ADD CONSTRAINT ck_relatorio_tamanho
    CHECK (tamanho_bytes IS NULL OR tamanho_bytes >= 0);

ALTER TABLE relatorios_gerados ADD CONSTRAINT uk_relatorio_chave_armazenamento
    UNIQUE (chave_armazenamento);
