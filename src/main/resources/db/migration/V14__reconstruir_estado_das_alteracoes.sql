CREATE TABLE instrumentos_estados_iniciais (
    instrumento_id BIGINT PRIMARY KEY,
    objeto VARCHAR(1000) NOT NULL,
    descricao VARCHAR(2000),
    natureza VARCHAR(150) NOT NULL,
    coordenador VARCHAR(150) NOT NULL,
    participes VARCHAR(2000) NOT NULL,
    valor_atual DECIMAL(19, 2) NOT NULL,
    vigencia_contratual_final DATE NOT NULL,
    vigencia_ted_final DATE,
    CONSTRAINT fk_estado_inicial_instrumento
        FOREIGN KEY (instrumento_id) REFERENCES instrumentos_contratuais (id)
);

INSERT INTO instrumentos_estados_iniciais (
    instrumento_id, objeto, descricao, natureza, coordenador, participes,
    valor_atual, vigencia_contratual_final, vigencia_ted_final
)
WITH momentos_efetivacao AS (
    SELECT entidade_id AS alteracao_id, MIN(criado_em) AS efetivado_em
    FROM registros_auditoria
    WHERE entidade = 'ALTERACAO_CONTRATUAL'
      AND sucesso = TRUE
      AND acao IN (
          'EFETIVAR_ALTERACAO', 'RETIFICAR_ALTERACAO', 'CANCELAR_ALTERACAO'
      )
    GROUP BY entidade_id
), valores_ordenados AS (
    SELECT a.instrumento_id,
           c.campo,
           c.valor_anterior,
           ROW_NUMBER() OVER (
               PARTITION BY a.instrumento_id, c.campo
               ORDER BY COALESCE(m.efetivado_em, a.criado_em), a.id
           ) AS posicao
    FROM alteracoes_contratuais a
    JOIN alteracoes_campos c ON c.alteracao_id = a.id
    LEFT JOIN momentos_efetivacao m ON m.alteracao_id = a.id
    WHERE a.estado = 'EFETIVADA'
), valores_iniciais AS (
    SELECT instrumento_id,
           MAX(CASE WHEN campo = 'OBJETO' AND posicao = 1 THEN 1 ELSE 0 END) AS tem_objeto,
           MAX(CASE WHEN campo = 'OBJETO' AND posicao = 1 THEN valor_anterior END) AS objeto,
           MAX(CASE WHEN campo = 'DESCRICAO' AND posicao = 1 THEN 1 ELSE 0 END) AS tem_descricao,
           MAX(CASE WHEN campo = 'DESCRICAO' AND posicao = 1 THEN valor_anterior END) AS descricao,
           MAX(CASE WHEN campo = 'NATUREZA' AND posicao = 1 THEN 1 ELSE 0 END) AS tem_natureza,
           MAX(CASE WHEN campo = 'NATUREZA' AND posicao = 1 THEN valor_anterior END) AS natureza,
           MAX(CASE WHEN campo = 'COORDENADOR' AND posicao = 1 THEN 1 ELSE 0 END) AS tem_coordenador,
           MAX(CASE WHEN campo = 'COORDENADOR' AND posicao = 1 THEN valor_anterior END) AS coordenador,
           MAX(CASE WHEN campo = 'PARTICIPES' AND posicao = 1 THEN 1 ELSE 0 END) AS tem_participes,
           MAX(CASE WHEN campo = 'PARTICIPES' AND posicao = 1 THEN valor_anterior END) AS participes,
           MAX(CASE WHEN campo = 'VALOR_ATUAL' AND posicao = 1 THEN 1 ELSE 0 END) AS tem_valor,
           MAX(CASE WHEN campo = 'VALOR_ATUAL' AND posicao = 1 THEN valor_anterior END) AS valor_atual,
           MAX(CASE WHEN campo = 'VIGENCIA_CONTRATUAL_FINAL' AND posicao = 1 THEN 1 ELSE 0 END) AS tem_vigencia_contratual,
           MAX(CASE WHEN campo = 'VIGENCIA_CONTRATUAL_FINAL' AND posicao = 1 THEN valor_anterior END) AS vigencia_contratual_final,
           MAX(CASE WHEN campo = 'VIGENCIA_TED_FINAL' AND posicao = 1 THEN 1 ELSE 0 END) AS tem_vigencia_ted,
           MAX(CASE WHEN campo = 'VIGENCIA_TED_FINAL' AND posicao = 1 THEN valor_anterior END) AS vigencia_ted_final
    FROM valores_ordenados
    GROUP BY instrumento_id
)
SELECT i.id,
       CASE WHEN COALESCE(v.tem_objeto, 0) = 1 THEN v.objeto ELSE i.objeto END,
       CASE WHEN COALESCE(v.tem_descricao, 0) = 1 THEN v.descricao ELSE i.descricao END,
       CASE WHEN COALESCE(v.tem_natureza, 0) = 1 THEN v.natureza ELSE i.natureza END,
       CASE WHEN COALESCE(v.tem_coordenador, 0) = 1 THEN v.coordenador ELSE i.coordenador END,
       CASE WHEN COALESCE(v.tem_participes, 0) = 1 THEN v.participes ELSE i.participes END,
       CASE WHEN COALESCE(v.tem_valor, 0) = 1
           THEN CAST(v.valor_atual AS DECIMAL(19, 2)) ELSE i.valor_atual END,
       CASE WHEN COALESCE(v.tem_vigencia_contratual, 0) = 1
           THEN CAST(v.vigencia_contratual_final AS DATE) ELSE i.vigencia_contratual_final END,
       CASE WHEN COALESCE(v.tem_vigencia_ted, 0) = 1
           THEN CAST(v.vigencia_ted_final AS DATE) ELSE i.vigencia_ted_final END
FROM instrumentos_contratuais i
LEFT JOIN valores_iniciais v ON v.instrumento_id = i.id;

ALTER TABLE alteracoes_contratuais
    ADD CONSTRAINT ck_alteracao_operacao_referencia
        CHECK (
            (operacao = 'ORIGINAL' AND referencia_id IS NULL)
            OR
            (operacao IN ('RETIFICACAO', 'CANCELAMENTO') AND referencia_id IS NOT NULL)
        );

ALTER TABLE alteracoes_contratuais
    ADD CONSTRAINT ck_alteracao_referencia_distinta
        CHECK (referencia_id IS NULL OR referencia_id <> id);

CREATE INDEX idx_alteracao_referencia
    ON alteracoes_contratuais (referencia_id);
