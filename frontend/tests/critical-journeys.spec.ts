import { expect, Page, test } from "@playwright/test";

const emptyPage = { content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 };
const dashboard = {
  processosPorStatus: {}, percentualConcluidos: 0, alertasContratuais: 0, alertasTed: 0,
  valorTotalVigente: 0, instrumentosPorTipo: {}, permanenciaMediaPorSetor: {},
  maiorGargalo: null, tempoMedioTramitacaoInicialDias: 0,
  formalizacoesMensais: {}, conclusoesMensais: {}
};

async function session(page: Page, perfil = "ADMINISTRADOR_DIPAC") {
  await page.addInitScript(value => localStorage.setItem("sicc-session", JSON.stringify(value)), {
    token: "jwt-de-teste", perfil, trocaSenhaObrigatoria: false
  });
  await page.route("**/api/v1/dashboard", route => route.fulfill({ json: dashboard }));
}

test("consulta pública aplica os cinco filtros e mostra somente a allowlist", async ({ page }) => {
  await page.route("**/api/v1/public/processos*", route => route.fulfill({ json: {
    ...emptyPage, totalElements: 1, totalPages: 1, content: [{
      numeroProcesso: "23005.000001/2026-10", tipoInstrumento: "CONVENIO", origem: "DIPAC",
      coordenador: "Maria Silva", status: "EM_VIGENCIA",
      vigenciaContratualFinal: "2027-12-31", vigenciaTedFinal: "2027-06-30"
    }]
  }}));
  await page.goto("/");
  await expect(page.getByRole("columnheader")).toHaveCount(7);
  await expect(page.getByText("23005.000001/2026-10")).toBeVisible();
  await page.getByLabel("Número").fill("23005");
  await page.getByLabel("Origem", { exact: true }).fill("DIPAC");
  await page.getByLabel("Tipo").selectOption("CONVENIO");
  await page.getByLabel("Status").selectOption("EM_VIGENCIA");
  await page.getByLabel("Vigência").selectOption("VALIDA");
  const request = page.waitForRequest(req => req.url().includes("/api/v1/public/processos?numero=23005"));
  await page.getByRole("button", { name: "Filtrar" }).click();
  const url = new URL((await request).url());
  expect(Object.fromEntries(url.searchParams)).toEqual({
    numero: "23005", origem: "DIPAC", tipo: "CONVENIO", status: "EM_VIGENCIA", vigencia: "VALIDA"
  });
  await expect(page.getByText("valor_atual")).toHaveCount(0);
});

test("primeiro login obriga a troca de senha antes da área interna", async ({ page }) => {
  await page.route("**/api/v1/public/processos*", route => route.fulfill({ json: emptyPage }));
  await page.route("**/api/v1/auth/login", route => route.fulfill({ json: {
    token: "temporario", perfil: "OPERADOR_DIPAC", trocaSenhaObrigatoria: true
  }}));
  await page.route("**/api/v1/auth/senha", async route => {
    expect(await route.request().postDataJSON()).toEqual({
      senhaAtual: "Temporaria123!", novaSenha: "Permanente123!"
    });
    await route.fulfill({ status: 204 });
  });
  await page.goto("/");
  await page.getByLabel("Login").fill("operador");
  await page.getByLabel("Senha").fill("Temporaria123!");
  await page.getByRole("button", { name: "Entrar no SICC" }).click();
  await expect(page.getByRole("heading", { name: "Crie sua senha permanente" })).toBeVisible();
  await page.getByLabel("Senha temporária").fill("Temporaria123!");
  await page.getByLabel("Nova senha").fill("Permanente123!");
  await page.getByRole("button", { name: "Definir senha" }).click();
  await expect(page.getByRole("button", { name: "Entrar no SICC" })).toBeVisible();
});

test("login informa carregamento e erro sem permitir envio duplicado", async ({ page }) => {
  await page.route("**/api/v1/public/processos*", route => route.fulfill({ json: emptyPage }));
  let liberarLogin!: () => void;
  const loginPendente = new Promise<void>(resolve => { liberarLogin = resolve; });
  await page.route("**/api/v1/auth/login", async route => {
    await loginPendente;
    await route.fulfill({ status: 422, json: { mensagem: "Credenciais inválidas." } });
  });
  await page.goto("/");
  await page.getByLabel("Login").fill("admin");
  await page.getByLabel("Senha").fill("senha-incorreta");
  await page.getByRole("button", { name: "Entrar no SICC" }).click();

  const botao = page.getByRole("button", { name: "Entrando…" });
  await expect(botao).toBeDisabled();
  liberarLogin();

  await expect(page.getByText("Credenciais inválidas.")).toBeVisible();
  await expect(page.getByRole("button", { name: "Entrar no SICC" })).toBeEnabled();
});

