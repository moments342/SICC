import { expect, test, type Locator, type Page } from "@playwright/test";

const emptyPage = { content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 };
const actor = { id: 1, nome: "Pessoa responsável com nome administrativo extenso", login: "operador" };
const title = "Documento administrativo com título extenso e referência " + "IDENTIFICACAO".repeat(8);
const filename = "arquivo_" + "referencia".repeat(14) + ".csv";
const user = { ...actor, email: "operador@sicc.test", perfil: "OPERADOR_DIPAC", ativo: true, senhaTemporaria: false };

async function fixture(page: Page) {
  const requests: string[] = [];
  await page.addInitScript(() => localStorage.setItem("sicc-session", JSON.stringify({
    token: "jwt-disposable-layout", perfil: "ADMINISTRADOR_DIPAC", trocaSenhaObrigatoria: false
  })));
  await page.route("**/api/v1/**", route => {
    const path = new URL(route.request().url()).pathname;
    const method = route.request().method();
    requests.push(`${method} ${path}`);
    if (path.endsWith("/arquivo")) return route.fulfill({
      body: "numero\nPROC-F11-F14\n", contentType: "text/csv",
      headers: { "Content-Disposition": 'attachment; filename="evidencia.csv"' }
    });
    if (path === "/api/v1/dashboard") return route.fulfill({ json: {
      processosPorStatus: { EM_FORMALIZACAO: 1 }, percentualConcluidos: 0,
      alertasContratuais: 0, alertasTed: 0, valorTotalVigente: 0, instrumentosPorTipo: {},
      permanenciaMediaPorSetor: {}, maiorGargalo: null, detalhesPermanenciaPorSetor: {},
      tempoMedioTramitacaoInicialDias: 2, detalhesTempoTramitacaoInicial: [],
      formalizacoesMensais: {}, conclusoesMensais: {}
    } });
    if (path === "/api/v1/documentos/proprietarios") return route.fulfill({ json: {
      ...emptyPage, totalElements: 1, totalPages: 1,
      content: [{ id: 1, numero: "PROC-F11-F14", origem: "DIPAC", processoAtivo: true, statusProcesso: "EM_FORMALIZACAO" }]
    } });
    if (path === "/api/v1/documentos") return route.fulfill({ json: [true, false].map((ativo, index) => ({
      id: index + 1, titulo: title, categoria: "ADMINISTRATIVO", ativo, criadoPor: actor,
      versoes: [12, 1].map(versao => ({ versao, nomeArquivo: filename, tamanho: 32,
        checksumSha256: "a".repeat(64), criadoPor: actor }))
    })) });
    if (path === "/api/v1/relatorios") return route.fulfill({ json: [{
      id: 1, tipo: "CONSOLIDADO", formato: "CSV", filtros: { origem: title }, criadoPor: actor,
      criadoEm: "2026-10-03T12:00:00", checksumSha256: "b".repeat(64),
      chaveArmazenamento: "relatorios/" + "referencia".repeat(10), tamanhoBytes: 32, nomeArquivo: filename
    }] });
    if (path === "/api/v1/admin/usuarios/1") return route.fulfill({ json: user });
    if (path === "/api/v1/admin/usuarios") return route.fulfill({ json: [user] });
    if (path === "/api/v1/auditoria" || path === "/api/v1/processos") return route.fulfill({ json: emptyPage });
    return route.fulfill({ json: [] });
  });
  return requests;
}

async function comfortable(controls: Locator, scale = 1) {
  const measures = await controls.evaluateAll(elements => elements.map(element => {
    const rect = element.getBoundingClientRect();
    return { text: element.getAttribute("aria-label") || element.textContent, width: rect.width, height: rect.height };
  }));
  expect(measures.length).toBeGreaterThan(0);
  for (const measure of measures) {
    expect(measure.width, JSON.stringify(measure)).toBeGreaterThanOrEqual(44 * scale - 0.5);
    expect(measure.height, JSON.stringify(measure)).toBeGreaterThanOrEqual(44 * scale - 0.5);
  }
  return measures;
}

