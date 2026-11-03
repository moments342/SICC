import { openFilters, answerConfirmation, openProcessAction, openMenu, openCreation, openLogin, openAlteration, closeModal, openUserActions } from "./navigation-helpers";
import { expect, test, type Page } from "@playwright/test";

test.describe.configure({ mode: "serial" });

const PDF_VALIDO_BASE64 =
  "JVBERi0xLjYKJfbk/N8KMSAwIG9iago8PAovVHlwZSAvQ2F0YWxvZwovVmVyc2lvbiAvMS42Ci9QYWdlcyAzIDAgUgo+PgplbmRvYmoKNSAwIG9iago8PAovTGVuZ3RoIDExOQovVHlwZSAvT2JqU3RtCi9OIDMKL0ZpbHRlciAvRmxhdGVEZWNvZGUKL0ZpcnN0IDE0Cj4+CnN0cmVhbQ0KeJwzUjBQMFYwMlYwUTAzVbCx0Q/JLMlJVdAoSy0qTszXNdRUsLMDC1cWpCroBySmpxYr6HtnphQrRJsAtQYpxCroO+eX5pUoGGKoVND3TU3JTHTKr1CINtAzUABhM0MjIGluCSJjQcqKUoF6jcFGAfUDAJFBJTUNCmVuZHN0cmVhbQplbmRvYmoKNiAwIG9iago8PAovTGVuZ3RoIDMyCi9Sb290IDEgMCBSCi9JbmZvIDIgMCBSCi9JRCBbPDY5OEZBMEE2NjNFOEQ2OThFOUE3NjQwODQ1MzE2QTlDM0VGQzhBMUMxNDY5QzAwRkFCRjMyODZBNzAyRDFFMDU+IDw2OThGQTBBNjYzRThENjk4RTlBNzY0MDg0NTMxNkE5QzNFRkM4QTFDMTQ2OUMwMEZBQkYzMjg2QTcwMkQxRTA1Pl0KL1R5cGUgL1hSZWYKL1NpemUgNwovSW5kZXggWzAgN10KL1cgWzEgMiAxXQovRmlsdGVyIC9GbGF0ZURlY29kZQo+PgpzdHJlYW0NCnicY2Bg+M/IwM/AxMAKwoxAzMTI4MfAyKjDAAAd8QGlDQplbmRzdHJlYW0KZW5kb2JqCnN0YXJ0eHJlZgozMDAKJSVFT0YK";

async function entrarComoAdministrador(page: Page) {
  await page.goto("/");
  await openLogin(page); await page.getByLabel("Login").fill("admin");
  await page.getByLabel("Senha").fill("Permanente456!");
  await page.getByRole("button", { name: "Entrar no SICC" }).click();
  await expect(page.getByRole("heading", { name: "Visão geral" })).toBeVisible();
}

test("primeiro Administrador DIPAC troca a senha temporária antes de acessar o sistema", async ({ page }) => {
  await page.goto("/");
  await openLogin(page); await page.getByLabel("Login").fill("admin");
  await page.getByLabel("Senha").fill("Temporaria123!");
  await page.getByRole("button", { name: "Entrar no SICC" }).click();

  await expect(page.getByRole("heading", { name: "Crie sua senha permanente" })).toBeVisible();
  await expect(page.getByRole("button", { name: "Processos Administrativos" })).toHaveCount(0);
  await page.getByLabel("Senha temporária").fill("Temporaria123!");
  await page.getByLabel("Nova senha").fill("Permanente456!");
  await page.getByRole("button", { name: "Definir senha" }).click();

  await openLogin(page);
  await expect(page.getByRole("button", { name: "Entrar no SICC" })).toBeVisible();
  await openLogin(page); await page.getByLabel("Login").fill("admin");
  await page.getByLabel("Senha").fill("Permanente456!");
  await page.getByRole("button", { name: "Entrar no SICC" }).click();

  await expect(page.getByRole("heading", { name: "Visão geral" })).toBeVisible();
  await expect(page.getByRole("button", { name: "Administração" })).toBeVisible();
});

test("F10: base descartável vazia, cadastro e busca sem resultados em desktop e celular", async ({ page }, info) => {
  await entrarComoAdministrador(page);
  await openMenu(page); await page.getByRole("button", { name: "Processos Administrativos", exact: true }).click();
  const catalog = page.locator("section").filter({ has: page.getByRole("heading", { name: "Processos Administrativos ativos" }) });
  for (const width of [1440, 390]) {
    await page.setViewportSize({ width, height: 1000 });
    await expect(catalog.getByRole("status").filter({ hasText: "Nenhum Processo Administrativo" })).toContainText("Nenhum Processo Administrativo ativo cadastrado.");
    await page.screenshot({ path: info.outputPath(`real-empty-${width}.png`), fullPage: true });
  }
  await openCreation(page);
  const create = page.locator("form").filter({ has: page.getByRole("button", { name: "Cadastrar processo" }) });
  await create.getByLabel("Número", { exact: true }).fill("PROC-CLARIFY-F10");
  await openFilters(page);
  await create.getByLabel("Origem", { exact: true }).fill("DIPAC");
  await create.getByRole("button", { name: "Cadastrar processo" }).click();
  await expect(page.getByRole("button", { name: /PROC-CLARIFY-F10/ })).toBeVisible();
  for (const width of [1440, 390]) {
    await page.setViewportSize({ width, height: 1000 });
    await openFilters(page);
    await page.getByLabel("Filtrar por número").fill("INEXISTENTE-F10");
    await page.keyboard.press("Enter");
    await expect(catalog.getByRole("status").filter({ hasText: "Nenhum Processo Administrativo" })).toContainText("Nenhum Processo Administrativo corresponde aos filtros.");
    await expect(catalog).not.toContainText("ativo cadastrado");
    await page.screenshot({ path: info.outputPath(`real-no-results-${width}.png`), fullPage: true });
    await catalog.getByRole("button", { name: "Limpar filtros" }).focus();
    await page.keyboard.press("Enter");
    await expect(page.getByLabel("Filtrar por número")).toBeFocused();
    await expect(page.getByRole("button", { name: /PROC-CLARIFY-F10/ })).toBeVisible();
  }
});

