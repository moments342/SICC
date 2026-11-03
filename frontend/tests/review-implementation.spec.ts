import { expect, test, type Page } from "@playwright/test";
import { openFilters, answerConfirmation, openMenu, openUserActions } from "./navigation-helpers";

const actor = { id: 2, nome: "Marina Oliveira", login: "marina", perfil: "OPERADOR_DIPAC", ativo: true, email: "marina@example.invalid" };
const instrument = { id: 77, numero: "017/2026", tipo: "CONVENIO", numeroProcesso: "23005.011488/2026-01", objeto: "Cooperação em pesquisa",
  natureza: "Acadêmica", coordenador: actor.nome, participes: ["UFGD"], valorAtual: 0, vigenciaContratualFinal: "2027-08-01", vigenciaTedFinal: "2027-04-30",
  documentoAssinadoId: 55, documentoAssinadoVersao: 1, documentoAssinadoChecksumSha256: "a".repeat(64), situacaoContratual: "VALIDA", situacaoTed: "VALIDA" };
const process = { id: 17, numero: instrument.numeroProcesso, origem: "UFGD", ativo: true, status: "EM_VIGENCIA", instrumento: instrument, responsavel: actor };
const sector = { id: 1, sigla: "DIPAC", nome: "Divisão de Parcerias", ativo: true };
const empty = { content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 };
const doc = { id: 55, titulo: "Instrumento assinado", categoria: "ASSINADO", ativo: true, criadoPor: actor,
  versoes: [{ versao: 1, tipoMime: "application/pdf", nomeArquivo: "assinado.pdf", tamanhoBytes: 100, checksumSha256: "a".repeat(64), criadoPor: actor }] };

async function setup(page: Page, formalized = true) {
  let record = formalized ? process : { ...process, instrumento: undefined, status: "EM_FORMALIZACAO" };
  const queries: string[] = [], mutations: string[] = [];
  const notifications = [1, 2, 3].map(id => ({ id, processoId: 17, tipo: "CHEGADA_TRAMITACAO", mensagem: `Movimentação de exemplo ${id}`, criadaEm: "2026-08-08T12:00:00", lida: id === 3 }));
  await page.addInitScript(() => {
    if (!sessionStorage.getItem("review-initialized")) {
      localStorage.setItem("sicc-session", JSON.stringify({ token: "implementation-review", perfil: "ADMINISTRADOR_DIPAC", trocaSenhaObrigatoria: false }));
      sessionStorage.setItem("review-initialized", "true");
    }
  });
  await page.route("**/api/v1/**", route => {
    const url = new URL(route.request().url()), path = url.pathname;
    queries.push(path + url.search);
    if (route.request().method() !== "GET") mutations.push(path);
    let json: unknown = [];
    if (path === "/api/v1/processos") json = { ...empty, content: [record], totalElements: 1, totalPages: 1 };
    else if (path === "/api/v1/processos/17") json = record;
    else if (path.endsWith("/tramitacao")) json = { setorAtual: sector, movimentacoes: [], permanencias: [{ setor: sector, dataChegada: "2026-08-01", diasCorridos: 7, aberta: true }] };
    else if (path.endsWith("/responsaveis")) json = [actor];
    else if (path.endsWith("/setores")) json = [sector];
    else if (path === "/api/v1/admin/usuarios") json = [actor];
    else if (path === "/api/v1/admin/usuarios/2") json = actor;
    else if (path === "/api/v1/documentos/proprietarios") json = { ...empty, content: [{ ...record, numeroProcesso: record.numero, processoAtivo: true, statusProcesso: record.status }], totalElements: 1, totalPages: 1 };
    else if (path === "/api/v1/documentos/proprietarios/17") json = { ...record, numeroProcesso: record.numero, processoAtivo: true, statusProcesso: record.status };
    else if (path === "/api/v1/documentos") json = [doc];
    else if (path === "/api/v1/processos/17/instrumento") { record = process; json = instrument; }
    else if (path === "/api/v1/alteracoes/instrumentos") json = { ...empty, content: [instrument], totalElements: 1, totalPages: 1 };
    else if (path === "/api/v1/alteracoes/instrumentos/77") json = instrument;
    else if (path === "/api/v1/notificacoes") json = notifications;
    else if (/\/notificacoes\/\d+\/lida$/.test(path)) { const item = notifications.find(n => n.id === Number(path.split("/").at(-2)))!; item.lida = true; json = item; }
    else if (path === "/api/v1/auditoria") json = { ...empty, content: [{ id: 9, acao: "LOGIN", resultado: "SUCESSO", ator: actor, objeto: { tipo: "USUARIO", id: 2 }, criadoEm: "2026-08-08T12:00:00" }], totalElements: 1, totalPages: 1 };
    return route.fulfill({ json });
  });
  page.on("pageerror", error => { throw error; });
  return { queries, mutations };
}

