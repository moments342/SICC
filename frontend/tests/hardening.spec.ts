import { mockInstrumentosAlteracao } from "./instrument-options";
import { expect, test, type Locator, type Page } from "@playwright/test";

const emptyPage = { content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 };
const dashboard = { processosPorStatus: {}, formalizacoesMensais: {}, conclusoesMensais: {} };
const notification = { id: 1, tipo: "ALERTA_VIGENCIA_CONTRATUAL", mensagem: "Vigência próxima do fim.",
  criadaEm: "2026-10-01T10:00:00", lida: false, processoId: null };
const report = { id: 1, tipo: "CONSOLIDADO", formato: "CSV", filtros: {}, nomeArquivo: "consolidado.csv",
  criadoEm: "2026-10-01T10:00:00", criadoPor: { nome: "Administrador", login: "admin" },
  checksumSha256: "a".repeat(64), chaveArmazenamento: "relatorio.csv", tamanhoBytes: 128 };

async function contrastRatio(locator: Locator) {
  return locator.evaluate(element => {
    function luminance(color: string) {
      const channels = color.match(/[\d.]+/g)!.slice(0, 3).map(Number).map(value => {
        const channel = value / 255;
        return channel <= .04045 ? channel / 12.92 : ((channel + .055) / 1.055) ** 2.4;
      });
      return channels[0] * .2126 + channels[1] * .7152 + channels[2] * .0722;
    }
    let parent: Element | null = element, background = "rgb(255, 255, 255)";
    while (parent) {
      const color = getComputedStyle(parent).backgroundColor;
      if (color !== "rgba(0, 0, 0, 0)") { background = color; break; }
      parent = parent.parentElement;
    }
    const foreground = luminance(getComputedStyle(element).color), back = luminance(background);
    return (Math.max(foreground, back) + .05) / (Math.min(foreground, back) + .05);
  });
}

async function internalSession(page: Page) {
  await page.addInitScript(() => localStorage.setItem("sicc-session", JSON.stringify({
    token: "hardening-test", perfil: "ADMINISTRADOR_DIPAC", trocaSenhaObrigatoria: false
  })));
  await page.route("**/api/v1/**", route => {
    const path = new URL(route.request().url()).pathname;
    return route.fulfill({ json: path.endsWith("/dashboard") ? dashboard
      : path.endsWith("/processos") || path.endsWith("/proprietarios") || path.endsWith("/alteracoes/instrumentos") ? emptyPage : [] });
  });
}