test("cadastro interno de processo fica imediatamente localizável na consulta pública", async ({ page }) => {
  const numero = "23005.000006/2026-10";
  await entrarComoAdministrador(page);

  await openMenu(page); await page.getByRole("button", { name: "Processos Administrativos" }).click(); await openCreation(page);
  const cadastro = page.locator("form").filter({
    has: page.getByRole("button", { name: "Cadastrar processo" })
  });
  await cadastro.getByLabel("Número", { exact: true }).fill(numero);
  await openFilters(page);
  await cadastro.getByLabel("Origem", { exact: true }).fill("DIPAC");
  await cadastro.getByLabel("Número do projeto").fill("P-006");
  await cadastro.getByLabel("Responsável DIPAC").selectOption("1");
  await cadastro.getByRole("button", { name: "Cadastrar processo" }).click();
  await expect(page.getByText("Processo Administrativo criado.")).toBeVisible();
  await expect(page.getByText(numero)).toBeVisible();

  await openMenu(page); await page.getByRole("button", { name: "Sair" }).click();
  const consulta = page.locator("section.public-list");
  await consulta.getByLabel("Número").fill(numero);
  await consulta.getByRole("button", { name: "Filtrar" }).click();

  const linha = consulta.locator("tbody tr").filter({ hasText: numero });
  await expect(linha).toBeVisible();
  await expect(linha).toContainText("Ainda não formalizado");
  await expect(linha).toContainText("Em formalização");
});

test("formaliza instrumento com PDF assinado e publica somente a allowlist", async ({ page }) => {
  const numeroProcesso = "23005.000010/2026-10";
  await entrarComoAdministrador(page);

  await openMenu(page); await page.getByRole("button", { name: "Processos Administrativos" }).click(); await openCreation(page);
  const cadastroProcesso = page.locator("form").filter({
    has: page.getByRole("button", { name: "Cadastrar processo" })
  });
  await cadastroProcesso.getByLabel("Número", { exact: true }).fill(numeroProcesso);
  await openFilters(page);
  await cadastroProcesso.getByLabel("Origem", { exact: true }).fill("DIPAC");
  const respostaProcesso = page.waitForResponse(resposta =>
    resposta.url().endsWith("/api/v1/processos")
    && resposta.request().method() === "POST");
  await cadastroProcesso.getByRole("button", { name: "Cadastrar processo" }).click();
  await respostaProcesso;

  await openMenu(page); await page.getByRole("button", { name: "Documentos" }).click();
  const cadastroDocumento = page.locator("form").filter({
    has: page.getByRole("button", { name: "Anexar documento" })
  });
  await page.locator('select[name="proprietarioId"]').selectOption({
    label: `${numeroProcesso} · DIPAC · Em formalização`
  });
  await page.getByRole("button", { name: "Anexar documento", exact: true }).click();
  await cadastroDocumento.getByLabel("Categoria").selectOption("ASSINADO");
  await cadastroDocumento.getByLabel("Título").fill("Instrumento assinado CV-010");
  await cadastroDocumento.getByLabel("Arquivo").setInputFiles({
    name: "instrumento-cv-010.pdf",
    mimeType: "application/pdf",
    buffer: Buffer.from(PDF_VALIDO_BASE64, "base64")
  });
  const respostaDocumento = page.waitForResponse(resposta =>
    resposta.url().endsWith("/api/v1/documentos")
    && resposta.request().method() === "POST");
  await cadastroDocumento.getByRole("button", { name: "Anexar documento" }).click();
  const documentoId = Number((await (await respostaDocumento).json()).id);

  await openMenu(page); await page.getByRole("button", { name: "Processos Administrativos" }).click();
  await page.getByRole("button", { name: new RegExp(numeroProcesso) }).click();
  const formalizacao = page.locator("form").filter({
    has: page.getByRole("button", { name: "Formalizar" })
  });
  await openProcessAction(page, "formalize");
  await formalizacao.getByLabel("Número").fill("CV-010/2026");
  await formalizacao.getByLabel("Tipo").selectOption("CONVENIO");
  await formalizacao.getByLabel("Objeto").fill("Cooperação institucional");
  await formalizacao.getByLabel("Natureza").fill("Administrativa");
  await formalizacao.getByLabel("Coordenador").fill("Maria Silva");
  await formalizacao.getByLabel("Partícipes, um por linha").fill("UFGD\nFundação");
  await formalizacao.getByLabel("Valor").fill("150000");
  await formalizacao.getByLabel("Vigência contratual").fill("2099-12-31");
  await formalizacao.getByLabel("Vigência TED").fill("2099-06-30");
  await formalizacao.getByLabel("Data de formalização").fill("2026-08-01");
  await formalizacao.getByLabel("Documento assinado PDF").selectOption(String(documentoId));
  await formalizacao.getByRole("button", { name: "Formalizar" }).click();
  await page.getByRole("button", { name: "Confirmar formalização", exact: true }).click();

  const resumo = page.locator("section.panel").filter({ hasText: "Instrumento Contratual · CV-010/2026" });
  await expect(resumo).toContainText("Convênio");
  await expect(resumo).toContainText("Maria Silva");
  await expect(resumo).toContainText("31/12/2099");
  await expect(resumo).toContainText("30/06/2099");
  await expect(resumo).toContainText(`Documento assinado #${documentoId}`);

  await openMenu(page); await page.getByRole("button", { name: "Sair" }).click();
  const consulta = page.locator("section.public-list");
  await consulta.getByLabel("Número").fill(numeroProcesso);
  await consulta.getByRole("button", { name: "Filtrar" }).click();
  const linha = consulta.locator("tbody tr").filter({ hasText: numeroProcesso });
  await expect(linha).toContainText("Convênio");
  await expect(linha).toContainText("Maria Silva");
  await expect(linha).toContainText("Em vigência");
  await expect(linha).toContainText("31/12/2099");
  await expect(linha).toContainText("30/06/2099");
  await expect(consulta.getByRole("columnheader")).toHaveCount(7);
});

