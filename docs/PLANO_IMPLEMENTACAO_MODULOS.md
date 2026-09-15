# Plano de Implementação dos Módulos do SICC

> **Registro histórico de planejamento, superado pelo contrato implementado.** As propostas abaixo foram formuladas antes da implementação. Bibliotecas, rotas de navegação e endpoints sugeridos não constituem requisitos de entrega atuais. Para o domínio vigente, consulte `CONTEXT.md`, os ADRs e os RF/RNF finais do TCC; para contratos HTTP e dependências, consulte os controllers, DTOs, testes de API e `frontend/package.json`. Em particular, a implementação não adota React Router, Axios, React Hook Form ou Zod, nem promete os endpoints de refresh/logout/me ou os caminhos de download propostos neste plano. A certificação atual está em `docs/VALIDACAO_PRE_ETAPA_2.md`.

## 1. Objetivo

Implementar os cinco módulos descritos no TCC como uma aplicação web com back-end Spring Boot, front-end React/Vite e PostgreSQL, respeitando as decisões consolidadas em `CONTEXT.md` e nos ADRs.

Este plano substitui, para a implementação, trechos do TCC que foram refinados durante o grill. As divergências estão listadas em `DIVERGENCIAS_TCC_IMPLEMENTACAO.md`.

## 2. Estado atual verificado

- Projeto Spring Boot 3.5.0 com Java 25 na raiz.
- Persistência JPA, Flyway, PostgreSQL e H2 configurados.
- Entidades iniciais: `Usuario`, `Processo`, `Tramitacao`, `DocumentoAnexo` e `LogAuditoria`.
- Cinco migrações Flyway de protótipo.
- 36 testes passando, sem falhas.
- Ainda não existem controllers REST, DTOs, tratamento HTTP de erros, Spring Security, JWT, notificações, relatórios ou front-end.
- O código atual mistura processo e instrumento, sobrescreve versões de documentos, usa status e etapas livres e combina os dois tipos de vigência em um único resultado.
- O esquema atual não contém dados reais e pode ser reconstruído.

## 3. Arquitetura-alvo

```text
SICC/
├── pom.xml
├── src/
│   ├── main/java/com/moments/sicc/
│   ├── main/resources/db/migration/
│   └── test/java/com/moments/sicc/
├── frontend/
│   ├── src/
│   ├── package.json
│   └── vite.config.*
├── storage/                 # apenas desenvolvimento; ignorado pelo Git
├── CONTEXT.md
└── docs/
    ├── adr/
    ├── PLANO_IMPLEMENTACAO_MODULOS.md
    └── DIVERGENCIAS_TCC_IMPLEMENTACAO.md
```

### Back-end

- API REST versionada em `/api/v1`.
- Camadas por módulo: `domain`, `application`, `repository` e `web`.
- Entidades JPA não serão expostas diretamente pela API.
- DTOs específicos para entrada, resposta pública e resposta interna.
- `Clock` injetável para regras de data e testes determinísticos.
- Transações na camada de aplicação.
- Erros padronizados com `ProblemDetail`.
- Spring Security e JWT conforme o TCC.

### Front-end

- React, Vite, React Router, Axios, React Hook Form e Zod.
- Rotas públicas e internas separadas.
- Estado de autenticação em memória; renovação de sessão protegida pelo back-end.
- Componentes e telas baseados nos mockups do TCC, corrigidos conforme o domínio final.

### Persistência

- PostgreSQL em desenvolvimento/produção.
- H2 para testes rápidos de domínio e integração.
- Testes de migração também executados contra PostgreSQL antes da entrega.
- Nova linha de base Flyway, pois o esquema atual é descartável.
- Arquivos fora do banco; metadados e vínculos no PostgreSQL.

## 4. Modelo de dados proposto

### Segurança e administração

**usuarios**

- id
- nome
- email único
- login único e imutável
- senha_hash
- perfil: `ADMINISTRADOR_DIPAC` ou `OPERADOR_DIPAC`
- senha_temporaria
- ativo
- data_criacao
- ultimo_acesso

**setores**

- id
- sigla única
- nome único
- ativo

Somente pessoas da DIPAC possuem contas. Os demais setores existem como destinos de tramitação.

