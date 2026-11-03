import { expect, test, type Page } from "@playwright/test";
import { closeModal, openMenu } from "./navigation-helpers";

const empty = { content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 };
const instrument = { id: 77, numero: "017/2026", tipo: "CONVENIO", objeto: "Cooperação em pesquisa",
  descricao: "Descrição do instrumento", natureza: "Acadêmica", coordenador: "Maria Silva", participes: ["UFGD"],
  valorAtual: 1500, vigenciaContratualFinal: "2027-12-31", vigenciaTedFinal: "2026-12-01",
  situacaoContratual: "VALIDA", situacaoTed: "PROXIMA_VENCIMENTO", documentoAssinadoId: 5,
  documentoAssinadoVersao: 1, documentoAssinadoChecksumSha256: "a".repeat(64) };
const process = { id: 17, numero: "23005.011488/2026-01", origem: "DIPAC", ativo: true, status: "EM_VIGENCIA", instrumento: instrument };
const dashboard = { processosPorStatus: { EM_FORMALIZACAO: 0, EM_VIGENCIA: 1, CONCLUIDO: 0 }, percentualConcluidos: 0,
  alertasContratuais: 1, alertasTed: 1, valorTotalVigente: 1500, instrumentosPorTipo: { CONVENIO: 1 },
  permanenciaMediaPorSetor: {}, maiorGargalo: null, detalhesPermanenciaPorSetor: {}, tempoMedioTramitacaoInicialDias: 10,
  detalhesTempoTramitacaoInicial: [], formalizacoesMensais: {}, conclusoesMensais: {} };

async function setup(page: Page, internal = true) {
  if (internal) await page.addInitScript(() => localStorage.setItem("sicc-session", JSON.stringify({
    token: "clarity-fixture", perfil: "ADMINISTRADOR_DIPAC", trocaSenhaObrigatoria: false
  })));
  await page.route("**/api/v1/**", route => {
    const url = new URL(route.request().url());
    const path = url.pathname;
    if (path === "/api/v1/dashboard") return route.fulfill({ json: dashboard });
    if (path === "/api/v1/processos") return route.fulfill({ json: { ...empty, content: [process], totalElements: 1, totalPages: 1 } });
    if (path === "/api/v1/processos/17") return route.fulfill({ json: process });
    if (path.endsWith("/tramitacao")) return route.fulfill({ json: { setorAtual: null, movimentacoes: [], permanencias: [] } });
    if (path === "/api/v1/documentos") return route.fulfill({ json: [] });
    if (path === "/api/v1/documentos/proprietarios") return route.fulfill({ json: empty });
    if (path === "/api/v1/alteracoes/instrumentos/77") return route.fulfill({ json: { ...instrument, numeroProcesso: process.numero } });
    if (path === "/api/v1/alteracoes/instrumentos") return route.fulfill({ json: { ...empty, content: [{ ...instrument, numeroProcesso: process.numero }], totalElements: 1, totalPages: 1 } });
    if (path === "/api/v1/public/processos") return route.fulfill({ json: { ...empty, content: [{numeroProcesso: process.numero,
      tipoInstrumento: "CONVENIO", origem: "DIPAC", coordenador: "Maria Silva", status: "EM_VIGENCIA",
      vigenciaContratualFinal: "2027-12-31", vigenciaTedFinal: "2026-12-01"}], totalElements: 1, totalPages: 1 } });
    return route.fulfill({ json: [] });
  });
}

