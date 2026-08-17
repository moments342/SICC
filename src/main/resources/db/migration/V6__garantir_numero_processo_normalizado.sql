UPDATE processos_administrativos
SET numero = UPPER(TRIM(numero));

ALTER TABLE processos_administrativos
    ADD CONSTRAINT ck_processo_numero_normalizado
        CHECK (numero = UPPER(TRIM(numero)));