### Processo e instrumento

**processos**

- id
- numero_processo único
- origem
- numero_projeto
- status: `EM_FORMALIZACAO`, `EM_VIGENCIA` ou `CONCLUIDO`
- responsavel_dipac_id
- ativo
- data_cadastro

**instrumentos**

- id
- processo_id único
- tipo: `CONTRATO_GESTAO`, `CONVENIO`, `ACORDO_PARCERIA` ou `ACORDO_COOPERACAO_TECNICA`
- numero_instrumento
- objeto
- descricao
- natureza
- coordenador
- participes
- data_fim_vigencia_contratual
- data_fim_vigencia_ted
- valor_total_atual
- data_formalizacao

A identidade do instrumento é formada por processo, tipo e número. Depois da formalização ela não pode ser alterada.

### Tramitações

**tramitacoes**

- id
- processo_id
- termo_aditivo_id opcional
- apostilamento_id opcional
- restrição garantindo um único objeto de alteração ou a formalização inicial

**movimentacoes**

- id
- tramitacao_id
- data_movimentacao
- sequencia_do_dia
- setor_origem_id
- setor_destino_id
- responsavel_dipac_id opcional
- acao_realizada
- observacao
- inserido_por_id
- inserido_em

Não haverá etapa atual nem fluxo configurado. O setor atual será derivado da última combinação `data_movimentacao + sequencia_do_dia`.

### Termos aditivos e apostilamentos

**termos_aditivos**

- id
- instrumento_id
- numero ou ordem oficial
- estado: `RASCUNHO` ou `EFETIVADO`
- natureza_registro: `NORMAL`, `RETIFICACAO` ou `CANCELAMENTO`
- registro_referenciado_id opcional
- data_efetivacao
- ativo
- criado_por_id
- criado_em

**apostilamentos**

- mesmos metadados de ciclo de vida do termo aditivo
- catálogo de campos limitado aos dados não contratuais previstos no escopo

**alteracoes_campos**

- id
- termo_aditivo_id ou apostilamento_id
- campo pertencente ao catálogo fechado
- valor_anterior
- valor_novo
- tipo do valor validado pelo domínio

Regras:

- Rascunhos são editáveis.
- Efetivação exige data e documento assinado.
- Registros efetivados são imutáveis.
- Retificação e cancelamento são novos registros.
- Para cada campo prevalece a alteração válida com data de efetivação mais recente.
- Em empate, prevalece a maior ordem oficial.
- O estado atual do instrumento é recalculado na mesma transação da efetivação, retificação ou cancelamento.

### Documentos

**documentos**

- id
- vínculo exclusivo com processo, instrumento, termo aditivo ou apostilamento
- descrição
- ativo
- criado_por_id
- criado_em

**documento_versoes**

- id
- documento_id
- número da versão
- nome original
- tipo detectado
- tamanho
- checksum
- chave no armazenamento
- enviado_por_id
- enviado_em

Regras:

- Conteúdo imutável por versão.
- Versões antigas continuam disponíveis internamente.
- Desativação lógica, sem apagar histórico ou arquivo.
- Documento assinado: PDF.
- Documento administrativo: PDF, DOCX, XLSX ou CSV.
- Limite de 20 MB por versão.
- Validação do conteúdo real, não somente da extensão.

### Notificações e auditoria

**notificacoes**

- id
- usuario_id
- tipo
- título
- mensagem
- lida
- criada_em
- referência ao objeto relacionado

**logs_auditoria**

- id
- data_hora técnica
- usuario_id opcional
- ação
- entidade
- identificador da entidade
- detalhes estruturados
- ip_origem

**relatorios_gerados**

- id
- tipo
- filtros
- formato
- chave do arquivo
- gerado_por_id
- gerado_em

## 5. APIs principais

### Autenticação

- `POST /api/v1/auth/login`
- `POST /api/v1/auth/refresh`
- `POST /api/v1/auth/logout`
- `GET /api/v1/auth/me`
- `PUT /api/v1/auth/password`

### Administração

- `/api/v1/usuarios`
- `/api/v1/setores`

Somente `ADMINISTRADOR_DIPAC`.

### Processos e instrumentos

