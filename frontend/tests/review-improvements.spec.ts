import { expect, test, type Page } from "@playwright/test";
import { answerConfirmation, closeModal, openInstrumentPicker } from "./navigation-helpers";

const empty = { content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 };
const option = { id: 77, numero: "017/2026", tipo: "CONVENIO", numeroProcesso: "23005.011488/2026-01" };
const instrument = { ...option, objeto: "Cooperação em pesquisa", coordenador: "Marina Oliveira", participes: ["UFGD"],
  valorAtual: 175000, vigenciaContratualFinal: "2027-08-01", vigenciaTedFinal: "2027-04-30" };
const record = { id: 17, numero: option.numeroProcesso, origem: "UFGD", ativo: true, status: "EM_VIGENCIA", instrumento: instrument };
const actor = { id: 2, nome: "Marina Oliveira", login: "marina", perfil: "OPERADOR_DIPAC", ativo: true, email: "marina@sicc.test" };
const audit = { id: 9, acao: "ATUALIZACAO_DE_INSTRUMENTO_CONTRATUAL", resultado: "SUCESSO", ator: actor,
  objeto: { tipo: "INSTRUMENTO_CONTRATUAL", id: 77 }, detalhes: "Referência administrativa " + "sem-espacos-".repeat(30),
  ipOrigem: "2001:db8:85a3:0000:0000:8a2e:0370:7334", criadoEm: "2026-08-08T12:00:00" };

async function setup(page: Page, internal = true) {
  const mutations: string[] = [];
  let outage = false;
  if (internal) await page.addInitScript(() => localStorage.setItem("sicc-session", JSON.stringify({
    token: "review-improvements", perfil: "ADMINISTRADOR_DIPAC", trocaSenhaObrigatoria: false
  })));
  await page.route("**/api/v1/**", route => {
    const path = new URL(route.request().url()).pathname;
    if (route.request().method() !== "GET") mutations.push(path);
    if (outage) return route.fulfill({ status: 503, json: { mensagem: "Serviço temporariamente indisponível." } });
    let json: unknown = [];
    if (path === "/api/v1/processos") json = { ...empty, content: [record], totalElements: 1, totalPages: 1 };
    else if (path === "/api/v1/dashboard") json = {
      processosPorStatus: { EM_FORMALIZACAO: 1, EM_VIGENCIA: 2, CONCLUIDO: 1 }, percentualConcluidos: 25,
      alertasContratuais: 1, alertasTed: 1, valorTotalVigente: 175000, instrumentosPorTipo: { CONVENIO: 3 },
      permanenciaMediaPorSetor: { DIPAC: 12 }, maiorGargalo: "DIPAC", detalhesPermanenciaPorSetor: {},
      tempoMedioTramitacaoInicialDias: 14, detalhesTempoTramitacaoInicial: [],
      formalizacoesMensais: { "2026-08": 2 }, conclusoesMensais: { "2026-08": 1 }
    };
    else if (path === "/api/v1/public/processos") json = { ...empty, content: [
      { numeroProcesso: record.numero, tipoInstrumento: "CONVENIO", origem: "UFGD", coordenador: "Marina Oliveira",
        status: "EM_VIGENCIA", vigenciaContratualFinal: "2027-08-01", vigenciaTedFinal: "2027-04-30" },
      { numeroProcesso: "23005.012345/2026-02", tipoInstrumento: "Ainda não formalizado", origem: "Unidade de pesquisa com nome extenso",
        coordenador: "Ainda não formalizado", status: "EM_FORMALIZACAO", vigenciaContratualFinal: null, vigenciaTedFinal: null }
    ], totalElements: 2, totalPages: 1 };
    else if (path === "/api/v1/auditoria") json = { ...empty, content: [audit, { ...audit, id: 10, ator: null, detalhes: null, ipOrigem: null }], totalElements: 2, totalPages: 1 };
    else if (path === "/api/v1/admin/usuarios") json = [actor];
    else if (path === "/api/v1/admin/usuarios/2") json = actor;
    else if (path === "/api/v1/alteracoes/instrumentos") json = { ...empty, content: [option], totalElements: 1, totalPages: 1 };
    else if (path === "/api/v1/alteracoes/instrumentos/77") json = instrument;
    else if (path === "/api/v1/documentos/proprietarios") json = empty;
    return route.fulfill({ json });
  });
  page.on("pageerror", error => { throw error; });
  return { mutations, setOutage: (value: boolean) => { outage = value; } };
}

async function noOverflow(page: Page) {
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(await page.evaluate(() => innerWidth));
  expect(await page.locator(".table-wrap:visible").evaluateAll(elements => elements.map(el => el.scrollWidth - el.clientWidth))).toEqual(
    await page.locator(".table-wrap:visible").evaluateAll(elements => elements.map(() => 0)));
}

