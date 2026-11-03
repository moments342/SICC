import { expect, test, type Page } from "@playwright/test";
import { openFilters, answerConfirmation, closeModal, openMenu, openProcessAction, openUserActions } from "./navigation-helpers";

const actor = { id: 1, nome: "Maria da Silva", login: "maria", email: "maria@sicc.test", perfil: "OPERADOR_DIPAC", ativo: true };
const sector = { id: 1, sigla: "DIPAC", nome: "Divisão de Parcerias", ativo: true };
const process = { id: 1, numero: "PROC-MODAL-001", origem: "DIPAC", numeroProjeto: "PROJ-01", ativo: true,
  status: "EM_FORMALIZACAO", responsavel: actor };
const history = { setorAtual: sector, movimentacoes: [], permanencias: [] };
const document = { id: 7, titulo: "Instrumento assinado", categoria: "ASSINADO", ativo: true, criadoPor: actor,
  versoes: [{ versao: 1, tipoMime: "application/pdf", nomeArquivo: "assinado.pdf", tamanho: 100,
    checksumSha256: "a".repeat(64), criadoPor: actor }] };
const otherDocument = { ...document, id: 8, titulo: "Outro instrumento assinado" };
const owner = { ...process, numeroProcesso: process.numero, statusProcesso: process.status, processoAtivo: true };
const empty = { content: [], number: 0, size: 20, totalPages: 0, totalElements: 0 };

async function setup(page: Page) {
  await page.addInitScript(() => localStorage.setItem("sicc-session", JSON.stringify({
    token: "process-confirmation-fixture", perfil: "ADMINISTRADOR_DIPAC", trocaSenhaObrigatoria: false
  })));
  await page.route("**/api/v1/**", route => {
    const path = new URL(route.request().url()).pathname;
    let json: unknown = [];
    if (path === "/api/v1/processos") json = { ...empty, content: [process], totalPages: 1, totalElements: 1 };
    else if (path === "/api/v1/processos/1") json = process;
    else if (path.endsWith("/tramitacao")) json = history;
    else if (path.endsWith("/responsaveis")) json = [actor];
    else if (path.endsWith("/setores")) json = [sector];
    else if (path === "/api/v1/documentos/proprietarios") json = { ...empty, content: [owner, { ...owner, id: 2, numero: "PROC-OUTRO" }], totalElements: 2, totalPages: 1 };
    else if (path === "/api/v1/documentos") json = [document, otherDocument];
    else if (path === "/api/v1/admin/usuarios") json = [actor];
    else if (path === "/api/v1/admin/usuarios/1") json = actor;
    else if (path === "/api/v1/public/processos") json = empty;
    return route.fulfill({ json });
  });
  page.on("pageerror", error => { throw error; });
}
async function selectProcess(page: Page) {
  await page.goto("/#/processos");
  await page.getByRole("button", { name: /PROC-MODAL-001/ }).click();
}
async function nav(page: Page, label: string) {
  await openMenu(page);
  await page.getByRole("navigation").getByRole("button", { name: label, exact: true }).click();
}
async function geometry(page: Page) {
  const dialog = page.locator("dialog[open]").last();
  const box = (await dialog.boundingBox())!, viewport = page.viewportSize()!;
  expect(box.x).toBeGreaterThanOrEqual(0); expect(box.y).toBeGreaterThanOrEqual(0);
  expect(box.x + box.width).toBeLessThanOrEqual(viewport.width + 1);
  expect(box.y + box.height).toBeLessThanOrEqual(viewport.height + 1);
  expect(await dialog.evaluate(element => element.scrollWidth <= element.clientWidth)).toBe(true);
  const close = dialog.locator(".modal-close"), circle = (await close.boundingBox())!, cross = (await close.locator("svg").boundingBox())!;
  expect(circle.width).toBe(44); expect(circle.height).toBe(44);
  expect(await close.evaluate(element => getComputedStyle(element, "::before").borderRadius)).toBe("50%");
  expect(Math.abs(cross.x + cross.width / 2 - circle.x - circle.width / 2)).toBeLessThan(1);
  expect(Math.abs(cross.y + cross.height / 2 - circle.y - circle.height / 2)).toBeLessThan(1);
}
async function fillFormalization(page: Page) {
  const dialog = page.getByRole("dialog", { name: "Formalizar Instrumento Contratual", exact: true });
  for (const [label, value] of Object.entries({ Número: "CV-2026", Objeto: "Cooperação acadêmica", Natureza: "Acadêmica",
    Coordenador: "Maria", "Partícipes, um por linha": "UFGD\nFundação", Valor: "0", "Vigência contratual": "2027-12-31", "Data de formalização": "2026-10-01" })) {
    await dialog.getByLabel(label, { exact: true }).fill(value);
  }
  await dialog.getByRole("combobox", { name: "Tipo", exact: true }).selectOption("CONVENIO");
  await dialog.getByLabel("Documento assinado PDF").selectOption("7");
}