for (const width of [1440, 768, 390, 320]) test.describe(`clareza · ${width}px`, () => {
  test.use({ viewport: { width, height: 900 } });

  test("busca compacta, ações próximas e preenchimento preservado ao consultar documentos", async ({ page }, info) => {
    await setup(page); await page.goto("/#/processos");
    const card = page.locator(".process-card"); await expect(card).toBeVisible();
    expect((await card.boundingBox())!.y).toBeLessThanOrEqual(540);
    await expect(page.getByLabel("Filtrar por origem")).toBeHidden();
    await page.getByText("Mais filtros", { exact: true }).click();
    await page.getByLabel("Filtrar por origem").fill("DIPAC");
    const search = page.waitForRequest(request => request.url().includes("/processos?") && new URL(request.url()).searchParams.get("origem") === "DIPAC");
    await page.getByRole("button", { name: "Aplicar filtros", exact: true }).click(); await search;
    await card.click();
    const actions = page.locator(".process-actions");
    for (const label of ["Nova movimentação", "Editar processo"]) {
      const action = actions.getByRole("button", { name: label, exact: true });
      await expect(action).toBeVisible(); expect((await action.boundingBox())!.y).toBeLessThan(540);
    }
    await actions.getByRole("button", { name: "Editar processo" }).click();
    await page.getByRole("dialog").getByLabel("Origem", { exact: true }).fill("Preenchimento preservado"); await closeModal(page);
    await page.getByRole("button", { name: "Documentos deste processo" }).click();
    await expect(page.locator(".document-context").first()).toContainText(process.numero);
    await expect(page.getByRole("dialog")).toHaveCount(0);
    await page.getByRole("button", { name: "Voltar ao Processo Administrativo", exact: true }).click();
    await actions.getByRole("button", { name: "Editar processo" }).click();
    await expect(page.getByRole("dialog").getByLabel("Origem", { exact: true })).toHaveValue("Preenchimento preservado");
    await closeModal(page); await page.getByRole("button", { name: "Voltar à lista" }).click();
    await expect(page.getByLabel("Filtrar por origem")).toHaveValue("DIPAC");
    await page.getByRole("button", { name: "Limpar filtros" }).click();
    await expect(page.getByLabel("Filtrar por origem")).toHaveValue("");
    await page.screenshot({ path: info.outputPath("processos.png"), fullPage: true });
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(width);
  });

  test("atalhos conservam o tipo de vínculo e selecionam o instrumento da alteração", async ({ page }) => {
    await setup(page); await page.goto("/#/processos"); await page.locator(".process-card").click();
    const documents = page.waitForRequest(request => request.url().includes("/documentos?")
      && new URL(request.url()).searchParams.get("proprietarioTipo") === "INSTRUMENTO");
    await page.getByRole("button", { name: "Documentos do instrumento", exact: true }).click();
    expect(new URL((await documents).url()).searchParams.get("proprietarioId")).toBe("77");
    await expect(page.locator(".document-context").first()).toContainText("017/2026");
    await page.getByRole("button", { name: "Trocar vínculo" }).click();
    await expect(page.getByLabel("Vincular documento a")).toHaveValue("INSTRUMENTO");
    await page.getByRole("button", { name: "Voltar ao Processo Administrativo", exact: true }).click();
    await page.getByRole("button", { name: "Alterações deste instrumento" }).click();
    await expect(page.locator(".alteration-scope .document-context")).toContainText(process.numero);
    await expect(page.getByRole("button", { name: "Trocar instrumento", exact: true })).toBeVisible();
    await page.getByRole("button", { name: "Preparar Termo Aditivo", exact: true }).click();
    await expect(page.getByRole("dialog")).toContainText("017/2026");
    await expect(page.getByRole("button", { name: "Criar rascunho", exact: true })).toBeEnabled();
  });
});

test("atalhos do painel preservam escopo e separam contratual de TED; limpar não reaplica o contexto", async ({ page }) => {
  await setup(page); await page.goto("/#/dashboard"); await page.getByText("Filtros do painel", { exact: true }).click();
  await page.getByLabel("Origem do processo").fill("DIPAC");
  await page.getByLabel("Tipo do instrumento").selectOption("CONVENIO");
  await page.getByLabel("Status do processo").selectOption("EM_VIGENCIA");
  await page.getByRole("button", { name: "Aplicar filtros" }).click();
  const request = page.waitForRequest(r => r.url().includes("/processos?") && new URL(r.url()).searchParams.has("vigenciaTed"));
  await page.getByRole("button", { name: "Ver processos · vigência do TED", exact: true }).click();
  const query = new URL((await request).url()).searchParams;
  expect(Object.fromEntries(query)).toMatchObject({ origem: "DIPAC", tipo: "CONVENIO", status: "EM_VIGENCIA", vigenciaTed: "PROXIMA_VENCIMENTO", page: "0" });
  expect(query.has("vigenciaContratual")).toBe(false); expect(query.has("vigencia")).toBe(false);
  await page.getByRole("button", { name: "Limpar filtros" }).click();
  await openMenu(page); await page.locator("aside nav").getByRole("button", { name: "Documentos", exact: true }).click();
  await openMenu(page); await page.locator("aside nav").getByRole("button", { name: "Processos Administrativos", exact: true }).click();
  await expect(page.getByRole("button", { name: "Limpar filtros" })).toHaveCount(0);
});

test("consulta pública prioriza resultados, limpa critérios e abre o acesso interno como modal", async ({ page }) => {
  await page.setViewportSize({width:390,height:900}); await setup(page,false); await page.goto("/");
  const row = page.locator(".public-list tbody tr").first(); await expect(row).toBeVisible();
  expect((await row.boundingBox())!.y).toBeLessThan(540);
  await expect(page.locator(".public-list th")).toHaveCount(7);
  await page.getByLabel("Número", {exact:true}).fill("23005"); await page.getByRole("button", {name:"Filtrar",exact:true}).click();
  await page.getByRole("button", {name:"Limpar filtros",exact:true}).click(); await expect(page.getByLabel("Número",{exact:true})).toHaveValue("");
  await expect(page.getByRole("button",{name:"Página anterior"})).toHaveCount(0);
  await page.getByRole("link",{name:"Acesso interno",exact:true}).click();
  await expect(page.getByRole("dialog",{name:"Área interna"})).toBeVisible(); await page.keyboard.press("Escape");
  await expect(page.getByRole("dialog")).toHaveCount(0);
});
