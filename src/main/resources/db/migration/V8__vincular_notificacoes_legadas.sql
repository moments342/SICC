UPDATE notificacoes
SET processo_id = (
    SELECT CASE
        WHEN m.contexto_tipo = 'FORMALIZACAO' THEN m.contexto_id
        ELSE i.processo_id
    END
    FROM movimentacoes m
    LEFT JOIN alteracoes_contratuais a
        ON m.contexto_tipo IN ('TERMO_ADITIVO', 'APOSTILAMENTO')
        AND a.id = m.contexto_id
    LEFT JOIN instrumentos_contratuais i
        ON i.id = a.instrumento_id
    WHERE notificacoes.chave_idempotencia =
        'CHEGADA:' || CAST(m.id AS VARCHAR) || ':' ||
        CAST(notificacoes.destinatario_id AS VARCHAR)
)
WHERE processo_id IS NULL
  AND tipo = 'CHEGADA_TRAMITACAO'
  AND EXISTS (
      SELECT 1
      FROM movimentacoes m
      WHERE notificacoes.chave_idempotencia =
          'CHEGADA:' || CAST(m.id AS VARCHAR) || ':' ||
          CAST(notificacoes.destinatario_id AS VARCHAR)
  );