test("troca obrigatória informa carregamento e mantém o usuário na tela quando falha", async ({ page }) => {
  await page.route("**/api/v1/public/processos*", route => route.fulfill({ json: emptyPage }));
  await page.route("**/api/v1/auth/login", route => route.fulfill({ json: {
    token: "temporario", perfil: "ADMINISTRADOR_DIPAC", trocaSenhaObrigatoria: true
  }}));
  let liberarTroca!: () => void;
  const trocaPendente = new Promise<void>(resolve => { liberarTroca = resolve; });
  await page.route("**/api/v1/auth/senha", async route => {
    await trocaPendente;
    await route.fulfill({ status: 422, json: { mensagem: "Senha atual inválida." } });
  });
  await page.goto("/");
  await page.getByLabel("Login").fill("admin");
  await page.getByLabel("Senha").fill("Temporaria123!");
  await page.getByRole("button", { name: "Entrar no SICC" }).click();
  await page.getByLabel("Senha temporária").fill("Incorreta123!");
  await page.getByLabel("Nova senha").fill("Permanente123!");
  await page.getByRole("button", { name: "Definir senha" }).click();

  const botao = page.getByRole("button", { name: "Salvando…" });
  await expect(botao).toBeDisabled();
  liberarTroca();

  await expect(page.getByText("Senha atual inválida.")).toBeVisible();
  await expect(page.getByRole("heading", { name: "Crie sua senha permanente" })).toBeVisible();
  await expect(page.getByRole("button", { name: "Definir senha" })).toBeEnabled();
});

test("perfil operador não recebe a navegação administrativa", async ({ page }) => {
  await session(page, "OPERADOR_DIPAC");
  await page.goto("/");
  await expect(page.getByRole("button", { name: "Administração" })).toHaveCount(0);
  await expect(page.getByRole("button", { name: "Processos Administrativos" })).toBeVisible();
});

test("administrador consulta o detalhe seguro de um Usuário Interno", async ({ page }) => {
  await session(page);
  const operador = {
    id: 2, nome: "Operador DIPAC", email: "operador@ufgd.edu.br", login: "operador",
    perfil: "OPERADOR_DIPAC", ativo: true, senhaTemporaria: true
  };
  await page.route("**/api/v1/admin/usuarios**", route => {
    const pathname = new URL(route.request().url()).pathname;
    return route.fulfill({ json: pathname.endsWith("/2") ? operador : [operador] });
  });
  await page.route("**/api/v1/admin/setores", route => route.fulfill({ json: [] }));

  await page.goto("/");
  await page.getByRole("button", { name: "Administração" }).click();
  await page.getByRole("button", { name: "Ver detalhes de Operador DIPAC" }).click();

  const detalhe = page.locator(".user-detail");
  await expect(detalhe.getByRole("heading", { name: "Detalhes do Usuário Interno" })).toBeVisible();
  await expect(detalhe.getByText("operador@ufgd.edu.br")).toBeVisible();
  await expect(detalhe.getByText("@operador")).toBeVisible();
  await expect(detalhe.getByText("TROCA DE SENHA OBRIGATÓRIA")).toBeVisible();
  await expect(page.getByText("senhaHash")).toHaveCount(0);
});

