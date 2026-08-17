ALTER TABLE instrumentos_contratuais
    ADD COLUMN documento_assinado_id BIGINT;

UPDATE instrumentos_contratuais instrumento
SET documento_assinado_id = (
    SELECT MIN(documento.id)
    FROM documentos documento
    WHERE documento.proprietario_tipo = 'INSTRUMENTO'
      AND documento.proprietario_id = instrumento.id
      AND documento.categoria = 'ASSINADO'
);

ALTER TABLE instrumentos_contratuais
    ALTER COLUMN documento_assinado_id SET NOT NULL;

ALTER TABLE instrumentos_contratuais
    ADD CONSTRAINT fk_instrumento_documento_assinado
        FOREIGN KEY (documento_assinado_id) REFERENCES documentos (id);

ALTER TABLE instrumentos_contratuais
    ADD CONSTRAINT uk_instrumento_documento_assinado
        UNIQUE (documento_assinado_id);

ALTER TABLE instrumentos_contratuais
    ADD CONSTRAINT ck_instrumento_tipo
        CHECK (tipo IN (
            'CONTRATO_GESTAO',
            'CONVENIO',
            'ACORDO_PARCERIA',
            'ACORDO_COOPERACAO_TECNICA'
        ));

ALTER TABLE instrumentos_contratuais
    ADD CONSTRAINT ck_instrumento_campos_preenchidos
        CHECK (
            CHAR_LENGTH(TRIM(numero)) > 0
            AND CHAR_LENGTH(TRIM(objeto)) > 0
            AND CHAR_LENGTH(TRIM(natureza)) > 0
            AND CHAR_LENGTH(TRIM(coordenador)) > 0
            AND CHAR_LENGTH(TRIM(participes)) > 0
        );

ALTER TABLE instrumentos_contratuais
    ADD CONSTRAINT ck_instrumento_valor_nao_negativo
        CHECK (valor_atual >= 0);
