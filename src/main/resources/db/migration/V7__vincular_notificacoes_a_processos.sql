ALTER TABLE notificacoes ADD COLUMN processo_id BIGINT;

ALTER TABLE notificacoes
    ADD CONSTRAINT fk_notificacao_processo
    FOREIGN KEY (processo_id) REFERENCES processos_administrativos (id);

CREATE INDEX idx_notificacoes_processo ON notificacoes (processo_id);
