import { expect, test, type Page, type TestInfo } from "@playwright/test";
import { openAlteration, closeModal, openUserActions, openInstrumentPicker } from "./navigation-helpers";
import { mockInstrumentosAlteracao } from "./instrument-options";

const actor = { id: 1, nome: "Maria da Silva", login: "maria", email: "maria@sicc.test",
  perfil: "OPERADOR_DIPAC", ativo: true, senhaTemporaria: true };
const sector = { id: 1, sigla: "DIPAC", nome: "Divisão de Parcerias e Convênios", ativo: true };
const instrument = { id: 10, numero: "CV-2026/10", tipo: "CONVENIO", objeto: "Cooperação institucional",
  natureza: "Acadêmica", coordenador: "Maria", participes: ["UFGD"], valorAtual: 100,
  vigenciaContratualFinal: "2027-12-31", vigenciaTedFinal: null };
const draft = { id: 11, instrumentoId: 10, numeroOficial: "TA-01/2026", tipo: "TERMO_ADITIVO",
  estado: "RASCUNHO", operacao: "ORIGINAL", mudancas: [{ campo: "OBJETO", valorAnterior: instrument.objeto, valorNovo: "Novo objeto" }],
  estadoAtualInstrumento: { ...instrument, statusProcesso: "EM_VIGENCIA", precedenciaPorCampo: {} },
  tramitacao: { setorAtual: null, movimentacoes: [], permanencias: [] } };
const official = { ...draft, id: 12, numeroOficial: "TA-02/2026", estado: "EFETIVADA", dataEfetivacao: "2026-01-01", ordemOficial: 1 };

async function setup(page: Page) {
  await page.addInitScript(() => localStorage.setItem("sicc-session", JSON.stringify({
    token: "modal-preview-fixture", perfil: "ADMINISTRADOR_DIPAC", trocaSenhaObrigatoria: false
  })));
  await page.route("**/api/v1/**", route => {
    const path = new URL(route.request().url()).pathname;
    let json: unknown = [];
    if (path === "/api/v1/admin/usuarios") json = [actor];
    else if (path === "/api/v1/admin/usuarios/1") json = actor;
    else if (path === "/api/v1/admin/setores" || path === "/api/v1/setores") json = [sector];
    else if (path === "/api/v1/alteracoes") json = [draft, official];
    else if (path === "/api/v1/alteracoes/11") json = draft;
    else if (path === "/api/v1/documentos") json = [{ id: 7, titulo: "Termo assinado", categoria: "ASSINADO", ativo: true,
      versoes: [{ versao: 1, tipoMime: "application/pdf" }] }];
    return route.fulfill({ json });
  });
  await mockInstrumentosAlteracao(page, [{ numero: "PROC-2026/10", instrumento: instrument }]);
  page.on("pageerror", error => { throw error; });
}

async function checkModal(page: Page, info: TestInfo, name: string) {
  const modal = page.getByRole("dialog");
  await expect(modal.getByRole("heading").first()).toBeFocused();
  const bounds = (await modal.boundingBox())!, viewport = page.viewportSize()!;
  expect(bounds.x).toBeGreaterThanOrEqual(0); expect(bounds.y).toBeGreaterThanOrEqual(0);
  expect(bounds.x + bounds.width).toBeLessThanOrEqual(viewport.width);
  expect(bounds.y + bounds.height).toBeLessThanOrEqual(viewport.height);
  expect(await modal.evaluate(el => el.scrollWidth <= el.clientWidth)).toBe(true);
  for (let i = 0; i < 12; i++) {
    await page.keyboard.press("Tab");
    expect(await page.evaluate(() => !!document.activeElement?.closest("dialog[open]"))).toBe(true);
  }
  await page.getByRole("dialog").screenshot({ path: info.outputPath(name + ".png") });
}

