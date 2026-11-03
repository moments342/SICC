import { expect, test } from "@playwright/test";
import { openFilters } from "./navigation-helpers";

for (const width of [1440, 390]) test(`relatórios gerenciais: setor, responsável e ano em ${width}px`, async ({ page }, info) => {
  await page.setViewportSize({ width, height: 1000 });
  await page.addInitScript(() => localStorage.setItem("sicc-session", JSON.stringify({
    token: "relatorios-gerenciais-teste", perfil: "OPERADOR_DIPAC", trocaSenhaObrigatoria: false
  })));
  await page.route("**/api/v1/**", route => route.fulfill({ json: [] }));
  await page.route("**/api/v1/relatorios/filtros", route => route.fulfill({ json: {
    setores: [{ id: 2, rotulo: "PROAP · Pró-Reitoria de Administração" }],
    responsaveis: [{ id: 7, rotulo: "Maria da Silva (inativo)" }]
  } }));
  const posts: unknown[] = [];
  await page.route("**/api/v1/relatorios", route => {
    if (route.request().method() === "POST") {
      posts.push(route.request().postDataJSON());
      return route.fulfill({ status: 201, json: { id: 1 } });
    }
    return route.fulfill({ json: [] });
  });
  await page.goto("/#/relatorios");
  await openFilters(page);
  await page.getByLabel("Ano do relatório", { exact: true }).fill("2025");
  await page.getByLabel("Setor atual do processo", { exact: true }).selectOption("2");
  await page.getByLabel("Responsável DIPAC", { exact: true }).selectOption("7");
  await expect(page).toHaveURL(/setorId=2/);
  await expect(page).toHaveURL(/responsavelId=7/);
  const applied = page.getByRole("group", { name: "Filtros aplicados", exact: true });
  await expect(applied).toContainText("PROAP · Pró-Reitoria de Administração");
  await expect(applied).toContainText("Maria da Silva (inativo)");
  await page.getByRole("button", { name: "Gerar CSV", exact: true }).click();
  await expect.poll(() => posts.length).toBe(1);
  expect(posts[0]).toEqual({ tipo: "ANUAL_PROCESSOS", formato: "CSV", filtros: { ano: "2025", setorId: "2", responsavelId: "7" } });
  await page.getByLabel("Tipo de relatório", { exact: true }).selectOption("CONSOLIDADO");
  await expect(page.getByText("Indicadores atuais da carteira filtrada, atividade mensal, permanência por setor e fichas dos processos.")).toBeVisible();
  await page.getByRole("button", { name: "Gerar XLSX", exact: true }).click();
  await expect.poll(() => posts.length).toBe(2);
  expect(posts[1]).toEqual({ tipo: "CONSOLIDADO", formato: "XLSX", filtros: { setorId: "2", responsavelId: "7" } });
  await page.reload(); await openFilters(page);
  await expect(page.getByLabel("Setor atual do processo", { exact: true })).toHaveValue("2");
  await expect(page.getByLabel("Responsável DIPAC", { exact: true })).toHaveValue("7");
  await applied.getByRole("button", { name: /Remover filtro Setor atual/ }).click();
  await expect(page.getByLabel("Setor atual do processo", { exact: true })).toHaveValue("");
  await page.getByLabel("Responsável DIPAC", { exact: true }).selectOption("SEM_RESPONSAVEL");
  await page.getByLabel("Setor atual do processo", { exact: true }).selectOption("SEM_SETOR");
  await expect(applied).toContainText("Sem responsável");
  await expect(applied).toContainText("Sem setor atual");
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.screenshot({ path: info.outputPath(`relatorios-gerenciais-${width}.png`), fullPage: true });
});

test("catálogo com erro preserva filtros e permite tentar novamente", async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem("sicc-session", JSON.stringify({
    token: "relatorios-gerenciais-teste", perfil: "OPERADOR_DIPAC", trocaSenhaObrigatoria: false
  })));
  await page.route("**/api/v1/**", route => route.fulfill({ json: [] }));
  let falhar = true;
  await page.route("**/api/v1/relatorios/filtros", route => falhar
    ? route.fulfill({ status: 503, json: { mensagem: "Catálogo indisponível." } })
    : route.fulfill({ json: { setores: [{ id: 2, rotulo: "PROAP" }], responsaveis: [] } }));
  await page.goto("/#/relatorios?setorId=2"); await openFilters(page);
  const setor = page.getByLabel("Setor atual do processo", { exact: true });
  await expect(setor).toBeDisabled();
  await expect(setor).toHaveValue("2");
  await expect(page.getByRole("alert").filter({ hasText: "Catálogo indisponível." })).toBeVisible();
  falhar = false;
  await page.getByRole("button", { name: "Tentar novamente", exact: true }).click();
  await expect(setor).toBeEnabled();
  await expect(setor).toHaveValue("2");
  await expect(page.getByRole("group", { name: "Filtros aplicados", exact: true })).toContainText("PROAP");
});