test("Processo Administrativo inativo preserva documentos para consulta e download", async ({ page }) => {
  const numeroProcesso = "23005.000010/2026-10";
  await entrarComoAdministrador(page);

  await openMenu(page); await page.getByRole("button", { name: "Processos Administrativos" }).click();
  await page.getByRole("button", { name: new RegExp(numeroProcesso) }).click();
  await page.getByRole("button", { name: "Desativar processo", exact: true }).click();
  await expect(page.getByRole("dialog")).toContainText(numeroProcesso);
  await expect(page.getByRole("dialog")).toContainText("histórico será preservado");
  await answerConfirmation(page);
  await expect(page.getByText(
    "Processo Administrativo desativado; o registro histórico foi preservado."
  )).toBeVisible();

  await openMenu(page); await page.getByRole("button", { name: "Documentos" }).click();
  const cadastro = page.locator("form").filter({
    has: page.getByRole("button", { name: "Anexar documento" })
  });
  await page.getByLabel("Vincular documento a").selectOption("INSTRUMENTO");
  await page.locator('select[name="proprietarioId"]').selectOption({
    label: `CV-010/2026 · Convênio · Processo ${numeroProcesso} · Inativo`
  });

  await expect(page.getByText(
    "O Processo Administrativo está inativo. Os documentos permanecem disponíveis somente para consulta e download."
  )).toBeVisible();
  await expect(page.getByRole("button", { name: "Anexar documento", exact: true })).toBeDisabled();
  for (const button of await page.getByRole("button", { name: "Nova versão", exact: true }).all()) await expect(button).toBeDisabled();

  const documento = page.locator("article.doc").filter({ hasText: "Instrumento assinado CV-010" });
  await expect(documento).toBeVisible();
  await expect(documento.getByRole("button", { name: "Desativar" })).toBeDisabled();
  const download = page.waitForEvent("download");
  await documento.getByRole("button", { name: "Baixar versão 1" }).click();
  expect((await download).suggestedFilename()).toBe("instrumento-cv-010.pdf");
});

test("gera, acompanha e baixa um relatório retido pela API real", async ({ page }) => {
  await entrarComoAdministrador(page);
  await openMenu(page); await page.getByRole("button", { name: "Relatórios" }).click();
  await openFilters(page);
  await page.getByLabel("Origem", { exact: true }).fill("DIPAC");

  const geracao = page.waitForResponse(resposta =>
    resposta.url().endsWith("/api/v1/relatorios")
    && resposta.request().method() === "POST");
  await page.getByLabel("Tipo de relatório", { exact: true }).selectOption("CONSOLIDADO");
  await openFilters(page);
  await page.locator(".report-actions").getByText("Consolidado", { exact: true }).locator("..")
    .getByRole("button", { name: "Gerar CSV" }).click();
  const relatorio = await (await geracao).json();

  await expect(page.getByText("Relatório gerado e retido para download.")).toBeVisible();
  const historico = page.locator("article.doc").filter({ hasText: relatorio.nomeArquivo });
  await expect(historico).toContainText("Origem: DIPAC");
  await expect(historico).toContainText(relatorio.checksumSha256);

  const download = page.waitForEvent("download");
  await historico.getByRole("button", { name: "Baixar" }).click();
  const arquivo = await download;
  expect(arquivo.suggestedFilename()).toBe(relatorio.nomeArquivo);
  const stream = await arquivo.createReadStream();
  let conteudo = "";
  for await (const parte of stream) conteudo += parte.toString("utf8");
  expect(conteudo).toContain("Relatório consolidado");
  expect(conteudo).toContain("origem=DIPAC");
});

