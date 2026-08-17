import { expect, Page, test } from "@playwright/test";

const emptyPage = { content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 };
const dashboard = {
  processosPorStatus: {}, percentualConcluidos: 0, alertasContratuais: 0, alertasTed: 0,
  valorTotalVigente: 0, instrumentosPorTipo: {}, permanenciaMediaPorSetor: {},
  maiorGargalo: null, tempoMedioTramitacaoInicialDias: 0,
  formalizacoesMensais: {}, conclusoesMensais: {}
};

const portfolioDashboard = {
  processosPorStatus: { EM_FORMALIZACAO: 1, EM_VIGENCIA: 2, CONCLUIDO: 1 },
  percentualConcluidos: 25, alertasContratuais: 1, alertasTed: 1,
  valorTotalVigente: 4500,
  instrumentosPorTipo: {
    CONTRATO_GESTAO: 1, CONVENIO: 1, ACORDO_PARCERIA: 1,
    ACORDO_COOPERACAO_TECNICA: 0
  },
  permanenciaMediaPorSetor: { DIPAC: 5, PROAP: 3 }, maiorGargalo: "DIPAC",
  detalhesPermanenciaPorSetor: {
    DIPAC: [{
      processoId: 17, numeroProcesso: "PROC-FORMALIZADO-017",
      dataChegada: "2026-07-29", dataSaida: "2026-08-03", diasCorridos: 5, aberta: false
    }],
    PROAP: [{
      processoId: 17, numeroProcesso: "PROC-FORMALIZADO-017",
      dataChegada: "2026-08-03", dataSaida: null, diasCorridos: 3, aberta: true
    }]
  },
  tempoMedioTramitacaoInicialDias: 5.5,
  detalhesTempoTramitacaoInicial: [{
    processoId: 18, numeroProcesso: "PROC-ABERTO-017", dataCadastro: "2026-08-02",
    dataFormalizacao: null, diasCorridos: 6, aberta: true
  }, {
    processoId: 17, numeroProcesso: "PROC-FORMALIZADO-017", dataCadastro: "2026-07-29",
    dataFormalizacao: "2026-08-03", diasCorridos: 5, aberta: false
  }],
  formalizacoesMensais: { "2026-05": 1, "2026-06": 1, "2026-07": 1 },
  conclusoesMensais: { "2026-07": 1 }
};

async function session(page: Page, perfil = "ADMINISTRADOR_DIPAC", mockDashboard = true) {
  await page.addInitScript(value => localStorage.setItem("sicc-session", JSON.stringify(value)), {
    token: "jwt-de-teste", perfil, trocaSenhaObrigatoria: false
  });
  if (mockDashboard) {
    await page.route("**/api/v1/dashboard", route => route.fulfill({ json: dashboard }));
  }
}

test("painel mostra o portfolio consolidado e aplica filtros coerentes", async ({ page }) => {
  await session(page, "ADMINISTRADOR_DIPAC", false);
  let liberarResposta!: () => void;
  const respostaPendente = new Promise<void>(resolve => { liberarResposta = resolve; });
  await page.route("**/api/v1/dashboard*", async route => {
    await respostaPendente;
    await route.fulfill({ json: portfolioDashboard });
  });

  await page.goto("/");
  await expect(page.getByText("Carregando painel…")).toBeVisible();
  liberarResposta();

  await expect(page.getByText("R$\u00a04.500,00")).toBeVisible();
  await expect(page.getByRole("heading", { name: "Instrumentos por tipo" })).toBeVisible();
  await expect(page.getByText("Acordo de cooperação técnica")).toBeVisible();
  await expect(page.getByRole("heading", { name: "Atividade mensal" })).toBeVisible();
  await expect(page.getByText("mai. de 2026")).toBeVisible();
  await expect(page.getByText("jul. de 2026")).toBeVisible();

  await page.getByLabel("Origem do processo").fill("DIPAC");
  await page.getByLabel("Tipo do instrumento").selectOption("CONVENIO");
  await page.getByLabel("Status do processo").selectOption("EM_VIGENCIA");
  const consultaFiltrada = page.waitForRequest(request =>
    request.url().includes("/api/v1/dashboard?origem=DIPAC"));
  await page.getByRole("button", { name: "Aplicar filtros" }).click();
  const url = new URL((await consultaFiltrada).url());
  expect(Object.fromEntries(url.searchParams)).toEqual({
    origem: "DIPAC", tipo: "CONVENIO", status: "EM_VIGENCIA"
  });
});

test("painel permite repetir a consulta com erro e distingue portfolio vazio", async ({ page }) => {
  await session(page, "ADMINISTRADOR_DIPAC", false);
  let consultas = 0;
  await page.route("**/api/v1/dashboard*", route => {
    consultas += 1;
    return consultas === 1
      ? route.fulfill({ status: 500, json: { mensagem: "Não foi possível carregar o painel." } })
      : route.fulfill({ json: dashboard });
  });

  await page.goto("/");
  await expect(page.getByText("Não foi possível carregar o painel.")).toBeVisible();
  await page.getByRole("button", { name: "Tentar novamente" }).click();
  await expect(page.getByText("Nenhum Processo Administrativo corresponde aos filtros.")).toBeVisible();
});

test("painel abre os processos que compõem as métricas de tramitação", async ({ page }) => {
  await session(page, "ADMINISTRADOR_DIPAC", false);
  await page.route("**/api/v1/dashboard*", route => route.fulfill({ json: portfolioDashboard }));

  await page.goto("/");
  await page.getByRole("button", { name: "DIPAC · média de 5,0 dias" }).click();
  await expect(page.getByRole("heading", { name: "Permanências em DIPAC" })).toBeVisible();
  await expect(page.getByText("PROC-FORMALIZADO-017")).toBeVisible();
  await expect(page.getByText("29/07/2026 a 03/08/2026")).toBeVisible();

  await page.getByRole("button", { name: "Tempo inicial médio · 5,5 dias" }).click();
  await expect(page.getByRole("heading", { name: "Tempo de Tramitação Inicial" })).toBeVisible();
  await expect(page.getByText("PROC-ABERTO-017")).toBeVisible();
  await expect(page.getByText("Aberto · 6 dias")).toBeVisible();
  await expect(page.getByText("Formalizado · 5 dias")).toBeVisible();
});

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
  await expect(page.getByText("Maria Silva")).toBeVisible();
  await expect(page.getByText("2027-12-31")).toBeVisible();
  await expect(page.getByText("2027-06-30")).toBeVisible();
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
  await expect(page.getByText("documentoAssinadoId")).toHaveCount(0);
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
  await expect(page.getByRole("button", { name: "Registros de Auditoria" })).toHaveCount(0);
  await expect(page.getByRole("button", { name: "Processos Administrativos" })).toBeVisible();
});

