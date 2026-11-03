import { expect, test, type Page } from "@playwright/test";
import { openFilters } from "./navigation-helpers";
import { openMenu } from "./navigation-helpers";

const actor = { id: 1, nome: "Operador DIPAC", login: "operador" };
const empty = { content: [], totalPages: 0, totalElements: 0, number: 0, size: 20 };
const process = { id: 1, numero: "PROC-001", origem: "DIPAC", ativo: true, status: "EM_FORMALIZACAO" };
const owner = { ...process, numeroProcesso: process.numero, statusProcesso: process.status, processoAtivo: true };
const document = { id: 12, titulo: "Documento de trabalho", categoria: "ADMINISTRATIVO", ativo: true, criadoPor: actor,
  versoes: [{ versao: 1, nomeArquivo: "original.pdf", tipoMime: "application/pdf", tamanho: 10,
    checksumSha256: "a".repeat(64), criadoPor: actor }] };

async function setup(page: Page) {
  await page.addInitScript(() => localStorage.setItem("sicc-session", JSON.stringify({
    token: "modal-fixture", perfil: "ADMINISTRADOR_DIPAC", trocaSenhaObrigatoria: false
  })));
  await page.route("**/api/v1/**", route => {
    const path = new URL(route.request().url()).pathname;
    let json: unknown = [];
    if (path === "/api/v1/dashboard") json = {
      processosPorStatus: { EM_FORMALIZACAO: 45 }, percentualConcluidos: 0,
      alertasContratuais: 0, alertasTed: 0, valorTotalVigente: 0, instrumentosPorTipo: {},
      permanenciaMediaPorSetor: { DIPAC: 10 }, maiorGargalo: "DIPAC", tempoMedioTramitacaoInicialDias: 10,
      formalizacoesMensais: {}, conclusoesMensais: {},
      detalhesPermanenciaPorSetor: { DIPAC: [{ processoId: 1, numeroProcesso: "PROC-001", dataChegada: "2026-01-01", diasCorridos: 10, aberta: true }] },
      detalhesTempoTramitacaoInicial: Array.from({ length: 45 }, (_, i) => ({ processoId: i + 1,
        numeroProcesso: `PROC-${String(i + 1).padStart(3, "0")}`, dataCadastro: "2026-01-01", diasCorridos: 10, aberta: true }))
    };
    else if (path === "/api/v1/processos") json = { ...empty, content: [process], totalPages: 1, totalElements: 1 };
    else if (path === "/api/v1/documentos/proprietarios") json = { ...empty,
      content: [owner, { ...owner, id: 2, numero: "PROC-INATIVO", processoAtivo: false }], totalPages: 1, totalElements: 2 };
    else if (path === "/api/v1/documentos") json = [document];
    return route.fulfill({ json });
  });
  page.on("pageerror", error => { throw error; });
}

async function nav(page: Page, name: string) {
  await openMenu(page);
  await page.getByRole("navigation").getByRole("button", { name, exact: true }).click();
}

async function fitsViewport(page: Page) {
  const box = (await page.getByRole("dialog").boundingBox())!;
  const viewport = page.viewportSize()!;
  expect(box.x).toBeGreaterThanOrEqual(0); expect(box.y).toBeGreaterThanOrEqual(0);
  expect(box.x + box.width).toBeLessThanOrEqual(viewport.width);
  expect(box.y + box.height).toBeLessThanOrEqual(viewport.height);
  expect(await page.getByRole("dialog").evaluate(el => el.scrollWidth <= el.clientWidth)).toBe(true);
}

