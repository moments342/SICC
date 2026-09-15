# Divergências entre o TCC e a Implementação Planejada

Registro histórico das decisões refinadas após a versão 1.0.0 do TCC. A revisão acadêmica já incorporou o domínio refinado e as correções de RF6/RF19. Este checklist não representa pendências atuais; consulte o TCC final versionado, `CONTEXT.md`, `docs/adr/` e `docs/VALIDACAO_PRE_ETAPA_2.md` para o estado vigente.

## Domínio

- Separar `Processo Administrativo` e `Instrumento Contratual`.
- Um processo pode existir sem instrumento durante a formalização.
- Depois de formalizado, o processo possui exatamente um instrumento.
- Tipos fixos: contrato de gestão, convênio, acordo de parceria e acordo de cooperação técnica.
- TED não é tipo de instrumento.
- Manter datas finais independentes de vigência contratual e vigência do TED.
- Não armazenar data inicial de vigência.
- Não armazenar Projeto Básico nem dados adicionais extraídos dele.
- Remover o campo textual `anexos`.
- Remover `publicoSimplificado`; todos os processos são públicos pela allowlist.

## Tramitação

- Tramitações são totalmente livres.
- Não existem modelos, etapas obrigatórias ou sequência predefinida.
- Não existe encerramento da tramitação.
- Formalização e efetivação não encerram seus históricos.
- Cada termo aditivo e apostilamento possui tramitação separada.
- Remover `etapaAtual`.
- Derivar setor atual da última movimentação.
- Movimentações usam data, sem horário, e sequência para empates.

## Alterações contratuais

- Termos aditivos e apostilamentos são registros próprios.
- Rascunho é editável; efetivado é imutável.
- Efetivação exige data e PDF assinado.
- Retificação e cancelamento são novos registros.
- Mudanças são registradas campo a campo.
- Data de efetivação decide prevalência; ordem oficial desempata.
- Termo aditivo não altera identidade do instrumento.

## Usuários e permissões

- Somente pessoas da DIPAC usam a área interna na primeira versão.
- Perfis fixos: administrador DIPAC e operador DIPAC.
- Remover permissões granulares por usuário, módulo ou funcionalidade.
- Docentes e outros setores não possuem contas no escopo atual.
- Primeiro administrador criado por variáveis de ambiente.
- Senha temporária exige troca.

## Transparência

- Consulta pública permanece sem autenticação.
- Todos os processos aparecem desde o cadastro.
- Campos públicos: número do processo, tipo, origem, coordenador, status e as duas datas finais de vigência.
- Valor, partícipes, documentos, usuários, tramitações detalhadas e auditoria não são públicos.

## Status, prazos e notificações

- Status fechado: em formalização, em vigência e concluído.
- Fim da vigência contratual conclui o processo.
- TED vencido não conclui o processo.
- Aditivo pode reativar processo ao prorrogar a vigência.
- Alertas começam 120 dias corridos antes do vencimento.
- Notificações são somente internas.
- Como outros setores não usam o sistema, notificações chegam ao responsável DIPAC ou à equipe DIPAC.

## Documentos

- Arquivos fora do PostgreSQL.
- Metadados e checksum no banco.
- Versionamento imutável.
- PDF obrigatório para documentos assinados.
- PDF, DOCX, XLSX e CSV para documentos administrativos.
- Limite de 20 MB.

## Arquitetura e desenvolvimento

- Back-end permanece na raiz.
- Front-end será criado em `frontend/`.
- Migrações iniciais podem ser reconstruídas porque não existem dados reais.
- Armazenamento local será acessado por interface substituível.

## Mockups que precisam ser atualizados

- Remover usuário “Comunidade Acadêmica” autenticado.
- Substituir contrato genérico pelos quatro tipos corretos.
- Remover início de vigência.
- Exibir vigência contratual e vigência do TED separadamente.
- Remover fluxo resumido com etapas predefinidas.
- Remover `etapaAtual`.
- Atualizar perfis para administrador DIPAC e operador DIPAC.
- Ajustar dashboard para a máquina de status final.