for (const width of [1440, 768, 390]) test.describe(`implementação da revisão · ${width}px`, () => {
  test.use({ viewport: { width, height: 900 } });

  test("consulta expõe filtros, datas e ordenação; URL recupera registro e histórico", async ({ page }, info) => {
    const fixture = await setup(page); await page.goto("/#/processos");
    await expect(page.locator(".process-card")).toContainText("01/08/2027");
    await expect(page.locator(".process-card")).toContainText("30/04/2027");
    await openFilters(page); await page.getByLabel("Filtrar por origem", { exact: true }).fill("UFGD");
    await page.getByRole("combobox", { name: "Filtrar por responsável DIPAC", exact: true }).selectOption("2");
    await page.getByRole("button", { name: "Aplicar filtros", exact: true }).click();
    await page.locator("summary").filter({ hasText: "Mais filtros" }).click();
    await expect(page.getByRole("group", { name: "Filtros aplicados", exact: true })).toContainText("Origem: UFGD");
    await page.getByRole("button", { name: "Remover filtro Origem: UFGD", exact: true }).click();
    await page.getByRole("combobox", { name: "Ordenar por", exact: true }).selectOption("VIGENCIA_TED");
    await expect.poll(() => fixture.queries.some(q => q.includes("ordenacao=VIGENCIA_TED") && q.includes("responsavelId=2"))).toBe(true);
    await page.screenshot({ path: info.outputPath("catalog-improved.png"), fullPage: true });
    await page.locator(".process-card").click();
    await expect(page).toHaveURL(/processo=17/);
    await expect(page.locator(".process-detail-heading .process-current-summary")).toContainText("7 dias no setor atual");
    await page.reload(); await expect(page.getByRole("heading", { name: `Processo Administrativo · ${process.numero}`, exact: true })).toBeVisible();
    await page.goBack(); await expect(page.locator(".process-card")).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  });

  test("formalização exige tipo, confere valores e cancelar não grava", async ({ page }, info) => {
    const fixture = await setup(page, false); await page.goto("/#/processos"); await page.locator(".process-card").click();
    await page.getByRole("button", { name: "Formalizar Instrumento Contratual", exact: true }).click();
    const form = page.getByRole("dialog", { name: "Formalizar Instrumento Contratual", exact: true });
    await expect(form.getByRole("group", { name: "Identidade e participantes", exact: true })).toBeVisible();
    await expect(form.getByRole("group", { name: "Condições e vigências", exact: true })).toBeAttached();
    await expect(form.getByRole("group", { name: "Comprovação da formalização", exact: true })).toBeAttached();
    await page.screenshot({ path: info.outputPath("formalization-form.png") });
    await expect(form.getByRole("combobox", { name: "Tipo", exact: true })).toHaveValue("");
    for (const [label, value] of Object.entries({ Número: "CV-CONFERIDO", Objeto: "Pesquisa", Natureza: "Acadêmica", Coordenador: actor.nome,
      "Partícipes, um por linha": "UFGD\nFundação", Valor: "0", "Vigência contratual": "2027-08-01", "Data de formalização": "2026-08-01" })) await form.getByLabel(label, { exact: true }).fill(value);
    await form.getByLabel("Documento assinado PDF").selectOption("55");
    await form.getByRole("button", { name: "Formalizar", exact: true }).click();
    expect(fixture.mutations).toEqual([]); await expect(page.getByRole("dialog", { name: "Conferir formalização" })).toBeHidden();
    await form.getByRole("combobox", { name: "Tipo", exact: true }).selectOption("CONVENIO");
    await form.getByRole("button", { name: "Formalizar", exact: true }).click();
    const review = page.getByRole("dialog", { name: "Conferir formalização", exact: true });
    await expect(review).toContainText("R$ 0,00"); await expect(review).toContainText("Instrumento assinado · versão 1");
    await page.screenshot({ path: info.outputPath("formalization-review.png") });
    await answerConfirmation(page, false); expect(fixture.mutations).toEqual([]);
    await expect(form.getByLabel("Número", { exact: true })).toHaveValue("CV-CONFERIDO");
    await form.getByRole("button", { name: "Formalizar", exact: true }).click();
     await answerConfirmation(page);
    await expect(form).toBeHidden(); expect(fixture.mutations).toEqual(["/api/v1/processos/17/instrumento"]);
  });

  test("notificações compartilham contador e filtro de leitura", async ({ page }) => {
    await setup(page); await page.goto("/#/notificacoes");
    await expect(page.locator(".nav-count")).toHaveText("2");
    await page.getByRole("button", { name: "Não lidas · 2", exact: true }).click();
    await expect(page.locator(".notification")).toHaveCount(2);
    await page.getByRole("button", { name: "Marcar como lida", exact: true }).first().click();
    await expect(page.locator(".nav-count")).toHaveText("1"); await expect(page.locator(".notification")).toHaveCount(1);
    await page.reload(); await expect(page.getByRole("button", { name: "Não lidas · 1", exact: true })).toHaveAttribute("aria-pressed", "true");
  });

  test("vínculo devolve foco e reabre pela URL; alterações exigem instrumento", async ({ page }) => {
    await setup(page); await page.goto("/#/documentos");
    const picker = page.getByRole("combobox", { name: "Selecionar Processo Administrativo", exact: true });
    await picker.selectOption("17"); await expect(page.getByRole("button", { name: "Trocar vínculo", exact: true })).toBeFocused();
    await page.reload(); await expect(page.locator(".document-scope > .document-context")).toContainText(process.numero);
    await page.goto("/#/alteracoes"); await expect(page.getByRole("button", { name: "Preparar Termo Aditivo", exact: true })).toBeDisabled();
    await page.locator(".alteration-scope select").selectOption("77");
    await expect(page.getByRole("button", { name: "Preparar Termo Aditivo", exact: true })).toBeEnabled();
    await page.reload(); await expect(page.locator(".alteration-scope > .document-context")).toContainText("017/2026");
  });
});