- `POST /api/v1/processos`
- `GET /api/v1/processos`
- `GET /api/v1/processos/{id}`
- `PUT /api/v1/processos/{id}`
- `DELETE /api/v1/processos/{id}` para desativação lógica
- `PUT /api/v1/processos/{id}/responsavel`
- `PUT /api/v1/processos/{id}/instrumento`

O instrumento passa a formalizado quando recebe os dados obrigatórios, a data de formalização e o documento assinado. Isso não encerra sua tramitação.

### Tramitações

- `GET /api/v1/processos/{id}/tramitacao`
- `POST /api/v1/processos/{id}/tramitacao/movimentacoes`
- endpoints equivalentes sob termos aditivos e apostilamentos

### Alterações do instrumento

- `/api/v1/instrumentos/{id}/termos-aditivos`
- `/api/v1/instrumentos/{id}/apostilamentos`
- operações de rascunho
- inclusão e remoção de alterações campo a campo
- efetivação
- retificação
- cancelamento

Efetivar uma alteração atualiza o instrumento e o status do processo na mesma transação, sem encerrar a tramitação.

### Documentos

- upload de documento
- criação de nova versão
- listagem de versões
- download interno
- desativação lógica

### Consulta pública

- `GET /api/v1/public/processos`
- `GET /api/v1/public/processos/{id}`

Campos permitidos:

- número do processo
- tipo do instrumento
- origem
- coordenador
- status
- data final da vigência contratual
- data final da vigência do TED

Todos os processos aparecem desde o cadastro. Campos contratuais inexistentes retornam estado explícito de não formalização.

### Dashboard e relatórios

- `GET /api/v1/dashboard`
- `POST /api/v1/relatorios`
- `GET /api/v1/relatorios`
- `GET /api/v1/relatorios/{id}/download`

## 6. Ordem de implementação

### Incremento 0 — Fundação e nova linha de base

1. Reorganizar pacotes sem mover o back-end da raiz.
2. Reescrever migrações Flyway.
3. Criar enums, relógio injetável e tratamento HTTP de erros.
4. Implementar auditoria transacional.
5. Preservar testes úteis e substituir testes presos ao modelo antigo.

Critério de saída:

- Aplicação inicializa em H2 e PostgreSQL.
- Migrações validam o esquema completo.
- Testes existentes relevantes continuam verdes.

### Módulo 1 — Autenticação, usuários, perfis e setores

1. Spring Security, hash de senha e JWT.
2. Bootstrap do primeiro administrador por variáveis de ambiente.
3. Login, refresh, logout e troca obrigatória de senha.
4. CRUD e desativação de usuários.
5. CRUD e desativação de setores.
6. Matriz fixa de permissões.
7. Criar o shell React, tela de login e área administrativa.

Critério de saída:

- Administrador e operador acessam apenas funções autorizadas.
- Usuário inativo não autentica.
- Senha temporária exige troca.
- Não existe senha padrão no código ou banco.

### Módulo 2 — Processos e instrumentos

1. Cadastro e edição do processo administrativo.
2. Número único e normalizado.
3. Responsável DIPAC.
4. Consulta interna paginada e filtrável.
5. Consulta pública desde o cadastro.
6. Cadastro do instrumento durante a formalização.
7. Status automático.
8. Situações independentes das duas vigências.

Critério de saída:

- Processo nasce em `EM_FORMALIZACAO`.
- Instrumento formalizado muda o processo para `EM_VIGENCIA`.
- Fim da vigência contratual muda para `CONCLUIDO`.
- TED vencido não conclui o processo.
- Área pública nunca retorna campos fora da allowlist.

### Dependência antecipada do Módulo 4 — Armazenamento mínimo

O documento assinado é necessário para formalizar o instrumento. Portanto, antes de concluir o Módulo 3, implementar:

1. Interface de armazenamento.
2. Armazenamento local configurável.
3. Metadados, checksum e upload de PDF.
4. Vínculo do documento assinado ao instrumento.

### Módulo 3 — Tramitações, prazos e notificações

1. Tramitação inicial livre.
2. Movimentações históricas por data e sequência.
3. Setor atual derivado.
4. Permanência por setor.
5. Tempo de tramitação inicial aberto ou formalizado.
6. Notificação de chegada.
7. Alertas independentes de vigência a 120 dias.
8. Rotina diária idempotente para atualizar conclusão e criar alertas.

