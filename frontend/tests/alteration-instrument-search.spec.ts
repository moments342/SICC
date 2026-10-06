import { expect, test, type Page } from "@playwright/test";

const options = Array.from({ length: 225 }, (_, index) => ({
  id: index + 1, numero: `CV-${String(index).padStart(4, "0")}`, tipo: "CONVENIO", numeroProcesso: `PA-${index}`
}));
const detail = (id: number) => ({ ...options[id - 1], objeto: `Objeto ${id}`, descricao: null,
  natureza: "Acadêmica", coordenador: "Maria", participes: ["UFGD"], valorAtual: 100,
  vigenciaContratualFinal: "2028-12-31", vigenciaTedFinal: null });
const selector = (page: Page) => page.getByRole("combobox", { name: "Instrumento Contratual", exact: true });
const search = (page: Page) => page.getByRole("button", { name: "Buscar instrumentos", exact: true });
const next = (page: Page) => page.getByRole("button", { name: "Próxima página de instrumentos" });

async function setup(page: Page) {
  const requests: string[] = [];
  await page.addInitScript(() => localStorage.setItem("sicc-session", JSON.stringify({
    token: "f13", perfil: "OPERADOR_DIPAC", trocaSenhaObrigatoria: false
  })));
  await page.route("**/api/v1/**", route => {
    const url = new URL(route.request().url()); requests.push(url.pathname + url.search);
    if (url.pathname === "/api/v1/alteracoes/instrumentos") {
      const term = (url.searchParams.get("busca") ?? "").toLowerCase();
      const number = Number(url.searchParams.get("page"));
      const filtered = options.filter(i => `${i.numero} ${i.numeroProcesso}`.toLowerCase().includes(term));
      return route.fulfill({ json: { content: filtered.slice(number * 20, (number + 1) * 20), number,
        size: 20, totalElements: filtered.length, totalPages: Math.ceil(filtered.length / 20) } });
    }
    if (/\/instrumentos\/\d+$/.test(url.pathname)) return route.fulfill({ json: detail(Number(url.pathname.split("/").at(-1))) });
    if (url.pathname.endsWith("/proprietarios")) return route.fulfill({ json: { content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 } });
    return route.fulfill({ json: [] });
  });
  page.on("pageerror", e => { throw e; });
  return requests;
}

