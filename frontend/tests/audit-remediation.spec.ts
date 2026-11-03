import { expect, test, type Page } from "@playwright/test";
import { openFilters, openUserActions } from "./navigation-helpers";
import { mockInstrumentosAlteracao } from "./instrument-options";
import { answerConfirmation, openProcessAction, openMenu, openAlteration, closeModal } from "./navigation-helpers";

const empty = { content: [], totalPages: 0, totalElements: 0, number: 0, size: 20 };
const actor = { id: 1, nome: "Operador", login: "operador" };
const instrument = { id: 10, numero: "CV-AUDITORIA", tipo: "CONVENIO", objeto: "Cooperação científica",
  descricao: "Descrição integral preservada", natureza: "Acadêmica", coordenador: "Maria",
  participes: ["UFGD", "Fundação de apoio"], valorAtual: 1234.56, vigenciaContratualFinal: "2027-12-31",
  vigenciaTedFinal: "2027-06-30", documentoAssinadoId: 1, documentoAssinadoVersao: 2,
  documentoAssinadoChecksumSha256: "a".repeat(64), situacaoContratual: "VALIDA", situacaoTed: "VALIDA" };
const process = { id: 1, numero: "PROC-AUDITORIA", origem: "DIPAC", ativo: true, status: "EM_FORMALIZACAO" };
const signed = { id: 1, titulo: "Assinado", ativo: true, categoria: "ASSINADO", criadoPor: actor,
  versoes: [{ versao: 2, tipoMime: "application/pdf", nomeArquivo: "assinado.pdf", tamanho: 10,
    checksumSha256: "a".repeat(64), criadoPor: actor }] };
const draft = { id: 1, numeroOficial: "TA-AUDITORIA", instrumentoId: 10, tipo: "TERMO_ADITIVO", estado: "RASCUNHO",
  operacao: "ORIGINAL", mudancas: [{ campo: "OBJETO", valorAnterior: "Cooperação científica", valorNovo: "Objeto proposto" }],
  estadoAtualInstrumento: { ...instrument, statusProcesso: "EM_VIGENCIA", precedenciaPorCampo: {} },
  tramitacao: { movimentacoes: [], permanencias: [], setorAtual: null } };

async function setup(page: Page, formalized = true) {
  await page.addInitScript(() => localStorage.setItem("sicc-session", JSON.stringify({
    token: "audit-fixture", perfil: "ADMINISTRADOR_DIPAC", trocaSenhaObrigatoria: false
  })));
  await page.route("**/api/v1/**", route => {
    const path = new URL(route.request().url()).pathname;
    if (route.request().method() !== "GET") return route.fulfill({ status: 503, json: { mensagem: "Falha simulada." } });
    let json: unknown = [];
    if (path.endsWith("/processos")) json = { ...empty, totalPages: 1, totalElements: 20,
      content: Array.from({ length: 20 }, (_, i) => ({ ...process, id: i + 1, numero: i ? `PROC-${i}` : process.numero,
        instrumento: formalized ? { ...instrument, id: i + 10 } : undefined })) };
    else if (path.endsWith("/tramitacao")) json = draft.tramitacao;
    else if (path.endsWith("/documentos")) json = [signed];
    else if (path.endsWith("/alteracoes")) json = [draft];
    else if (path.endsWith("/setores")) json = [{ id: 1, sigla: "DIPAC", nome: "Parcerias", ativo: true }];
    else if (path.endsWith("/admin/usuarios")) json = [{ ...actor, email: "operador@sicc.test", perfil: "OPERADOR_DIPAC", ativo: true }];
    else if (path.endsWith("/admin/usuarios/1")) json = { ...actor, email: "operador@sicc.test", perfil: "OPERADOR_DIPAC", ativo: true };
    else if (path.endsWith("/proprietarios")) json = { ...empty, content: [{ ...process, numeroProcesso: process.numero, statusProcesso: process.status, processoAtivo: true }], totalPages: 1, totalElements: 1 };
    return route.fulfill({ json });
  });
  await mockInstrumentosAlteracao(page, [{ ...process, instrumento: instrument }]);
  page.on("pageerror", error => { throw error; });
}
async function nav(page: Page, name: string) {
  await openMenu(page); await page.getByRole("navigation").getByRole("button", { name, exact: true }).click();
}