async function readableActions(controls: Locator) {
  // Each word must occupy one line. A button may wrap between whole words.
  const fragmented = await controls.evaluateAll(elements => elements.flatMap(element => {
    const node = element.firstChild;
    if (!node || node.nodeType !== Node.TEXT_NODE) return ["Missing action text"];
    return [...(node.textContent ?? "").matchAll(/\S+/g)].flatMap(match => {
      const range = document.createRange();
      range.setStart(node, match.index!);
      range.setEnd(node, match.index! + match[0].length);
      return range.getClientRects().length > 1 ? [match[0]] : [];
    });
  }));
  expect(fragmented).toEqual([]);
}

async function noOverflow(page: Page) {
  expect(await page.evaluate(() => {
    const issues: string[] = [];
    if (document.documentElement.scrollWidth > innerWidth + 1) issues.push("page");
    for (const element of document.querySelectorAll<HTMLElement>(".document-card, .document-header, .document-version, .report-actions, .reports-panel .doc, button")) {
      const rect = element.getBoundingClientRect();
      if (!rect.width || !rect.height) continue;
      if (element.scrollWidth > element.clientWidth + 1 || rect.left < -1 || rect.right > innerWidth + 1)
        issues.push(element.className || element.textContent || element.tagName);
    }
    return issues;
  })).toEqual([]);
}

const layouts = [
  { width: 1440, scale: 1 }, { width: 1101, scale: 1 }, { width: 768, scale: 1 },
  { width: 390, scale: 1 }, { width: 320, scale: 1 }, { width: 1440, scale: 2 }
];

for (const { width, scale } of layouts) {
  test(`F11/F14: ações legíveis e confortáveis em ${width}px, ampliação ${scale * 100}%`, async ({ page }, info) => {
    await page.setViewportSize({ width, height: 1000 });
    await fixture(page);
    await page.goto("/");
    // CSS zoom checks magnified content and reflow; native browser zoom is a separate manual check.
    if (scale !== 1) await page.evaluate(scale => { document.documentElement.style.zoom = String(scale); }, scale);
    await page.getByRole("button", { name: "Documentos", exact: true }).click();
    await page.getByLabel("Objeto proprietário").selectOption("1");
    const cards = page.locator(".document-card");
    await expect(cards).toHaveCount(2);
    await expect(cards.last().getByRole("button", { name: "Desativar" })).toBeDisabled();
    await expect(cards.last().getByRole("button", { name: "Baixar versão 1", exact: true })).toBeEnabled();
    const documentMeasures = await comfortable(cards.getByRole("button"), scale);
    await readableActions(cards.getByRole("button"));
    for (const card of await cards.all()) {
      const header = (await card.locator(".document-header").boundingBox())!;
      const versions = (await card.locator(".document-versions").boundingBox())!;
      expect(versions.y).toBeGreaterThanOrEqual(header.y + header.height);
      expect(Math.abs(versions.x - header.x)).toBeLessThanOrEqual(1);
      expect(Math.abs(versions.width - header.width)).toBeLessThanOrEqual(1);
    }
    await noOverflow(page);
    await cards.first().screenshot({ path: info.outputPath("document-card.png") });
    await page.getByRole("button", { name: "Relatórios", exact: true }).click();
    await expect(page.locator(".report-metadata")).toBeVisible();
    const reportMeasures = await comfortable(page.locator(".report-actions button, .reports-panel .doc > button"), scale);
    await readableActions(page.locator(".report-actions button, .reports-panel .doc > button"));
    await noOverflow(page);
    await page.locator(".report-actions").screenshot({ path: info.outputPath("report-actions.png") });
    await page.locator(".reports-panel .doc").screenshot({ path: info.outputPath("report-history.png") });
    await info.attach("target-sizes", { body: JSON.stringify({ documentMeasures, reportMeasures }), contentType: "application/json" });
  });
}