test("requisitos de senha são associados aos três campos de nova senha", async ({ page }) => {
  await setup(page); await page.goto("/#/administracao");
  await page.getByRole("button", { name: "Criar usuário", exact: true }).click();
  await expect(page.getByLabel("Senha temporária", { exact: true })).toHaveAccessibleDescription(/10 caracteres.*maiúscula.*minúscula.*número/);
  await page.getByRole("button", { name: "Fechar criação de usuário", exact: true }).click();
  await openUserActions(page);
  await page.getByRole("button", { name: "Redefinir senha de Marina Oliveira", exact: true }).click();
  await expect(page.getByLabel("Nova senha temporária", { exact: true })).toHaveAccessibleDescription(/10 caracteres/);
  await page.evaluate(() => localStorage.setItem("sicc-session", JSON.stringify({ token: "first-access", perfil: "OPERADOR_DIPAC", trocaSenhaObrigatoria: true })));
  await page.reload(); await expect(page.getByLabel("Nova senha", { exact: true })).toHaveAccessibleDescription(/10 caracteres/);
});

test("auditoria mantém o resultado mais novo quando uma resposta anterior atrasa", async ({ page }) => {
  await setup(page); let release!: () => void;
  const gate = new Promise<void>(resolve => { release = resolve; });
  await page.route("**/api/v1/auditoria?*", async route => {
    const old = new URL(route.request().url()).searchParams.get("acao") === "ANTIGA";
    if (old) await gate;
    await route.fulfill({ json: { ...empty, totalElements: old ? 99 : 2 } }).catch(() => {});
  });
  await page.goto("/#/auditoria"); await openFilters(page); await page.getByLabel("Ação", { exact: true }).fill("ANTIGA");
  await page.getByRole("button", { name: "Aplicar filtros", exact: true }).click();
  await page.getByLabel("Ação", { exact: true }).fill("NOVA"); await page.getByRole("button", { name: "Aplicar filtros", exact: true }).click();
  await expect(page.locator(".panel-title")).toContainText("2 registro(s)"); release();
  await expect(page.locator(".panel-title")).toContainText("2 registro(s)");
});

test("catálogo com oitenta registros mantém vinte por página em desktop e celular", async ({ page }) => {
  await setup(page);
  const records = Array.from({ length: 80 }, (_, index) => ({ ...process, id: index + 1, numero: `PA-VOLUME-${index + 1}`,
    instrumento: { ...instrument, vigenciaContratualFinal: new Date(Date.UTC(2027, 0, 80 - index)).toISOString().slice(0, 10) } }));
  await page.route("**/api/v1/processos?*", route => {
    const params = new URL(route.request().url()).searchParams;
    const number = Number(params.get("page"));
    const sorted = params.get("ordenacao") === "VIGENCIA_CONTRATUAL" ? [...records].reverse() : records;
    return route.fulfill({ json: { ...empty, content: sorted.slice(number * 20, number * 20 + 20), number, totalElements: 80, totalPages: 4 } });
  });
  for (const width of [1440, 390]) {
    await page.setViewportSize({ width, height: 900 }); await page.goto("/#/processos");
    await expect(page.locator(".process-card")).toHaveCount(20);
    await page.getByRole("combobox", { name: "Ordenar por", exact: true }).selectOption("VIGENCIA_CONTRATUAL");
    await expect(page.locator(".process-card").first()).toContainText("PA-VOLUME-80");
    await page.getByRole("button", { name: "Próxima página", exact: true }).click();
    await expect(page.locator(".process-card").first()).toContainText("PA-VOLUME-60");
    await expect(page.locator(".process-card")).toHaveCount(20);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  }
});

