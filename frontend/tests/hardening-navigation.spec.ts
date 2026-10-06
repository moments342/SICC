import { mockInstrumentosAlteracao } from "./instrument-options";
import { expect, test, type Page } from "@playwright/test";

const empty = { content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 };
const instrument = { id: 10, numero: "CV-TESTE", tipo: "CONVENIO", objeto: "Cooperação", natureza: "Acadêmica",
  coordenador: "Maria", participes: ["UFGD"], valorAtual: 100, vigenciaContratualFinal: "2027-12-31",
  documentoAssinadoId: 1, documentoAssinadoVersao: 1, documentoAssinadoChecksumSha256: "a".repeat(64) };
const process = { id: 1, numero: "PROC-TESTE", origem: "DIPAC", status: "EM_VIGENCIA", ativo: true, instrumento: instrument };
const draft = { id: 1, instrumentoId: 10, tipo: "TERMO_ADITIVO", estado: "RASCUNHO", numeroOficial: "TA-TESTE",
  operacao: "ORIGINAL", mudancas: [{ campo: "OBJETO", valorAnterior: "Cooperação", valorNovo: "Objeto proposto" }],
  estadoAtualInstrumento: { ...instrument, statusProcesso: "EM_VIGENCIA", precedenciaPorCampo: {} },
  tramitacao: { setorAtual: null, movimentacoes: [], permanencias: [] } };
const user = { id: 1, nome: "Operador de Teste", email: "operador@sicc.test", login: "operador",
  perfil: "OPERADOR_DIPAC", ativo: true, senhaTemporaria: false };

async function setup(page: Page, profile = "ADMINISTRADOR_DIPAC") {
  await page.addInitScript(profile => {
    if (!sessionStorage.getItem("test-initialized")) {
      localStorage.setItem("sicc-session", JSON.stringify({ token: "navigation-test", perfil: profile, trocaSenhaObrigatoria: false }));
      sessionStorage.setItem("test-initialized", "true");
    }
  }, profile);
  await page.route("**/api/v1/**", route => {
    const path = new URL(route.request().url()).pathname;
    let json: unknown = [];
    if (path.endsWith("/dashboard")) json = { processosPorStatus: {}, formalizacoesMensais: {}, conclusoesMensais: {} };
    else if (path === "/api/v1/public/processos") json = empty;
    else if (path.endsWith("/processos")) json = { ...empty, totalPages: 1, totalElements: 2,
      content: [process, { ...process, id: 2, numero: "PROC-OUTRO", instrumento: { ...instrument, id: 20, numero: "CV-OUTRO" } }] };
    else if (path.endsWith("/proprietarios") || path.endsWith("/auditoria")) json = empty;
    else if (path.endsWith("/tramitacao")) json = { setorAtual: null, movimentacoes: [], permanencias: [] };
    else if (path.endsWith("/alteracoes")) json = new URL(route.request().url()).searchParams.get("instrumentoId") === "10" ? [draft] : [];
    else if (path.endsWith("/admin/usuarios")) json = [user];
    else if (path.endsWith("/admin/usuarios/1")) json = user;
    return route.fulfill({ json });
  });
  await mockInstrumentosAlteracao(page, [process, { ...process, numero: "PROC-OUTRO", instrumento: { ...instrument, id: 20, numero: "CV-OUTRO" } }]);
  page.on("pageerror", error => { throw error; });
}

async function nav(page: Page, name: string) {
  await page.getByRole("navigation").getByRole("button", { name, exact: true }).click();
  await expect(page.getByRole("heading", { name, level: 1, exact: true })).toBeFocused();
}

