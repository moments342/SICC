# SICC

O SICC acompanha processos administrativos e seus instrumentos contratuais no contexto universitário, preservando a distinção entre o processo e o instrumento que dele resulta.

## Language

**Processo Administrativo**:
Unidade de acompanhamento institucional identificada por um número único. Reúne origem, número do projeto, andamento e dados de cadastro; durante a tramitação de formalização pode ainda não possuir instrumento contratual e, depois de formalizado, possui exatamente um.
_Avoid_: Processo ou contrato, contrato

**Instrumento Contratual**:
Resultado da formalização de um processo administrativo, classificado como contrato de gestão, convênio, acordo de parceria ou acordo de cooperação técnica e vinculado exclusivamente a esse processo. Reúne número do instrumento, objeto, descrição, natureza, coordenador, partícipes, vigência contratual, vigência do TED e valor total; pode receber vários apostilamentos e termos aditivos, mas sua identidade não pode ser modificada depois da efetivação.
_Avoid_: Processo

**Tipo de Instrumento**:
Classificação pertencente ao conjunto fechado formado por contrato de gestão, convênio, acordo de parceria e acordo de cooperação técnica. Novos tipos não podem ser cadastrados por usuários do sistema.
_Avoid_: Contrato genérico, TED

**Vigência Contratual**:
Data final de validade das condições do instrumento contratual. A data inicial não faz parte do escopo do SICC.
_Avoid_: Vigência do TED

**Vigência do TED**:
Data final de disponibilidade do recurso proveniente do governo associado ao instrumento contratual. A data inicial não faz parte do escopo do SICC, e TED não é um tipo de instrumento.
_Avoid_: Tipo de instrumento, vigência contratual

**Situação de Vigência**:
Avaliação independente da vigência contratual e da vigência do TED como válida, próxima do vencimento, vencida ou não informada quando a data está ausente. A janela de proximidade inclui a data corrente até 120 dias corridos à frente; uma data anterior à corrente está vencida. O vencimento contratual muda o status do processo para concluído, enquanto o vencimento do TED não altera o status.
_Avoid_: Situação única para as duas vigências

**Processo Concluído**:
Processo cujo instrumento possui data final de vigência contratual anterior à data corrente, com status calculado automaticamente como concluído. O vencimento da vigência do TED, isoladamente, não conclui o processo.
_Avoid_: Tramitação encerrada, TED vencido

**Status do Processo**:
Estado automático classificado como em formalização, em vigência ou concluído. A formalização cria a transição para em vigência, o fim da vigência contratual produz a conclusão e uma alteração válida que prorrogue a vigência pode reativar o processo.
_Avoid_: Etapa da tramitação, status preenchido manualmente

**Notificação Interna**:
Aviso persistente exibido somente dentro do SICC, com estado de leitura. No escopo atual, o sistema não envia notificações por e-mail, SMS ou outro canal externo.
_Avoid_: E-mail, alerta externo

**Notificação de Chegada**:
Notificação interna gerada quando uma tramitação chega a um novo destino. É enviada ao responsável DIPAC pelo processo ou, se não houver responsável, a todos os usuários ativos da DIPAC, exceto o autor da movimentação, ainda que o setor de destino seja outra unidade administrativa.
_Avoid_: Notificação ao remetente

**Tramitação de Formalização**:
Histórico contínuo das movimentações do processo administrativo pelas unidades responsáveis por sua formalização, sem modelo, sequência, etapas obrigatórias ou estado terminal previamente configurado. O processo ainda pode não possuir um instrumento contratual durante esse percurso.
_Avoid_: Instrumento contratual

**Formalização**:
Constituição do único instrumento contratual do processo administrativo mediante o registro da data de formalização e a anexação do documento assinado. A formalização não encerra nem apaga o histórico de tramitação.
_Avoid_: Encerramento da tramitação, cadastro inicial do processo

**Tramitação de Alteração**:
Histórico contínuo das movimentações de um termo aditivo ou apostilamento pelas unidades responsáveis, sem modelo, sequência, etapas obrigatórias ou estado terminal previamente configurado. Seu histórico permanece separado da formalização inicial e das demais alterações do instrumento.
_Avoid_: Tramitação de formalização