for (const width of [1440, 768, 390, 320]) test.describe(`Processos e confirmações · ${width}px`, () => {
  test.use({ viewport: { width, height: 900 } });

  test("edição preserva campos e foco, mostra erro local e bloqueia envio repetido", async ({ page }, info) => {
    await setup(page); let puts = 0, release!: () => void;
    const pending = new Promise<void>(resolve => { release = resolve; });
    await page.route("**/api/v1/processos/1", async route => {
      if (route.request().method() === "GET") return route.fulfill({ json: process });
      puts++;
      if (puts === 1) return route.fulfill({ status: 503, json: { mensagem: "Edição indisponível." } });
      await pending; return route.fulfill({ json: { ...process, origem: "Origem revisada" } });
    });
    await selectProcess(page); await openProcessAction(page, "edit"); await geometry(page);
    const dialog = page.getByRole("dialog", { name: "Editar Processo Administrativo", exact: true });
    await openFilters(page);
    await dialog.getByLabel("Origem", { exact: true }).fill("Origem revisada");
    await closeModal(page); await expect(page.getByRole("button", { name: "Editar processo", exact: true })).toBeFocused();
    await openProcessAction(page, "edit"); await expect(dialog.getByLabel("Origem", { exact: true })).toHaveValue("Origem revisada");
    await dialog.getByRole("button", { name: "Salvar", exact: true }).click();
    await expect(dialog.getByRole("alert")).toContainText("Edição indisponível");
    await dialog.screenshot({ path: info.outputPath("edit-process.png") });
    await dialog.locator("form").evaluate(form => { for (let i = 0; i < 4; i++) (form as HTMLFormElement).requestSubmit(); });
    await expect.poll(() => puts).toBe(2); await expect(dialog.locator(".modal-close")).toBeDisabled();
    await page.keyboard.press("Escape"); await expect(dialog).toBeVisible();
    release(); await expect(dialog).toBeHidden(); await expect(page.locator(".process-administration")).toContainText("Origem revisada");
    expect(puts).toBe(2);
  });

  test("movimentação mantém o rascunho e confirma o registro mesmo se a leitura posterior falhar", async ({ page }, info) => {
    await setup(page); let posts = 0, failRead = false;
    await page.route("**/api/v1/processos/1/tramitacao", route => route.fulfill(failRead
      ? { status: 503, json: { mensagem: "Histórico indisponível." } } : { json: history }));
    await page.route("**/api/v1/movimentacoes", route => { posts++; failRead = true;
      expect(route.request().postDataJSON()).toMatchObject({ contextoTipo: "FORMALIZACAO", contextoId: 1, setorDestinoId: 1 });
      return route.fulfill({ status: 201, json: {} }); });
    await selectProcess(page); await openProcessAction(page, "movement"); await geometry(page);
    const dialog = page.getByRole("dialog", { name: "Registrar movimentação do processo", exact: true });
    await dialog.getByLabel("Destino").selectOption("1"); await dialog.getByLabel("Observação").fill("Análise documental");
    await closeModal(page); await openProcessAction(page, "movement");
    await expect(dialog.getByLabel("Observação")).toHaveValue("Análise documental");
    await dialog.screenshot({ path: info.outputPath("process-movement.png") });
    await dialog.getByRole("button", { name: "Registrar", exact: true }).click();
    await expect(dialog).toBeHidden(); await expect(page.getByText("Histórico indisponível.")).toBeVisible();
    await openProcessAction(page, "movement"); await expect(dialog.getByLabel("Observação")).toHaveValue(""); expect(posts).toBe(1);
  });

  test("formalização mantém zero e campos ao visitar Documentos e recarrega os PDFs ao abrir", async ({ page }, info) => {
    await setup(page); let docsRead = 0, fail = true;
    await page.route("**/api/v1/documentos?*", route => { docsRead++; return route.fulfill({ json: [document] }); });
    await page.route("**/api/v1/processos/1/instrumento", route => route.fulfill(fail
      ? { status: 503, json: { mensagem: "Formalização indisponível." } }
      : { status: 201, json: { numero: "CV-2026", tipo: "CONVENIO", participes: ["UFGD"], valorAtual: 0, vigenciaContratualFinal: "2027-12-31", documentoAssinadoId: 7, documentoAssinadoVersao: 1, documentoAssinadoChecksumSha256: "a".repeat(64) } }));
    await selectProcess(page); await openProcessAction(page, "formalize"); await geometry(page); await fillFormalization(page);
    const dialog = page.getByRole("dialog", { name: "Formalizar Instrumento Contratual", exact: true });
    await dialog.getByRole("button", { name: "Formalizar", exact: true }).click();
    await page.getByRole("button", { name: "Confirmar formalização", exact: true }).click();
    const alert = dialog.getByRole("alert").filter({ hasText: "Formalização indisponível" });
    await expect(alert).toBeInViewport({ ratio: 1 });
    await expect(alert).toBeFocused();
    await expect(dialog.getByLabel("Objeto", { exact: true })).toHaveValue("Cooperação acadêmica");
    await dialog.getByRole("button", { name: "Formalizar", exact: true }).click();
    await page.getByRole("button", { name: "Confirmar formalização", exact: true }).click();
    await expect(alert).toBeInViewport({ ratio: 1 });
    await expect(alert).toBeFocused();
    await page.screenshot({ path: info.outputPath("formalization-error-visible.png") });
    const firstReads = docsRead;
    await dialog.getByRole("button", { name: "Anexar documento deste processo" }).click();
    await expect(page.getByRole("dialog", { name: "Anexar documento", exact: true })).toBeVisible();
    await closeModal(page); await page.getByRole("button", { name: "Voltar ao processo em preenchimento" }).click();
    await openProcessAction(page, "formalize"); await expect.poll(() => docsRead).toBeGreaterThan(firstReads);
    await expect(dialog.getByLabel("Valor", { exact: true })).toHaveValue("0");
    await expect(dialog.getByLabel("Objeto", { exact: true })).toHaveValue("Cooperação acadêmica");
    await dialog.getByRole("button", { name: "Formalizar", exact: true }).focus();
    await page.screenshot({ path: info.outputPath("formalization-bottom.png") });
    await geometry(page);
  });

  test("confirmações administrativas cancelam sem gravar e informam usuário, perfil e setor", async ({ page }, info) => {
    await setup(page); const mutations: string[] = [];
    page.on("request", request => { if (request.method() !== "GET") mutations.push(request.url()); });
    await page.goto("/#/administracao");
    await openUserActions(page);
    await page.getByRole("dialog", { name: "Gerenciar usuário", exact: true }).getByRole("button", { name: "Desativar acesso", exact: true }).click(); await geometry(page);
    await expect(page.getByRole("dialog").last()).toContainText(actor.nome);
    await expect(page.getByRole("button", { name: "Cancelar", exact: true })).toBeFocused();
    await page.getByRole("dialog").last().screenshot({ path: info.outputPath("confirmation.png") });
    await page.keyboard.press("Escape");
    await page.getByRole("dialog", { name: "Gerenciar usuário", exact: true }).getByLabel("Perfil").selectOption("ADMINISTRADOR_DIPAC");
    await page.getByRole("button", { name: "Salvar perfil", exact: true }).click();
    await expect(page.getByRole("dialog").last()).toContainText("Administrador DIPAC"); await answerConfirmation(page, false);
    await expect(page.getByRole("dialog", { name: "Gerenciar usuário", exact: true }).getByLabel("Perfil")).toHaveValue("OPERADOR_DIPAC");
    await closeModal(page);
    await page.locator(".linha-setor").getByRole("button", { name: "Desativar", exact: true }).click();
    await expect(page.getByRole("dialog")).toContainText("DIPAC"); await closeModal(page);
    expect(mutations).toEqual([]);
  });

  test("documentos pedem descarte ao trocar arquivo ou vínculo e confirmam desativação", async ({ page }) => {
    await setup(page); let deletes = 0;
    await page.route("**/api/v1/documentos/7", route => { deletes++; return route.fulfill({ json: {} }); });
    await page.goto("/#/documentos"); await page.locator('select[name="proprietarioId"]').selectOption("1");
    const cards = page.locator(".document-card");
    await cards.first().getByRole("button", { name: "Nova versão", exact: true }).click();
    const file = page.getByRole("dialog", { name: "Nova versão", exact: true }).getByLabel("Arquivo", { exact: true });
    await file.setInputFiles({ name: "rascunho.pdf", mimeType: "application/pdf", buffer: Buffer.from("%PDF-test") });
    await closeModal(page); await cards.last().getByRole("button", { name: "Nova versão", exact: true }).click();
    await answerConfirmation(page, false);
    await cards.first().getByRole("button", { name: "Nova versão", exact: true }).click();
    await expect(file).toHaveValue(/rascunho.pdf$/); await closeModal(page);
    await cards.last().getByRole("button", { name: "Nova versão", exact: true }).click(); await answerConfirmation(page);
    await expect(file).toHaveValue(""); await closeModal(page);
    await page.getByRole("button", { name: "Anexar documento", exact: true }).click();
    await page.getByLabel("Título", { exact: true }).fill("Preenchimento preservado"); await closeModal(page);
    await page.getByRole("button", { name: "Trocar vínculo", exact: true }).click();
    await page.getByLabel("Vincular documento a").selectOption("INSTRUMENTO"); await answerConfirmation(page, false);
    await expect(page.getByLabel("Vincular documento a")).toHaveValue("PROCESSO");
    await page.getByRole("button", { name: "Anexar documento", exact: true }).click();
    await expect(page.getByLabel("Título", { exact: true })).toHaveValue("Preenchimento preservado"); await closeModal(page);
    await cards.first().getByRole("button", { name: "Desativar", exact: true }).click(); await answerConfirmation(page, false); expect(deletes).toBe(0);
    await cards.first().getByRole("button", { name: "Desativar", exact: true }).click(); await answerConfirmation(page); await expect.poll(() => deletes).toBe(1);
  });

  test("saída cancela com foco seguro e só descarta os campos após confirmação", async ({ page }, info) => {
    await setup(page); await selectProcess(page); await openProcessAction(page, "edit");
    await openFilters(page);
    await page.getByRole("dialog").getByLabel("Origem", { exact: true }).fill("Origem ainda não salva");
    await nav(page, "Sair"); await geometry(page);
    await expect(page.getByRole("dialog")).toContainText("preenchimento não salvo");
    for (let i = 0; i < 6; i++) {
      await page.keyboard.press("Tab");
      expect(await page.evaluate(() => Boolean(document.activeElement?.closest("dialog[open]")))).toBe(true);
    }
    await page.getByRole("dialog").screenshot({ path: info.outputPath("logout-confirmation.png") });
    await answerConfirmation(page, false); await openProcessAction(page, "edit");
    await expect(page.getByRole("dialog").getByLabel("Origem", { exact: true })).toHaveValue("Origem ainda não salva");
    await nav(page, "Sair"); await answerConfirmation(page);
    await expect(page.getByRole("heading", { name: "Consulta pública", exact: true })).toBeVisible();
  });
});
