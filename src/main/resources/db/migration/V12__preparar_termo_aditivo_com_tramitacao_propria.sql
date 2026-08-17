ALTER TABLE alteracoes_contratuais
    ADD CONSTRAINT ck_alteracao_tipo
        CHECK (tipo IN ('TERMO_ADITIVO', 'APOSTILAMENTO'));

ALTER TABLE alteracoes_contratuais
    ADD CONSTRAINT ck_alteracao_estado
        CHECK (estado IN ('RASCUNHO', 'EFETIVADA'));

ALTER TABLE alteracoes_contratuais
    ADD CONSTRAINT ck_alteracao_operacao
        CHECK (operacao IN ('ORIGINAL', 'RETIFICACAO', 'CANCELAMENTO'));

ALTER TABLE alteracoes_contratuais
    ADD CONSTRAINT ck_alteracao_numero_preenchido
        CHECK (CHAR_LENGTH(TRIM(numero_oficial)) > 0);

ALTER TABLE alteracoes_contratuais
    ADD CONSTRAINT ck_alteracao_estado_oficial
        CHECK (
            (estado = 'RASCUNHO'
                AND ordem_oficial IS NULL
                AND data_efetivacao IS NULL
                AND documento_assinado_id IS NULL)
            OR
            (estado = 'EFETIVADA'
                AND ordem_oficial IS NOT NULL
                AND ordem_oficial > 0
                AND data_efetivacao IS NOT NULL
                AND documento_assinado_id IS NOT NULL)
        );

ALTER TABLE alteracoes_campos
    ADD CONSTRAINT ck_alteracao_campo_catalogo
        CHECK (campo IN (
            'OBJETO',
            'DESCRICAO',
            'NATUREZA',
            'COORDENADOR',
            'PARTICIPES',
            'VALOR_ATUAL',
            'VIGENCIA_CONTRATUAL_FINAL',
            'VIGENCIA_TED_FINAL'
        ));

CREATE INDEX idx_alteracao_instrumento_estado
    ON alteracoes_contratuais (instrumento_id, estado, id);