for (const width of [1440, 390]) test.describe(`F13 · ${width}px`, () => {
  test.use({ viewport: { width, height: 1000 } });

  test("consulta limitada mantém seleção e preenchimento entre páginas, busca vazia e contextos", async ({ page }, info) => {
    const requests = await setup(page); await page.goto("/#/alteracoes");
    await expect(page.getByText("Página 1 de 12 · 225 instrumento(s)", { exact: true })).toBeVisible();
    expect(requests.filter(p => p.startsWith("/api/v1/processos"))).toEqual([]);
    expect([...new Set(requests.filter(p => p.startsWith("/api/v1/alteracoes/instrumentos?")))]).toEqual(["/api/v1/alteracoes/instrumentos?busca=&page=0&size=20"]);
    await expect(selector(page).locator("option")).toHaveCount(21);
    await selector(page).selectOption("1"); await expect(page.getByLabel("Valor anterior", { exact: true })).toHaveValue("Objeto 1");
    await page.getByLabel("Identificação do termo", { exact: true }).fill("TA não salvo");
    await page.getByLabel("Novo valor", { exact: true }).fill("Proposta 1");
    await page.getByLabel("Identificação da outra alteração").fill("AP não salvo");
    await next(page).click(); await expect(page.getByText("Página 2 de 12 · 225 instrumento(s)", { exact: true })).toBeVisible();
    await expect(selector(page)).toHaveValue("1"); await expect(selector(page).locator("option")).toHaveCount(22);
    await page.getByRole("button", { name: "Página anterior de instrumentos" }).click();
    await expect(page.getByText("Página 1 de 12 · 225 instrumento(s)", { exact: true })).toBeVisible();
    await expect(selector(page)).toHaveValue("1");
    for (let number = 2; number <= 12; number++) {
      await next(page).click();
      await expect(page.getByText(`Página ${number} de 12 · 225 instrumento(s)`, { exact: true })).toBeVisible();
    }
    await expect(selector(page).locator("option")).toHaveCount(7); // 5 resultados, seleção fixada e opção vazia
    await expect(next(page)).toBeDisabled();
    await page.getByLabel("Buscar instrumento", { exact: true }).fill("inexistente"); await page.getByLabel("Buscar instrumento", { exact: true }).press("Enter");
    await expect(page.getByText("Nenhum instrumento encontrado.", { exact: true })).toBeVisible();
    await expect(selector(page)).toHaveValue("1"); await expect(next(page)).toBeDisabled();
    await expect(page.getByLabel("Identificação do termo", { exact: true })).toHaveValue("TA não salvo");
    await expect(page.getByLabel("Identificação da outra alteração")).toHaveValue("AP não salvo");
    await page.getByLabel("Buscar instrumento", { exact: true }).fill("CV-0224"); await search(page).click();
    await expect(page.getByText("Página 1 de 1 · 1 instrumento(s)", { exact: true })).toBeVisible();
    await selector(page).selectOption("225"); await expect(page.getByLabel("Valor anterior", { exact: true })).toHaveValue("Objeto 225");
    await expect(page.getByLabel("Identificação do termo", { exact: true })).toHaveValue("");
    await page.getByLabel("Identificação do termo", { exact: true }).fill("TA outro");
    await page.getByLabel("Buscar instrumento", { exact: true }).fill("CV-0000"); await search(page).click();
    await selector(page).selectOption("1"); await expect(page.getByLabel("Identificação do termo", { exact: true })).toHaveValue("TA não salvo");
    await page.getByRole("button", { name: "Apostilamentos", exact: true }).click();
    await expect(page.getByLabel("Identificação do apostilamento", { exact: true })).toHaveValue("");
    await page.getByRole("button", { name: "Termos Aditivos", exact: true }).click();
    await page.getByRole("navigation").getByRole("button", { name: "Documentos", exact: true }).click();
    await page.getByRole("navigation").getByRole("button", { name: "Alterações contratuais", exact: true }).click();
    await expect(selector(page)).toHaveValue("1");
    await expect(page.getByLabel("Novo valor", { exact: true })).toHaveValue("Proposta 1");
    await expect(page.getByLabel("Identificação da outra alteração")).toHaveValue("AP não salvo");
    page.once("dialog", dialog => dialog.dismiss()); await page.getByRole("button", { name: "Sair", exact: true }).click();
    await expect(selector(page)).toHaveValue("1");
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBe(true);
    await page.screenshot({ path: info.outputPath("f13-preserved.png"), fullPage: true });
    expect(requests.filter(p => p.startsWith("/api/v1/processos"))).toEqual([]);
    expect(requests.filter(p => p.includes("/instrumentos?")).every(p => p.includes("size=20"))).toBe(true);
  });

  test("busca lenta, falha e nova tentativa preservam contexto e ignoram resposta substituída", async ({ page }) => {
    await setup(page); await page.goto("/#/alteracoes"); await selector(page).selectOption("1");
    await page.getByLabel("Identificação do termo", { exact: true }).fill("Preservar");
    let release!: () => void; const pending = new Promise<void>(resolve => { release = resolve; });
    let started!: () => void; const received = new Promise<void>(resolve => { started = resolve; });
    await page.route("**/api/v1/alteracoes/instrumentos?**busca=lenta**", async route => {
      started(); await pending; await route.fulfill({ json: { content: [options[50]], number: 0, size: 20, totalElements: 1, totalPages: 1 } });
    });
    await page.getByLabel("Buscar instrumento", { exact: true }).fill("lenta"); await search(page).click(); await received;
    await expect(page.getByText("Carregando instrumentos…", { exact: true })).toBeVisible(); await expect(selector(page)).toBeDisabled();
    await page.getByLabel("Buscar instrumento", { exact: true }).fill("CV-0224"); await search(page).click();
    await expect(page.getByText("Página 1 de 1 · 1 instrumento(s)", { exact: true })).toBeVisible(); release();
    await expect(selector(page).locator('option[value="225"]')).toHaveCount(1);
    let failures = 0;
    await page.route("**/api/v1/alteracoes/instrumentos?**busca=CV-0224**", route => {
      if (++failures === 1) return route.fulfill({ status: 503, json: { mensagem: "Indisponível para teste." } });
      return route.fallback();
    });
    await search(page).click(); await expect(page.getByRole("alert").filter({ hasText: "Não foi possível carregar" })).toContainText("Não foi possível carregar instrumentos");
    await expect(page.getByText("Nenhum instrumento encontrado.", { exact: true })).toHaveCount(0);
    await expect(selector(page)).toHaveValue("1"); await expect(page.getByLabel("Identificação do termo", { exact: true })).toHaveValue("Preservar");
    await page.getByRole("button", { name: "Tentar novamente", exact: true }).click();
    await expect(selector(page)).toBeEnabled(); await expect(selector(page).locator('option[value="225"]')).toHaveCount(1);
    await expect(selector(page).locator('option[value="51"]')).toHaveCount(0); expect(failures).toBe(2);
  });

  test("dados do selecionado têm nova tentativa e resposta tardia não libera ação no contexto errado", async ({ page }) => {
    await setup(page);
    let attempts = 0;
    await page.route("**/api/v1/alteracoes/instrumentos/1", route => ++attempts === 1
      ? route.fulfill({ status: 503, json: { mensagem: "Falha do detalhe." } }) : route.fallback());
    await page.goto("/#/alteracoes"); await selector(page).selectOption("1");
    await expect(page.getByRole("alert").filter({ hasText: "Não foi possível carregar" })).toContainText("dados do instrumento selecionado");
    await expect(page.getByRole("button", { name: "Criar rascunho", exact: true })).toBeDisabled();
    await page.getByLabel("Identificação da outra alteração").fill("Preservado durante falha");
    await page.getByRole("button", { name: "Tentar novamente", exact: true }).click();
    await expect(page.getByLabel("Valor anterior", { exact: true })).toHaveValue("Objeto 1");
    await expect(page.getByLabel("Identificação da outra alteração")).toHaveValue("Preservado durante falha");
    let release!: () => void; const pending = new Promise<void>(resolve => { release = resolve; });
    await page.route("**/api/v1/alteracoes/instrumentos/2", async route => { await pending; await route.fulfill({ json: detail(2) }); });
    await selector(page).selectOption("2");
    await expect(page.getByText("Carregando dados do instrumento selecionado…", { exact: true })).toBeVisible();
    await expect(page.getByRole("button", { name: "Criar rascunho", exact: true })).toBeDisabled();
    await selector(page).selectOption("3"); await expect(page.getByLabel("Valor anterior", { exact: true })).toHaveValue("Objeto 3");
    release(); await expect(selector(page)).toHaveValue("3");
    await expect(page.getByLabel("Valor anterior", { exact: true })).toHaveValue("Objeto 3");
  });
});