for (const width of [1440, 390]) {
  test(`F11/F14: teclado, downloads e retorno de foco em ${width}px`, async ({ page }, info) => {
    await page.setViewportSize({ width, height: 1000 });
    const requests = await fixture(page);
    await page.goto("/");
    const trigger = page.getByRole("button", { name: /Tempo inicial médio/ });
    await trigger.focus();
    await page.keyboard.press("Enter");
    await expect(page.getByRole("heading", { name: "Tempo de Tramitação Inicial", exact: true })).toBeFocused();
    await page.keyboard.press("Tab");
    const closeMetric = page.getByRole("button", { name: "Fechar detalhamento" });
    await expect(closeMetric).toBeFocused();
    await comfortable(closeMetric);
    await noOverflow(page);
    await page.locator(".dashboard-metric-details").screenshot({ path: info.outputPath("metric-detail.png") });
    await page.keyboard.press("Enter");
    await expect(trigger).toBeFocused();

    await page.getByRole("button", { name: "Administração", exact: true }).click();
    const userTrigger = page.getByRole("button", { name: /Ver detalhes de/ });
    await userTrigger.focus();
    await page.keyboard.press("Enter");
    await expect(page.getByRole("heading", { name: "Detalhes do Usuário Interno" })).toBeFocused();
    await page.keyboard.press("Tab");
    const closeUser = page.getByRole("button", { name: "Fechar detalhes", exact: true });
    await expect(closeUser).toBeFocused();
    await comfortable(closeUser);
    await noOverflow(page);
    await page.locator(".user-detail").screenshot({ path: info.outputPath("user-detail.png") });
    await page.keyboard.press("Enter");
    await expect(userTrigger).toBeFocused();

    await page.getByRole("button", { name: "Documentos", exact: true }).click();
    await page.getByLabel("Objeto proprietário").selectOption("1");
    const card = page.locator(".document-card").first();
    await card.getByRole("button", { name: "Desativar" }).focus();
    await page.keyboard.press("Tab");
    await expect(card.getByRole("button", { name: "Baixar versão 12" })).toBeFocused();
    await page.keyboard.press("Tab");
    await expect(card.getByRole("button", { name: "Baixar versão 1", exact: true })).toBeFocused();
    await page.keyboard.press("Shift+Tab");
    await expect(card.getByRole("button", { name: "Baixar versão 12" })).toBeFocused();
    let downloaded = page.waitForEvent("download");
    await page.keyboard.press("Enter");
    expect((await downloaded).suggestedFilename()).toBe("evidencia.csv");
    expect(requests).toContain("GET /api/v1/documentos/1/versoes/12/arquivo");
    expect(requests.some(request => request.startsWith("DELETE"))).toBe(false);

    await page.getByRole("button", { name: "Relatórios", exact: true }).click();
    const formats = page.getByRole("group", { name: "Anual de processos", exact: true });
    await formats.getByRole("button", { name: "PDF", exact: true }).focus();
    await page.keyboard.press("Tab");
    await expect(formats.getByRole("button", { name: "XLSX", exact: true })).toBeFocused();
    await page.keyboard.press("Shift+Tab");
    await expect(formats.getByRole("button", { name: "PDF", exact: true })).toBeFocused();
    await page.getByRole("button", { name: "Baixar", exact: true }).focus();
    downloaded = page.waitForEvent("download");
    await page.keyboard.press("Enter");
    await downloaded;
    expect(requests).toContain("GET /api/v1/relatorios/1/arquivo");
  });
}

test("F11: toque na borda das áreas ampliadas aciona formato, download e fechamento", async ({ browser }, info) => {
  const context = await browser.newContext({ viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true });
  const page = await context.newPage();
  try {
    const requests = await fixture(page);
    await page.goto(info.project.use.baseURL!);
    await page.getByRole("button", { name: /Tempo inicial médio/ }).tap();
    const close = page.getByRole("button", { name: "Fechar detalhamento" });
    await close.scrollIntoViewIfNeeded();
    const box = (await close.boundingBox())!;
    await page.touchscreen.tap(box.x + 3, box.y + box.height - 3);
    await expect(close).toHaveCount(0);
    await page.getByRole("button", { name: "Relatórios", exact: true }).tap();
    const csv = page.getByRole("group", { name: "Consolidado", exact: true }).getByRole("button", { name: "CSV", exact: true });
    await csv.scrollIntoViewIfNeeded();
    const formatBox = (await csv.boundingBox())!;
    await page.touchscreen.tap(formatBox.x + 3, formatBox.y + formatBox.height - 3);
    await expect.poll(() => requests.filter(request => request === "POST /api/v1/relatorios").length).toBe(1);
    const download = page.getByRole("button", { name: "Baixar", exact: true });
    await download.scrollIntoViewIfNeeded();
    const downloadBox = (await download.boundingBox())!;
    const downloaded = page.waitForEvent("download");
    await page.touchscreen.tap(downloadBox.x + 3, downloadBox.y + downloadBox.height - 3);
    await downloaded;
    await noOverflow(page);
  } finally { await context.close(); }
});
