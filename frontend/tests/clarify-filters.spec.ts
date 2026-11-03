import { openFilters, openMenu, openCreation, openLogin } from "./navigation-helpers";
import { expect, test, type Page } from "@playwright/test";

const empty = { content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 };
const process = { id: 1, numero: "PROC-F10", origem: "DIPAC", status: "EM_FORMALIZACAO", ativo: true };
const labels: Record<string, string> = {
  numero: "Número do processo", origem: "Origem", ano: "Ano do relatório",
  dataInicial: "Início do período", dataFinal: "Fim do período", tipo: "Tipo de instrumento",
  contexto: "Contexto da tramitação", status: "Status do processo",
  vigenciaContratual: "Situação da vigência contratual", vigenciaTed: "Situação da vigência do TED"
};
const common = { numero: "PROC-F10", origem: "DIPAC", tipo: "CONVENIO", status: "EM_VIGENCIA",
  vigenciaContratual: "VALIDA", vigenciaTed: "PROXIMA_VENCIMENTO" };
const period = { contexto: "FORMALIZACAO", dataInicial: "2026-08-01", dataFinal: "2026-08-31" };
const filled: Record<string, string> = { ...common, ano: "2026", ...period };
// Expectations deliberately independent of the UI catalog: API contract regression.
const reports = [
  { type: "ANUAL_PROCESSOS", label: "Anual de processos", filters: { ...common, ano: "2026" } },
  { type: "INSTRUMENTOS_POR_TIPO", label: "Instrumentos por tipo", filters: common },
  { type: "HISTORICO_TRAMITACOES", label: "Histórico de tramitações", filters: { ...common, ...period } },
  { type: "VIGENCIAS", label: "Vigências", filters: common },
  { type: "CONSOLIDADO", label: "Consolidado", filters: common }
];

async function setup(page: Page) {
  await page.addInitScript(() => localStorage.setItem("sicc-session", JSON.stringify({
    token: "clarify-test", perfil: "ADMINISTRADOR_DIPAC", trocaSenhaObrigatoria: false
  })));
  await page.route("**/api/v1/**", route => route.fulfill({ json:
    new URL(route.request().url()).pathname.endsWith("/processos") ? empty : [] }));
  page.on("pageerror", error => { throw error; });
}

async function fillReports(page: Page) {
  for (const [name, value] of Object.entries(filled)) {
    await page.getByLabel("Tipo de relatório", { exact: true }).selectOption(name in period ? "HISTORICO_TRAMITACOES" : "ANUAL_PROCESSOS");
    await openFilters(page);
    const control = page.getByLabel(labels[name], { exact: true });
    if (["tipo", "contexto", "status", "vigenciaContratual", "vigenciaTed"].includes(name)) {
      await control.selectOption(value);
    } else await control.fill(value);
  }
}