for (const width of [1440, 768, 390, 320]) test.describe(`modais · ${width}px`, () => {
  test.use({ viewport: { width, height: 900 } });
  test("indicadores: busca, paginação, teclado e retorno ao contexto", async ({ page }, info) => {
    await setup(page); await page.goto("/#/dashboard");
    const trigger = page.getByRole("button", { name: "Tempo inicial médio · 10,0 dias", exact: true });
    const before = await page.locator(".shell").boundingBox();
    await trigger.click(); await fitsViewport(page);
    const dialog = page.getByRole("dialog", { name: "Tempo de Tramitação Inicial", exact: true });
    await expect(dialog.getByRole("heading")).toBeFocused();
    await expect(dialog.locator("tbody tr")).toHaveCount(20);
    await dialog.getByRole("button", { name: "Próxima", exact: true }).click();
    await expect(dialog).toContainText("21–40 de 45");
    const next = dialog.getByRole("button", { name: "Próxima", exact: true });
    const previous = dialog.getByRole("button", { name: "Anterior", exact: true });
    await next.press("Enter");
    await expect(dialog).toContainText("41–45 de 45");
    await expect(next).toBeDisabled(); await expect(previous).toBeFocused();
    await previous.press("Enter");
    await expect(dialog).toContainText("21–40 de 45");
    await expect(previous).toBeFocused();
    await previous.press("Enter");
    await expect(dialog).toContainText("1–20 de 45");
    await expect(previous).toBeDisabled(); await expect(next).toBeFocused();
    await dialog.getByRole("searchbox").fill("PROC-045");
    await expect(dialog.getByRole("searchbox")).toBeFocused();
    await expect(dialog.locator("tbody tr")).toHaveCount(1);
    await expect(dialog).toContainText("1–1 de 1");
    await dialog.getByRole("searchbox").fill("INEXISTENTE");
    await expect(dialog.getByRole("status")).toContainText("Nenhum registro");
    await dialog.getByRole("searchbox").fill("");
    for (let i = 0; i < 8; i++) {
      await page.keyboard.press("Tab");
      expect(await page.evaluate(() => Boolean(document.activeElement?.closest("dialog[open]")))).toBe(true);
    }
    await dialog.screenshot({ path: info.outputPath("dashboard-modal.png") });
    await dialog.getByRole("searchbox").fill("PROC-001");
    await page.keyboard.press("Escape");
    await expect(dialog).toBeHidden(); await expect(trigger).toBeFocused();
    expect((await page.locator(".shell").boundingBox())!.height).toBe(before!.height);
    await page.getByRole("button", { name: "Maior gargalo · DIPAC", exact: true }).click();
    await expect(page.getByRole("dialog", { name: "Permanências em DIPAC" })).toContainText("PROC-001");
  });

  test("cadastro: preserva rascunho, mantém erro e fecha somente após confirmação", async ({ page }, info) => {
    await setup(page); let attempts = 0;
    await page.route("**/api/v1/processos", route => {
      if (route.request().method() === "GET") return route.fulfill({ json: { ...empty, content: [process], totalPages: 1, totalElements: 1 } });
      attempts++;
      return route.fulfill(attempts === 1 ? { status: 503, json: { mensagem: "Falha de teste; tente novamente." } } : { status: 201, json: process });
    });
    await page.goto("/#/processos");
    const before = await page.locator(".process-card").first().boundingBox();
    const trigger = page.getByRole("button", { name: "Novo Processo Administrativo", exact: true });
    await trigger.click(); await fitsViewport(page);
    const dialog = page.getByRole("dialog", { name: "Novo Processo Administrativo", exact: true });
    await dialog.getByLabel("Número", { exact: true }).fill("PROC-NOVO");
    await openFilters(page);
    await dialog.getByLabel("Origem", { exact: true }).fill("Faculdade de Ciências Humanas");
    await page.keyboard.press("Escape"); await expect(trigger).toBeFocused();
    expect((await page.locator(".process-card").first().boundingBox())!.y).toBe(before!.y);
    await nav(page, "Documentos"); await nav(page, "Processos Administrativos"); await trigger.click();
    await expect(dialog.getByLabel("Número", { exact: true })).toHaveValue("PROC-NOVO");
    await dialog.getByRole("button", { name: "Cadastrar processo", exact: true }).click();
    await expect(dialog.getByRole("alert")).toContainText("Falha de teste");
    await expect(dialog.getByLabel("Origem", { exact: true })).toHaveValue("Faculdade de Ciências Humanas");
    await dialog.screenshot({ path: info.outputPath("process-modal.png") });
    await dialog.getByRole("button", { name: "Cadastrar processo", exact: true }).click();
    await expect(dialog).toBeHidden(); await expect(trigger).toBeFocused(); expect(attempts).toBe(2);
    await trigger.click(); await expect(dialog.getByLabel("Número", { exact: true })).toHaveValue("");
  });

  test("documentos: anexação e versão preservam arquivo; inativo mantém somente consulta", async ({ page }, info) => {
    await setup(page); let uploads = 0, versions = 0;
    await page.route("**/api/v1/documentos", route => {
      if (route.request().method() === "GET") return route.fulfill({ json: [document] });
      uploads++;
      expect(route.request().postDataBuffer()?.toString()).toContain('name="proprietarioId"\r\n\r\n1');
      return route.fulfill(uploads === 1 ? { status: 503, json: { mensagem: "Envio indisponível." } } : { status: 201, json: document });
    });
    await page.route("**/api/v1/documentos/12/versoes", route => {
      versions++; return route.fulfill({ status: 201, json: {} });
    });
    await page.goto("/#/documentos"); await page.locator('select[name="proprietarioId"]').selectOption("1");
    await expect(page.locator(".document-card")).toBeVisible();
    await page.getByRole("button", { name: "Anexar documento", exact: true }).click(); await fitsViewport(page);
    const dialog = page.getByRole("dialog", { name: "Anexar documento", exact: true });
    await dialog.getByLabel("Título", { exact: true }).fill("Anexo preservado");
    await dialog.getByLabel("Arquivo", { exact: true }).setInputFiles({ name: "anexo.pdf", mimeType: "application/pdf", buffer: Buffer.from("%PDF-test") });
    await dialog.getByRole("button", { name: "Fechar anexação" }).click();
    await page.getByRole("button", { name: "Anexar documento", exact: true }).click();
    await expect(dialog.getByLabel("Título", { exact: true })).toHaveValue("Anexo preservado");
    expect(await dialog.getByLabel("Arquivo", { exact: true }).evaluate(input => (input as HTMLInputElement).files?.[0]?.name)).toBe("anexo.pdf");
    await dialog.getByRole("button", { name: "Anexar documento" }).click();
    await expect(dialog.getByRole("alert")).toContainText("Envio indisponível");
    await dialog.screenshot({ path: info.outputPath("document-modal.png") });
    await dialog.getByRole("button", { name: "Anexar documento" }).click(); await expect(dialog).toBeHidden();
    await page.getByRole("button", { name: "Nova versão", exact: true }).click(); await fitsViewport(page);
    const version = page.getByRole("dialog", { name: "Nova versão", exact: true });
    await expect(version).toContainText(document.titulo);
    await version.getByLabel("Arquivo", { exact: true }).setInputFiles({ name: "versao.pdf", mimeType: "application/pdf", buffer: Buffer.from("%PDF-version") });
    await page.keyboard.press("Escape");
    await page.getByRole("button", { name: "Atualizar", exact: true }).click();
    await expect(page.getByText("Carregando os documentos…", { exact: true })).toBeHidden();
    await page.getByRole("button", { name: "Nova versão", exact: true }).click();
    expect(await version.getByLabel("Arquivo", { exact: true }).evaluate(input => (input as HTMLInputElement).files?.[0]?.name)).toBe("versao.pdf");
    await version.getByRole("button", { name: "Adicionar versão" }).click(); await expect(version).toBeHidden(); expect(versions).toBe(1);
    await page.getByRole("button", { name: "Trocar vínculo" }).click();
    await page.locator('select[name="proprietarioId"]').selectOption("2");
    await expect(page.getByRole("button", { name: "Anexar documento", exact: true })).toBeDisabled();
    await expect(page.getByRole("button", { name: "Nova versão", exact: true })).toBeDisabled();
    await expect(page.getByRole("button", { name: "Baixar versão 1", exact: true })).toBeEnabled();
  });
});