test("administrador consulta e filtra os registros de auditoria paginados", async ({ page }) => {
  await session(page);
  let liberarConsulta!: () => void;
  const consultaPendente = new Promise<void>(resolve => { liberarConsulta = resolve; });
  await page.route("**/api/v1/auditoria?*", async route => {
    await consultaPendente;
    await route.fulfill({ json: {
      content: [{
        id: 18, acao: "LOGIN", resultado: "FALHA", ator: null,
        objeto: { tipo: "USUARIO_INTERNO", id: null },
        detalhes: "Credenciais inválidas.", ipOrigem: "127.0.0.1",
        criadoEm: "2026-07-30T14:30:00"
      }],
      totalElements: 21, totalPages: 2, number: 0, size: 20
    } });
  });

  await page.goto("/");
  await page.getByRole("button", { name: "Registros de Auditoria" }).click();
  await expect(page.getByText("Carregando registros de auditoria…")).toBeVisible();
  liberarConsulta();

  await expect(page.getByRole("cell", { name: "LOGIN" })).toBeVisible();
  await expect(page.getByText("Ator não identificado")).toBeVisible();
  await expect(page.getByText("USUARIO INTERNO")).toBeVisible();
  await expect(page.getByText("Página 1 de 2")).toBeVisible();

  await page.getByLabel("Ação").fill("LOGIN");
  await page.getByLabel("Resultado").selectOption("FALHA");
  await page.getByLabel("Usuário").fill("admin");
  await page.getByLabel("Data inicial").fill("2026-07-01");
  await page.getByLabel("Data final").fill("2026-07-30");
  const filtrada = page.waitForRequest(request => {
    const url = new URL(request.url());
    return url.pathname === "/api/v1/auditoria" && url.searchParams.get("acao") === "LOGIN";
  });
  await page.getByRole("button", { name: "Aplicar filtros" }).click();
  const parametros = new URL((await filtrada).url()).searchParams;
  expect(Object.fromEntries(parametros)).toEqual({
    page: "0", size: "20", acao: "LOGIN", resultado: "FALHA", usuario: "admin",
    dataInicial: "2026-07-01", dataFinal: "2026-07-30"
  });

  const proxima = page.waitForRequest(request =>
    new URL(request.url()).searchParams.get("page") === "1");
  await page.getByRole("button", { name: "Próxima página" }).click();
  await proxima;
});

test("registros de auditoria apresentam o estado vazio", async ({ page }) => {
  await session(page);
  await page.route("**/api/v1/auditoria?*", route => route.fulfill({ json: emptyPage }));

  await page.goto("/");
  await page.getByRole("button", { name: "Registros de Auditoria" }).click();

  await expect(page.getByText("Nenhum registro de auditoria encontrado.")).toBeVisible();
  await expect(page.getByText("Altere os filtros para ampliar a consulta.")).toBeVisible();
});