for (const width of [1440, 390]) test(`F10: filtros informados correspondem aos cinco relatórios reais em ${width}px`, async ({ page }) => {
  await page.setViewportSize({ width, height: 1000 });
  await entrarComoAdministrador(page);
  await openMenu(page); await page.getByRole("button", { name: "Relatórios", exact: true }).click();
  await openFilters(page);
  await page.getByLabel("Número do processo", { exact: true }).fill("PROC-CLARIFY-F10");
  await openFilters(page);
  await page.getByLabel("Origem", { exact: true }).fill("DIPAC");
  await page.getByLabel("Ano do relatório").fill("2026");
  await page.getByLabel("Tipo de relatório", { exact: true }).selectOption("HISTORICO_TRAMITACOES");
  await openFilters(page);
  await page.getByLabel("Contexto da tramitação").selectOption("FORMALIZACAO");
  await page.getByLabel("Início do período").fill("2026-01-01");
  await page.getByLabel("Fim do período").fill("2026-12-31");
  const common = { numero: "PROC-CLARIFY-F10", origem: "DIPAC" };
  const reports = [
    { type: "ANUAL_PROCESSOS", label: "Anual de processos", filters: { ...common, ano: "2026" } },
    { type: "INSTRUMENTOS_POR_TIPO", label: "Instrumentos por tipo", filters: common },
    { type: "HISTORICO_TRAMITACOES", label: "Histórico de tramitações", filters: { ...common, contexto: "FORMALIZACAO", dataInicial: "2026-01-01", dataFinal: "2026-12-31" } },
    { type: "VIGENCIAS", label: "Vigências", filters: common },
    { type: "CONSOLIDADO", label: "Consolidado", filters: common }
  ];
  for (const report of reports) {
    await page.getByLabel("Tipo de relatório", { exact: true }).selectOption(report.type);
    await openFilters(page);
    const group = page.getByRole("group", { name: report.label, exact: true });
    await expect(group).toContainText("Filtros aplicados: Número do processo: PROC-CLARIFY-F10 · Origem: DIPAC");
    await expect(group).toContainText("Não se aplicam a este relatório:");
    const generated = page.waitForResponse(response => response.url().endsWith("/api/v1/relatorios") && response.request().method() === "POST");
    await group.getByRole("button", { name: "Gerar CSV", exact: true }).focus(); await page.keyboard.press("Enter");
    const response = await generated;
    expect(response.ok()).toBe(true);
    const reportData = await response.json();
    expect(reportData.filtros).toEqual(report.filters);
    expect(reportData.tipo).toBe(report.type);
    const history = page.locator("article.doc").filter({ hasText: reportData.nomeArquivo });
    await expect(history).toContainText("Número do processo: PROC-CLARIFY-F10");
    const downloading = page.waitForEvent("download");
    await history.getByRole("button", { name: "Baixar", exact: true }).click();
    const download = await downloading;
    expect(download.suggestedFilename()).toBe(reportData.nomeArquivo);
    const stream = await download.createReadStream();
    let content = "";
    for await (const chunk of stream) content += chunk.toString("utf8");
    for (const [key, value] of Object.entries(report.filters)) expect(content).toContain(`${key}=${value}`);
    if (report.type !== "ANUAL_PROCESSOS") expect(content).not.toContain("ano=2026");
    if (report.type !== "HISTORICO_TRAMITACOES") expect(content).not.toContain("dataInicial=2026-01-01");
  }
});