test("envio pendente bloqueia Escape e cliques repetidos; falha de atualização não repete o POST", async ({ page }) => {
  await setup(page); let posts = 0, reads = 0, release!: () => void;
  const pending = new Promise<void>(resolve => { release = resolve; });
  await page.route("**/api/v1/documentos?*", route => {
    reads++; return route.fulfill(reads > 1 ? { status: 503, json: { mensagem: "Consulta indisponível" } } : { json: [document] });
  });
  await page.route("**/api/v1/documentos", async route => {
    posts++; await pending; await route.fulfill({ status: 201, json: document });
  });
  await page.goto("/#/documentos"); await page.locator('select[name="proprietarioId"]').selectOption("1");
  await page.getByRole("button", { name: "Anexar documento", exact: true }).click();
  const dialog = page.getByRole("dialog", { name: "Anexar documento", exact: true });
  await dialog.getByLabel("Título", { exact: true }).fill("Confirmação única");
  await dialog.getByLabel("Arquivo", { exact: true }).setInputFiles({ name: "unico.pdf", mimeType: "application/pdf", buffer: Buffer.from("%PDF-test") });
  await dialog.locator("form").evaluate(form => { for (let i = 0; i < 4; i++) (form as HTMLFormElement).requestSubmit(); });
  await expect.poll(() => posts).toBe(1); await page.keyboard.press("Escape");
  await expect(dialog).toBeVisible(); await expect(dialog.getByRole("button", { name: "Fechar anexação" })).toBeDisabled();
  release(); await expect(dialog).toBeHidden();
  await expect(page.locator(".toast")).toContainText("Documento e primeira versão armazenados");
  await expect(page.getByRole("alert").filter({ hasText: "Consulta indisponível" })).toBeVisible(); expect(posts).toBe(1);
});