for (const width of [1440, 390]) test.describe(`clarify F10 · ${width}px`, () => {
  test.use({ viewport: { width, height: 1000 } });

  test("catálogo vazio sem filtros orienta cadastro, sem confundir valores ainda não enviados", async ({ page }, info) => {
    await setup(page); await page.goto("/#/processos");
    const catalog = page.locator("section").filter({ has: page.getByRole("heading", { name: "Processos Administrativos ativos" }) });
    await expect(catalog.getByRole("status").filter({ hasText: "Nenhum Processo Administrativo" })).toContainText("Nenhum Processo Administrativo ativo cadastrado.");
    await openFilters(page);
    await page.getByLabel("Filtrar por número").fill("AINDA NÃO ENVIADO");
    await expect(catalog.getByRole("status").filter({ hasText: "Nenhum Processo Administrativo" })).toContainText("Use Novo Processo Administrativo");
    await expect(catalog.getByRole("button", { name: "Limpar filtros" })).toHaveCount(0);
    await page.screenshot({ path: info.outputPath("empty-database.png"), fullPage: true });
  });

  test("busca sem resultados permite limpar por teclado, volta à primeira página e preserva cadastro", async ({ page }, info) => {
    await setup(page);
    const queries: URLSearchParams[] = [];
    await page.route("**/api/v1/processos?*", route => {
      const query = new URL(route.request().url()).searchParams; queries.push(query);
      const filtered = [...query.keys()].some(name => !["page", "size"].includes(name));
      return route.fulfill({ json: filtered ? empty : { ...empty, content: [process], totalPages: 2,
        totalElements: 21, number: Number(query.get("page")) } });
    });
    await page.goto("/#/processos");
    await page.getByRole("button", { name: "Próxima página" }).click();
    await expect(page.getByText("Página 2 de 2")).toBeVisible();
    await openCreation(page);
    await page.getByLabel("Número", { exact: true }).fill("CADASTRO PRESERVADO");
    await page.getByRole("button", { name: "Fechar novo processo" }).click();
    const catalog = page.locator("section").filter({ has: page.getByRole("heading", { name: "Processos Administrativos ativos" }) });
    await openFilters(page);
    for (const name of ["número", "origem", "objeto", "coordenador"]) await page.getByLabel(`Filtrar por ${name}`).fill("INEXISTENTE");
    await openFilters(page);
    await page.getByLabel("Filtrar por tipo").selectOption("CONVENIO");
    await openFilters(page);
    await page.getByLabel("Filtrar por status").selectOption("EM_VIGENCIA");
    await openFilters(page);
    await page.getByLabel("Filtrar por vigência").selectOption("VALIDA");
    await openFilters(page);
    await page.getByLabel("Vigência do TED", { exact: true }).focus(); await page.keyboard.press("Tab");
    await expect(page.getByRole("combobox", { name: "Filtrar por responsável DIPAC", exact: true })).toBeFocused();
    await page.keyboard.press("Tab");
    await expect(catalog.getByRole("button", { name: "Aplicar filtros", exact: true })).toBeFocused();
    await page.keyboard.press("Enter");
    await expect(catalog.getByRole("status").filter({ hasText: "Nenhum Processo Administrativo" })).toContainText("Nenhum Processo Administrativo corresponde aos filtros.");
    await expect(catalog.getByRole("status").filter({ hasText: "Nenhum Processo Administrativo" })).toContainText("Revise os filtros ou use Limpar filtros");
    await expect(catalog).not.toContainText("cadastrado");
    expect(queries.at(-1)?.get("page")).toBe("0");
    await page.screenshot({ path: info.outputPath("no-results.png"), fullPage: true });
    await catalog.getByRole("button", { name: "Limpar filtros" }).focus();
    await expect(catalog.getByRole("button", { name: "Limpar filtros" })).toBeFocused();
    await page.keyboard.press("Enter");
    await expect(page.getByLabel("Filtrar por número")).toBeFocused();
    await expect(page.getByRole("button", { name: /PROC-F10/ })).toBeVisible();
    for (const control of await catalog.locator("input, select").all()) await expect(control).toHaveValue("");
    expect(Object.fromEntries(queries.at(-1)!)).toEqual({ page: "0", size: "20" });
    await openCreation(page);
    await expect(page.getByLabel("Número", { exact: true })).toHaveValue("CADASTRO PRESERVADO");
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBe(true);
  });

  test("carregamento e falha não anunciam base vazia ou busca sem resultados", async ({ page }) => {
    await setup(page);
    let release!: () => void;
    const pending = new Promise<void>(resolve => { release = resolve; });
    await page.route("**/api/v1/processos?*", async route => {
      await pending; await route.fulfill({ status: 503, json: { mensagem: "Consulta indisponível." } });
    });
    await page.goto("/#/processos");
    await expect(page.getByText(/Carregando os processos/)).toBeVisible();
    await expect(page.getByText(/Nenhum Processo Administrativo/)).toHaveCount(0);
    release(); await expect(page.getByRole("alert").filter({ hasText: "Não foi possível carregar os processos" })).toContainText("Consulta indisponível");
    await expect(page.getByText(/Nenhum Processo Administrativo/)).toHaveCount(0);
  });

  test("rótulos, ajuda acessível e navegação por teclado explicam os relatórios sem filtros", async ({ page }, info) => {
    await setup(page); await page.goto("/#/relatorios");
    await openFilters(page);
    for (const [name, label] of Object.entries(labels)) {
      await page.getByLabel("Tipo de relatório", { exact: true }).selectOption(name in period ? "HISTORICO_TRAMITACOES" : "ANUAL_PROCESSOS");
      await openFilters(page);
      const field = page.getByLabel(label, { exact: true });
      await expect(field).toBeVisible();
      await expect(field).toHaveAccessibleName(label);
      const applicable = ({ ano: "Ano da atividade: cadastros, formalizações, conclusões e permanências. Em branco, usa o ano atual. A carteira é apresentada na data de emissão.",
        dataInicial: "Primeiro dia incluído no período da tramitação.",
        dataFinal: "Último dia incluído no período da tramitação." } as Record<string, string>)[name] ?? "";
      await expect(field).toHaveAccessibleDescription(applicable);
      if (applicable) await expect(page.locator(`#report-filter-${name}-hint`)).toHaveText(applicable);
      else await expect(page.locator(`#report-filter-${name}-hint`)).toHaveCount(0);
    }
    for (const report of reports) {
      await page.getByLabel("Tipo de relatório", { exact: true }).selectOption(report.type);
      await openFilters(page);
      const group = page.getByRole("group", { name: report.label, exact: true });
      await expect(group).toContainText("Será gerado sem filtros.");
      for (const button of await group.getByRole("button").all()) await expect(button).toHaveAccessibleDescription("Será gerado sem filtros.");
    }
    await page.getByLabel("Tipo de relatório", { exact: true }).selectOption("ANUAL_PROCESSOS");
    await openFilters(page);
    await page.getByLabel(labels.numero, { exact: true }).focus(); await page.keyboard.press("Tab");
    await expect(page.getByLabel(labels.origem, { exact: true })).toBeFocused();
    await page.keyboard.press("Shift+Tab"); await expect(page.getByLabel(labels.numero, { exact: true })).toBeFocused();
    await page.getByLabel(labels.vigenciaTed, { exact: true }).focus(); await page.keyboard.press("Tab");
    const annual = page.getByRole("group", { name: "Anual de processos", exact: true });
    await expect(annual.getByRole("button", { name: "Gerar PDF", exact: true })).toBeFocused();
    await page.screenshot({ path: info.outputPath("report-labels.png"), fullPage: true });
  });

  for (const report of reports) test(`${report.label}: resumo corresponde ao envio nos três formatos`, async ({ page }, info) => {
    await setup(page);
    const posts: unknown[] = [];
    await page.route("**/api/v1/relatorios", route => {
      if (route.request().method() === "POST") {
        posts.push(route.request().postDataJSON());
        return route.fulfill({ status: 201, json: {} });
      }
      return route.fulfill({ json: [] });
    });
    await page.goto("/#/relatorios"); await fillReports(page);
    await openFilters(page);
    await page.getByLabel("Tipo de relatório", { exact: true }).selectOption(report.type);
    await openFilters(page);
    const group = page.getByRole("group", { name: report.label, exact: true });
    const summary = group.locator(".report-filter-summary");
    for (const name of Object.keys(report.filters)) await expect(summary.locator("span").first()).toContainText(`${labels[name]}:`);
    const ignored = Object.keys(filled).filter(name => !(name in report.filters));
    await expect(summary.locator("span").last()).toHaveText(`Não se aplicam a este relatório: ${ignored.map(name => labels[name]).join(", ")}.`);
    await expect(group.getByRole("button", { name: "Gerar CSV", exact: true })).toHaveAccessibleDescription((await summary.innerText()).replace(/\s+/g, " "));
    if (report.type === "ANUAL_PROCESSOS") await page.screenshot({ path: info.outputPath("reports-filled.png"), fullPage: true });
    for (const format of ["PDF", "XLSX", "CSV"]) {
      await group.getByRole("button", { name: `Gerar ${format}`, exact: true }).focus();
      await page.keyboard.press("Enter");
      await expect(group.getByRole("button", { name: `Gerar ${format}`, exact: true })).toBeEnabled();
      await expect.poll(() => posts.length).toBe(["PDF", "XLSX", "CSV"].indexOf(format) + 1);
      expect(posts.at(-1)).toEqual({ tipo: report.type, formato: format, filtros: report.filters });
    }
    for (const [name, value] of Object.entries(filled)) {
      await page.getByLabel("Tipo de relatório", { exact: true }).selectOption(name in period ? "HISTORICO_TRAMITACOES" : "ANUAL_PROCESSOS");
      await openFilters(page);
      await expect(page.getByLabel(labels[name], { exact: true })).toHaveValue(value);
    }
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBe(true);
  });

  test("filtros não aplicáveis ficam explícitos sem alterar o bloqueio da mesma geração", async ({ page }) => {
    await setup(page); let posts = 0, release!: () => void;
    const pending = new Promise<void>(resolve => { release = resolve; });
    await page.route("**/api/v1/relatorios", async route => {
      if (route.request().method() !== "POST") return route.fulfill({ json: [] });
      posts++; expect(route.request().postDataJSON().filtros).toEqual({});
      await pending; await route.fulfill({ status: 201, json: {} });
    });
    await page.goto("/#/relatorios");
    await openFilters(page);
    const group = page.getByRole("group", { name: "Consolidado", exact: true });
    await page.getByLabel(labels.ano, { exact: true }).fill("2026");
    await page.getByLabel("Tipo de relatório", { exact: true }).selectOption("CONSOLIDADO");
    await openFilters(page);
    await expect(group).toContainText("Será gerado sem filtros.");
    await expect(group).toContainText("Não se aplicam a este relatório: Ano do relatório.");
    await group.getByRole("button", { name: "Gerar CSV", exact: true }).click();
    await page.getByLabel("Tipo de relatório", { exact: true }).selectOption("ANUAL_PROCESSOS");
    await openFilters(page);
    await page.getByLabel(labels.ano, { exact: true }).fill("2025");
    await page.getByLabel("Tipo de relatório", { exact: true }).selectOption("CONSOLIDADO");
    await openFilters(page);
    await expect(group.getByRole("button", { name: /CSV/ })).toBeDisabled();
    await page.getByLabel("Tipo de relatório", { exact: true }).selectOption("ANUAL_PROCESSOS");
    await openFilters(page);
    await page.getByLabel(labels.ano, { exact: true }).fill("");
    await page.getByLabel("Tipo de relatório", { exact: true }).selectOption("CONSOLIDADO");
    await openFilters(page);
    await expect(group).not.toContainText("Não se aplicam");
    expect(posts).toBe(1); release();
    await expect(group.getByRole("button", { name: "Gerar CSV", exact: true })).toBeEnabled();
  });
});

test("F10: filtros extensos permanecem legíveis com ampliação de conteúdo a 200%", async ({ page }, info) => {
  await page.setViewportSize({ width: 1440, height: 1000 });
  await setup(page); await page.goto("/#/relatorios"); await fillReports(page);
  await openFilters(page);
  await openFilters(page);
  await page.getByLabel("Origem", { exact: true }).fill("Divisão de Parcerias e Convênios com identificação extensa para revisão");
  await page.evaluate(() => { document.documentElement.style.zoom = "2"; });
  for (const report of reports) {
      await page.getByLabel("Tipo de relatório", { exact: true }).selectOption(report.type);
      await openFilters(page);
    const group = page.getByRole("group", { name: report.label, exact: true });
    await expect(group.locator(".report-filter-summary")).toBeVisible();
    await expect(group.getByRole("button", { name: "Gerar CSV", exact: true })).toBeVisible();
  }
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBe(true);
  await page.screenshot({ path: info.outputPath("reports-200-percent.png"), fullPage: true });
});