test("celular: troca real de Administrador para Operador limpa preenchimento e restringe navegação", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await entrarComoAdministrador(page);
  const token = await page.evaluate(() => JSON.parse(localStorage.getItem("sicc-session")!).token);
  const created = await page.request.post("/api/v1/admin/usuarios", {
    headers: { Authorization: `Bearer ${token}` },
    data: { nome: "Operador de Navegação", email: "navegacao@sicc.test", login: "navegacao",
      senhaTemporaria: "Temporaria123!", perfil: "OPERADOR_DIPAC" }
  });
  expect(created.ok()).toBe(true);
  const headers = { Authorization: `Bearer ${token}` };
  const processResponse = await page.request.post("/api/v1/processos", { headers,
    data: { numero: "PROC-TROCA-USUARIO", origem: "DIPAC" } });
  expect(processResponse.ok()).toBe(true);
  const processId = String((await processResponse.json()).id);
  const signed = await page.request.post("/api/v1/documentos", { headers, multipart: {
    proprietarioTipo: "PROCESSO", proprietarioId: processId, categoria: "ASSINADO", titulo: "Instrumento da troca de usuário",
    arquivo: { name: "instrumento.pdf", mimeType: "application/pdf", buffer: Buffer.from(PDF_VALIDO_BASE64, "base64") }
  } });
  expect(signed.ok()).toBe(true);
  const formalized = await page.request.post(`/api/v1/processos/${processId}/instrumento`, { headers, data: {
    numero: "CV-TROCA-USUARIO", tipo: "CONVENIO", objeto: "Teste de isolamento de preenchimento",
    natureza: "Administrativa", coordenador: "Coordenação", participes: ["UFGD"], valorAtual: 0,
    vigenciaContratualFinal: "2099-12-31", dataFormalizacao: "2026-08-01", documentoAssinadoId: (await signed.json()).id
  } });
  expect(formalized.ok()).toBe(true);
  const instrumentId = String((await formalized.json()).id);
  await openMenu(page); await page.getByRole("navigation").getByRole("button", { name: "Alterações contratuais", exact: true }).click();
  await page.getByRole("combobox", { name: "Instrumento Contratual", exact: true }).selectOption(instrumentId);
  await openAlteration(page, "draft");
  await page.getByLabel("Identificação do termo", { exact: true }).fill("PREENCHIMENTO DO ADMINISTRADOR");
  await openMenu(page); await page.getByRole("button", { name: "Registros de Auditoria", exact: true }).click();
    await openMenu(page); await page.getByRole("button", { name: "Sair", exact: true }).click();
  await answerConfirmation(page);
  await openLogin(page); await page.getByLabel("Login", { exact: true }).fill("navegacao");
  await page.getByLabel("Senha", { exact: true }).fill("Temporaria123!");
  await page.getByRole("button", { name: "Entrar no SICC" }).click();
  await page.getByLabel("Senha temporária").fill("Temporaria123!");
  await page.getByLabel("Nova senha").fill("Permanente456!");
  await page.getByRole("button", { name: "Definir senha" }).click();
  await openLogin(page); await page.getByLabel("Login", { exact: true }).fill("navegacao");
  await page.getByLabel("Senha", { exact: true }).fill("Permanente456!");
  await page.getByRole("button", { name: "Entrar no SICC" }).click();
  await expect(page.getByRole("heading", { name: "Visão geral", level: 1 })).toBeFocused();
  await expect(page.getByRole("button", { name: "Administração", exact: true })).toHaveCount(0);
  await page.evaluate(() => { location.hash = "/auditoria"; });
  await expect(page).toHaveURL(/#\/dashboard$/);
  await openMenu(page); await page.getByRole("button", { name: "Alterações contratuais", exact: true }).click();
  await expect(page.getByRole("button", { name: "Preparar Termo Aditivo", exact: true })).toBeDisabled();
  await page.getByRole("combobox", { name: "Instrumento Contratual", exact: true }).selectOption(instrumentId);
  await openAlteration(page, "draft");
  await expect(page.getByLabel("Identificação do termo", { exact: true })).toHaveValue("");
  const operatorToken = await page.evaluate(() => JSON.parse(localStorage.getItem("sicc-session")!).token);
  const restricted = await page.request.get("/api/v1/auditoria", { headers: { Authorization: `Bearer ${operatorToken}` } });
  expect(restricted.status()).toBe(403);
});