for (const width of [1440, 768, 390, 320]) test.describe(`administração e alterações · ${width}px`, () => {
  test.use({ viewport: { width, height: 844 } });

  test("usuário: foco, rascunho, erro local e confirmação única", async ({ page }, info) => {
    await setup(page); let posts = 0, release!: () => void;
    const pending = new Promise<void>(resolve => { release = resolve; });
    await page.route("**/api/v1/admin/usuarios", async route => {
      if (route.request().method() === "GET") return route.fulfill({ json: [actor] });
      if (++posts === 1) return route.fulfill({ status: 503, json: { mensagem: "Cadastro indisponível." } });
      await pending; return route.fulfill({ status: 201, json: actor });
    });
    await page.goto("/#/administracao");
    const trigger = page.getByRole("button", { name: "Criar usuário", exact: true });
    await expect(page.locator(".user-row")).toBeVisible(); await trigger.click();
    await checkModal(page, info, "new-user");
    const modal = page.getByRole("dialog");
    await modal.getByLabel("Nome", { exact: true }).fill("Pessoa de Teste");
    await modal.getByLabel("E-mail").fill("teste@sicc.test"); await modal.getByLabel("Login imutável").fill("teste");
    await modal.getByLabel("Senha temporária", { exact: true }).fill("SenhaSomenteFixture123!");
    await page.keyboard.press("Escape"); await expect(trigger).toBeFocused(); await trigger.click();
    await expect(modal.getByLabel("Nome", { exact: true })).toHaveValue("Pessoa de Teste");
    await modal.getByRole("button", { name: "Criar usuário" }).click();
    await expect(modal.getByRole("alert")).toContainText("Cadastro indisponível");
    await modal.getByRole("button", { name: "Criar usuário" }).click();
    await expect.poll(() => posts).toBe(2); await page.keyboard.press("Escape");
    await expect(modal).toBeVisible(); await expect(modal.locator(".modal-close")).toBeDisabled();
    release(); await expect(modal).toBeHidden(); await expect(trigger).toBeFocused();
    await trigger.click(); await expect(modal.getByLabel("Nome", { exact: true })).toHaveValue("");
  });

  test("administração: detalhes seguros, senha contextual e setores preservados", async ({ page }, info) => {
    await setup(page); await page.goto("/#/administracao");
    const details = page.getByRole("button", { name: "Gerenciar usuário Maria da Silva" });
    await details.click(); await checkModal(page, info, "user-detail");
    await expect(page.getByRole("dialog")).toContainText("maria@sicc.test");
    await expect(page.getByRole("dialog")).not.toContainText("senhaHash");
    await page.keyboard.press("Escape"); await expect(details).toBeFocused();
    await openUserActions(page);
    await page.getByRole("button", { name: "Redefinir senha de Maria da Silva" }).click();
    await checkModal(page, info, "password");
    await expect(page.getByRole("dialog", { name: "Redefinir senha temporária", exact: true })).toContainText("Maria da Silva · @maria");
    await closeModal(page);
    const create = page.getByRole("button", { name: "Criar setor", exact: true });
    await create.click(); await checkModal(page, info, "new-sector");
    await page.getByRole("dialog").getByLabel("Sigla", { exact: true }).fill("PROAP");
    await closeModal(page); await create.click();
    await expect(page.getByRole("dialog").getByLabel("Sigla", { exact: true })).toHaveValue("PROAP");
    await closeModal(page); await page.getByRole("button", { name: "Editar setor DIPAC" }).click();
    await checkModal(page, info, "edit-sector");
    await page.getByLabel("Nome do setor").fill("Nome de setor preservado");
    await closeModal(page); await page.getByRole("button", { name: "Criar setor", exact: true }).click();
    await expect(page.getByRole("dialog").getByLabel("Sigla", { exact: true })).toHaveValue("PROAP");
    await closeModal(page); await page.getByRole("button", { name: "Editar setor DIPAC" }).click();
    await expect(page.getByLabel("Nome do setor")).toHaveValue("Nome de setor preservado");
  });

  test("preparação e edição: valores de domínio, erro e rascunhos independentes", async ({ page }, info) => {
    await setup(page); await page.route("**/api/v1/alteracoes", route => route.request().method() === "POST"
      ? route.fulfill({ status: 503, json: { mensagem: "Criação indisponível." } }) : route.fulfill({ json: [draft, official] }));
    await page.goto("/#/alteracoes"); await page.getByRole("combobox", { name: "Instrumento Contratual", exact: true }).selectOption("10");
    await openAlteration(page, "draft"); await checkModal(page, info, "new-alteration");
    await page.getByLabel("Identificação do termo", { exact: true }).fill("TA-NOVO");
    await page.getByLabel("Novo valor", { exact: true }).fill("Proposta preservada");
    await page.getByRole("button", { name: "Criar rascunho", exact: true }).click();
    await expect(page.getByRole("dialog").getByRole("alert")).toContainText("Criação indisponível");
    await closeModal(page); await page.locator(".alteracao-lista button").filter({ hasText: "TA-01/2026" }).click();
    await openAlteration(page, "edit"); await checkModal(page, info, "edit-alteration");
    await page.getByLabel("Identificação do rascunho").fill("TA-EDITADO");
    await closeModal(page); await openAlteration(page, "draft");
    await expect(page.getByLabel("Identificação do termo", { exact: true })).toHaveValue("TA-NOVO");
    await expect(page.getByLabel("Novo valor", { exact: true })).toHaveValue("Proposta preservada");
    await openAlteration(page, "edit"); await expect(page.getByLabel("Identificação do rascunho")).toHaveValue("TA-EDITADO");
  });

  test("comparação legível e falha de edição visível sem mudar os valores da API", async ({ page }, info) => {
    await setup(page);
    const longObject = "Cooperação acadêmica para pesquisa, extensão e formação de estudantes em parceria com instituições públicas. " + "REFERENCIA".repeat(16);
    const current = { ...instrument, objeto: longObject, valorAtual: 180000, participes: ["UFGD", "Fundação de Apoio"] };
    const changes = [
      { campo: "VALOR_ATUAL", valorAnterior: "180000.00", valorNovo: "195000" },
      { campo: "VIGENCIA_CONTRATUAL_FINAL", valorAnterior: "2027-12-31", valorNovo: "2028-06-30" },
      { campo: "OBJETO", valorAnterior: longObject, valorNovo: "Proposta de cooperação preservada" },
      { campo: "PARTICIPES", valorAnterior: "UFGD\nFundação de Apoio", valorNovo: "UFGD\nFundação de Apoio" }
    ];
    const record = { ...draft, mudancas: changes, estadoAtualInstrumento: { ...draft.estadoAtualInstrumento, ...current } };
    await mockInstrumentosAlteracao(page, [
      { numero: "PROC-2026/10", instrumento: current },
      { numero: "PROC-2026/20", instrumento: { ...current, id: 20, numero: "CV-ZERO", valorAtual: 0 } }
    ]);
    await page.route("**/api/v1/alteracoes?*", route => route.fulfill({ json: [record] }));
    let updates = 0;
    await page.route("**/api/v1/alteracoes/11", route => {
      if (route.request().method() === "GET") return route.fulfill({ json: record });
      updates++;
      expect(route.request().postDataJSON().mudancas).toEqual(changes);
      return route.fulfill({ status: 503, json: { mensagem: "Edição indisponível. Tente novamente." } });
    });
    await page.goto("/#/alteracoes");
    await page.getByRole("combobox", { name: "Instrumento Contratual", exact: true }).selectOption("10");
    await page.getByRole("button", { name: /TA-01\/2026 #11/ }).click();
    await openAlteration(page, "edit");
    const dialog = page.getByRole("dialog", { name: "Editar rascunho", exact: true });
    await expect(dialog.getByRole("group", { name: "Valor anterior editado", exact: true })).toContainText(/R\$\s*180\.000,00/);
    await expect(dialog.getByRole("group", { name: "Estado atual do campo 2", exact: true })).toContainText("31/12/2027");
    const object = dialog.getByRole("group", { name: "Estado atual do campo 3", exact: true }).locator("p");
    await expect(object).toHaveText(longObject);
    expect(await object.evaluate(el => el.scrollWidth <= el.clientWidth && el.scrollHeight <= el.clientHeight)).toBe(true);
    await expect(dialog.getByRole("group", { name: "Estado atual do campo 4", exact: true }).locator("p")).toHaveText("UFGD\nFundação de Apoio");
    await page.screenshot({ path: info.outputPath("previous-values-readable.png") });
    await dialog.getByRole("button", { name: "Salvar rascunho", exact: true }).click();
    const alert = dialog.getByRole("alert");
    await expect(alert).toContainText("Edição indisponível");
    await expect(alert).toBeInViewport({ ratio: 1 }); await expect(alert).toBeFocused();
    await expect(dialog.getByLabel("Conteúdo proposto 3", { exact: true })).toHaveValue("Proposta de cooperação preservada");
    expect(updates).toBe(1);
    await page.screenshot({ path: info.outputPath("alteration-error-visible.png") });
    await closeModal(page); await openAlteration(page, "draft");
    await page.getByRole("combobox", { name: "Campo da mudança", exact: true }).selectOption("VIGENCIA_TED_FINAL");
    await expect(page.getByRole("group", { name: "Valor anterior", exact: true })).toContainText("Não informado");
    await closeModal(page); await openAlteration(page, "other");
    await page.getByRole("combobox", { name: "Tipo da alteração", exact: true }).selectOption("TERMO_ADITIVO");
    await page.getByRole("combobox", { name: "Campo da outra alteração", exact: true }).selectOption("OBJETO");
    await expect(page.getByRole("group", { name: "Estado atual do campo", exact: true })).toContainText(longObject);
    await closeModal(page);
    await openInstrumentPicker(page);
    await page.getByRole("combobox", { name: "Instrumento Contratual", exact: true }).selectOption("20");
    await openAlteration(page, "draft");
    await page.getByRole("combobox", { name: "Campo da mudança", exact: true }).selectOption("VALOR_ATUAL");
    await expect(page.getByRole("group", { name: "Valor anterior", exact: true })).toContainText(/R\$\s*0,00/);
  });

  test("efetivação e movimentação: confirmação, retorno de foco e POST aceito", async ({ page }, info) => {
    await setup(page); let effects = 0, moves = 0;
    await page.route("**/api/v1/alteracoes/11/efetivacao", route => {
      effects++; return route.fulfill({ json: { ...draft, estado: "EFETIVADA" } });
    });
    await page.route("**/api/v1/movimentacoes", route => { moves++; return route.fulfill({ status: 201, json: {} }); });
    await page.route("**/api/v1/alteracoes/11", route => route.fulfill({ status: 503, json: { mensagem: "Consulta indisponível." } }));
    await page.goto("/#/alteracoes"); await page.getByRole("combobox", { name: "Instrumento Contratual", exact: true }).selectOption("10");
    await page.locator(".alteracao-lista button").filter({ hasText: "TA-01/2026" }).click();
    await openAlteration(page, "effectuate"); await checkModal(page, info, "effectuation");
    await page.getByLabel("Data de efetivação").fill("2026-01-02"); await page.getByLabel("Ordem oficial").fill("2");
    await page.getByLabel("PDF assinado").selectOption("7");
    await page.getByRole("button", { name: "Confirmar efetivação" }).click();
    await expect(page.getByRole("dialog")).toBeHidden(); expect(effects).toBe(1);
    await expect(page.locator(".alteration-detail h2")).toBeFocused();
    await expect(page.getByRole("button", { name: "Editar rascunho", exact: true })).toHaveCount(0);
    await openAlteration(page, "movement"); await checkModal(page, info, "movement");
    await page.getByLabel("Data da movimentação").fill("2026-01-03"); await page.getByLabel("Setor de destino").selectOption("1");
    await page.getByLabel("Observação da movimentação").fill("Análise da DIPAC"); await closeModal(page);
    await openAlteration(page, "movement"); await expect(page.getByLabel("Observação da movimentação")).toHaveValue("Análise da DIPAC");
    await page.getByRole("button", { name: "Registrar movimentação", exact: true }).click();
    await expect(page.getByRole("dialog")).toBeHidden(); expect(moves).toBe(1);
    await expect(page.getByRole("alert").filter({ hasText: "A movimentação foi registrada" })).toBeVisible();
  });

  test("outras operações: retificação e cancelamento conservam a referência", async ({ page }, info) => {
    await setup(page); let payload: Record<string, unknown> = {};
    await page.route("**/api/v1/alteracoes", route => {
      if (route.request().method() === "GET") return route.fulfill({ json: [draft, official] });
      payload = route.request().postDataJSON(); return route.fulfill({ status: 201, json: { ...draft, id: 13, ...payload } });
    });
    await page.goto("/#/alteracoes"); await page.getByRole("combobox", { name: "Instrumento Contratual", exact: true }).selectOption("10");
    await openAlteration(page, "other"); await checkModal(page, info, "other-alteration");
    await page.getByLabel("Tipo da alteração").selectOption("TERMO_ADITIVO");
    await page.getByLabel("Operação da alteração").selectOption("RETIFICACAO");
    await page.getByLabel("Identificação da outra alteração").fill("RET-01"); await page.getByLabel("Alteração de referência").selectOption("12");
    await page.getByLabel("Valor proposto na outra alteração").fill("Objeto retificado");
    await closeModal(page); await openAlteration(page, "other");
    await expect(page.getByLabel("Alteração de referência")).toHaveValue("12");
    await page.getByLabel("Operação da alteração").selectOption("CANCELAMENTO");
    await page.getByLabel("Identificação da outra alteração").fill("CAN-01"); await page.getByLabel("Alteração de referência").selectOption("12");
    await page.getByRole("button", { name: "Criar outra alteração" }).click();
    await expect(page.getByRole("dialog")).toBeHidden(); expect(payload).toMatchObject({ operacao: "CANCELAMENTO", referenciaId: 12, mudancas: [] });
    await openAlteration(page, "other"); await page.getByLabel("Operação da alteração").selectOption("RETIFICACAO");
    await expect(page.getByLabel("Identificação da outra alteração")).toHaveValue("RET-01");
    await expect(page.getByLabel("Valor proposto na outra alteração")).toHaveValue("Objeto retificado");
  });
});
