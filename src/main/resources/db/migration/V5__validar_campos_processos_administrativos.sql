UPDATE processos_administrativos
SET numero = UPPER(TRIM(numero)),
    origem = TRIM(origem),
    numero_projeto = NULLIF(TRIM(numero_projeto), '');

ALTER TABLE processos_administrativos
    ADD CONSTRAINT ck_processo_numero_preenchido
        CHECK (CHAR_LENGTH(TRIM(numero)) > 0);

ALTER TABLE processos_administrativos
    ADD CONSTRAINT ck_processo_origem_preenchida
        CHECK (CHAR_LENGTH(TRIM(origem)) > 0);

CREATE INDEX idx_processos_origem
    ON processos_administrativos (origem);