test("registros de auditoria apresentam o erro e permitem tentar novamente", async ({ page }) => {
  await session(page);
  await page.route("**/api/v1/auditoria?*", route => route.fulfill({
    status: 503, json: { mensagem: "Serviço de auditoria indisponível." }
  }));

  await page.goto("/");
  await page.getByRole("button", { name: "Registros de Auditoria" }).click();

  await expect(page.getByRole("alert")).toContainText("Não foi possível carregar os registros de auditoria.");
  await expect(page.getByRole("alert")).toContainText("Serviço de auditoria indisponível.");
  await expect(page.getByRole("button", { name: "Tentar novamente" })).toBeVisible();
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

test("administrador cadastra, edita e diferencia setores ativos e inativos", async ({ page }) => {
  await session(page);
  const chamadas: string[] = [];
  let setores = [
    { id: 1, sigla: "DIPAC", nome: "Divisão de Parcerias e Convênios", ativo: true },
    { id: 2, sigla: "PRAD", nome: "Pró-Reitoria de Administração", ativo: false }
  ];
  await page.route("**/api/v1/admin/usuarios", route => route.fulfill({ json: [] }));
  await page.route("**/api/v1/admin/setores**", async route => {
    const requisicao = route.request();
    const url = new URL(requisicao.url());
    if (requisicao.method() === "GET") return route.fulfill({ json: setores });
    chamadas.push(`${requisicao.method()} ${url.pathname}${url.search}`);
    if (requisicao.method() === "POST") {
      const body = await requisicao.postDataJSON();
      expect(body).toEqual({ sigla: "PROAP", nome: "Pró-Reitoria de Avaliação" });
      setores = [...setores, { id: 3, ...body, ativo: true }];
      return route.fulfill({ status: 201, json: setores[2] });
    }
    if (requisicao.method() === "PUT") {
      const body = await requisicao.postDataJSON();
      expect(body).toEqual({ sigla: "DIPRO", nome: "Diretoria de Projetos" });
      setores = setores.map(setor => setor.id === 1 ? { ...setor, ...body } : setor);
      return route.fulfill({ json: setores[0] });
    }
    setores = setores.map(setor => setor.id === 2 ? { ...setor, ativo: true } : setor);
    return route.fulfill({ json: setores[1] });
  });

  await page.goto("/");
  await page.getByRole("button", { name: "Administração" }).click();
  const painel = page.locator("section.panel").filter({ hasText: "Catálogo de setores" });
  await expect(painel.locator(".linha-setor.inativo")).toContainText("PRAD");
  await expect(painel.locator(".linha-setor.inativo").getByText("INATIVO")).toBeVisible();

  await painel.getByLabel("Sigla", { exact: true }).fill("PROAP");
  await painel.getByLabel("Nome", { exact: true }).fill("Pró-Reitoria de Avaliação");
  await painel.getByRole("button", { name: "Adicionar" }).click();
  await expect(painel.getByText("PROAP")).toBeVisible();

  await painel.getByRole("button", { name: "Editar setor DIPAC" }).click();
  await painel.getByLabel("Sigla do setor").fill("DIPRO");
  await painel.getByLabel("Nome do setor").fill("Diretoria de Projetos");
  await painel.getByRole("button", { name: "Salvar alterações" }).click();
  await expect(painel.getByText("DIPRO")).toBeVisible();

  await painel.locator(".linha-setor.inativo").getByRole("button", { name: "Reativar" }).click();
  await expect(painel.locator(".linha-setor.inativo")).toHaveCount(0);
  await expect.poll(() => chamadas).toEqual(expect.arrayContaining([
    "POST /api/v1/admin/setores",
    "PUT /api/v1/admin/setores/1",
    "PATCH /api/v1/admin/setores/2/ativo?ativo=true"
  ]));
});

test("Processo Administrativo é cadastrado, movimentado e formalizado", async ({ page }) => {
  await session(page);
  let criado = false;
  let formalizado = false;
  const movimentacoes = [{
    id: 10, contextoTipo: "FORMALIZACAO", contextoId: 8,
    dataMovimentacao: "2026-07-26", sequenciaDiaria: 1,
    setorDestino: { id: 3, sigla: "DIPAC", nome: "Divisão de Parcerias", ativo: true },
    autor: { id: 1, login: "admin", nome: "Administrador" },
    observacao: "Chegada inicial", inseridoEm: "2026-07-26T09:30:00"
  }];
  const processo = {
    id: 8, numero: "23005.000008/2026-10", origem: "DIPAC", numeroProjeto: "P-008",
    status: "EM_FORMALIZACAO", ativo: true
  };
  const instrumento = {
    id: 21, numero: "CV-008/2026", tipo: "CONVENIO", coordenador: "Maria Silva",
    valorAtual: 50000, vigenciaContratualFinal: "2027-07-27", vigenciaTedFinal: "2027-04-30",
    documentoAssinadoId: 55, situacaoContratual: "VALIDA", situacaoTed: "VALIDA"
  };
  await page.route("**/api/v1/setores", route => route.fulfill({ json: [
    { id: 3, sigla: "DIPAC", nome: "Divisão de Parcerias", ativo: true }
  ] }));
  await page.route("**/api/v1/processos/responsaveis", route => route.fulfill({ json: [] }));
  await page.route("**/api/v1/processos?*", route => route.fulfill({ json: {
    ...emptyPage, content: criado ? [{
      ...processo, ...(formalizado ? { status: "EM_VIGENCIA", instrumento } : {})
    }] : [],
    totalElements: criado ? 1 : 0
  }}));
  await page.route("**/api/v1/documentos?proprietarioTipo=PROCESSO&proprietarioId=8", route =>
    route.fulfill({ json: [{
      id: 55, titulo: "Instrumento assinado", categoria: "ASSINADO", ativo: true,
      criadoPor: { id: 1, nome: "Administrador" }, criadoEm: "2026-07-27T10:00:00",
      versoes: [{
        versao: 1, nomeArquivo: "instrumento.pdf", tipoMime: "application/pdf", tamanho: 18,
        checksumSha256: "a".repeat(64), criadoPor: { id: 1, nome: "Administrador" },
        criadoEm: "2026-07-27T10:00:00"
      }]
    }] })
  );
  await page.route("**/api/v1/processos/8", route => route.fulfill({ json: {
    ...processo, status: "EM_VIGENCIA", instrumento
  }}));
  await page.route("**/api/v1/processos/8/tramitacao", route => route.fulfill({ json: {
    setorAtual: { id: 3, sigla: "DIPAC", nome: "Divisão de Parcerias", ativo: true },
    movimentacoes,
    permanencias: [{
      setor: { id: 3, sigla: "DIPAC", nome: "Divisão de Parcerias", ativo: true },
      dataChegada: "2026-07-26", dataSaida: null, diasCorridos: 4, aberta: true
    }]
  }}));
  await page.route("**/api/v1/processos", async route => {
    if (route.request().method() !== "POST") return route.fallback();
    criado = true;
    expect(await route.request().postDataJSON()).toEqual({
      numero: processo.numero, origem: "DIPAC", numeroProjeto: "P-008", responsavelId: null
    });
    await route.fulfill({ status: 201, json: processo });
  });
  await page.route("**/api/v1/movimentacoes", async route => {
    const body = await route.request().postDataJSON();
    expect(body).toMatchObject({
      contextoTipo: "FORMALIZACAO", contextoId: 8, setorDestinoId: 3
    });
    movimentacoes.push({
      id: 11, contextoTipo: "FORMALIZACAO", contextoId: 8,
      dataMovimentacao: String(body.dataMovimentacao), sequenciaDiaria: 1,
      setorDestino: { id: 3, sigla: "DIPAC", nome: "Divisão de Parcerias", ativo: true },
      autor: { id: 1, login: "admin", nome: "Administrador" },
      observacao: String(body.observacao), inseridoEm: "2026-07-30T10:00:00"
    });
    await route.fulfill({ status: 201, json: movimentacoes.at(-1) });
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
  await expect(page.getByRole("heading", { name: "Linha do tempo" })).toBeVisible();
  await expect(page.getByText("Chegada inicial")).toBeVisible();
  await expect(page.getByText("4 dias no setor atual")).toBeVisible();
  const movementForm = page.locator("form").filter({ has: page.getByLabel("Destino") });
  await movementForm.getByLabel("Destino").selectOption("3");
  await movementForm.getByLabel("Observação").fill("Entrada na DIPAC");
  await movementForm.getByRole("button", { name: "Registrar" }).click();
  await expect(page.getByText("Movimentação registrada sem alterar o histórico.")).toBeVisible();
  await expect(page.getByText("Entrada na DIPAC")).toBeVisible();
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
  await formalizationForm.getByLabel("Documento assinado PDF").selectOption("55");
  await formalizationForm.getByRole("button", { name: "Formalizar" }).click();
  await expect(page.getByText("Instrumento Contratual formalizado.")).toBeVisible();
  await expect(page.getByRole("heading", { name: "Instrumento Contratual · CV-008/2026" })).toBeVisible();
  const resumo = page.locator("section.panel").filter({ hasText: "Instrumento Contratual · CV-008/2026" });
  await expect(resumo).toContainText("CONVENIO");
  await expect(resumo).toContainText("Maria Silva");
  await expect(resumo).toContainText("2027-07-27");
  await expect(resumo).toContainText("2027-04-30");
  await expect(resumo).toContainText("Documento assinado #55");
});

test("operador escolhe um responsável DIPAC ativo ao cadastrar processo", async ({ page }) => {
  await session(page, "OPERADOR_DIPAC");
  let processoCriado = false;
  await page.route("**/api/v1/setores", route => route.fulfill({ json: [] }));
  await page.route("**/api/v1/processos/responsaveis", route => route.fulfill({ json: [
    { id: 1, nome: "Administrador DIPAC", perfil: "ADMINISTRADOR_DIPAC" },
    { id: 2, nome: "Operador DIPAC", perfil: "OPERADOR_DIPAC" }
  ] }));
  await page.route("**/api/v1/processos?*", route => route.fulfill({ json: {
    ...emptyPage,
    content: processoCriado ? [{
      id: 6,
      numero: "23005.000006/2026-10",
      origem: "DIPAC",
      numeroProjeto: "P-006",
      status: "EM_FORMALIZACAO",
      ativo: true,
      responsavel: { id: 2, nome: "Operador DIPAC", perfil: "OPERADOR_DIPAC" }
    }] : [],
    totalElements: processoCriado ? 1 : 0
  }}));
  await page.route("**/api/v1/processos", async route => {
    if (route.request().method() !== "POST") return route.fallback();
    expect(await route.request().postDataJSON()).toEqual({
      numero: "23005.000006/2026-10",
      origem: "DIPAC",
      numeroProjeto: "P-006",
      responsavelId: 2
    });
    processoCriado = true;
    return route.fulfill({ status: 201, json: {} });
  });

  await page.goto("/");
  await page.getByRole("button", { name: "Processos Administrativos" }).click();
  const cadastro = page.locator("form").filter({ has: page.getByRole("button", { name: "Cadastrar processo" }) });
  await cadastro.getByLabel("Número", { exact: true }).fill("23005.000006/2026-10");
  await cadastro.getByLabel("Origem", { exact: true }).fill("DIPAC");
  await cadastro.getByLabel("Número do projeto").fill("P-006");
  await cadastro.getByLabel("Responsável DIPAC").selectOption("2");
  await cadastro.getByRole("button", { name: "Cadastrar processo" }).click();

  await expect(page.getByText("Processo Administrativo criado.")).toBeVisible();
  await expect(page.getByText("23005.000006/2026-10")).toBeVisible();
});

test("documento recebe nova versão antes de se tornar evidência oficial", async ({ page }) => {
  await session(page);
  await page.route("**/api/v1/documentos?*", route => route.fulfill({ json: [] }));
  await page.route("**/api/v1/documentos/12/versoes", route => route.fulfill({ status: 201, json: {} }));
  await page.goto("/");
  await page.getByRole("button", { name: "Documentos" }).click();
  await page.getByLabel("Documento ID").fill("12");
  await page.locator('form').filter({ hasText: "Adicionar versão" }).getByLabel("Arquivo")
    .setInputFiles({ name: "versao.pdf", mimeType: "application/pdf", buffer: Buffer.from("%PDF-1.4") });
  await page.getByRole("button", { name: "Adicionar versão" }).click();
  await expect(page.getByText("Nova versão imutável armazenada.")).toBeVisible();
});

test("operador prepara, edita e tramita Termo Aditivo em uma única interface", async ({ page }) => {
  await session(page, "OPERADOR_DIPAC");
  const instrumento = {
    id: 77, numero: "CV-012/2026", tipo: "CONVENIO", objeto: "Cooperação institucional",
    descricao: null, natureza: "Administrativa", coordenador: "Maria Silva",
    participes: ["UFGD", "Fundação"], valorAtual: 150000,
    vigenciaContratualFinal: "2027-08-01", vigenciaTedFinal: "2027-04-30",
    documentoAssinadoId: 55, situacaoContratual: "VALIDA", situacaoTed: "VALIDA"
  };
  const setores = [
    { id: 1, sigla: "DIPAC", nome: "Divisão de Parcerias", ativo: true },
    { id: 2, sigla: "PROAP", nome: "Pró-Reitoria de Administração", ativo: true }
  ];
  let termos: any[] = [];
  await page.route("**/api/v1/processos?*", route => route.fulfill({ json: {
    ...emptyPage, totalElements: 1, totalPages: 1, content: [{
      id: 12, numero: "PROC-TA-012", origem: "DIPAC", status: "EM_VIGENCIA",
      ativo: true, instrumento
    }]
  } }));
  await page.route("**/api/v1/setores", route => route.fulfill({ json: setores }));
  await page.route("**/api/v1/alteracoes?*", route => route.fulfill({ json: termos }));
  await page.route("**/api/v1/documentos?*", route => route.fulfill({ json: [] }));
  await page.route("**/api/v1/alteracoes/31", async route => {
    if (route.request().method() === "PUT") {
      const payload = await route.request().postDataJSON();
      expect(payload).toEqual({
        numeroOficial: "TA-01/2026 revisado",
        mudancas: [
          { campo: "COORDENADOR", valorAnterior: "Maria Silva", valorNovo: "Ana Souza" },
          { campo: "DESCRICAO", valorAnterior: null, valorNovo: "Descrição incluída" }
        ]
      });
      termos[0] = { ...termos[0], numeroOficial: payload.numeroOficial, mudancas: payload.mudancas };
      return route.fulfill({ json: termos[0] });
    }
    return route.fulfill({ json: termos[0] });
  });
  await page.route("**/api/v1/alteracoes", async route => {
    if (route.request().method() !== "POST") return route.fallback();
    const payload = await route.request().postDataJSON();
    expect(payload).toEqual({
      instrumentoId: 77,
      tipo: "TERMO_ADITIVO",
      numeroOficial: "TA-01/2026",
      operacao: "ORIGINAL",
      referenciaId: null,
      mudancas: [
        { campo: "VALOR_ATUAL", valorAnterior: "150000.00", valorNovo: "175000.00" },
        { campo: "DESCRICAO", valorAnterior: null, valorNovo: "Descrição incluída" }
      ]
    });
    termos = [{
      id: 31, instrumentoId: 77, tipo: "TERMO_ADITIVO", estado: "RASCUNHO",
      numeroOficial: payload.numeroOficial, operacao: "ORIGINAL", referenciaId: null,
      mudancas: payload.mudancas,
      estadoAtualInstrumento: {
        objeto: instrumento.objeto, descricao: instrumento.descricao, natureza: instrumento.natureza,
        coordenador: instrumento.coordenador, participes: instrumento.participes,
        valorAtual: instrumento.valorAtual, vigenciaContratualFinal: instrumento.vigenciaContratualFinal,
        vigenciaTedFinal: instrumento.vigenciaTedFinal, statusProcesso: "EM_VIGENCIA",
        precedenciaPorCampo: {}
      },
      tramitacao: { setorAtual: null, movimentacoes: [], permanencias: [] }
    }];
    return route.fulfill({ status: 201, json: termos[0] });
  });
  await page.route("**/api/v1/movimentacoes", async route => {
    const payload = await route.request().postDataJSON();
    expect(payload).toMatchObject({
      contextoTipo: "TERMO_ADITIVO", contextoId: 31, setorDestinoId: 2,
      dataMovimentacao: "2026-08-07", observacao: "Análise jurídica"
    });
    termos[0].tramitacao = {
      setorAtual: setores[1], permanencias: [], movimentacoes: [{
        id: 91, contextoTipo: "TERMO_ADITIVO", contextoId: 31,
        dataMovimentacao: "2026-08-07", sequenciaDiaria: 1,
        setorDestino: setores[1], autor: { id: 2, login: "operador", nome: "Operador DIPAC" },
        observacao: "Análise jurídica", inseridoEm: "2026-08-07T12:00:00"
      }]
    };
    return route.fulfill({ status: 201, json: termos[0].tramitacao.movimentacoes[0] });
  });

  await page.goto("/");
  await page.getByRole("button", { name: "Alterações contratuais" }).click();
  await page.getByLabel("Instrumento Contratual").selectOption("77");
  await page.getByLabel("Identificação do termo").fill("TA-01/2026");
  await page.getByLabel("Campo da mudança").selectOption("VALOR_ATUAL");
  await expect(page.getByLabel("Valor anterior")).toHaveValue("150000.00");
  await page.getByLabel("Novo valor").fill("175000.00");
  await page.getByRole("button", { name: "Adicionar mudança", exact: true }).click();
  await page.getByLabel("Campo da mudança 2").selectOption("DESCRICAO");
  await expect(page.getByLabel("Valor anterior 2")).toHaveValue("Não informado");
  await page.getByLabel("Novo valor 2").fill("Descrição incluída");
  await page.getByRole("button", { name: "Criar rascunho" }).click();

  await page.getByRole("button", { name: /TA-01\/2026/ }).click();
  await expect(page.getByRole("heading", { name: "Mudanças do rascunho" })).toBeVisible();
  await page.getByLabel("Identificação do rascunho").fill("TA-01/2026 revisado");
  await page.getByLabel("Campo editado").selectOption("COORDENADOR");
  await page.getByLabel("Novo valor editado").fill("Ana Souza");
  await page.getByRole("button", { name: "Salvar rascunho" }).click();

  await page.getByLabel("Data da movimentação").fill("2026-08-07");
  await page.getByLabel("Setor de destino").selectOption("2");
  await page.getByLabel("Observação da movimentação").fill("Análise jurídica");
  await page.getByRole("button", { name: "Registrar movimentação" }).click();
  await expect(page.getByText("PROAP", { exact: true })).toBeVisible();
  await expect(page.getByText("Análise jurídica")).toBeVisible();
});

test("operador confere efeitos e vê o estado resultante ao efetivar Termo Aditivo", async ({ page }) => {
  await session(page, "OPERADOR_DIPAC");
  const instrumento = {
    id: 77, numero: "CV-013/2026", tipo: "CONVENIO", objeto: "Cooperação institucional",
    descricao: null, natureza: "Administrativa", coordenador: "Maria Silva",
    participes: ["UFGD", "Fundação"], valorAtual: 180000,
    vigenciaContratualFinal: "2027-08-01", vigenciaTedFinal: "2027-04-30",
    documentoAssinadoId: 55, situacaoContratual: "VALIDA", situacaoTed: "VALIDA"
  };
  let termo = {
    id: 31, instrumentoId: 77, tipo: "TERMO_ADITIVO", estado: "RASCUNHO",
    numeroOficial: "TA-013/2026", operacao: "ORIGINAL", referenciaId: null,
    documentoAssinadoId: null,
    mudancas: [
      { campo: "VALOR_ATUAL", valorAnterior: "150000.00", valorNovo: "175000.00" },
      { campo: "COORDENADOR", valorAnterior: "Maria Silva", valorNovo: "Ana Souza" }
    ],
    estadoAtualInstrumento: {
      objeto: instrumento.objeto, descricao: instrumento.descricao, natureza: instrumento.natureza,
      coordenador: instrumento.coordenador, participes: instrumento.participes,
      valorAtual: instrumento.valorAtual, vigenciaContratualFinal: instrumento.vigenciaContratualFinal,
      vigenciaTedFinal: instrumento.vigenciaTedFinal, statusProcesso: "EM_VIGENCIA",
      precedenciaPorCampo: { VALOR_ATUAL: { dataEfetivacao: "2026-08-08", ordemOficial: 3 } }
    },
    tramitacao: { setorAtual: null, movimentacoes: [], permanencias: [] }
  };
  const alteracaoPrevalente = {
    ...termo, id: 30, estado: "EFETIVADA", numeroOficial: "TA-012/2026",
    dataEfetivacao: "2026-08-08", ordemOficial: 3, documentoAssinadoId: 87,
    mudancas: [{ campo: "VALOR_ATUAL", valorAnterior: "150000.00", valorNovo: "180000.00" }]
  };
  const documento = {
    id: 88, titulo: "Termo Aditivo assinado", categoria: "ASSINADO", ativo: true,
    criadoPor: { id: 2, nome: "Operador DIPAC" }, criadoEm: "2026-08-08T10:00:00",
    versoes: [{
      versao: 1, nomeArquivo: "ta-013.pdf", tipoMime: "application/pdf", tamanho: 24,
      checksumSha256: "a".repeat(64), criadoPor: { id: 2, nome: "Operador DIPAC" },
      criadoEm: "2026-08-08T10:00:00"
    }]
  };
  await page.route("**/api/v1/processos?*", route => route.fulfill({ json: {
    ...emptyPage, totalElements: 1, totalPages: 1, content: [{
      id: 13, numero: "PROC-TA-013", origem: "DIPAC", status: "EM_VIGENCIA",
      ativo: true, instrumento
    }]
  } }));
  await page.route("**/api/v1/setores", route => route.fulfill({ json: [] }));
  await page.route("**/api/v1/alteracoes?*", route => route.fulfill({ json: [termo, alteracaoPrevalente] }));
  await page.route("**/api/v1/documentos?*", route => route.fulfill({ json: [documento] }));
  await page.route("**/api/v1/alteracoes/31/efetivacao", async route => {
    expect(await route.request().postDataJSON()).toEqual({
      dataEfetivacao: "2026-08-08", ordemOficial: 2, documentoAssinadoId: 88
    });
    termo = {
      ...termo, estado: "EFETIVADA", documentoAssinadoId: 88,
      estadoAtualInstrumento: {
        ...termo.estadoAtualInstrumento, valorAtual: 180000, coordenador: "Ana Souza"
      }
    };
    await route.fulfill({ json: termo });
  });

  await page.goto("/");
  await page.getByRole("button", { name: "Alterações contratuais" }).click();
  await page.getByLabel("Instrumento Contratual").selectOption("77");
  await page.getByRole("button", { name: /TA-013\/2026/ }).click();

  const previa = page.getByRole("region", { name: "Efeitos a confirmar" });
  await expect(previa).toContainText("Valor atual");
  await expect(previa).toContainText("R$ 180.000,00");
  await expect(previa).toContainText("R$ 175.000,00");
  await expect(previa).toContainText("Ana Souza");
  await page.getByLabel("Data de efetivação").fill("2026-08-08");
  await page.getByLabel("Ordem oficial").fill("2");
  const valorPrevisto = previa.locator(".efeito-alteracao").filter({ hasText: "Valor atual" });
  await expect(valorPrevisto).toContainText("Resultado previsto");
  await expect(valorPrevisto).toContainText("R$ 180.000,00");
  await expect(valorPrevisto).toContainText("Não prevalece sobre a alteração oficial mais recente");
  await page.getByLabel("PDF assinado").selectOption("88");
  await page.getByRole("button", { name: "Confirmar efetivação" }).click();

  const resultado = page.getByRole("region", { name: "Estado resultante" });
  await expect(resultado).toContainText("R$ 180.000,00");
  await expect(resultado).toContainText("Ana Souza");
  await expect(resultado).toContainText("EM VIGENCIA");
  await expect(page.getByRole("button", { name: "Registrar movimentação" })).toBeVisible();
 });

test("usuário interno envia, versiona, baixa histórico e desativa documento", async ({ page }) => {
  await session(page, "OPERADOR_DIPAC");
  let criado = false;
  let versionado = false;
  let desativado = false;
  const documento = () => ({
    id: 12,
    proprietarioTipo: "PROCESSO",
    proprietarioId: 8,
    categoria: "ADMINISTRATIVO",
    titulo: "Plano de trabalho",
    ativo: true,
    criadoPor: { id: 1, nome: "Administrador DIPAC" },
    criadoEm: "2026-07-29T09:00:00",
    versoes: [
      ...(versionado ? [{
        versao: 2,
        nomeArquivo: "plano-atual.csv",
        tipoMime: "text/csv",
        tamanho: 42,
        checksumSha256: "b".repeat(64),
        criadoPor: { id: 2, nome: "Operador DIPAC" },
        criadoEm: "2026-07-30T09:00:00"
      }] : []),
      {
        versao: 1,
        nomeArquivo: "plano-inicial.pdf",
        tipoMime: "application/pdf",
        tamanho: 30,
        checksumSha256: "a".repeat(64),
        criadoPor: { id: 1, nome: "Administrador DIPAC" },
        criadoEm: "2026-07-29T09:00:00"
      }
    ]
  });
  await page.route("**/api/v1/documentos?*", route =>
    route.fulfill({ json: criado && !desativado ? [documento()] : [] }));
  await page.route("**/api/v1/documentos", async route => {
    if (route.request().method() !== "POST") return route.fallback();
    expect(route.request().headers()["content-type"]).toContain("multipart/form-data");
    expect(route.request().postDataBuffer()?.toString()).toContain("plano-inicial.pdf");
    criado = true;
    return route.fulfill({ status: 201, json: documento() });
  });
  await page.route("**/api/v1/documentos/12/versoes", async route => {
    expect(route.request().method()).toBe("POST");
    expect(route.request().postDataBuffer()?.toString()).toContain("plano-atual.csv");
    versionado = true;
    return route.fulfill({ status: 201, json: documento() });
  });
  await page.route("**/api/v1/documentos/12", route => {
    expect(route.request().method()).toBe("DELETE");
    desativado = true;
    return route.fulfill({ json: { ...documento(), ativo: false } });
  });
  await page.route("**/api/v1/documentos/12/versoes/*/arquivo", route => route.fulfill({
    status: 200,
    contentType: "application/pdf",
    headers: { "Content-Disposition": 'attachment; filename="plano-inicial.pdf"' },
    body: "%PDF-1.4\n%%EOF"
  }));

  await page.goto("/");
  await page.getByRole("button", { name: "Documentos" }).click();
  const cadastro = page.locator("form").filter({
    has: page.getByRole("button", { name: "Armazenar versão 1" })
  });
  await expect(cadastro.getByRole("option", { name: "INSTRUMENTO" }))
    .toHaveAttribute("disabled", "");
  await cadastro.getByLabel("ID do proprietário").fill("8");
  await cadastro.getByLabel("Título").fill("Plano de trabalho");
  await cadastro.getByLabel("Arquivo").setInputFiles({
    name: "plano-inicial.pdf",
    mimeType: "application/pdf",
    buffer: Buffer.from("%PDF-1.4\n%%EOF")
  });
  await cadastro.getByRole("button", { name: "Armazenar versão 1" }).click();
  await expect(page.getByText("Documento e primeira versão armazenados.")).toBeVisible();

  const novaVersao = page.locator("form").filter({
    has: page.getByRole("button", { name: "Adicionar versão" })
  });
  await novaVersao.getByLabel("Documento ID").fill("12");
  await novaVersao.getByLabel("Arquivo").setInputFiles({
    name: "plano-atual.csv",
    mimeType: "text/csv",
    buffer: Buffer.from("numero,origem\nPA-001,DIPAC\n")
  });
  await novaVersao.getByRole("button", { name: "Adicionar versão" }).click();
  await expect(page.getByText("Nova versão imutável armazenada.")).toBeVisible();

  const card = page.locator("article.doc").filter({ hasText: "Plano de trabalho" });
  await expect(card.getByText("Versão 2 · plano-atual.csv")).toBeVisible();
  await expect(card.getByText("Enviada por Operador DIPAC")).toBeVisible();
  await expect(card.getByText("Versão 1 · plano-inicial.pdf")).toBeVisible();
  await expect(card.getByText("Enviada por Administrador DIPAC")).toBeVisible();
  const historica = page.waitForRequest(request =>
    new URL(request.url()).pathname === "/api/v1/documentos/12/versoes/1/arquivo");
  await card.getByRole("button", { name: "Baixar versão 1" }).click();
  await historica;
  await card.getByRole("button", { name: "Desativar" }).click();
  await expect(page.getByText(
    "Documento desativado; as versões históricas foram preservadas."
  )).toBeVisible();
  await expect(card).toHaveCount(0);
});

test("relatório filtrado é gerado e mantido no histórico", async ({ page }) => {
  await session(page);
  let gerado = false;
  await page.route("**/api/v1/relatorios/44/arquivo", route => route.fulfill({
    status: 200,
    body: "relatorio;Relatório de vigências",
    headers: {
      "content-type": "text/csv",
      "content-disposition": "attachment; filename=vigencias-44.csv"
    }
  }));
  await page.route("**/api/v1/relatorios", async route => {
    if (route.request().method() === "POST") {
      const body = await route.request().postDataJSON();
      expect(body).toMatchObject({
        tipo: "VIGENCIAS",
        formato: "CSV",
        filtros: {
          origem: "DIPAC",
          vigenciaContratual: "VALIDA",
          vigenciaTed: "PROXIMA_VENCIMENTO"
        }
      });
      gerado = true;
      return route.fulfill({ status: 201, json: { id: 44 } });
    }
    return route.fulfill({ json: gerado ? [{
      id: 44, tipo: "VIGENCIAS", formato: "CSV",
      filtros: {
        origem: "DIPAC",
        vigenciaContratual: "VALIDA",
        vigenciaTed: "PROXIMA_VENCIMENTO"
      },
      criadoPor: { id: 2, login: "operador", nome: "Operador DIPAC" },
      criadoEm: "2026-07-27T20:00:00",
      checksumSha256: "a".repeat(64),
      chaveArmazenamento: "relatorios/vigencias/arquivo-44",
      tamanhoBytes: 512,
      nomeArquivo: "vigencias-44.csv"
    }] : [] });
  });
  await page.goto("/");
  await page.getByRole("button", { name: "Relatórios" }).click();
  await page.getByLabel("origem").fill("DIPAC");
  await page.getByLabel("Vigência contratual").selectOption("VALIDA");
  await page.getByLabel("Vigência TED").selectOption("PROXIMA_VENCIMENTO");
  await page.locator(".report-actions").getByText("VIGENCIAS").locator("..")
    .getByRole("button", { name: "CSV" }).click();
  await expect(page.getByText("Relatório gerado e retido para download.")).toBeVisible();
  await expect(page.getByText("VIGENCIAS", { exact: true })).toHaveCount(2);
  const historico = page.locator("article.doc").filter({ hasText: "vigencias-44.csv" });
  await expect(historico).toContainText("Operador DIPAC (operador)");
  await expect(historico).toContainText("origem: DIPAC");
  await expect(historico).toContainText("vigenciaContratual: VALIDA");
  await expect(historico).toContainText("vigenciaTed: PROXIMA_VENCIMENTO");
  await expect(historico).toContainText("a".repeat(64));
  const requisicaoDownload = page.waitForRequest("**/api/v1/relatorios/44/arquivo");
  await historico.getByRole("button", { name: "Baixar" }).click();
  expect((await requisicaoDownload).headers().authorization).toBe("Bearer jwt-de-teste");
});

test("relatório do histórico de tramitações envia contexto e período sem filtros alheios", async ({ page }) => {
  await session(page);
  let gerado = false;
  await page.route("**/api/v1/relatorios", async route => {
    if (route.request().method() === "POST") {
      const body = await route.request().postDataJSON();
      expect(body).toEqual({
        tipo: "HISTORICO_TRAMITACOES",
        formato: "CSV",
        filtros: {
          numero: "PROC-REL-TRAM-019",
          contexto: "FORMALIZACAO",
          dataInicial: "2026-07-31",
          dataFinal: "2026-08-04"
        }
      });
      gerado = true;
      return route.fulfill({ status: 201, json: { id: 45 } });
    }
    return route.fulfill({ json: gerado ? [{
      id: 45, tipo: "HISTORICO_TRAMITACOES", formato: "CSV",
      filtros: {
        numero: "PROC-REL-TRAM-019",
        contexto: "FORMALIZACAO",
        dataInicial: "2026-07-31",
        dataFinal: "2026-08-04"
      },
      criadoPor: { id: 2, login: "operador", nome: "Operador DIPAC" },
      criadoEm: "2026-08-08T12:00:00",
      checksumSha256: "b".repeat(64),
      chaveArmazenamento: "relatorios/historico_tramitacoes/arquivo-45",
      tamanhoBytes: 640,
      nomeArquivo: "historico_tramitacoes-45.csv"
    }] : [] });
  });

  await page.goto("/");
  await page.getByRole("button", { name: "Relatórios" }).click();
  await page.getByLabel("numero").fill("PROC-REL-TRAM-019");
  await page.getByLabel("Contexto").selectOption("FORMALIZACAO");
  await page.getByLabel("dataInicial").fill("2026-07-31");
  await page.getByLabel("dataFinal").fill("2026-08-04");
  await page.locator(".report-actions").getByText("HISTORICO TRAMITACOES").locator("..")
    .getByRole("button", { name: "CSV" }).click();

  await expect(page.getByText("Relatório gerado e retido para download.")).toBeVisible();
  await expect(page.locator("article.doc").filter({ hasText: "historico_tramitacoes-45.csv" }))
    .toContainText("contexto: FORMALIZACAO");
});

test("notificação de chegada abre o Processo Administrativo e pode ser marcada como lida", async ({ page }) => {
  await session(page, "OPERADOR_DIPAC");
  let lida = false;
  const notificacaoDeChegada = {
    id: 81,
    tipo: "CHEGADA_TRAMITACAO",
    mensagem: "O Processo Administrativo PROC-NOT-008 chegou ao setor DIPAC.",
    processoId: 42,
    criadaEm: "2026-07-30T19:00:00"
  };
  await page.route("**/api/v1/notificacoes", route => route.fulfill({ json: [
    {
      ...notificacaoDeChegada,
      lida
    },
    {
      id: 82,
      tipo: "VIGENCIA_CONTRATUAL",
      mensagem: "A vigência contratual exige atenção.",
      processoId: null,
      lida: true,
      criadaEm: "2026-07-29T19:00:00"
    }
  ] }));
  await page.route("**/api/v1/notificacoes/81/lida", async route => {
    expect(route.request().method()).toBe("PATCH");
    lida = true;
    await route.fulfill({ json: {
      ...notificacaoDeChegada,
      lida: true,
    } });
  });
  await page.route("**/api/v1/processos?*", route => route.fulfill({ json: {
    ...emptyPage,
    content: []
  } }));
  await page.route("**/api/v1/notificacoes/81/processo", route => route.fulfill({ json: {
      id: 42,
      numero: "PROC-NOT-008",
      origem: "DIPAC",
      status: "EM_FORMALIZACAO",
      ativo: false,
      setorAtual: "DIPAC"
  } }));
  await page.route("**/api/v1/processos/42/tramitacao", route => route.fulfill({ json: {
    setorAtual: { id: 5, sigla: "DIPAC", nome: "Divisão de Parcerias e Convênios", ativo: true },
    movimentacoes: [],
    permanencias: []
  } }));
  await page.route("**/api/v1/setores", route => route.fulfill({ json: [] }));
  await page.route("**/api/v1/processos/responsaveis", route => route.fulfill({ json: [] }));

  await page.goto("/");
  await page.getByRole("button", { name: "Notificações" }).click();
  await expect(page.getByText("O Processo Administrativo PROC-NOT-008 chegou ao setor DIPAC.")).toBeVisible();

  await page.getByRole("button", { name: "Marcar como lida" }).click();
  await expect(page.getByText("Lida", { exact: true })).toHaveCount(2);

  await expect(page.getByRole("button", { name: "Ver Processo Administrativo" })).toHaveCount(1);
  await page.getByRole("button", { name: "Ver Processo Administrativo" }).click();
  await expect(page.getByRole("heading", {
    name: "Processo Administrativo · PROC-NOT-008"
  })).toBeVisible();
});

test("caixa de entrada separa alertas contratuais e da vigência do TED", async ({ page }) => {
  await session(page, "OPERADOR_DIPAC");
  await page.route("**/api/v1/notificacoes", route => route.fulfill({ json: [
    {
      id: 91,
      tipo: "ALERTA_VIGENCIA_CONTRATUAL",
      mensagem: "A vigência contratual do instrumento CV-120 vence em 120 dias.",
      processoId: 51,
      lida: false,
      criadaEm: "2026-08-01T01:05:00"
    },
    {
      id: 92,
      tipo: "ALERTA_VIGENCIA_TED",
      mensagem: "A vigência do TED do instrumento CV-120 vence em 120 dias.",
      processoId: 51,
      lida: false,
      criadaEm: "2026-08-01T01:05:00"
    },
    {
      id: 93,
      tipo: "CHEGADA_TRAMITACAO",
      mensagem: "O Processo Administrativo PROC-051 chegou ao setor DIPAC.",
      processoId: 51,
      lida: false,
      criadaEm: "2026-07-31T18:00:00"
    }
  ] }));

  await page.goto("/");
  await page.getByRole("button", { name: "Notificações" }).click();

  const contratuais = page.getByRole("region", { name: "Alertas de Vigência Contratual" });
  const ted = page.getByRole("region", { name: "Alertas de Vigência do TED" });
  const demais = page.getByRole("region", { name: "Outras Notificações Internas" });
  await expect(contratuais.getByText("vigência contratual do instrumento CV-120")).toBeVisible();
  await expect(ted.getByText("vigência do TED do instrumento CV-120")).toBeVisible();
  await expect(demais.getByText("PROC-051 chegou ao setor DIPAC")).toBeVisible();
});

test("operador prepara, tramita e efetiva Apostilamento em fluxo próprio", async ({ page }) => {
  await session(page, "OPERADOR_DIPAC");
  const instrumento = {
    id: 77, numero: "CV-014/2026", tipo: "CONVENIO", objeto: "Cooperação institucional",
    descricao: null, natureza: "Administrativa", coordenador: "Maria Silva",
    participes: ["UFGD", "Fundação"], valorAtual: 150000,
    vigenciaContratualFinal: "2027-08-01", vigenciaTedFinal: "2027-04-30",
    documentoAssinadoId: 55, situacaoContratual: "VALIDA", situacaoTed: "VALIDA"
  };
  const setores = [
    { id: 1, sigla: "DIPAC", nome: "Divisão de Parcerias", ativo: true },
    { id: 2, sigla: "PROAP", nome: "Pró-Reitoria de Administração", ativo: true }
  ];
  const documento = {
    id: 98, titulo: "Apostilamento assinado", categoria: "ASSINADO", ativo: true,
    criadoPor: { id: 2, nome: "Operador DIPAC" }, criadoEm: "2026-08-08T10:00:00",
    versoes: [{
      versao: 1, nomeArquivo: "apostilamento-014.pdf", tipoMime: "application/pdf", tamanho: 24,
      checksumSha256: "b".repeat(64), criadoPor: { id: 2, nome: "Operador DIPAC" },
      criadoEm: "2026-08-08T10:00:00"
    }]
  };
  let apostilamentos: any[] = [];
  await page.route("**/api/v1/processos?*", route => route.fulfill({ json: {
    ...emptyPage, totalElements: 1, totalPages: 1, content: [{
      id: 14, numero: "PROC-AP-014", origem: "DIPAC", status: "EM_VIGENCIA",
      ativo: true, instrumento
    }]
  } }));
  await page.route("**/api/v1/setores", route => route.fulfill({ json: setores }));
  await page.route("**/api/v1/alteracoes?*", route => route.fulfill({ json: apostilamentos }));
  await page.route("**/api/v1/documentos?*", route => {
    expect(route.request().url()).toContain("proprietarioTipo=APOSTILAMENTO");
    return route.fulfill({ json: [documento] });
  });
  await page.route("**/api/v1/alteracoes", async route => {
    if (route.request().method() !== "POST") return route.fallback();
    const payload = await route.request().postDataJSON();
    expect(payload).toEqual({
      instrumentoId: 77, tipo: "APOSTILAMENTO", numeroOficial: "AP-01/2026",
      operacao: "ORIGINAL", referenciaId: null,
      mudancas: [{ campo: "COORDENADOR", valorAnterior: "Maria Silva", valorNovo: "Ana Souza" }]
    });
    apostilamentos = [{
      id: 41, instrumentoId: 77, tipo: "APOSTILAMENTO", estado: "RASCUNHO",
      numeroOficial: payload.numeroOficial, operacao: "ORIGINAL", referenciaId: null,
      documentoAssinadoId: null, mudancas: payload.mudancas,
      estadoAtualInstrumento: {
        objeto: instrumento.objeto, descricao: instrumento.descricao, natureza: instrumento.natureza,
        coordenador: instrumento.coordenador, participes: instrumento.participes,
        valorAtual: instrumento.valorAtual, vigenciaContratualFinal: instrumento.vigenciaContratualFinal,
        vigenciaTedFinal: instrumento.vigenciaTedFinal, statusProcesso: "EM_VIGENCIA",
        precedenciaPorCampo: {}
      },
      tramitacao: { setorAtual: null, movimentacoes: [], permanencias: [] }
    }];
    return route.fulfill({ status: 201, json: apostilamentos[0] });
  });
  await page.route("**/api/v1/alteracoes/41", async route => {
    if (route.request().method() === "PUT") {
      const payload = await route.request().postDataJSON();
      expect(payload).toEqual({
        numeroOficial: "AP-01/2026 revisado",
        mudancas: [{ campo: "VIGENCIA_TED_FINAL", valorAnterior: "2027-04-30", valorNovo: "2027-10-31" }]
      });
      apostilamentos[0] = { ...apostilamentos[0], numeroOficial: payload.numeroOficial, mudancas: payload.mudancas };
    }
    return route.fulfill({ json: apostilamentos[0] });
  });
  await page.route("**/api/v1/movimentacoes", async route => {
    expect(await route.request().postDataJSON()).toMatchObject({
      contextoTipo: "APOSTILAMENTO", contextoId: 41, setorDestinoId: 2
    });
    apostilamentos[0].tramitacao = {
      setorAtual: setores[1], permanencias: [], movimentacoes: [{
        id: 101, contextoTipo: "APOSTILAMENTO", contextoId: 41,
        dataMovimentacao: "2026-08-08", sequenciaDiaria: 1, setorDestino: setores[1],
        autor: { id: 2, login: "operador", nome: "Operador DIPAC" },
        observacao: "Conferência", inseridoEm: "2026-08-08T12:00:00"
      }]
    };
    return route.fulfill({ status: 201, json: apostilamentos[0].tramitacao.movimentacoes[0] });
  });
  await page.route("**/api/v1/alteracoes/41/efetivacao", async route => {
    expect(await route.request().postDataJSON()).toEqual({
      dataEfetivacao: "2026-08-08", ordemOficial: 1, documentoAssinadoId: 98
    });
    apostilamentos[0] = {
      ...apostilamentos[0], estado: "EFETIVADA", dataEfetivacao: "2026-08-08",
      ordemOficial: 1, documentoAssinadoId: 98,
      estadoAtualInstrumento: {
        ...apostilamentos[0].estadoAtualInstrumento, vigenciaTedFinal: "2027-10-31"
      }
    };
    return route.fulfill({ json: apostilamentos[0] });
  });

  await page.goto("/");
  await page.getByRole("button", { name: "Alterações contratuais" }).click();
  await page.getByRole("button", { name: "Apostilamentos", exact: true }).click();
  await page.getByLabel("Instrumento Contratual").selectOption("77");
  await expect(page.getByLabel("Campo da mudança").locator("option")).toHaveCount(2);
  await expect(page.getByLabel("Campo da mudança").locator('option[value="OBJETO"]')).toHaveCount(0);
  await page.getByLabel("Identificação do apostilamento").fill("AP-01/2026");
  await page.getByLabel("Campo da mudança").selectOption("COORDENADOR");
  await page.getByLabel("Novo valor").fill("Ana Souza");
  await page.getByRole("button", { name: "Criar rascunho" }).click();

  await page.getByLabel("Identificação do rascunho").fill("AP-01/2026 revisado");
  await page.getByLabel("Campo editado").selectOption("VIGENCIA_TED_FINAL");
  await page.getByLabel("Novo valor editado").fill("2027-10-31");
  await page.getByRole("button", { name: "Salvar rascunho" }).click();
  await page.getByLabel("Data da movimentação").fill("2026-08-08");
  await page.getByLabel("Setor de destino").selectOption("2");
  await page.getByRole("button", { name: "Registrar movimentação" }).click();
  await expect(page.getByText("PROAP", { exact: true })).toBeVisible();

  await page.getByLabel("Data de efetivação").fill("2026-08-08");
  await page.getByLabel("Ordem oficial").fill("1");
  await page.getByLabel("PDF assinado").selectOption("98");
  await page.getByRole("button", { name: "Confirmar efetivação" }).click();
  await expect(page.getByRole("region", { name: "Estado resultante" })).toContainText("31/10/2027");
  await expect(page.getByRole("button", { name: "Registrar movimentação" })).toBeVisible();
});

test("operador consulta estado atual e cadeia de retificação e cancelamento", async ({ page }) => {
  await session(page, "OPERADOR_DIPAC");
  const instrumento = {
    id: 77, numero: "CV-015/2026", tipo: "CONVENIO", objeto: "Cooperação institucional",
    descricao: null, natureza: "Administrativa", coordenador: "Maria Silva",
    participes: ["UFGD", "Fundação"], valorAtual: 175000,
    vigenciaContratualFinal: "2027-08-01", vigenciaTedFinal: "2027-04-30",
    documentoAssinadoId: 55, situacaoContratual: "VALIDA", situacaoTed: "VALIDA"
  };
  const cadeia = [
    { id: 41, numeroOficial: "TA-ORIGINAL-015/2026", tipo: "TERMO_ADITIVO", estado: "EFETIVADA",
      operacao: "ORIGINAL", referenciaId: null, dataEfetivacao: "2026-08-08", ordemOficial: 1,
      produzEfeitoAtual: true, valoresProduzidos: { VALOR_ATUAL: "175000.00" } },
    { id: 42, numeroOficial: "TA-RETIFICA-015/2026", tipo: "TERMO_ADITIVO", estado: "EFETIVADA",
      operacao: "RETIFICACAO", referenciaId: 41, dataEfetivacao: "2026-08-08", ordemOficial: 2,
      produzEfeitoAtual: false, valoresProduzidos: { VALOR_ATUAL: "180000.00" } },
    { id: 43, numeroOficial: "TA-CANCELA-015/2026", tipo: "TERMO_ADITIVO", estado: "EFETIVADA",
      operacao: "CANCELAMENTO", referenciaId: 42, dataEfetivacao: "2026-08-08", ordemOficial: 3,
      produzEfeitoAtual: false, valoresProduzidos: { VALOR_ATUAL: "175000.00" } }
  ];
  const alteracao = {
    ...cadeia[0], instrumentoId: 77, documentoAssinadoId: 98,
    mudancas: [{ campo: "VALOR_ATUAL", valorAnterior: "150000.00", valorNovo: "175000.00" }],
    estadoAtualInstrumento: {
      objeto: instrumento.objeto, descricao: instrumento.descricao, natureza: instrumento.natureza,
      coordenador: instrumento.coordenador, participes: instrumento.participes,
      valorAtual: instrumento.valorAtual, vigenciaContratualFinal: instrumento.vigenciaContratualFinal,
      vigenciaTedFinal: instrumento.vigenciaTedFinal, statusProcesso: "EM_VIGENCIA",
      precedenciaPorCampo: { VALOR_ATUAL: { dataEfetivacao: "2026-08-08", ordemOficial: 1 } }
    },
    tramitacao: { setorAtual: null, movimentacoes: [], permanencias: [] }, cadeia
  };
  await page.route("**/api/v1/processos?*", route => route.fulfill({ json: {
    ...emptyPage, totalElements: 1, totalPages: 1,
    content: [{ id: 15, numero: "PROC-015", origem: "DIPAC", status: "EM_VIGENCIA", ativo: true, instrumento }]
  } }));
  await page.route("**/api/v1/setores", route => route.fulfill({ json: [] }));
  await page.route("**/api/v1/alteracoes?*", route => route.fulfill({ json: [alteracao] }));
  await page.route("**/api/v1/documentos?*", route => route.fulfill({ json: [] }));

  await page.goto("/");
  await page.getByRole("button", { name: "Alterações contratuais" }).click();
  await page.getByLabel("Instrumento Contratual").selectOption("77");
  await page.getByRole("button", { name: /TA-ORIGINAL-015\/2026/ }).click();

  const estadoAtual = page.getByRole("region", { name: "Estado atual do Instrumento Contratual" });
  await expect(estadoAtual).toContainText("R$ 175.000,00");
  const historico = page.getByRole("region", { name: "Cadeia da alteração" });
  await expect(historico).toContainText("TA-ORIGINAL-015/2026");
  await expect(historico).toContainText("TA-RETIFICA-015/2026");
  await expect(historico).toContainText("TA-CANCELA-015/2026");
  await expect(historico).toContainText("Valor produzido: R$ 180.000,00");
  await expect(historico).toContainText("Valor restaurado: R$ 175.000,00");
  await expect(historico).toContainText("Produz efeito");
  await expect(historico).toContainText("Sem efeito atual");
});