for (const width of [1440, 768, 390]) test.describe(`melhorias da revisão · ${width}px`, () => {
  test.use({ viewport: { width, height: width === 768 ? 1024 : width === 390 ? 844 : 900 } });

  test("auditoria mantém contexto e oferece todos os detalhes por teclado", async ({ page }) => {
    await setup(page); await page.goto("/#/auditoria");
    const event = page.locator(".audit-table tbody tr").first();
    await expect(event).toContainText("Atualização de instrumento contratual");
    await expect(event.getByText("Marina Oliveira", { exact: true })).toBeVisible();
    await expect(event.locator(".badge")).toHaveText("Sucesso");
    const trigger = event.getByText("Ver detalhes", { exact: true });
    await trigger.focus(); await page.keyboard.press("Enter");
    await expect(event.getByText(audit.detalhes, { exact: true })).toBeVisible();
    await expect(event.getByText(audit.ipOrigem, { exact: true })).toBeVisible();
    await expect(page.getByText("Ator não identificado", { exact: true })).toBeVisible();
    await noOverflow(page);
  });

  test("consulta pública permite ler o primeiro registro completo sem perder campos", async ({ page }) => {
    await setup(page, false); await page.goto("/");
    const row = page.locator(".public-list tbody tr").first();
    await expect(row).toBeVisible(); await expect(row.locator("td")).toHaveCount(7);
    for (const text of [record.numero, "Convênio", "UFGD", "Marina Oliveira", "Em vigência", "01/08/2027", "30/04/2027"]) {
      await expect(row.getByText(text, { exact: true })).toBeVisible();
    }
    const box = (await row.boundingBox())!;
    expect(box.y + box.height).toBeLessThanOrEqual(page.viewportSize()!.height);
    if (width < 1100) expect(box.height).toBeLessThan(360);
    await expect(page.locator(".public-list tbody tr").nth(1).getByText("Não informada", { exact: true })).toHaveCount(2);
    await noOverflow(page);
  });

  test("painel prioriza as duas vigências sem somar processos e conserva os demais indicadores", async ({ page }) => {
    await setup(page); await page.goto("/#/dashboard");
    const priority = page.getByRole("region", { name: "Vigências que exigem atenção" });
    await expect(priority).toBeVisible();
    expect((await priority.boundingBox())!.y).toBeLessThan((await page.locator(".metrics").boundingBox())!.y);
    await expect(priority.locator(".expiry-count strong")).toHaveText(["1", "1"]);
    await expect(page.locator(".metric")).toHaveCount(4);
    for (const title of ["Tramitação", "Instrumentos por tipo", "Atividade mensal"]) await expect(page.getByRole("heading", { name: title, exact: true })).toBeVisible();
    await expect(page.getByText("R$ 175.000,00", { exact: true })).toBeVisible();
    await noOverflow(page);
  });

  test("seleção recolhida devolve foco e mantém busca e rascunho ao reabrir", async ({ page }) => {
    await setup(page); await page.goto("/#/alteracoes");
    await page.locator(".alteration-scope select").selectOption("77");
    const toggle = page.getByRole("button", { name: "Trocar instrumento", exact: true });
    await expect(toggle).toBeFocused();
    await expect(page.getByLabel("Buscar instrumento", { exact: true })).toBeHidden();
    await page.getByRole("button", { name: "Preparar Termo Aditivo", exact: true }).click();
    await page.getByLabel("Identificação do termo", { exact: true }).fill("TA preservado");
    await openInstrumentPicker(page);
    await page.getByLabel("Buscar instrumento", { exact: true }).fill("017");
    await page.getByRole("button", { name: "Buscar instrumentos", exact: true }).click();
    await page.getByRole("button", { name: "Recolher seleção", exact: true }).click();
    await page.getByRole("button", { name: "Preparar Termo Aditivo", exact: true }).click();
    await expect(page.getByLabel("Identificação do termo", { exact: true })).toHaveValue("TA preservado");
    await closeModal(page); await toggle.click();
    await expect(page.getByLabel("Buscar instrumento", { exact: true })).toHaveValue("017");
  });

  test("uma falha geral aparece uma vez no catálogo e pode ser repetida", async ({ page }) => {
    const fixture = await setup(page); fixture.setOutage(true); await page.goto("/#/processos");
    const error = page.getByRole("alert").filter({ hasText: "Serviço temporariamente indisponível." });
    await expect(error).toHaveCount(1); await expect(error).toBeVisible();
    await expect(page.locator(".toast:visible")).toHaveCount(0);
    fixture.setOutage(false); await page.getByRole("button", { name: "Tentar novamente", exact: true }).click();
    await expect(page.locator(".process-card")).toContainText(record.numero);
    await expect(error).toHaveCount(0);
  });

  test("ações de acesso ficam agrupadas e cancelar preserva usuário e permissões", async ({ page }) => {
    const fixture = await setup(page); await page.goto("/#/administracao");
    const user = page.locator(".user-row");
    await expect(user).toContainText("@marina");
    await expect(user).toContainText("Operador DIPAC");
    await expect(user.getByRole("button", { name: "Desativar", exact: true })).toBeHidden();
    await user.getByRole("button", { name: "Gerenciar usuário Marina Oliveira", exact: true }).focus(); await page.keyboard.press("Enter");
    await page.getByRole("dialog", { name: "Gerenciar usuário", exact: true }).getByRole("button", { name: "Desativar acesso", exact: true }).click();
    await expect(page.getByRole("dialog").last()).toContainText("Marina Oliveira");
    await answerConfirmation(page, false);
    expect(fixture.mutations).toEqual([]);
    await expect(page.getByRole("dialog", { name: "Gerenciar usuário", exact: true }).getByLabel("Perfil")).toHaveValue("OPERADOR_DIPAC");
    await page.getByRole("button", { name: "Redefinir senha de Marina Oliveira" }).click();
    await expect(page.getByRole("dialog", { name: "Redefinir senha temporária", exact: true })).toContainText("Marina Oliveira · @marina");
  });
});
