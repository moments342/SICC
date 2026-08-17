CREATE UNIQUE INDEX uk_alteracao_data_ordem_oficial
    ON alteracoes_contratuais (instrumento_id, data_efetivacao, ordem_oficial);

CREATE UNIQUE INDEX uk_alteracao_documento_oficial
    ON alteracoes_contratuais (documento_assinado_id);