**Tramitação Livre**:
Forma de tramitação na qual cada movimentação pode escolher livremente qualquer setor ativo como próximo destino, sem depender de um fluxo previamente configurado.
_Avoid_: Modelo de fluxo, etapa obrigatória

**Setor**:
Unidade administrativa pertencente a um catálogo padronizado, que pode estar ativa ou inativa. Setores ativos podem ser escolhidos livremente como destino de tramitações, mesmo que não possuam usuários do SICC.
_Avoid_: Nome de setor digitado livremente

**Setor Atual**:
Setor de destino da movimentação mais recente de uma tramitação. É derivado do histórico e substitui o preenchimento de uma etapa atual no processo.
_Avoid_: Etapa atual, setor duplicado no processo

**Permanência no Setor**:
Quantidade de dias corridos entre a chegada de uma tramitação a um setor e sua próxima movimentação para outro setor. Permanências abertas são medidas até a data atual, e movimentações dentro do mesmo setor não reiniciam o intervalo.
_Avoid_: Quantidade de movimentações

**Movimentação de Tramitação**:
Registro imutável de uma movimentação em determinada data, sem horário. Pode representar uma data histórica, mas nunca futura; uma sequência automática preserva a ordem entre movimentações do mesmo dia. Correções são novos registros, e a auditoria preserva quando e por quem o dado foi inserido.
_Avoid_: Movimentação editável, data futura

**Tempo de Tramitação Inicial**:
Intervalo entre o cadastro do processo e a formalização do instrumento ou, enquanto não houver formalização, o momento atual. Todos os processos participam do cálculo da média.
_Avoid_: Duração total do contrato

**Usuário Interno**:
Pessoa da DIPAC com acesso autenticado às funcionalidades internas do SICC, classificada como administrador DIPAC ou operador DIPAC. Outros setores não possuem usuários no escopo atual.
_Avoid_: Usuário de qualquer setor

**Administrador DIPAC**:
Usuário interno responsável por gerenciar usuários e setores, com acesso às demais funcionalidades internas do SICC.
_Avoid_: Operador DIPAC

**Operador DIPAC**:
Usuário interno responsável pela operação de processos, instrumentos, tramitações, documentos, relatórios e notificações, sem acesso ao gerenciamento de usuários.
_Avoid_: Administrador DIPAC

**Perfil de Acesso**:
Conjunto fixo de permissões atribuído a um usuário interno como administrador DIPAC ou operador DIPAC. Permissões não são personalizadas por usuário, módulo ou funcionalidade.
_Avoid_: Permissão individual

**Login**:
Identificador único e imutável utilizado com a senha para autenticar um usuário interno. O e-mail também é único, mas não pode ser utilizado no lugar do login.
_Avoid_: E-mail, nome do usuário

**Senha Temporária**:
Senha definida por um administrador no cadastro ou na redefinição do acesso, que deve obrigatoriamente ser substituída pelo usuário no próximo login. Senhas anteriores não podem ser recuperadas ou exibidas.
_Avoid_: Recuperação de senha, senha permanente criada pelo administrador

**Consulta Pública Simplificada**:
Consulta sem autenticação, paginada e pesquisável por número do processo, com filtros por tipo, origem, status e situação das vigências. Todos os processos participam desde o cadastro; antes da formalização, campos do instrumento são apresentados como `Ainda não formalizado`. A consulta apresenta somente número do processo, tipo do instrumento, origem, coordenador, status, data final da vigência contratual e data final da vigência do TED; não expõe documentos, valor, partícipes, usuários, tramitações detalhadas ou registros de auditoria.
_Avoid_: Acesso interno, consulta completa

**Identidade do Instrumento**:
Combinação entre o número, o tipo e o processo administrativo vinculado ao instrumento contratual. Depois da efetivação, qualquer mudança nessa combinação representa um novo instrumento.
_Avoid_: Condição contratual