for (const width of [1440, 390]) {
  test.describe(`harden F01–F04 · ${width}px`, () => {
    test.use({ viewport: { width, height: 1000 } });
    test.beforeEach(async ({ page }) => {
      page.on("pageerror", error => { throw error; });
    });

    test("ações de alterações ficam legíveis em seleção, hover, foco e rascunho", async ({ page }) => {
      await internalSession(page);
      const instrument = { id: 10, numero: "CV-TESTE", tipo: "CONVENIO", objeto: "Cooperação", natureza: "Acadêmica",
        coordenador: "Maria", participes: ["UFGD"], valorAtual: 100, vigenciaContratualFinal: "2027-12-31" };
      const draft = { id: 1, instrumentoId: 10, tipo: "TERMO_ADITIVO", estado: "RASCUNHO", numeroOficial: "TA-TESTE",
        operacao: "ORIGINAL", mudancas: [{ campo: "COORDENADOR", valorAnterior: "Maria", valorNovo: "Ana" }],
        estadoAtualInstrumento: { ...instrument, statusProcesso: "EM_VIGENCIA", precedenciaPorCampo: {} },
        tramitacao: { setorAtual: null, movimentacoes: [], permanencias: [] } };
      await page.route("**/api/v1/processos?*", route => route.fulfill({ json: { ...emptyPage, totalPages: 1,
        content: [{ id: 1, numero: "PROC-TESTE", ativo: true, instrumento: instrument }] } }));
      await mockInstrumentosAlteracao(page, [{ numero: "PROC-TESTE", instrumento: instrument }]);
      await page.route("**/api/v1/alteracoes?*", route => route.fulfill({ json: [draft] }));
      await page.goto("/"); await page.getByRole("button", { name: "Alterações contratuais", exact: true }).click();
      await expect(page.getByRole("button", { name: "Criar rascunho" })).toBeDisabled();
      await page.getByLabel("Instrumento Contratual").selectOption("10");
      await page.locator(".alteracao-lista button").filter({ hasText: "TA-TESTE" }).click();
      for (const name of ["Termos Aditivos", "Criar rascunho", "Salvar rascunho"]) {
        const button = page.getByRole("button", { name, exact: true });
        await expect(button).toBeEnabled();
        expect(await contrastRatio(button)).toBeGreaterThanOrEqual(4.5);
        await button.hover(); expect(await contrastRatio(button)).toBeGreaterThanOrEqual(4.5);
        await button.focus(); await page.keyboard.press("Tab"); await page.keyboard.press("Shift+Tab");
        await expect(button).toBeFocused(); await expect(button).toHaveCSS("outline-style", "solid");
      }
      await page.getByRole("button", { name: "Apostilamentos", exact: true }).click();
      expect(await contrastRatio(page.getByRole("button", { name: "Apostilamentos", exact: true }))).toBeGreaterThanOrEqual(4.5);
    });

    test("textos de apoio e cabeçalhos públicos atingem contraste de 4,5:1", async ({ page }) => {
      await page.route("**/api/v1/public/processos?*", route => route.fulfill({ json: emptyPage }));
      await page.goto("/"); await expect(page.getByText("Nenhum processo encontrado.")).toBeVisible();
      for (const selector of [".public-list .eyebrow", ".empty", ".login-card > p", "th"]) {
        expect(await contrastRatio(page.locator(selector).first())).toBeGreaterThanOrEqual(4.5);
      }
    });

    for (const entry of [
      { page: "Relatórios", endpoint: "relatorios", loading: "o histórico de relatórios", empty: "Nenhum relatório gerado." },
      { page: "Notificações", endpoint: "notificacoes", loading: "as notificações", empty: "Nenhuma notificação." }
    ]) {
      test(`${entry.page}: demora, falha, nova tentativa e vazio são estados distintos`, async ({ page }) => {
        await internalSession(page);
        let release!: () => void;
        const pending = new Promise<void>(resolve => { release = resolve; });
        let attempts = 0;
        await page.route(`**/api/v1/${entry.endpoint}`, async route => {
          if (++attempts === 1) {
            await pending;
            await route.fulfill({ status: 503, json: { mensagem: "Serviço temporariamente indisponível." } });
          } else await route.fulfill({ json: [] });
        });
        await page.goto("/");
        await page.getByRole("button", { name: entry.page, exact: true }).click();
        await expect(page.getByRole("status").filter({ hasText: `Carregando ${entry.loading}` })).toBeVisible();
        await expect(page.getByText(entry.empty)).toHaveCount(0);
        release();
        await expect(page.getByRole("alert").filter({ hasText: "Serviço temporariamente" })).toBeVisible();
        await expect(page.getByText(entry.empty)).toHaveCount(0);
        await page.getByRole("button", { name: "Tentar novamente" }).click();
        await expect(page.getByText(entry.empty)).toBeVisible();
      });
    }

    for (const failed of ["usuarios", "setores"]) {
      test(`administração recupera ${failed} sem esconder a outra consulta ou apagar o formulário`, async ({ page }) => {
        await internalSession(page);
        const users = [{ id: 1, nome: "Usuário disponível", email: "teste@sicc.test", login: "teste",
          perfil: "OPERADOR_DIPAC", ativo: true, senhaTemporaria: false }];
        const sectors = [{ id: 1, sigla: "DIPAC", nome: "Setor disponível", ativo: true }];
        let recovered = false;
        for (const endpoint of ["usuarios", "setores"]) await page.route(`**/api/v1/admin/${endpoint}`, route =>
          endpoint === failed && !recovered
            ? route.fulfill({ status: 503, json: { mensagem: "Consulta indisponível." } })
            : route.fulfill({ json: endpoint === "usuarios" ? users : sectors }));
        await page.goto("/"); await page.getByRole("button", { name: "Administração", exact: true }).click();
        await expect(page.getByRole("alert").filter({ hasText: "Consulta indisponível" })).toBeVisible();
        await expect(page.getByText(failed === "usuarios" ? "Setor disponível" : "Usuário disponível", { exact: true })).toBeVisible();
        await expect(page.getByText(/Nenhum (usuário|setor) cadastrado/)).toHaveCount(0);
        const name = page.locator('form').filter({ has: page.getByRole("button", { name: "Criar usuário" }) }).getByLabel("Nome", { exact: true });
        await name.fill("Cadastro ainda em edição");
        recovered = true;
        await page.getByRole("button", { name: "Tentar novamente" }).click();
        await expect(page.getByText(failed === "usuarios" ? "Usuário disponível" : "Setor disponível", { exact: true })).toBeVisible();
        await expect(name).toHaveValue("Cadastro ainda em edição");
      });
    }

    test("consulta pública e login mantêm erros separados, com recuperação local", async ({ page }) => {
      let recovered = false;
      await page.route("**/api/v1/public/processos?*", route => recovered ? route.fulfill({ json: emptyPage }) : route.abort());
      await page.route("**/api/v1/auth/login", route => route.fulfill({ status: 401, json: { mensagem: "Credenciais inválidas." } }));
      await page.goto("/");
      await expect(page.locator(".public-list").getByRole("alert")).toContainText("Verifique sua conexão");
      await expect(page.getByText("Nenhum processo encontrado.")).toHaveCount(0);
      await expect(page.locator(".login-card").getByRole("alert")).toBeEmpty();
      await page.getByLabel("Login", { exact: true }).fill("admin");
      await page.getByLabel("Senha", { exact: true }).fill("senha-incorreta");
      await page.getByRole("button", { name: "Entrar no SICC" }).click();
      await expect(page.locator(".login-card").getByRole("alert")).toContainText("Credenciais inválidas");
      await expect(page.getByLabel("Senha", { exact: true })).toHaveAttribute("aria-describedby", "login-error");
      recovered = true;
      await page.getByRole("button", { name: "Tentar novamente" }).click();
      await expect(page.getByText("Nenhum processo encontrado.")).toBeVisible();
      await expect(page.locator(".login-card").getByRole("alert")).toContainText("Credenciais inválidas");
      await expect(page.getByLabel("Login", { exact: true })).toHaveValue("admin");
    });

    test("download mostra erro, aceita teclado e descarta feedback depois de sair da página", async ({ page }) => {
      await internalSession(page);
      await page.route("**/api/v1/relatorios", route => route.fulfill({ json: [report] }));
      let late = false;
      let release!: () => void;
      const pending = new Promise<void>(resolve => { release = resolve; });
      await page.route("**/api/v1/relatorios/1/arquivo", async route => {
        if (late) await pending;
        await route.fulfill({ status: 503 });
      });
      await page.goto("/"); await page.getByRole("button", { name: "Relatórios", exact: true }).click();
      await page.getByRole("button", { name: "Baixar", exact: true }).click();
      await expect(page.locator(".toast").getByRole("alert")).toContainText("consolidado.csv");
      await page.getByRole("button", { name: "Fechar mensagem" }).focus();
      await page.keyboard.press("Enter");
      await expect(page.locator(".toast")).toHaveCount(0);
      late = true;
      const requested = page.waitForRequest("**/api/v1/relatorios/1/arquivo");
      await page.getByRole("button", { name: "Baixar", exact: true }).click(); await requested;
      await page.getByRole("button", { name: "Notificações", exact: true }).click();
      const response = page.waitForResponse("**/api/v1/relatorios/1/arquivo"); release(); await response;
      await expect(page.getByText("Nenhuma notificação.")).toBeVisible();
      await expect(page.locator(".toast")).toHaveCount(0);
      await page.getByRole("button", { name: "Relatórios", exact: true }).click();
      await expect(page.getByRole("button", { name: "Baixar", exact: true })).toBeVisible();
      await expect(page.locator(".toast")).toHaveCount(0);
    });

    test("falha ao marcar leitura preserva notificação e permite repetir", async ({ page }) => {
      await internalSession(page);
      let read = false, attempts = 0;
      await page.route("**/api/v1/notificacoes", route => route.fulfill({ json: [{ ...notification, lida: read }] }));
      await page.route("**/api/v1/notificacoes/1/lida", route => {
        if (++attempts === 1) return route.fulfill({ status: 503, json: { mensagem: "Tente mais tarde." } });
        read = true; return route.fulfill({ status: 204 });
      });
      await page.goto("/"); await page.getByRole("button", { name: "Notificações", exact: true }).click();
      await page.getByRole("button", { name: "Marcar como lida" }).click();
      await expect(page.locator(".toast").getByRole("alert")).toContainText("Não foi possível marcar");
      await expect(page.getByText(notification.mensagem)).toBeVisible();
      await page.getByRole("button", { name: "Marcar como lida" }).click();
      await expect(page.locator(".toast").getByRole("status")).toHaveText("Notificação marcada como lida.");
      await expect(page.getByText("Lida", { exact: true })).toBeVisible();
      await page.getByRole("button", { name: "Relatórios", exact: true }).click();
      await expect(page.locator(".toast")).toHaveCount(0);
    });

    test("limites de processo e erros de campo preservam entrada e movem foco", async ({ page }) => {
      await internalSession(page);
      await page.route("**/api/v1/processos", route => route.fulfill({ status: 400,
        json: { mensagem: "numero: número inválido; origem: origem inválida" } }));
      await page.goto("/"); await page.getByRole("button", { name: "Processos Administrativos", exact: true }).click();
      const form = page.locator("form").filter({ has: page.getByRole("button", { name: "Cadastrar processo" }) });
      const number = form.locator('input[name="numero"]');
      const origin = form.locator('input[name="origem"]');
      await expect(number).toHaveAttribute("maxlength", "60");
      await expect(origin).toHaveAttribute("maxlength", "150");
      await expect(form.locator('input[name="projeto"]')).toHaveAttribute("maxlength", "80");
      await number.fill("N".repeat(61)); await expect(number).toHaveValue("N".repeat(60));
      await number.fill("PROCESSO-TESTE"); await origin.fill("DIPAC");
      await form.getByRole("button", { name: "Cadastrar processo" }).click();
      await expect(form.getByRole("alert")).toContainText("Revise os campos");
      await expect(number).toBeFocused(); await expect(number).toHaveAttribute("aria-invalid", "true");
      await expect(number).toHaveAccessibleDescription("número inválido");
      await expect(origin).toHaveAccessibleDescription("origem inválida");
      await expect(origin).toHaveValue("DIPAC");
    });
  });
}