test("atalho de teclado chega ao conteúdo sem alterar o registro do link", async ({ page }) => {
  await setup(page); await page.goto("/#/processos?processo=17");
  await expect(page.locator(".process-detail-heading")).toBeVisible();
  const link = page.getByRole("link", { name: "Ir para o conteúdo", exact: true });
  await link.focus(); await expect(link).toBeInViewport(); await page.keyboard.press("Enter");
  await expect(page.locator("#workspace-content")).toBeFocused(); await expect(page).toHaveURL(/processo=17/);
});

for (const width of [2172, 1440, 1211, 768, 390]) test(`comentários visuais: escolha, alinhamento e criação em ${width}px`, async ({ page }, info) => {
  await page.setViewportSize({ width, height: 1000 });
  const fixture = await setup(page);
  await page.goto("/#/alteracoes");
  const picker = page.getByRole("group", { name: "Tipo de alteração", exact: true });
  const term = picker.getByRole("button", { name: "Termos Aditivos", exact: true });
  const amendment = picker.getByRole("button", { name: "Apostilamentos", exact: true });
  const termBounds = (await term.boundingBox())!, amendmentBounds = (await amendment.boundingBox())!;
  expect(Math.abs(termBounds.y - amendmentBounds.y)).toBeLessThanOrEqual(1);
  expect(amendmentBounds.x - termBounds.x - termBounds.width).toBeLessThanOrEqual(8);
  await page.getByRole("combobox", { name: "Instrumento Contratual", exact: true }).selectOption("77");
  await expect(page.getByRole("button", { name: "Preparar Termo Aditivo", exact: true })).toBeEnabled();
  await amendment.focus(); await page.keyboard.press("Enter");
  await expect(amendment).toHaveAttribute("aria-pressed", "true");
  await expect(term).toHaveAttribute("aria-pressed", "false");
  await expect(picker).toContainText("dados não contratuais");
  await expect(page).toHaveURL(/instrumento=77.*tipo=APOSTILAMENTO/);
  await expect(page.getByRole("button", { name: "Preparar Apostilamento", exact: true })).toBeEnabled();
  await page.reload(); await expect(amendment).toHaveAttribute("aria-pressed", "true");
  await page.screenshot({ path: info.outputPath(`comments-alteracoes-${width}.png`), fullPage: true });

  await page.goto("/#/documentos");
  const owner = page.getByRole("combobox", { name: "Vincular documento a", exact: true });
  const search = page.getByRole("textbox", { name: "Buscar Processo Administrativo", exact: true });
  const ownerBounds = (await owner.boundingBox())!, searchBounds = (await search.boundingBox())!;
  expect(ownerBounds.height).toBe(44);
  expect(searchBounds.height).toBe(44);
  if (width > 600) expect(Math.abs(ownerBounds.y - searchBounds.y)).toBeLessThanOrEqual(1);
  await page.screenshot({ path: info.outputPath(`comments-documentos-${width}.png`), fullPage: true });

  await page.goto("/#/administracao");
  const createUser = page.getByRole("button", { name: "Criar usuário", exact: true });
  const createSector = page.getByRole("button", { name: "Criar setor", exact: true });
  const userBounds = (await createUser.boundingBox())!, sectorBounds = (await createSector.boundingBox())!;
  expect(userBounds.height).toBeGreaterThanOrEqual(44);
  expect(sectorBounds.height).toBe(userBounds.height);
  expect(userBounds.width).toBeGreaterThan(100);
  await page.screenshot({ path: info.outputPath(`comments-administracao-${width}.png`), fullPage: true });
  for (const [trigger, title] of [[createUser, "Criar usuário"], [createSector, "Criar setor"]] as const) {
    await trigger.focus(); await page.keyboard.press("Enter");
    await expect(page.getByRole("dialog", { name: title, exact: true })).toBeVisible();
    await page.keyboard.press("Escape"); await expect(trigger).toBeFocused();
  }
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  expect(fixture.mutations).toEqual([]);
});