**Estado Atual do Instrumento**:
Conjunto dos dados vigentes do instrumento contratual. Para cada atributo alterado, prevalece o valor da alteração válida com a data de efetivação mais recente; em caso de empate, prevalece a maior ordem oficial do documento, sempre independentemente da ordem de cadastro no sistema.
_Avoid_: Último registro cadastrado

**Ordem de Prevalência**:
Sequência oficial utilizada para decidir qual alteração prevalece quando documentos efetivados na mesma data modificam o mesmo atributo.
_Avoid_: Ordem de cadastro

**Apostilamento**:
Registro próprio vinculado a um instrumento contratual, utilizado para modificar somente dados de natureza não contratual já previstos no escopo atual do SICC. Pode ser editado enquanto estiver em rascunho; torna-se efetivado quando recebe uma data de efetivação e o documento assinado, passando a preservar a alteração e atualizar os dados correntes correspondentes.
_Avoid_: Anexo, versão do contrato

**Termo Aditivo**:
Registro próprio vinculado a um instrumento contratual, utilizado para modificar qualquer condição contratual, exceto a identidade do instrumento. Pode ser editado enquanto estiver em rascunho; torna-se efetivado quando recebe uma data de efetivação e o documento assinado, passando a preservar a alteração e atualizar os dados correntes correspondentes do instrumento.
_Avoid_: Anexo, versão do contrato

**Retificação**:
Novo registro que corrige uma alteração já efetivada sem apagar ou reescrever o registro original.
_Avoid_: Edição do histórico

**Cancelamento**:
Novo registro que invalida os efeitos de uma alteração já efetivada sem apagar ou reescrever o registro original.
_Avoid_: Exclusão da alteração

**Projeto Básico**:
Documento externo ao escopo atual do SICC. O sistema não armazena o projeto básico nem informações adicionais extraídas dele.
_Avoid_: Cadastro de projeto básico

**Documento Anexo**:
Documento digital associado a um processo administrativo, instrumento contratual, termo aditivo ou apostilamento, identificado por seus metadados e composto por uma ou mais versões. Movimentações de tramitação não possuem documentos próprios; um documento pode ser desativado, mas não é apagado com seu histórico.
_Avoid_: Campo textual de anexos

**Versão de Documento**:
Conteúdo imutável de um documento anexo em determinado momento, limitado a 20 MB e validado por seu formato real. A versão mais recente é a atual, e as anteriores permanecem consultáveis e baixáveis pela DIPAC. Uma nova versão só é confirmada quando seu conteúdo, seus metadados e a auditoria de sucesso pertencem à mesma operação; em rollback, o conteúdo recém-gravado é removido por compensação.
_Avoid_: Sobrescrita de arquivo

**Documento Assinado**:
Documento oficial em formato PDF exigido para formalizar um instrumento ou efetivar um termo aditivo ou apostilamento.
_Avoid_: DOCX, planilha

**Documento Administrativo**:
Documento preparatório ou administrativo associado a um Processo Administrativo, Instrumento Contratual, Termo Aditivo ou Apostilamento, aceito nos formatos PDF, DOCX, XLSX ou CSV.
_Avoid_: Documento assinado

**Registro de Auditoria**:
Registro imutável de autenticações, mudanças de estado, tramitações, operações com documentos, relatórios e administração de usuários ou setores. Consultas comuns e navegação pública não geram um registro individual.
_Avoid_: Histórico editável, log de toda leitura pública

**Relatório**:
Exportação filtrável em PDF, XLSX ou CSV, classificada como relatório anual de processos, instrumentos por tipo, histórico de tramitações, vigências ou dados consolidados.
_Avoid_: Dashboard

**Dashboard Gerencial**:
Visão consolidada de processos por status, percentual concluído, vigências, valor vigente, tipos de instrumento, tempo por setor, gargalos e formalizações ou conclusões mensais.
_Avoid_: Relatório exportado

**Valor Total Vigente**:
Soma dos valores correntes dos instrumentos em vigência, considerando termos aditivos válidos. Exclui processos em formalização, concluídos e valores históricos substituídos.
_Avoid_: Soma histórica de alterações