for (const width of [1440, 390]) test(`F11/F14: versões e ações de documentos com API real em ${width}px`, async ({ page }, info) => {
  await page.setViewportSize({ width, height: 1000 });
  await entrarComoAdministrador(page);
  const numero = `PROC-ADAPT-F11-F14-${width}`;
  const titulo = "Documento administrativo de acompanhamento institucional com título extenso para validação de versões";
  const filename = "documento_administrativo_com_referencia_extensa_para_verificacao_de_versoes.csv";
  await openMenu(page); await page.getByRole("button", { name: "Processos Administrativos", exact: true }).click(); await openCreation(page);
  const createProcess = page.locator("form").filter({ has: page.getByRole("button", { name: "Cadastrar processo" }) });
  await createProcess.getByLabel("Número", { exact: true }).fill(numero);
  await openFilters(page);
  await createProcess.getByLabel("Origem", { exact: true }).fill("DIPAC");
  const created = page.waitForResponse(response => response.url().endsWith("/api/v1/processos") && response.request().method() === "POST");
  await createProcess.getByRole("button", { name: "Cadastrar processo" }).click();
  const processResponse = await created;
  expect(processResponse.ok()).toBe(true);
  const process = await processResponse.json();

  await openMenu(page); await page.getByRole("button", { name: "Documentos", exact: true }).click();
  await page.locator('select[name="proprietarioId"]').selectOption(String(process.id));
  await page.getByRole("button", { name: "Anexar documento", exact: true }).click();
  const createDocument = page.locator("form").filter({ has: page.getByRole("button", { name: "Anexar documento" }) });
  await createDocument.getByLabel("Título").fill(titulo);
  const original = "numero,versao\n" + numero + ",1\n";
  await createDocument.getByLabel("Arquivo").setInputFiles({ name: filename, mimeType: "text/csv", buffer: Buffer.from(original) });
  const stored = page.waitForResponse(response => response.url().endsWith("/api/v1/documentos") && response.request().method() === "POST");
  await createDocument.getByRole("button", { name: "Anexar documento" }).click();
  const storedResponse = await stored;
  expect(storedResponse.ok()).toBe(true);
  const document = await storedResponse.json();
  const card = page.locator(".document-card").filter({ hasText: titulo });
  await expect(card.getByRole("button", { name: "Baixar versão 1" })).toBeVisible();
  const versionForm = page.locator("form").filter({ has: page.getByRole("button", { name: "Adicionar versão" }) });
  await card.getByRole("button", { name: "Nova versão", exact: true }).click();
  await versionForm.getByLabel("Arquivo").setInputFiles({ name: filename, mimeType: "text/csv", buffer: Buffer.from("numero,versao\n" + numero + ",2\n") });
  await versionForm.getByRole("button", { name: "Adicionar versão" }).click();
  await expect(card.getByRole("button", { name: "Baixar versão 2" })).toBeVisible();
  await card.getByRole("button", { name: "Desativar" }).focus();
  await page.keyboard.press("Tab");
  await expect(card.locator(".document-version > div > details > summary").first()).toBeFocused();
  await page.keyboard.press("Tab");
  await expect(card.getByRole("button", { name: "Baixar versão 2" })).toBeFocused();
  await page.keyboard.press("Tab");
  await expect(card.locator(".document-history > summary")).toBeFocused();
  await page.keyboard.press("Enter"); await page.keyboard.press("Tab"); await page.keyboard.press("Tab");
  const originalDownload = card.getByRole("button", { name: "Baixar versão 1" });
  await expect(originalDownload).toBeFocused();
  const downloading = page.waitForEvent("download");
  await page.keyboard.press("Enter");
  const downloaded = await downloading;
  expect(downloaded.suggestedFilename()).toBe(filename);
  let content = "";
  for await (const chunk of await downloaded.createReadStream()) content += chunk.toString("utf8");
  expect(content).toBe(original);
  for (const button of await card.getByRole("button").all()) {
    const box = (await button.boundingBox())!;
    expect(box.width).toBeGreaterThanOrEqual(44);
    expect(box.height).toBeGreaterThanOrEqual(44);
  }
  await card.screenshot({ path: info.outputPath(`real-document-${width}.png`) });
    const deactivated = page.waitForResponse(response => response.url().endsWith(`/api/v1/documentos/${document.id}`) && response.request().method() === "DELETE");
  await card.getByRole("button", { name: "Desativar" }).focus();
  await page.keyboard.press("Enter");
  await answerConfirmation(page);
  expect((await deactivated).ok()).toBe(true);
  await expect(card.getByRole("button", { name: "Desativar" })).toBeDisabled();
  await expect(card).toContainText("Inativo");
  await expect(originalDownload).toBeEnabled();
  const historicalDownload = page.waitForEvent("download");
  await originalDownload.click();
  expect((await historicalDownload).suggestedFilename()).toBe(filename);
  expect(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)).toBe(false);
});