for (const width of [1440, 390]) test.describe(`auditoria · ${width}px`, () => {
  test.use({ viewport: { width, height: 900 } });
  test("A01/A02: detalhe recebe foco antes da lista e exibe ficha completa com download", async ({ page }) => {
    await setup(page); await page.goto("/#/processos");
    await expect(page.getByRole("button", { name: "Cadastrar processo" })).toBeHidden();
    await expect(page.locator(".process-card")).toHaveCount(20);
    await page.getByRole("button", { name: /PROC-AUDITORIA/ }).click();
    const heading = page.getByRole("heading", { name: "Processo Administrativo · PROC-AUDITORIA", exact: true });
    await expect(heading).toBeFocused();
    expect((await heading.boundingBox())!.y).toBeLessThan(900);
    await expect(page.locator(".process-catalog")).toBeHidden();
    const summary = page.locator(".instrumento-resumo");
    for (const text of [instrument.objeto, instrument.descricao, instrument.natureza, "Fundação de apoio", "1.234,56", "31/12/2027", "30/06/2027"]) await expect(summary).toContainText(text);
    await page.route("**/api/v1/documentos/1/versoes/2/arquivo", route => route.fulfill({ body: "%PDF-test", contentType: "application/pdf" }));
    const request = page.waitForRequest("**/api/v1/documentos/1/versoes/2/arquivo");
    await page.getByRole("button", { name: "Baixar documento assinado" }).click(); await request;
    await page.getByRole("button", { name: "Voltar à lista" }).click();
    await expect(page.getByRole("button", { name: /PROC-AUDITORIA/ })).toBeFocused();
  });

  test("A04/A05: cadastro e bloqueio de envio sobrevivem à navegação; falha mantém dados", async ({ page }) => {
    await setup(page); let posts = 0, release!: () => void;
    const pending = new Promise<void>(resolve => { release = resolve; });
    await page.route("**/api/v1/processos", async route => { posts++; await pending; await route.fulfill({ status: 503, json: { mensagem: "Falha simulada." } }); });
    await page.goto("/#/processos"); await page.getByRole("button", { name: "Novo Processo Administrativo", exact: true }).click();
    const form = page.locator("form").filter({ has: page.getByRole("button", { name: "Cadastrar processo" }) });
    await form.getByLabel("Número", { exact: true }).fill("PROCESSO-NOVO");
    await openFilters(page);
    await form.getByLabel("Origem", { exact: true }).fill("Origem preenchida");
    await form.evaluate(form => { for (let i = 0; i < 5; i++) (form as HTMLFormElement).requestSubmit(); });
    await expect.poll(() => posts).toBe(1);
    await page.goto("/#/documentos"); await page.goto("/#/processos");
    await expect(form.getByRole("button", { name: "Cadastrar processo" })).toBeDisabled();
    await expect(form.getByLabel("Número", { exact: true })).toHaveValue("PROCESSO-NOVO");
    release(); await expect(form.getByRole("alert")).toContainText("Falha simulada");
    await expect(form.getByRole("button", { name: "Cadastrar processo" })).toBeEnabled(); expect(posts).toBe(1);
    await nav(page, "Sair"); await answerConfirmation(page, false);
    await page.getByRole("button", { name: "Novo Processo Administrativo", exact: true }).click();
    await expect(form.getByLabel("Origem", { exact: true })).toHaveValue("Origem preenchida");
  });

  test("A04/A07: documento contextual preserva formalização e POST aceito não vira falha de gravação", async ({ page }) => {
    await setup(page, false); let posts = 0;
    await page.route("**/api/v1/processos/1/instrumento", route => { posts++; return route.fulfill({ status: 201, json: instrument }); });
    await page.route("**/api/v1/processos/1", route => route.fulfill({ status: 503, json: { mensagem: "Consulta indisponível" } }));
    await page.goto("/#/processos"); await page.getByRole("button", { name: /PROC-AUDITORIA/ }).click();
    await openProcessAction(page, "formalize");
    const form = page.locator("form").filter({ has: page.getByRole("button", { name: "Formalizar", exact: true }) });
    for (const [label, value] of Object.entries({ Número: "CV-NOVO", Objeto: "Objeto preenchido", Descrição: "Descrição", Natureza: "Acadêmica", Coordenador: "Maria", Valor: "1234.56", "Partícipes, um por linha": "UFGD\nFundação", "Vigência contratual": "2027-12-31", "Data de formalização": "2026-01-01" })) await form.getByLabel(label, { exact: true }).fill(value);
    await page.getByRole("button", { name: "Anexar documento deste processo" }).click();
    await expect(page.locator('select[name="proprietarioId"]')).toHaveValue("1");
    await expect(page.getByRole("combobox", { name: "Categoria", exact: true })).toHaveValue("ASSINADO");
    await page.getByRole("button", { name: "Fechar anexação" }).click();
    await page.getByRole("button", { name: "Voltar ao processo em preenchimento" }).click();
    await openProcessAction(page, "formalize");
    await expect(form.getByLabel("Objeto", { exact: true })).toHaveValue("Objeto preenchido");
    await form.getByRole("combobox", { name: "Tipo", exact: true }).selectOption("CONVENIO");
    await form.getByLabel("Documento assinado PDF").selectOption("1");
    await form.getByRole("button", { name: "Formalizar", exact: true }).click();
    await page.getByRole("button", { name: "Confirmar formalização", exact: true }).click();
    await expect(page.locator(".instrumento-resumo")).toContainText(instrument.numero);
    await expect(page.locator(".toast:visible")).toContainText("Instrumento Contratual formalizado");
    await expect(page.getByRole("button", { name: "Formalizar", exact: true })).toHaveCount(0); expect(posts).toBe(1);
  });
});