for (const width of [1440, 390]) test.describe(`harden F05–F09 · ${width}px`, () => {
  test.use({ viewport: { width, height: 1000 } });

  test("F05: cancelar não envia DELETE; falha preserva o processo e permite confirmar novamente", async ({ page }) => {
    await setup(page);
    let deletes = 0;
    await page.route("**/api/v1/processos/1", route => {
      expect(route.request().method()).toBe("DELETE"); deletes++;
      return deletes === 1 ? route.fulfill({ status: 503, json: { mensagem: "Tente novamente." } }) : route.fulfill({ json: { ...process, ativo: false } });
    });
    await page.goto("/#/processos");
    await page.getByRole("button", { name: /PROC-TESTE/ }).click();
    await expect(page.getByRole("button", { name: /PROC-TESTE/ })).toHaveAttribute("aria-pressed", "true");
    const deactivate = page.getByRole("button", { name: "Desativar", exact: true });
    page.once("dialog", async dialog => {
      expect(dialog.message()).toContain("PROC-TESTE"); expect(dialog.message()).toContain("histórico será preservado");
      await dialog.dismiss();
    });
    await deactivate.click(); expect(deletes).toBe(0); await expect(deactivate).toBeFocused();
    page.once("dialog", dialog => dialog.accept()); await deactivate.click();
    await expect(page.locator(".toast").getByRole("alert")).toContainText("Tente novamente");
    await expect(deactivate).toBeEnabled();
    page.once("dialog", dialog => dialog.accept()); await deactivate.click();
    await expect(page.locator(".toast").getByRole("status")).toContainText("histórico foi preservado");
    expect(deletes).toBe(2);
  });

  test("F06: preenchimento fica separado por tipo/instrumento e retorna após visitar Documentos", async ({ page }, info) => {
    await setup(page); await page.goto("/#/alteracoes");
    await page.getByRole("combobox", { name: "Instrumento Contratual", exact: true }).selectOption("10");
    const number = page.getByLabel("Identificação do termo", { exact: true });
    await number.fill("TA-Árvore-研究-📝"); await page.getByLabel("Novo valor", { exact: true }).fill("Proposta preservada");
    await nav(page, "Documentos"); await nav(page, "Alterações contratuais");
    await expect(number).toHaveValue("TA-Árvore-研究-📝");
    await expect(page.getByLabel("Novo valor", { exact: true })).toHaveValue("Proposta preservada");
    await page.getByRole("combobox", { name: "Instrumento Contratual", exact: true }).selectOption("20");
    await expect(number).toHaveValue(""); await number.fill("TA-OUTRO");
    await page.getByRole("combobox", { name: "Instrumento Contratual", exact: true }).selectOption("10");
    await expect(number).toHaveValue("TA-Árvore-研究-📝");
    await page.getByRole("button", { name: "Apostilamentos", exact: true }).click();
    await expect(page.getByRole("button", { name: "Apostilamentos", exact: true })).toHaveAttribute("aria-pressed", "true");
    await expect(page.getByLabel("Identificação do apostilamento", { exact: true })).toHaveValue("");
    await page.getByRole("button", { name: "Termos Aditivos", exact: true }).click();
    await expect(number).toHaveValue("TA-Árvore-研究-📝");
    await page.screenshot({ path: info.outputPath("draft-preserved.png"), fullPage: true });
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBe(true);
  });

  test("F06: edição, efetivação e outras operações retornam; erro não limpa e salvar limpa somente o formulário enviado", async ({ page }) => {
    await setup(page); await page.goto("/#/alteracoes");
    await page.getByRole("combobox", { name: "Instrumento Contratual", exact: true }).selectOption("10");
    const selected = page.locator(".alteracao-lista button").filter({ hasText: "TA-TESTE" });
    await selected.click(); await expect(selected).toHaveAttribute("aria-pressed", "true");
    await page.getByLabel("Identificação do rascunho").fill("TA-EDITADO");
    await page.getByLabel("Data de efetivação").fill("2026-10-03");
    await page.getByLabel("Ordem oficial").fill("7");
    await page.getByLabel("Identificação da outra alteração").fill("AP-PENDENTE");
    await nav(page, "Documentos"); await nav(page, "Alterações contratuais");
    await expect(page.getByLabel("Identificação do rascunho")).toHaveValue("TA-EDITADO");
    await expect(page.getByLabel("Data de efetivação")).toHaveValue("2026-10-03");
    await expect(page.getByLabel("Ordem oficial")).toHaveValue("7");
    await expect(page.getByLabel("Identificação da outra alteração")).toHaveValue("AP-PENDENTE");
    let saves = 0;
    await page.route("**/api/v1/alteracoes", route => ++saves === 1
      ? route.fulfill({ status: 503, json: { mensagem: "Criação indisponível." } })
      : route.fulfill({ status: 201, json: { ...draft, id: 2, numeroOficial: "TA-NOVO" } }));
    await page.getByLabel("Identificação do termo", { exact: true }).fill("TA-NOVO");
    await page.getByLabel("Novo valor", { exact: true }).fill("Novo objeto");
    await page.getByRole("button", { name: "Criar rascunho", exact: true }).click();
    await expect(page.locator(".toast").getByRole("alert")).toContainText("Criação indisponível");
    await expect(page.getByLabel("Identificação do termo", { exact: true })).toHaveValue("TA-NOVO");
    await page.getByRole("button", { name: "Criar rascunho", exact: true }).click();
    await expect(page.getByLabel("Identificação do termo", { exact: true })).toHaveValue("");
    await expect(page.getByLabel("Identificação da outra alteração")).toHaveValue("AP-PENDENTE");
  });

  test("F06/F07: saída e recarga avisam; nova identidade não recebe rascunhos ou página administrativa", async ({ page }) => {
    await setup(page); await page.goto("/#/alteracoes");
    await page.getByLabel("Identificação do termo", { exact: true }).fill("NÃO COMPARTILHAR");
    const warning = page.waitForEvent("dialog");
    await page.evaluate(() => { window.setTimeout(() => window.location.reload(), 0); });
    const dialog = await warning;
    expect(dialog.type()).toBe("beforeunload");
    await dialog.dismiss();
    await expect(page.getByLabel("Identificação do termo", { exact: true })).toHaveValue("NÃO COMPARTILHAR");
    page.once("dialog", dialog => dialog.dismiss());
    await page.getByRole("button", { name: "Sair", exact: true }).click();
    await expect(page.getByLabel("Identificação do termo", { exact: true })).toHaveValue("NÃO COMPARTILHAR");
    await nav(page, "Registros de Auditoria");
    page.once("dialog", dialog => dialog.accept());
    await page.getByRole("button", { name: "Sair", exact: true }).click();
    await page.route("**/api/v1/auth/login", route => route.fulfill({ json: {
      token: "operator-new", perfil: "OPERADOR_DIPAC", trocaSenhaObrigatoria: false
    } }));
    let adminReads = 0;
    page.on("request", request => { if (/\/api\/v1\/(admin|auditoria)/.test(request.url())) adminReads++; });
    await page.getByLabel("Login", { exact: true }).fill("operador"); await page.getByLabel("Senha", { exact: true }).fill("senha-teste");
    await page.getByRole("button", { name: "Entrar no SICC" }).click();
    await expect(page).toHaveURL(/#\/dashboard$/); await expect(page).toHaveTitle("Visão geral · SICC");
    await expect(page.getByRole("button", { name: "Administração", exact: true })).toHaveCount(0);
    await page.evaluate(() => { location.hash = "/auditoria"; });
    await expect(page).toHaveURL(/#\/dashboard$/);
    await nav(page, "Alterações contratuais");
    await expect(page.getByLabel("Identificação do termo", { exact: true })).toHaveValue("");
    expect(adminReads).toBe(0);
  });

  test("F08: URL, histórico, seleção, título e foco acompanham a página; operador não monta administração", async ({ page }) => {
    await setup(page, "OPERADOR_DIPAC"); await page.goto("/#/administracao");
    await expect(page).toHaveURL(/#\/dashboard$/);
    await nav(page, "Documentos"); await expect(page).toHaveTitle("Documentos · SICC");
    await expect(page.getByRole("button", { name: "Documentos", exact: true })).toHaveAttribute("aria-current", "page");
    await page.reload(); await expect(page).toHaveTitle("Documentos · SICC");
    await expect(page.getByRole("heading", { name: "Documentos", level: 1 })).toBeFocused();
    await nav(page, "Relatórios"); await page.goBack(); await expect(page).toHaveTitle("Documentos · SICC");
    await page.goForward(); await expect(page).toHaveTitle("Relatórios · SICC");
    await expect(page.getByRole("main")).toHaveCount(1);
  });

  test("F08: detalhes recebem foco e devolvem ao acionador pelo teclado", async ({ page }, info) => {
    await setup(page); await page.goto("/#/administracao");
    const trigger = page.getByRole("button", { name: "Ver detalhes de Operador de Teste" });
    await trigger.focus(); await page.keyboard.press("Enter");
    await expect(page.getByRole("heading", { name: "Detalhes do Usuário Interno" })).toBeFocused();
    await expect(trigger).toHaveAttribute("aria-expanded", "true");
    await page.screenshot({ path: info.outputPath("user-detail-focus.png"), fullPage: true });
    await page.keyboard.press("Tab"); await expect(page.getByRole("button", { name: "Fechar detalhes" })).toBeFocused();
    await page.keyboard.press("Enter"); await expect(trigger).toBeFocused();
    await expect(trigger).toHaveAttribute("aria-expanded", "false");
  });

  test("F09: envio repetido é bloqueado inclusive após navegar; formatos diferentes continuam disponíveis", async ({ page }, info) => {
    await setup(page);
    let release!: () => void, posts = 0;
    const pending = new Promise<void>(resolve => { release = resolve; });
    await page.route("**/api/v1/relatorios", async route => {
      if (route.request().method() !== "POST") return route.fulfill({ json: [] });
      posts++; await pending; await route.fulfill({ status: 201, json: {} });
    });
    await page.goto("/#/relatorios");
    const row = page.locator(".report-actions > div").filter({ hasText: "Consolidado" });
    const csv = row.getByRole("button", { name: /^CSV/ });
    await csv.evaluate(button => { for (let i = 0; i < 10; i++) (button as HTMLButtonElement).click(); });
    await expect(csv).toBeDisabled(); await expect(csv).toContainText("Gerando");
    await expect(page.getByRole("status").filter({ hasText: "Gerando:" })).toContainText("Consolidado · CSV");
    await expect(row.getByRole("button", { name: "PDF", exact: true })).toBeEnabled();
    await page.screenshot({ path: info.outputPath("report-pending.png"), fullPage: true });
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBe(true);
    await nav(page, "Documentos"); await nav(page, "Relatórios"); await expect(csv).toBeDisabled();
    await row.getByRole("button", { name: "PDF", exact: true }).click();
    await expect.poll(() => posts).toBe(2);
    release(); await expect(csv).toBeEnabled();
    await expect(row.getByRole("button", { name: "PDF", exact: true })).toBeEnabled();
    expect(posts).toBe(2);
  });

  test("F09: falha libera nova tentativa e mantém os filtros", async ({ page }) => {
    await setup(page); let posts = 0;
    await page.route("**/api/v1/relatorios", route => {
      if (route.request().method() !== "POST") return route.fulfill({ json: [] });
      posts++; expect(route.request().postDataJSON().filtros).toEqual({ origem: "DIPAC" });
      return route.fulfill({ status: 503, json: { mensagem: "Geração indisponível." } });
    });
    await page.goto("/#/relatorios"); await page.getByLabel("Origem", { exact: true }).fill("DIPAC");
    const csv = page.locator(".report-actions > div").filter({ hasText: "Consolidado" }).getByRole("button", { name: "CSV", exact: true });
    await csv.click(); await expect(page.locator(".toast").getByRole("alert")).toContainText("Geração indisponível");
    await expect(csv).toBeEnabled(); await expect(page.getByLabel("Origem", { exact: true })).toHaveValue("DIPAC");
    await csv.click(); await expect.poll(() => posts).toBe(2); await expect(csv).toBeEnabled();
  });

  test("F09: falha pendente é anunciada após sair e voltar à página", async ({ page }) => {
    await setup(page);
    let release!: () => void, posts = 0;
    const pending = new Promise<void>(resolve => { release = resolve; });
    await page.route("**/api/v1/relatorios", async route => {
      if (route.request().method() !== "POST") return route.fulfill({ json: [] });
      if (++posts > 1) return route.fulfill({ status: 201, json: {} });
      await pending; await route.fulfill({ status: 503, json: { mensagem: "Geração indisponível." } });
    });
    await page.goto("/#/relatorios");
    const csv = page.locator(".report-actions > div").filter({ hasText: "Consolidado" }).getByRole("button", { name: /^CSV/ });
    await csv.click(); await expect(csv).toBeDisabled();
    await nav(page, "Documentos"); await nav(page, "Relatórios");
    release();
    await expect(page.locator(".toast").getByRole("alert")).toContainText("Geração indisponível");
    await expect(csv).toBeEnabled(); await csv.click();
    await expect(page.locator(".toast").getByRole("status")).toContainText("Relatório gerado");
    expect(posts).toBe(2);
  });

  test("F06: resposta lenta não apaga o preenchimento posterior ao envio", async ({ page }) => {
    await setup(page);
    let release!: () => void;
    const pending = new Promise<void>(resolve => { release = resolve; });
    await page.route("**/api/v1/alteracoes", async route => {
      await pending;
      await route.fulfill({ status: 201, json: { ...draft, id: 3, numeroOficial: "TA-ENVIADO" } });
    });
    await page.goto("/#/alteracoes");
    await page.getByRole("combobox", { name: "Instrumento Contratual", exact: true }).selectOption("10");
    await page.getByLabel("Identificação do termo", { exact: true }).fill("TA-ENVIADO");
    await page.getByLabel("Novo valor", { exact: true }).fill("Objeto enviado");
    const sent = page.waitForRequest(request => request.method() === "POST" && request.url().endsWith("/alteracoes"));
    await page.getByRole("button", { name: "Criar rascunho", exact: true }).click();
    await sent;
    await nav(page, "Documentos"); await nav(page, "Alterações contratuais");
    await page.getByLabel("Identificação do termo", { exact: true }).fill("TA-PRÓXIMO");
    await page.getByLabel("Novo valor", { exact: true }).fill("Objeto ainda não enviado");
    const response = page.waitForResponse(response => response.request().method() === "POST" && response.url().endsWith("/alteracoes"));
    release(); await (await response).finished();
    await page.getByRole("button", { name: "Apostilamentos", exact: true }).click();
    await page.getByRole("button", { name: "Termos Aditivos", exact: true }).click();
    await expect(page.getByLabel("Identificação do termo", { exact: true })).toHaveValue("TA-PRÓXIMO");
    await expect(page.getByLabel("Novo valor", { exact: true })).toHaveValue("Objeto ainda não enviado");
  });

  test("F07: resposta 401 da sessão anterior não encerra a nova sessão", async ({ page }) => {
    await setup(page);
    let release!: () => void;
    const pending = new Promise<void>(resolve => { release = resolve; });
    await page.route("**/api/v1/relatorios", async route => {
      if (route.request().method() !== "POST") return route.fulfill({ json: [] });
      await pending;
      await route.fulfill({ status: 401, json: { mensagem: "Sessão anterior expirada." } });
    });
    await page.goto("/#/relatorios");
    const row = page.locator(".report-actions > div").filter({ hasText: "Consolidado" });
    await row.getByRole("button", { name: "CSV", exact: true }).click();
    await expect(row.getByRole("button", { name: /^CSV/ })).toBeDisabled();
    await page.getByRole("button", { name: "Sair", exact: true }).click();
    await page.route("**/api/v1/auth/login", route => route.fulfill({ json: {
      token: "operator-new", perfil: "OPERADOR_DIPAC", trocaSenhaObrigatoria: false
    } }));
    await page.getByLabel("Login", { exact: true }).fill("operador");
    await page.getByLabel("Senha", { exact: true }).fill("senha-teste");
    await page.getByRole("button", { name: "Entrar no SICC" }).click();
    await expect(page.getByRole("heading", { name: "Visão geral", level: 1 })).toBeVisible();
    const response = page.waitForResponse(response => response.status() === 401);
    release(); await (await response).finished();
    await nav(page, "Relatórios");
    await expect(row.getByRole("button", { name: "CSV", exact: true })).toBeEnabled();
    await expect(page.locator(".toast").getByRole("alert")).toHaveCount(0);
    expect(await page.evaluate(() => JSON.parse(localStorage.getItem("sicc-session")!).token)).toBe("operator-new");
  });

  test("F06: criação lenta permanece vinculada ao instrumento enviado", async ({ page }) => {
    await setup(page);
    let release!: () => void;
    const pending = new Promise<void>(resolve => { release = resolve; });
    await page.route("**/api/v1/alteracoes", async route => {
      expect(route.request().postDataJSON().instrumentoId).toBe(10);
      await pending;
      await route.fulfill({ status: 201, json: { ...draft, id: 3, numeroOficial: "TA-ENVIADO" } });
    });
    await page.goto("/#/alteracoes");
    const selector = page.getByRole("combobox", { name: "Instrumento Contratual", exact: true });
    await selector.selectOption("10");
    await page.getByLabel("Identificação do termo", { exact: true }).fill("TA-ENVIADO");
    await page.getByLabel("Novo valor", { exact: true }).fill("Objeto enviado");
    const sent = page.waitForRequest(request => request.method() === "POST" && request.url().endsWith("/alteracoes"));
    await page.getByRole("button", { name: "Criar rascunho", exact: true }).click(); await sent;
    await selector.selectOption("20");
    await page.getByLabel("Identificação do termo", { exact: true }).fill("TA-OUTRO");
    const response = page.waitForResponse(response => response.request().method() === "POST" && response.url().endsWith("/alteracoes"));
    release(); await (await response).finished();
    await expect(page.locator(".toast").getByRole("status")).toContainText("Rascunho #3 criado");
    await expect(selector).toHaveValue("20");
    await expect(page.locator(".alteracao-lista button")).toHaveCount(0);
    await expect(page.getByLabel("Identificação do termo", { exact: true })).toHaveValue("TA-OUTRO");
  });

  test("F08: fechar detalhes cancela uma reabertura pendente e preserva o foco", async ({ page }) => {
    await setup(page); await page.goto("/#/administracao");
    const trigger = page.getByRole("button", { name: "Ver detalhes de Operador de Teste" });
    await trigger.click();
    await expect(page.getByRole("heading", { name: "Detalhes do Usuário Interno" })).toBeFocused();
    let release!: () => void;
    const pending = new Promise<void>(resolve => { release = resolve; });
    await page.route("**/api/v1/admin/usuarios/1", async route => {
      await pending; await route.fulfill({ json: user }).catch(() => {});
    });
    const sent = page.waitForRequest("**/api/v1/admin/usuarios/1");
    await trigger.click(); await sent;
    await page.getByRole("button", { name: "Fechar detalhes", exact: true }).focus();
    await page.keyboard.press("Enter");
    await expect(trigger).toBeFocused();
    release();
    await page.keyboard.press("Tab"); await page.keyboard.press("Shift+Tab");
    await expect(trigger).toBeFocused();
    await expect(page.getByRole("heading", { name: "Detalhes do Usuário Interno" })).toHaveCount(0);
    const navigation = page.getByRole("navigation");
    await navigation.getByRole("button", { name: "Documentos", exact: true }).focus();
    await page.keyboard.press("Enter");
    await expect(page.getByRole("heading", { name: "Documentos", level: 1 })).toBeFocused();
  });
});

test("F08: consulta pública e troca obrigatória de senha possuem um main", async ({ page }) => {
  await page.route("**/api/v1/public/processos?*", route => route.fulfill({ json: empty }));
  await page.goto("/"); await expect(page.getByRole("main")).toHaveCount(1); await expect(page).toHaveTitle("Consulta pública · SICC");
  await page.route("**/api/v1/auth/login", route => route.fulfill({ json: {
    token: "temporary", perfil: "OPERADOR_DIPAC", trocaSenhaObrigatoria: true
  } }));
  await page.getByLabel("Login", { exact: true }).fill("operador"); await page.getByLabel("Senha", { exact: true }).fill("temporaria");
  await page.getByRole("button", { name: "Entrar no SICC" }).click();
  await expect(page.getByRole("heading", { name: "Crie sua senha permanente" })).toBeVisible();
  await expect(page.getByRole("main")).toHaveCount(1); await expect(page).toHaveTitle("Troca de senha · SICC");
});
