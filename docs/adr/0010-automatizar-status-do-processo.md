# Automatizar o status do processo

O status do processo será uma máquina de estados fechada: `EM_FORMALIZACAO` antes da criação do instrumento, `EM_VIGENCIA` após a formalização enquanto a vigência contratual estiver válida e `CONCLUIDO` depois de seu término. Um termo aditivo efetivado que prorrogue a vigência para uma data futura reativa automaticamente o processo. O vencimento do TED não altera esse status, e usuários não o preenchem manualmente.

As consultas calculam o status pela vigência contratual na referência temporal da operação, sem depender da próxima execução do scheduler para expor a mudança de dia. Listagens, dashboard e relatórios compartilham uma referência por operação; a emissão de um relatório usa a mesma data para filtros, conteúdo e permanências.