test("A03: valores respeitam multiline, datas, remoção opcional e moeda brasileira", async ({ page }) => {
  await setup(page); const submitted: any[] = [];
  await page.route("**/api/v1/alteracoes", route => { submitted.push(route.request().postDataJSON()); return route.fulfill({ status: 201, json: draft }); });
  await closeModal(page);
  await page.goto("/#/alteracoes"); await page.getByRole("combobox", { name: "Instrumento Contratual", exact: true }).selectOption("10");
  for (const [campo, value, expected] of [["PARTICIPES", "UFGD\nFundação", "UFGD\nFundação"], ["VIGENCIA_TED_FINAL", "", ""], ["DESCRICAO", "", ""], ["VALOR_ATUAL", "1.234,56", "1234.56"], ["VALOR_ATUAL", "0", "0"], ["VIGENCIA_CONTRATUAL_FINAL", "2028-02-29", "2028-02-29"]]) {
    await openAlteration(page, "draft");
    await page.getByLabel("Identificação do termo", { exact: true }).fill(`TA-${submitted.length}`);
    await page.getByRole("combobox", { name: "Campo da mudança", exact: true }).selectOption(campo);
    const field = page.getByLabel("Novo valor", { exact: true });
    if (campo === "PARTICIPES") expect(await field.evaluate(e => e.tagName)).toBe("TEXTAREA");
    if (campo.startsWith("VIGENCIA")) await expect(field).toHaveAttribute("type", "date");
    await field.fill(value); const count = submitted.length;
    await page.getByRole("button", { name: "Criar rascunho", exact: true }).click();
    await expect.poll(() => submitted.length).toBe(count + 1);
    expect(submitted.at(-1).mudancas[0].valorNovo).toBe(expected);
    await expect(page.getByLabel("Identificação do termo", { exact: true })).toHaveValue("");
  }
});

test("A06: erros de catálogo e PDF não são tratados como ausência de registros", async ({ page }) => {
  await setup(page); let failCatalog = true, failDocs = true;
  await page.route("**/api/v1/alteracoes?*", route => route.fulfill(failCatalog ? { status: 503, json: { mensagem: "Catálogo indisponível" } } : { json: [draft] }));
  await page.route("**/api/v1/documentos?*", route => route.fulfill(failDocs ? { status: 503, json: { mensagem: "PDF indisponível" } } : { json: [signed] }));
  await closeModal(page);
  await page.goto("/#/alteracoes"); await page.getByRole("combobox", { name: "Instrumento Contratual", exact: true }).selectOption("10");
  await expect(page.getByRole("alert").filter({ hasText: "Catálogo indisponível" })).toBeVisible();
  await expect(page.getByText(/Nenhum Termo Aditivo preparado/)).toHaveCount(0);
  failCatalog = false; await page.getByRole("button", { name: "Tentar novamente" }).click();
  await page.getByRole("button", { name: /TA-AUDITORIA/ }).click();
  await openAlteration(page, "effectuate");
  await expect(page.getByRole("alert").filter({ hasText: "PDF indisponível" })).toBeVisible();
  await expect(page.getByText(/Anexe um Documento Assinado/)).toHaveCount(0);
  await expect(page.getByRole("button", { name: "Confirmar efetivação" })).toBeDisabled();
  failDocs = false; await page.getByRole("button", { name: "Tentar novamente" }).click();
  await expect(page.getByRole("button", { name: "Confirmar efetivação" })).toBeEnabled();
});