test("administrador executa as operações de gestão de Usuários Internos", async ({ page }) => {
  await session(page);
  const chamadas: string[] = [];
  const operador = {
    id: 2, nome: "Operador DIPAC", email: "operador@ufgd.edu.br", login: "operador",
    perfil: "OPERADOR_DIPAC", ativo: true, senhaTemporaria: true
  };
  await page.route("**/api/v1/admin/usuarios**", async route => {
    const requisicao = route.request();
    const url = new URL(requisicao.url());
    if (requisicao.method() === "GET") {
      return route.fulfill({ json: url.pathname.endsWith("/2") ? operador : [operador] });
    }
    chamadas.push(`${requisicao.method()} ${url.pathname}${url.search}`);
    if (requisicao.method() === "POST") {
      expect(await requisicao.postDataJSON()).toMatchObject({
        login: "novo.operador", perfil: "OPERADOR_DIPAC"
      });
      return route.fulfill({ status: 201, json: { ...operador, id: 3, login: "novo.operador" } });
    }
    if (url.pathname.endsWith("/perfil")) operador.perfil = url.searchParams.get("perfil")!;
    if (url.pathname.endsWith("/ativo")) operador.ativo = url.searchParams.get("ativo") === "true";
    return route.fulfill({ json: operador });
  });
  await page.route("**/api/v1/admin/setores", route => route.fulfill({ json: [] }));

  await page.goto("/");
  await page.getByRole("button", { name: "Administração" }).click();
  const painel = page.locator("section.panel").first();
  await painel.getByLabel("Nome").fill("Novo Operador");
  await painel.getByLabel("E-mail").fill("novo.operador@ufgd.edu.br");
  await painel.getByLabel("Login imutável").fill("novo.operador");
  await painel.getByLabel("Senha temporária", { exact: true }).fill("Operador123!");
  await painel.getByRole("button", { name: "Criar usuário" }).click();
  await expect(page.locator(".toast")).toHaveText("Usuário criado com senha temporária.");
  await page.locator(".toast").click();
  await painel.getByLabel("Usuário ID").fill("2");
  await painel.getByLabel("Nova senha temporária").fill("Operador456!");
  await painel.getByRole("button", { name: "Redefinir" }).click();
  await expect(page.locator(".toast")).toContainText("Senha temporária redefinida");
  await page.locator(".toast").click();

  const linha = painel.locator("article.doc").filter({ hasText: "Operador DIPAC" });
  await linha.getByLabel("Perfil").selectOption("ADMINISTRADOR_DIPAC");
  await expect(page.locator(".toast")).toHaveText("Perfil de acesso atualizado.");
  await page.locator(".toast").click();
  await linha.getByRole("button", { name: "Desativar" }).click();

  await expect.poll(() => chamadas).toEqual(expect.arrayContaining([
    "POST /api/v1/admin/usuarios",
    "PATCH /api/v1/admin/usuarios/2/senha",
    "PATCH /api/v1/admin/usuarios/2/perfil?perfil=ADMINISTRADOR_DIPAC",
    "PATCH /api/v1/admin/usuarios/2/ativo?ativo=false"
  ]));
  await expect(linha.getByRole("button", { name: "Reativar" })).toBeVisible();
});

test("Processo Administrativo é cadastrado, movimentado e formalizado", async ({ page }) => {
  await session(page);
  let criado = false;
  let formalizado = false;
  const processo = {
    id: 8, numero: "23005.000008/2026-10", origem: "DIPAC", numeroProjeto: "P-008",
    status: "EM_FORMALIZACAO", ativo: true
  };
  const instrumento = {
    id: 21, numero: "CV-008/2026", tipo: "CONVENIO", coordenador: "Maria Silva",
    valorAtual: 50000, vigenciaContratualFinal: "2027-07-27", vigenciaTedFinal: null
  };
  await page.route("**/api/v1/setores", route => route.fulfill({ json: [
    { id: 3, sigla: "DIPAC", nome: "Divisão de Parcerias", ativo: true }
  ] }));
  await page.route("**/api/v1/processos?*", route => route.fulfill({ json: {
    ...emptyPage, content: criado ? [{ ...processo, ...(formalizado ? { instrumento } : {}) }] : [],
    totalElements: criado ? 1 : 0
  }}));
  await page.route("**/api/v1/processos", async route => {
    if (route.request().method() !== "POST") return route.fallback();
    criado = true;
    expect(await route.request().postDataJSON()).toEqual({
      numero: processo.numero, origem: "DIPAC", numeroProjeto: "P-008"
    });
    await route.fulfill({ status: 201, json: processo });
  });
  await page.route("**/api/v1/movimentacoes", async route => {
    expect(await route.request().postDataJSON()).toMatchObject({
      contextoTipo: "FORMALIZACAO", contextoId: 8, setorDestinoId: 3
    });
    await route.fulfill({ status: 201, json: { id: 1, sequenciaDiaria: 1 } });
  });
  await page.route("**/api/v1/processos/8/instrumento", async route => {
    const body = await route.request().postDataJSON();
    expect(body).toMatchObject({
      numero: "CV-008/2026", tipo: "CONVENIO", documentoAssinadoId: 55
    });
    formalizado = true;
    await route.fulfill({ status: 201, json: instrumento });
  });
  await page.goto("/");
  await page.getByRole("button", { name: "Processos Administrativos" }).click();
  await page.getByLabel("Número", { exact: true }).fill(processo.numero);
  await page.getByLabel("Origem", { exact: true }).fill("DIPAC");
  await page.getByLabel("Número do projeto").fill("P-008");
  await page.getByRole("button", { name: "Cadastrar processo" }).click();
  await page.getByRole("button", { name: new RegExp(processo.numero) }).click();
  await expect(page.getByRole("heading", { name: "Formalizar Instrumento Contratual" })).toBeVisible();
  const movementForm = page.locator("form").filter({ has: page.getByLabel("Destino") });
  await movementForm.getByLabel("Destino").selectOption("3");
  await movementForm.getByLabel("Observação").fill("Entrada na DIPAC");
  await movementForm.getByRole("button", { name: "Registrar" }).click();
  await expect(page.getByText("Movimentação registrada sem alterar o histórico.")).toBeVisible();
  const formalizationForm = page.locator("form").filter({ has: page.getByLabel("Partícipes, um por linha") });
  await formalizationForm.getByLabel("Número").fill("CV-008/2026");
  await formalizationForm.getByLabel("Tipo").selectOption("CONVENIO");
  await formalizationForm.getByLabel("Objeto").fill("Projeto institucional");
  await formalizationForm.getByLabel("Natureza").fill("Administrativa");
  await formalizationForm.getByLabel("Coordenador").fill("Maria Silva");
  await formalizationForm.getByLabel("Partícipes, um por linha").fill("UFGD\nFundação");
  await formalizationForm.getByLabel("Valor").fill("50000");
  await formalizationForm.getByLabel("Vigência contratual").fill("2027-07-27");
  await formalizationForm.getByLabel("Data de formalização").fill("2026-07-27");
  await formalizationForm.getByLabel("Documento ID").fill("55");
  await formalizationForm.getByRole("button", { name: "Formalizar" }).click();
  await expect(page.getByText("Instrumento Contratual formalizado.")).toBeVisible();
});