for (const width of [1440, 390]) test(`modais administrativos e contratuais com API real em ${width}px`, async ({ page }) => {
  test.setTimeout(60000);
  await page.setViewportSize({ width, height: 900 });
  await entrarComoAdministrador(page);
  await openMenu(page); await page.getByRole("button", { name: "Administração", exact: true }).click();
  await page.getByRole("button", { name: "Criar usuário", exact: true }).click();
  let modal = page.getByRole("dialog");
  await modal.getByLabel("Nome", { exact: true }).fill(`Operador Modal ${width}`);
  await modal.getByLabel("E-mail").fill(`modal${width}@sicc.test`);
  await modal.getByLabel("Login imutável").fill(`modal${width}`);
  await modal.getByLabel("Senha temporária", { exact: true }).fill("Temporaria123!");
  await modal.getByRole("button", { name: "Criar usuário", exact: true }).click();
  await expect(modal).toBeHidden();
  await page.getByRole("button", { name: `Gerenciar usuário Operador Modal ${width}` }).click();
  await expect(page.getByRole("dialog")).toContainText(`modal${width}@sicc.test`);
  await closeModal(page);
  await openUserActions(page, `Operador Modal ${width}`);
  await page.getByRole("button", { name: `Redefinir senha de Operador Modal ${width}` }).click();
  await page.getByRole("dialog").getByLabel("Nova senha temporária").fill("Temporaria456!");
  await page.getByRole("dialog").getByRole("button", { name: "Redefinir", exact: true }).click();
  await expect(page.getByRole("dialog")).toBeHidden();

  await page.getByRole("button", { name: "Criar setor", exact: true }).click();
  modal = page.getByRole("dialog");
  await modal.getByLabel("Sigla", { exact: true }).fill(`M${width}`);
  await modal.getByLabel("Nome", { exact: true }).fill(`Setor de conferência dos modais ${width}`);
  await modal.getByRole("button", { name: "Adicionar", exact: true }).click();
  await expect(modal).toBeHidden();
  await page.getByRole("button", { name: `Editar setor M${width}`, exact: true }).click();
  await page.getByLabel("Nome do setor").fill(`Setor de conferência atualizado ${width}`);
  await page.getByRole("button", { name: "Salvar alterações", exact: true }).click();
  await expect(page.locator(".linha-setor").filter({ hasText: `M${width}` })).toContainText(`Setor de conferência atualizado ${width}`);

  const token = await page.evaluate(() => JSON.parse(localStorage.getItem("sicc-session")!).token);
  const headers = { Authorization: `Bearer ${token}` };
  const processResponse = await page.request.post("/api/v1/processos", { headers,
    data: { numero: `PROC-MODAL-${width}`, origem: "DIPAC" } });
  expect(processResponse.ok()).toBe(true);
  const processId = String((await processResponse.json()).id);
  const signed = await page.request.post("/api/v1/documentos", { headers, multipart: {
    proprietarioTipo: "PROCESSO", proprietarioId: processId, categoria: "ASSINADO", titulo: `Instrumento Modal ${width}`,
    arquivo: { name: "instrumento.pdf", mimeType: "application/pdf", buffer: Buffer.from(PDF_VALIDO_BASE64, "base64") }
  } });
  expect(signed.ok()).toBe(true);
  const signedId = String((await signed.json()).id);
  await openMenu(page); await page.getByRole("button", { name: "Processos Administrativos", exact: true }).click();
  await page.getByRole("button", { name: new RegExp(`PROC-MODAL-${width}`) }).click();
  await openProcessAction(page, "edit");
  await page.getByRole("dialog").getByLabel("Número do projeto").fill(`PROJ-MODAL-${width}`);
  await page.getByRole("dialog").getByRole("button", { name: "Salvar", exact: true }).click();
  await expect(page.getByRole("dialog")).toBeHidden();
  await expect(page.locator(".process-administration")).toContainText(`PROJ-MODAL-${width}`);
  await openProcessAction(page, "movement");
  const processSectors = await (await page.request.get("/api/v1/setores", { headers })).json();
  await page.getByRole("dialog").getByLabel("Destino").selectOption(String(processSectors.find((s: { sigla: string }) => s.sigla === `M${width}`).id));
  await page.getByRole("dialog").getByLabel("Observação").fill(`Conferência da formalização ${width}`);
  await page.getByRole("button", { name: "Registrar", exact: true }).click();
  await expect(page.getByRole("dialog")).toBeHidden();
  await expect(page.locator(".tramitacao-panel .timeline")).toContainText(`Conferência da formalização ${width}`);
  await openProcessAction(page, "formalize");
  const formalization = page.getByRole("dialog", { name: "Formalizar Instrumento Contratual", exact: true });
  for (const [label, value] of Object.entries({ Número: `CV-MODAL-${width}`, Objeto: "Conferência dos modais", Natureza: "Administrativa",
    Coordenador: "Coordenação original", "Partícipes, um por linha": "UFGD\nFundação", Valor: "1000", "Vigência contratual": "2099-12-31",
    "Data de formalização": new Date().toISOString().slice(0, 10) })) await formalization.getByLabel(label, { exact: true }).fill(value);
  await formalization.getByRole("combobox", { name: "Tipo", exact: true }).selectOption("CONVENIO");
  await formalization.getByLabel("Documento assinado PDF").selectOption(signedId);
  const formalized = page.waitForResponse(response => response.url().endsWith(`/api/v1/processos/${processId}/instrumento`) && response.request().method() === "POST");
  await formalization.getByRole("button", { name: "Formalizar", exact: true }).click();
  await page.getByRole("button", { name: "Confirmar formalização", exact: true }).click();
  const formalizedResponse = await formalized;
  expect(formalizedResponse.ok()).toBe(true);
  const instrumentId = String((await formalizedResponse.json()).id);
  await expect(formalization).toBeHidden();
  await expect(page.getByRole("heading", { name: `Processo Administrativo · PROC-MODAL-${width}`, exact: true })).toBeFocused();
  await expect(page.locator(".instrumento-resumo")).toContainText(`CV-MODAL-${width}`);
  await openMenu(page); await page.getByRole("button", { name: "Alterações contratuais", exact: true }).click();
  await page.getByRole("combobox", { name: "Instrumento Contratual", exact: true }).selectOption(instrumentId);
  await openAlteration(page, "draft");
  await page.getByLabel("Identificação do termo", { exact: true }).fill(`TA-MODAL-${width}`);
  await page.getByRole("combobox", { name: "Campo da mudança", exact: true }).selectOption("COORDENADOR");
  await page.getByLabel("Novo valor", { exact: true }).fill(`Coordenação Modal ${width}`);
  const created = page.waitForResponse(response => response.url().endsWith("/api/v1/alteracoes") && response.request().method() === "POST");
  await page.getByRole("button", { name: "Criar rascunho", exact: true }).click();
  const alteration = await (await created).json();
  await expect(page.getByRole("dialog")).toBeHidden();
  await openAlteration(page, "edit");
  await page.getByLabel("Identificação do rascunho").fill(`TA-MODAL-${width}-REVISADO`);
  await page.getByRole("button", { name: "Salvar rascunho", exact: true }).click();
  await expect(page.getByRole("dialog")).toBeHidden();
  const document = await page.request.post("/api/v1/documentos", { headers, multipart: {
    proprietarioTipo: "TERMO_ADITIVO", proprietarioId: String(alteration.id), categoria: "ASSINADO", titulo: `PDF do modal ${width}`,
    arquivo: { name: "termo-assinado.pdf", mimeType: "application/pdf", buffer: Buffer.from(PDF_VALIDO_BASE64, "base64") }
  } });
  expect(document.ok()).toBe(true);
  const documentId = String((await document.json()).id);
  await openAlteration(page, "effectuate");
  await page.getByLabel("Data de efetivação").fill(new Date().toISOString().slice(0, 10));
  await page.getByLabel("Ordem oficial").fill(width === 1440 ? "1" : "2");
  await page.getByLabel("PDF assinado").selectOption(documentId);
  await page.getByRole("button", { name: "Confirmar efetivação", exact: true }).click();
  await expect(page.getByRole("dialog")).toBeHidden();
  await expect(page.locator(".alteration-detail h2")).toBeFocused();
  await expect(page.getByRole("button", { name: "Editar rascunho", exact: true })).toHaveCount(0);
  await openAlteration(page, "movement");
  await page.getByLabel("Data da movimentação").fill(new Date().toISOString().slice(0, 10));
  const sectors = await (await page.request.get("/api/v1/setores", { headers })).json();
  await page.getByLabel("Setor de destino").selectOption(String(sectors.find((s: { sigla: string }) => s.sigla === `M${width}`).id));
  await page.getByLabel("Observação da movimentação").fill("Conferência no banco temporário");
  await page.getByRole("button", { name: "Registrar movimentação", exact: true }).click();
  await expect(page.getByRole("dialog")).toBeHidden();
  await expect(page.locator(".alteration-detail .timeline").last()).toContainText("Conferência no banco temporário");
  await openAlteration(page, "other");
  await page.getByLabel("Tipo da alteração").selectOption("TERMO_ADITIVO");
  await page.getByLabel("Operação da alteração").selectOption("CANCELAMENTO");
  await page.getByLabel("Identificação da outra alteração").fill(`CAN-MODAL-${width}`);
  await page.getByLabel("Alteração de referência").selectOption(String(alteration.id));
  await page.getByRole("button", { name: "Criar outra alteração", exact: true }).click();
  await expect(page.getByRole("dialog")).toBeHidden();
  await expect(page.locator(".alteracao-lista")).toContainText(`CAN-MODAL-${width}`);
});