test("A08: cancelar confirmações não altera documento, acesso, perfil ou setor", async ({ page }) => {
  await setup(page); const mutations: string[] = [];
  page.on("request", request => { if (request.method() !== "GET") mutations.push(request.url()); });
  await page.goto("/#/documentos"); await page.locator('select[name="proprietarioId"]').selectOption("1");
  await page.locator(".document-card").getByRole("button", { name: "Desativar" }).click();
  await answerConfirmation(page, false);
  await nav(page, "Administração");
  await openUserActions(page);
  await page.getByRole("dialog", { name: "Gerenciar usuário", exact: true }).getByRole("button", { name: "Desativar acesso" }).click();
  await answerConfirmation(page, false);
  await page.getByRole("dialog", { name: "Gerenciar usuário", exact: true }).getByLabel("Perfil").selectOption("ADMINISTRADOR_DIPAC");
    await page.getByRole("button", { name: "Salvar perfil", exact: true }).click();
  await answerConfirmation(page, false);
  await expect(page.getByRole("dialog", { name: "Gerenciar usuário", exact: true }).getByLabel("Perfil")).toHaveValue("OPERADOR_DIPAC");
  await closeModal(page);
    await page.locator(".linha-setor").getByRole("button", { name: "Desativar" }).click();
  await answerConfirmation(page, false);
  expect(mutations).toEqual([]);
});

test("A10: 60 e 300 dias têm proporção visual de 1 para 5", async ({ page }) => {
  await setup(page);
  await page.route("**/api/v1/dashboard**", route => route.fulfill({ json: { processosPorStatus: { EM_VIGENCIA: 2 }, percentualConcluidos: 0,
    alertasContratuais: 0, alertasTed: 0, valorTotalVigente: 0, instrumentosPorTipo: {}, tempoMedioTramitacaoInicialDias: 1,
    formalizacoesMensais: {}, conclusoesMensais: {}, permanenciaMediaPorSetor: { DIPAC: 60, PROAP: 300 }, detalhesPermanenciaPorSetor: {} } }));
  await page.goto("/#/dashboard");
  const bars = page.locator(".bar i"); await expect(bars).toHaveCount(2);
  const small = (await bars.nth(0).boundingBox())!.width, large = (await bars.nth(1).boundingBox())!.width;
  expect(small / large).toBeCloseTo(0.2, 2);
});

test("A09/A11: consulta móvel exibe datas locais e registros sem rolagem horizontal", async ({ page }) => {
  await page.setViewportSize({ width: 320, height: 800 });
  await page.route("**/api/v1/public/processos?*", route => route.fulfill({ json: { ...empty, totalPages: 1, totalElements: 1,
    content: [{ numeroProcesso: process.numero, origem: process.origem, coordenador: instrument.coordenador,
      tipoInstrumento: instrument.tipo, status: "EM_VIGENCIA", vigenciaContratualFinal: "2027-12-31", vigenciaTedFinal: null }] } }));
  await page.goto("/");
  await openFilters(page);
  await expect(page.getByLabel("Vigência (contratual ou TED)")).toBeVisible();
  await expect(page.locator(".login-card")).toBeHidden();
  await expect(page.getByText("31/12/2027", { exact: true })).toBeVisible();
  await expect(page.locator('td[data-label="Vigência TED"]')).toHaveText("Não informada");
  expect(await page.locator(".table-wrap").evaluate(e => e.scrollWidth <= e.clientWidth + 1)).toBe(true);
  await page.getByRole("link", { name: "Acesso interno", exact: true }).click();
  await expect(page.getByLabel("Login", { exact: true })).toBeVisible();
});
