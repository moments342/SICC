# Validação das correções pré-etapa 2

Data: 6 de setembro de 2026. Branch: `correções-pré-etapa-2`. Base da revisão integral: `37d73c8`; início desta continuação: `8820760`. Trabalho local, sem push.

## Escopo e critérios

A continuação fecha armazenamento seguro, qualidade estrutural de backend/frontend, catálogos, legibilidade do dashboard, documentação de domínio e certificação contra os requisitos refinados do TCC. A execução seguiu a skill `implement`, com regressões comportamentais, revisão independente Standards e Spec e commits na branch existente. Os subagentes desta continuação após a regra explícita do usuário usam GPT-5.6 Sol com esforço High.

## Correções e evidências

| Pendência | Resultado e evidência |
| --- | --- |
| Armazenamento seguro | Handles Windows e descritores Unix permanecem fixados até concluir a transação; validação antes do commit e compensação pelo arquivo/pai original. Regressões de troca de pai, substituição de folha e rollback passaram. |
| Separação nativa | `ConfiguracaoUnix` concentra plataforma/flags; `ChamadasNativasUnix` concentra libc/JNA e layouts ABI; sessão segura mantém navegação e ciclo de vida. |
| DTOs e referência temporal | Projeções de processo, consulta pública e alteração calculam status atual sem esperar scheduler. Relatórios/dashboard usam uma referência por operação. Regressões de meia-noite passaram. |
| Estrutura backend | `GeracaoRelatorios` reúne tipo, título, filtros e gerador; `TipoAlteracao` concentra semântica; projeções de usuário/setor, campos de instrumento, permanências e formatos foram extraídos. |
| Estrutura frontend | `App` é a composição da aplicação; Processos e Alterações delegam estados/operações aos respectivos fluxos. Catálogos, projeções, parsing, relógio e formatação foram separados. Seleções obsoletas são canceladas e formulários reiniciados por identidade. |
| Catálogos e tipagem | Estados canônicos e tipos fechados são reutilizados pelos DTOs. `reportCatalog` reúne rótulos, filtros e formatos de cada relatório. |
| Legibilidade | Gráfico desktop reserva a linha inteira para o nome do setor; regressão reproduzida antes e aprovada depois. Captura em 1280 px inspecionada: palavras completas e sem sobreposição. |
| Histórico documental | Inclusão explícita de proprietários e documentos inativos para leitura autenticada, preservando bloqueio de mutações; regressões de API, tela e jornada com backend real aprovadas. |
| Documentação | `CONTEXT.md` e ADRs versionados; ADR 0015 descreve garantias e limites reais. Plano antigo e checklist de divergências identificados como históricos. |

## Certificação

Os resultados abaixo são da execução final desta continuação, após as correções de histórico e de catálogo, não inferidos de relatórios anteriores. Não restam achados abertos das revisões Standards e Spec no escopo desta entrega.

| Verificação | Resultado atual |
| --- | --- |
| Maven completo, PostgreSQL real habilitado | 202 testes, zero falhas/erros/ignorados. |
| Build frontend | TypeScript e Vite aprovados, inclusive após o catálogo tipado. |
| E2E completo frontend | 56 testes aprovados após todas as correções. |
| Integração navegador/API real | 5 jornadas aprovadas: bootstrap e troca de senha, cadastro/consulta pública, formalização com PDF/allowlist, desativação com consulta/download histórico e relatório com download. |
| Armazenamento Windows | 39 testes aprovados. |
| Armazenamento Linux/libc real | 39 casos: 27 executados e aprovados, 12 exclusivos de Windows ignorados. Executado em Ubuntu 22.04 no WSL, com fontes copiadas para filesystem Linux. |
| Responsividade | Cobertura em 320, 360, 768, 1101 e 1280 px. |
| Documento acadêmico | DOCX e PDF de 61 páginas extraídos e cotejados; páginas corrigidas de RF6 e RF19 e descrições de desativação/estado atual/relatórios (páginas 34, 40 e 47) renderizadas e inspecionadas visualmente. |
| Revisões independentes | Standards e Spec independentes concluídas; achados de armazenamento, backend, frontend, histórico e documentação corrigidos e reavaliados, sem achados abertos. |
| Git | `git diff --check` sem erros; avisos LF/CRLF são da configuração local. |

Comandos principais: Maven `clean test` com `SICC_POSTGRES_TEST=true` apontando para cluster temporário PostgreSQL 18 em `127.0.0.1:55436`; `npm run build`; `npm run test:e2e`; `npm run test:e2e:integration`. A integração usa o backend Spring Boot real com perfil de teste e banco H2 isolado; a sequência de migrações e constraints é validada separadamente no PostgreSQL real. Os testes não dependem do banco institucional. O cluster temporário foi encerrado após a suíte aprovada; seus artefatos permanecem em `.runtime/postgres-certification`.

Evidências locais de execução foram preservadas em `.runtime/`: `certification-maven-complete.log`, `certification-e2e-complete.log`, `certification-integration-complete.log`, `linux-storage-certification.log`, XMLs de `linux-storage-reports`, `dashboard-desktop-final.png`, renderizações `tcc-rf6.png` e `tcc-rf19.png` e revisão `final-spec-review.md`. São artefatos locais de execução, não dependências do código.

## Correspondência com o TCC

A auditoria independente confrontou o código e testes com os 23 RF e 10 RNF do escopo refinado: cadastro/formalização/status (RF1–6), tramitação (RF7–10), documentos (RF11–12), alterações (RF13–15), acesso/notificações/consulta pública (RF16–19), relatórios/dashboard (RF20–22) e auditoria (RF23). O detalhe temporal de alterações encontrado nessa auditoria foi corrigido; o acesso documental de proprietários e documentos inativos também foi corrigido e validado de ponta a ponta. Os RF1–23 estão atendidos no escopo refinado auditado. RNF1–7 têm implementação identificável. RNF8–10 continuam responsabilidades da implantação institucional, como descrito no TCC: não se declara implantação de HTTPS, MFA, backup/DR nem conformidade LGPD integral por estes testes.

## Limites explícitos

- O adaptador macOS possui testes de layout/chamadas simuladas, mas não foi executado em um host macOS. A execução Unix real certificada aqui é Linux.
- O armazenamento local exige administração confiável da raiz e dos ancestrais. Não oferece isolamento contra outro processo malicioso com o mesmo usuário do sistema ou root, nem atomicidade distribuída entre banco e filesystem. Queda abrupta exige reconciliação operacional; ver ADR 0015.
- A revisão visual documental focalizou as páginas alteradas. A auditoria semântica percorreu o escopo refinado inteiro; não equivale a revisão editorial de todas as páginas do TCC.
- Artefatos não relacionados e arquivos locais de execução foram preservados. Nenhum push, implantação ou alteração em banco institucional foi realizado.

## Entrega local

Correções consolidadas na branch existente. Não restam alterações de código da tarefa fora dos commits. `CONTEXT.md`, os 15 ADRs e este registro de validação integram a entrega; os documentos antigos são mantidos apenas como histórico explicitamente identificado. O encerramento desta etapa não afirma certificação nativa de macOS nem implantação dos requisitos operacionais institucionais listados acima.