test("documento recebe nova versão e alteração é efetivada", async ({ page }) => {
  await session(page);
  await page.route("**/api/v1/documentos?*", route => route.fulfill({ json: [] }));
  await page.route("**/api/v1/documentos/12/versoes", route => route.fulfill({ status: 201, json: {} }));
  await page.route("**/api/v1/alteracoes/31/efetivacao", async route => {
    expect(await route.request().postDataJSON()).toMatchObject({ ordemOficial: 2, documentoAssinadoId: 12 });
    await route.fulfill({ json: { id: 31, estado: "EFETIVADA" } });
  });
  await page.goto("/");
  await page.getByRole("button", { name: "Documentos" }).click();
  await page.getByLabel("Documento ID").fill("12");
  await page.locator('form').filter({ hasText: "Adicionar versão" }).getByLabel("Arquivo")
    .setInputFiles({ name: "versao.pdf", mimeType: "application/pdf", buffer: Buffer.from("%PDF-1.4") });
  await page.getByRole("button", { name: "Adicionar versão" }).click();
  await expect(page.getByText("Nova versão imutável armazenada.")).toBeVisible();
  await page.getByRole("button", { name: "Alterações contratuais" }).click();
  await page.getByLabel("Alteração ID").fill("31");
  await page.getByLabel("Data").fill("2026-07-27");
  await page.getByLabel("Ordem oficial").fill("2");
  await page.getByLabel("Documento ID").fill("12");
  await page.getByRole("button", { name: "Efetivar" }).click();
  await expect(page.getByText("Alteração efetivada e estado atual recomputado.")).toBeVisible();
});

test("relatório filtrado é gerado e mantido no histórico", async ({ page }) => {
  await session(page);
  let gerado = false;
  await page.route("**/api/v1/relatorios", async route => {
    if (route.request().method() === "POST") {
      const body = await route.request().postDataJSON();
      expect(body).toMatchObject({ tipo: "VIGENCIAS", formato: "CSV", filtros: { origem: "DIPAC" } });
      gerado = true;
      return route.fulfill({ status: 201, json: { id: 44 } });
    }
    return route.fulfill({ json: gerado ? [{
      id: 44, tipo: "VIGENCIAS", formato: "CSV", criadoEm: "2026-07-27T20:00:00"
    }] : [] });
  });
  await page.goto("/");
  await page.getByRole("button", { name: "Relatórios" }).click();
  await page.getByLabel("origem").fill("DIPAC");
  await page.locator(".report-actions").getByText("VIGENCIAS").locator("..")
    .getByRole("button", { name: "CSV" }).click();
  await expect(page.getByText("Relatório gerado e retido para download.")).toBeVisible();
  await expect(page.getByText("VIGENCIAS", { exact: true })).toHaveCount(2);
});
