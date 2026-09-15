# Melhorias de arquitetura pré-etapa 3

Base: `9dfe8c2`. Branch: `alterações-pré-etapa-3`. Trabalho local autorizado a partir dos três candidatos do relatório visual de 6 de setembro de 2026, seguindo `implement`, testes comportamentais e revisão Standards/Spec.

Commits de implementação: `f6933ca` (relatórios), `d04d2b8` (Estado Atual do Instrumento) e `f1a28ef` (proprietários de documentos). Nenhum push foi realizado.

## Relatório

O conteúdo trafega como linhas e células, com distinção entre texto e valores derivados. A composição dos cinco tipos de Relatório e de seus metadados fica em `GeracaoRelatorios`; a codificação pertence aos adapters CSV, PDF e XLSX. O XLSX percorre as células diretamente, sem reconstruí-las a partir de delimitadores CSV.

A política textual anterior é preservada: ponto e vírgula vira vírgula e quebras de linha/tabulações viram espaços. Somente células textuais do CSV recebem a proteção contra fórmulas; valores derivados negativos, como dias após o vencimento, continuam numéricos. PDF e XLSX não recebem o apóstrofo de proteção CSV. As células têm construtor privado e fábricas explícitas por tipo. A normalização de espaços Unicode fica em um único utilitário interno.

`EmissaoRelatorio` deriva a data, a vigência e os metadados de emissão de um único instante do `Clock`, preservando filtros, autoria, checksum, retenção e auditoria transacional.

## Estado Atual do Instrumento

`EstadoAtualInstrumento` concentra o carregamento da cronologia, as mudanças por campo, a validade das referências, a precedência, a reconstrução histórica e a explicação das cadeias. Uma avaliação reutiliza esses dados e resultados para o Instrumento Contratual. A avaliação preserva campos opcionais ausentes e fixa a referência de vigência.

`AlteracaoService` mantém a coordenação transacional, os bloqueios, as autorizações de mutação, o Documento Assinado e a auditoria. Permanecem as regras de ordem por data e ordem oficial, imutabilidade após efetivação, validação de `valorAnterior`, retificação por campo e cancelamento com reconstrução do estado materializado.

`Avaliacao.efetivar` reúne a validação da referência bloqueada, a fixação de valores anteriores, a efetivação e a recomposição do estado. O bloqueio da referência ocorre antes da avaliação. Os testes do módulo usam JPA com H2 real, sem simular `EntityManager` ou consultas.

## Proprietário de Documento Anexo

A consulta autenticada `GET /api/v1/documentos/proprietarios` resolve a hierarquia no banco e retorna uma página de projeções, sem carregar entidades completas nem todos os instrumentos e alterações no navegador.

| Parâmetro | Contrato |
| --- | --- |
| `tipo` | `PROCESSO`, `INSTRUMENTO`, `TERMO_ADITIVO` ou `APOSTILAMENTO`; obrigatório. |
| `busca` | Busca parcial, sem distinção de maiúsculas/minúsculas, por número do proprietário, processo, origem ou instrumento. `%`, `_` e barra invertida são literais. |
| `incluirInativos` | Padrão `false`; a tela de documentos usa `true` para permitir consulta histórica. |
| `page` | Índice a partir de zero; deslocamento limitado ao suportado pelo banco via JPA. |
| `size` | Padrão 20; intervalo permitido de 1 a 100. |

A ordenação é por número do proprietário e ID como desempate. A projeção identifica o proprietário, seu processo e instrumento, o estado da alteração quando aplicável, o status atual do processo e se o processo está ativo. A consulta pública conserva sua lista permitida de campos.

A tela oferece busca, navegação entre páginas, estado vazio e nova tentativa em caso de erro. Trocar o tipo, aplicar busca ou navegar limpa a seleção. Requisições substituídas são canceladas. A tela conserva os identificadores semânticos e permite consulta/download de históricos inativos, com as mutações bloqueadas também pelo backend.

## Validação

Certificação concluída em 6 de setembro de 2026 sobre as fontes finais:

| Verificação | Resultado |
| --- | --- |
| Maven `clean test`, com `SICC_POSTGRES_TEST=true` e PostgreSQL isolado | 203 testes; nenhuma falha, erro ou exclusão. |
| TypeScript e build Vite | Aprovados. |
| Suíte completa E2E com API simulada | 57/57 aprovados. |
| Regressões afetadas após o ajuste visual final do seletor | 7/7 aprovadas, incluindo layouts de 320, 360, 768, 1101 e 1280 px. |
| Integração de frontend/backend com H2 | 5/5 jornadas aprovadas. |
| Integração de frontend/backend com PostgreSQL real, perfil `dev`, banco e armazenamento isolados | 5/5 jornadas aprovadas. |
| Revisão independente Standards | Nenhum achado aberto após as correções. |
| Revisão independente Spec | Nenhum requisito faltante/parcial, desvio de escopo ou implementação incorreta identificado. |
| `git diff --check` | Aprovado. |

As jornadas de integração cobrem troca de senha inicial, cadastro e consulta pública, formalização com PDF assinado, consulta/download de documentos de processo inativo e geração/download de relatório retido. Os testes específicos do seletor cobrem os quatro tipos de proprietário, paginação, busca literal, resultados vazios, erro com nova tentativa e limpeza da seleção.

As revisões Standards e Spec foram estáticas e independentes, realizadas por GPT-5.6 Sol com esforço High; a execução dos testes foi feita separadamente. A revisão Spec utilizou uma execução CLI efêmera em modo somente leitura. Os resultados não equivalem a uma nova auditoria integral do TCC ou das plataformas nativas de armazenamento, que não foram alteradas neste trabalho.

Evidências locais preservadas em `.runtime/`: `arch-maven-final.log`, `arch-build-final.log`, `arch-e2e-final.log`, `arch-owner-layout.log`, `arch-integration.log`, `arch-integration-postgres.log` e `arch-spec-review.md`. O manifesto `arch-final-sources.json` permite conferir os hashes das fontes e testes certificados. Esses artefatos de execução não integram os commits de código.