Critério de saída:

- Não existem etapas obrigatórias.
- Movimentação futura é rejeitada.
- Movimentação salva é imutável.
- Empates de data preservam sequência.
- Notificações nunca são enviadas externamente.

### Módulo 4 — Documentos, termos aditivos e apostilamentos

1. Completar versionamento de documentos.
2. Implementar rascunho e efetivação.
3. Criar alterações campo a campo.
4. Criar tramitação separada por alteração.
5. Implementar ordem de prevalência.
6. Implementar retificação e cancelamento.
7. Recalcular instrumento e status.

Critério de saída:

- Versão anterior continua baixável.
- Alteração efetivada não pode ser editada.
- Cancelamento restaura o valor válido anterior.
- Aditivo que prorroga vigência pode reativar processo concluído.
- Ordem de cadastro nunca decide prevalência.

### Módulo 5 — Relatórios, indicadores e dashboard

1. Relatório anual de processos.
2. Instrumentos por tipo.
3. Histórico de tramitações.
4. Vigências.
5. Dados consolidados.
6. Exportação PDF, XLSX e CSV.
7. Histórico de relatórios gerados.
8. Dashboard gerencial.

Critério de saída:

- Filtros produzem o mesmo conjunto na tela e nos arquivos.
- Valores usam o estado atual do instrumento.
- Valor total vigente exclui processos concluídos.
- Permanência por setor usa dias corridos.
- Processos não formalizados participam do tempo médio.

## 7. Front-end

### Rotas públicas

- `/`
- `/processos`
- `/processos/:id`
- `/login`

### Rotas internas

- `/app/dashboard`
- `/app/processos`
- `/app/processos/novo`
- `/app/processos/:id`
- `/app/tramitacoes`
- `/app/documentos`
- `/app/relatorios`
- `/app/notificacoes`
- `/app/admin/usuarios`
- `/app/admin/setores`

### Telas prioritárias

1. Login e troca de senha.
2. Listagem pública.
3. Listagem interna de processos.
4. Cadastro do processo.
5. Detalhe com abas: processo, instrumento, tramitação, documentos, aditivos, apostilamentos e auditoria relacionada.
6. Central de notificações.
7. Dashboard.
8. Relatórios.

## 8. Estratégia de testes

### Domínio

- Máquina de status.
- Limites de 120 dias.
- Independência TED/contrato.
- Ordem de prevalência.
- Empate de efetivação.
- Retificação e cancelamento.
- Reativação por prorrogação.
- Permanência no setor.
- Sequência no mesmo dia.

### Aplicação e persistência

- Transações de formalização e efetivação.
- Restrições de unicidade e identidade.
- Migrações Flyway.
- Versionamento documental.
- Auditoria.
- Geração idempotente de notificações.

### API e segurança

- Matriz administrador/operador.
- Senha temporária.
- Usuário inativo.
- DTO público por allowlist.
- Upload por tipo real e limite.
- Paginação e filtros.

### Front-end

- Formulários e validações.
- Proteção de rotas.
- Visibilidade de ações por perfil.
- Estados de carregamento, vazio e erro.
- Fluxos ponta a ponta prioritários.

## 9. Definition of Done

Cada incremento só é concluído quando:

- regras de domínio possuem testes;
- migração correspondente foi validada;
- API possui contrato e erros padronizados;
- autorização foi testada;
- auditoria foi verificada;
- front-end cobre o fluxo completo;
- documentação do TCC afetada está identificada;
- `mvn test` e testes do front-end passam;
- nenhum segredo ou arquivo enviado fica versionado no Git.

## 10. Sequência recomendada de trabalho

1. Implementar Incremento 0.
2. Entregar Módulo 1 completo.
3. Entregar cadastro de processo e consulta pública do Módulo 2.
4. Antecipar armazenamento mínimo.
5. Completar formalização e Módulo 3.
6. Completar Módulo 4.
7. Finalizar Módulo 5.
8. Revisar o PDF do TCC e os diagramas usando `DIVERGENCIAS_TCC_IMPLEMENTACAO.md`.
