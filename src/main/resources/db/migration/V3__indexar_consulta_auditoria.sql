CREATE INDEX idx_auditoria_ordem
    ON registros_auditoria (criado_em DESC, id DESC);

CREATE INDEX idx_auditoria_acao_resultado_ordem
    ON registros_auditoria (acao, sucesso, criado_em DESC);

CREATE INDEX idx_auditoria_resultado_ordem
    ON registros_auditoria (sucesso, criado_em DESC);

CREATE INDEX idx_auditoria_usuario_ordem
    ON registros_auditoria (usuario_id, criado_em DESC);
