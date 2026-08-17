ALTER TABLE setores ADD COLUMN sigla_normalizada VARCHAR(30);
ALTER TABLE setores ADD COLUMN nome_normalizado VARCHAR(150);

UPDATE setores
SET sigla = UPPER(TRIM(REGEXP_REPLACE(TRIM(sigla), '\s+', ' ', 'g'))),
    nome = TRIM(REGEXP_REPLACE(TRIM(nome), '\s+', ' ', 'g'));

UPDATE setores
SET sigla_normalizada = LOWER(sigla),
    nome_normalizado = LOWER(nome);

ALTER TABLE setores ALTER COLUMN sigla_normalizada SET NOT NULL;
ALTER TABLE setores ALTER COLUMN nome_normalizado SET NOT NULL;

CREATE UNIQUE INDEX uk_setores_sigla_normalizada
    ON setores (sigla_normalizada);
CREATE UNIQUE INDEX uk_setores_nome_normalizado
    ON setores (nome_normalizado);