test("relatórios gerenciais reais filtram setor e responsável e baixam os indicadores", async ({ page }) => {
  await entrarComoAdministrador(page);
  const token = await page.evaluate(() => JSON.parse(localStorage.getItem("sicc-session")!).token);
  const headers = { Authorization: `Bearer ${token}` };
  const responsaveis = await (await page.request.get("/api/v1/processos/responsaveis", { headers })).json();
  const setores = await (await page.request.get("/api/v1/setores", { headers })).json();
  const responsavel = responsaveis[0];
  const setor = setores[0];
  const created = await page.request.post("/api/v1/processos", { headers, data: {
    numero: "PROC-RELATORIO-GERENCIAL-REAL", origem: "DIPAC", responsavelId: responsavel.id
  } });
  expect(created.ok()).toBe(true);
  const processo = await created.json();
  const movimentado = await page.request.post("/api/v1/movimentacoes", { headers, data: {
    contextoTipo: "FORMALIZACAO", contextoId: processo.id, dataMovimentacao: processo.dataCadastro,
    setorDestinoId: setor.id, observacao: "Recorte gerencial de teste"
  } });
  expect(movimentado.ok()).toBe(true);
  await openMenu(page); await page.getByRole("button", { name: "Relatórios", exact: true }).click();
  for (const width of [1440, 390]) {
    await page.setViewportSize({ width, height: 1000 });
    await openFilters(page);
    await page.getByLabel("Número do processo", { exact: true }).fill(processo.numero);
    await page.getByLabel("Setor atual do processo", { exact: true }).selectOption(String(setor.id));
    await page.getByLabel("Responsável DIPAC", { exact: true }).selectOption(String(responsavel.id));
    for (const tipo of ["CONSOLIDADO", "ANUAL_PROCESSOS"]) {
      await page.getByLabel("Tipo de relatório", { exact: true }).selectOption(tipo);
      await openFilters(page);
      if (tipo === "ANUAL_PROCESSOS") await page.getByLabel("Ano do relatório", { exact: true }).fill(processo.dataCadastro.slice(0, 4));
      const gerando = page.waitForResponse(r => r.url().endsWith("/api/v1/relatorios") && r.request().method() === "POST");
      await page.getByRole("button", { name: "Gerar CSV", exact: true }).click();
      const response = await gerando;
      expect(response.ok()).toBe(true);
      const relatorio = await response.json();
      expect(relatorio.filtros.setorId).toBe(String(setor.id));
      expect(relatorio.filtros.responsavelId).toBe(String(responsavel.id));
      const download = page.waitForEvent("download");
      await page.locator("article.doc").filter({ hasText: relatorio.nomeArquivo }).getByRole("button", { name: "Baixar", exact: true }).click();
      let conteudo = "";
      for await (const chunk of await (await download).createReadStream()) conteudo += chunk.toString("utf8");
      expect(conteudo).toContain("total_processos;1;processos");
      expect(conteudo).toContain("em_formalizacao;1;processos");
      expect(conteudo).toContain(processo.numero);
      if (tipo === "ANUAL_PROCESSOS") expect(conteudo).toContain("cadastros_ano;1;processos");
    }
  }
});
