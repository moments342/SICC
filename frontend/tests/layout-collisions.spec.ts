import { expect, test, type Page, type TestInfo } from "@playwright/test";

const emptyPage = { content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 };
const processo = {
  id: 17,
  numero: "PROC-ADMINISTRATIVO-COM-IDENTIFICACAO-EXTENSA-017/2026",
  origem: "Divisao de Parcerias e Convenios com denominacao extensa",
  numeroProjeto: "PROJETO-INSTITUCIONAL-017/2026",
  status: "EM_VIGENCIA",
  ativo: true,
  instrumento: {
    id: 77,
    numero: "CONVENIO-INSTITUCIONAL-017/2026",
    tipo: "CONVENIO",
    objeto: "Cooperacao institucional com descricao suficientemente extensa para pressionar o layout",
    descricao: "Descricao detalhada do instrumento contratual",
    natureza: "Administrativa",
    coordenador: "Nome completo da pessoa coordenadora do instrumento",
    participes: ["Universidade Federal da Grande Dourados", "Fundacao de Apoio"],
    valorAtual: 175000,
    vigenciaContratualFinal: "2027-08-01",
    vigenciaTedFinal: "2027-04-30",
    documentoAssinadoId: 55,
    situacaoContratual: "VALIDA",
    situacaoTed: "VALIDA"
  }
};

const dashboard = {
  processosPorStatus: { EM_FORMALIZACAO: 1, EM_VIGENCIA: 2, CONCLUIDO: 1 },
  percentualConcluidos: 25,
  alertasContratuais: 1,
  alertasTed: 1,
  valorTotalVigente: 175000,
  instrumentosPorTipo: { CONTRATO_GESTAO: 1, CONVENIO: 1 },
  permanenciaMediaPorSetor: { "SETOR COM DENOMINACAO MUITO EXTENSA": 12 },
  maiorGargalo: "SETOR COM DENOMINACAO MUITO EXTENSA",
  detalhesPermanenciaPorSetor: {},
  tempoMedioTramitacaoInicialDias: 14,
  detalhesTempoTramitacaoInicial: [],
  formalizacoesMensais: { "2026-08": 2 },
  conclusoesMensais: { "2026-08": 1 }
};

async function mockApi(page: Page) {
  await page.addInitScript(value => localStorage.setItem("sicc-session", JSON.stringify(value)), {
    token: "jwt-layout-test",
    perfil: "ADMINISTRADOR_DIPAC",
    trocaSenhaObrigatoria: false
  });

  await page.route("**/api/v1/**", route => {
    const url = new URL(route.request().url());
    const path = url.pathname;
    if (path === "/api/v1/dashboard") return route.fulfill({ json: dashboard });
    if (path === "/api/v1/processos") return route.fulfill({ json: { ...emptyPage, content: [processo], totalElements: 1, totalPages: 1 } });
    if (path === "/api/v1/processos/responsaveis") return route.fulfill({ json: [] });
    if (path === "/api/v1/documentos/proprietarios") return route.fulfill({ json: {
      ...emptyPage, totalElements: 1, totalPages: 1, content: [{
        id: processo.id, numero: processo.numero, numeroProcesso: processo.numero,
        origem: processo.origem, processoAtivo: processo.ativo, statusProcesso: processo.status,
        numeroInstrumento: processo.instrumento.numero, tipoInstrumento: processo.instrumento.tipo,
        estadoAlteracao: null
      }]
    } });
    if (path === "/api/v1/setores" || path === "/api/v1/admin/setores") return route.fulfill({ json: [
      { id: 1, sigla: "DIPAC", nome: "Divisao de Parcerias e Convenios", ativo: true },
      { id: 2, sigla: "SETOR-EXTENSO", nome: "Setor com uma denominacao administrativa muito extensa", ativo: true }
    ] });
    if (path === "/api/v1/documentos") return route.fulfill({ json: [{
      id: 98,
      titulo: "Documento administrativo com titulo deliberadamente muito extenso para validar a quebra responsiva",
      categoria: "ADMINISTRATIVO",
      ativo: true,
      criadoPor: { id: 2, nome: "Operador DIPAC" },
      criadoEm: "2026-08-08T10:00:00",
      versoes: [{
        versao: 1,
        nomeArquivo: "documento-administrativo-com-nome-de-arquivo-muito-extenso.pdf",
        tipoMime: "application/pdf",
        tamanho: 2048,
        checksumSha256: "a".repeat(64),
        criadoPor: { id: 2, nome: "Operador DIPAC" },
        criadoEm: "2026-08-08T10:00:00"
      }]
    }] });
    if (path === "/api/v1/alteracoes/instrumentos") return route.fulfill({ json: { ...emptyPage,
      content: [{ id: processo.instrumento.id, numero: processo.instrumento.numero,
        tipo: processo.instrumento.tipo, numeroProcesso: processo.numero }], totalElements: 1, totalPages: 1 } });
    if (path === "/api/v1/alteracoes/instrumentos/77") return route.fulfill({ json: {
      ...processo.instrumento, numeroProcesso: processo.numero
    } });
    if (path === "/api/v1/alteracoes") return route.fulfill({ json: [] });
    if (path === "/api/v1/relatorios") return route.fulfill({ json: [{
      id: 44,
      tipo: "HISTORICO_TRAMITACOES",
      formato: "CSV",
      filtros: {
        numero: processo.numero,
        origem: processo.origem,
        tipo: "CONVENIO",
        status: "EM_VIGENCIA"
      },
      criadoPor: { id: 2, login: "operador-com-login-extenso", nome: "Nome completo do operador responsavel" },
      criadoEm: "2026-08-08T12:00:00",
      checksumSha256: "b".repeat(64),
      chaveArmazenamento: "relatorios/historico/arquivo-44",
      tamanhoBytes: 640,
      nomeArquivo: "historico-de-tramitacoes-com-nome-extenso-44.csv"
    }] });
    if (path === "/api/v1/notificacoes") return route.fulfill({ json: [{
      id: 81,
      tipo: "CHEGADA_TRAMITACAO",
      mensagem: `O Processo Administrativo ${processo.numero} chegou ao setor com denominacao muito extensa.`,
      processoId: 17,
      lida: false,
      criadaEm: "2026-08-08T12:00:00"
    }] });
    if (path === "/api/v1/auditoria") return route.fulfill({ json: {
      ...emptyPage,
      content: [{
        id: 1,
        acao: "ATUALIZACAO_DE_INSTRUMENTO_CONTRATUAL",
        resultado: "SUCESSO",
        ator: { id: 2, login: "operador-com-login-extenso", nome: "Nome completo do operador responsavel" },
        objeto: { tipo: "INSTRUMENTO_CONTRATUAL", id: 77 },
        detalhes: "Detalhes extensos da operacao de auditoria para validacao responsiva",
        ipOrigem: "2001:0db8:85a3:0000:0000:8a2e:0370:7334",
        criadoEm: "2026-08-08T12:00:00"
      }],
      totalElements: 1,
      totalPages: 1
    } });
    if (path === "/api/v1/admin/usuarios") return route.fulfill({ json: [{
      id: 2,
      login: "operador-com-login-muito-extenso",
      nome: "Nome completo do operador responsavel pela DIPAC",
      email: "operador-com-endereco-extenso@universidade.example.br",
      perfil: "OPERADOR_DIPAC",
      ativo: true,
      senhaTemporaria: false
    }] });
    return route.fulfill({ json: [] });
  });
}

type LayoutIssue = { kind: string; target: string; details: string };

async function captureLayout(page: Page, testInfo: TestInfo, name: string) {
  if (process.env.SICC_CAPTURE_LAYOUT !== "true") return;
  await page.screenshot({ path: testInfo.outputPath(`${name}.png`), fullPage: true });
}

async function layoutIssues(page: Page): Promise<LayoutIssue[]> {
  return page.evaluate(() => {
    const issues: { kind: string; target: string; details: string }[] = [];
    const root = document.documentElement;
    if (root.scrollWidth > root.clientWidth + 1) {
      issues.push({ kind: "page-overflow", target: "html", details: `${root.scrollWidth}px > ${root.clientWidth}px` });
    }

    const visible = (element: Element) => {
      const rect = element.getBoundingClientRect();
      const style = getComputedStyle(element);
      return rect.width > 0 && rect.height > 0 && style.visibility !== "hidden" && style.display !== "none";
    };
    const selector = "button, input, select, textarea, a[href]";
    const controls = [...document.querySelectorAll<HTMLElement>(selector)].filter(visible);
    const identify = (element: HTMLElement) => {
      const text = (element.innerText || element.getAttribute("aria-label") || element.getAttribute("name") || element.tagName).trim();
      return `${element.tagName.toLowerCase()}[${text.slice(0, 48)}]`;
    };

    for (const control of controls) {
      const rect = control.getBoundingClientRect();
      if (rect.left < -1 || rect.right > innerWidth + 1) {
        issues.push({ kind: "control-outside-viewport", target: identify(control), details: `${Math.round(rect.left)}..${Math.round(rect.right)} of ${innerWidth}` });
      }
    }

    for (let leftIndex = 0; leftIndex < controls.length; leftIndex += 1) {
      const left = controls[leftIndex];
      const leftRect = left.getBoundingClientRect();
      for (let rightIndex = leftIndex + 1; rightIndex < controls.length; rightIndex += 1) {
        const right = controls[rightIndex];
        if (left.contains(right) || right.contains(left)) continue;
        const rightRect = right.getBoundingClientRect();
        const overlapWidth = Math.min(leftRect.right, rightRect.right) - Math.max(leftRect.left, rightRect.left);
        const overlapHeight = Math.min(leftRect.bottom, rightRect.bottom) - Math.max(leftRect.top, rightRect.top);
        if (overlapWidth > 1 && overlapHeight > 1) {
          issues.push({
            kind: "controls-overlap",
            target: `${identify(left)} x ${identify(right)}`,
            details: `${Math.round(overlapWidth)}x${Math.round(overlapHeight)}px`
          });
        }
      }
    }
    return issues;
  });
}

const tabs = ["dashboard", "processos", "documentos", "alteracoes", "relatorios", "notificacoes", "administracao", "auditoria"];
const viewports = [
  { name: "mobile-min", width: 320, height: 800 },
  { name: "mobile", width: 360, height: 800 },
  { name: "tablet", width: 768, height: 900 },
  { name: "desktop-boundary", width: 1101, height: 800 },
  { name: "desktop", width: 1280, height: 800 }
];

for (const viewport of viewports) {
  test(`nao ha colisoes na consulta publica em ${viewport.name}`, async ({ page }, testInfo) => {
    await page.setViewportSize({ width: viewport.width, height: viewport.height });
    await page.route("**/api/v1/public/processos?*", route => route.fulfill({ json: emptyPage }));
    await page.goto("/");

    expect(await layoutIssues(page)).toEqual([]);
    await captureLayout(page, testInfo, `${viewport.name}-publica`);
  });

  test(`nao ha colisoes nas telas internas em ${viewport.name}`, async ({ page }, testInfo) => {
    await page.setViewportSize({ width: viewport.width, height: viewport.height });
    await mockApi(page);
    await page.goto("/");

    const issuesByTab: Record<string, LayoutIssue[]> = {};
    for (const [tabIndex, tabName] of tabs.entries()) {
      await page.locator("aside nav button").nth(tabIndex).click();
      await page.waitForTimeout(30);
      const issues = await layoutIssues(page);
      if (issues.length) issuesByTab[tabName] = issues;
      await captureLayout(page, testInfo, `${viewport.name}-${tabName}`);
    }

    expect(issuesByTab).toEqual({});
  });
}

test("cartao de processo continua legivel na menor largura suportada", async ({ page }) => {
  await page.setViewportSize({ width: 320, height: 800 });
  await mockApi(page);
  await page.goto("/");
  await page.locator("aside nav button").nth(1).click();

  const card = page.locator(".process-card").first();
  const cardBox = await card.boundingBox();
  const identityBox = await card.locator(":scope > div").boundingBox();
  const badgeBox = await card.locator(".badge").boundingBox();

  expect(cardBox).not.toBeNull();
  expect(identityBox).not.toBeNull();
  expect(badgeBox).not.toBeNull();
  expect(identityBox!.width).toBeGreaterThan(cardBox!.width * 0.8);
  expect(badgeBox!.height).toBeLessThan(40);
});

test("consulta publica separa filtros dos resultados e omite simbolo da marca", async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 800 });
  await page.route("**/api/v1/public/processos?*", route => route.fulfill({ json: emptyPage }));
  await page.goto("/");

  await expect(page.locator(".hero > .brand > span")).toHaveCount(0);
  await expect(page.getByLabel("Tipo").locator("option[value=CONTRATO_GESTAO]"))
    .toHaveText("Contrato de gestão");
  await expect(page.getByLabel("Status").locator("option[value=EM_VIGENCIA]"))
    .toHaveText("Em vigência");
  await expect(page.locator('select[name="vigencia"] option[value=PROXIMA_VENCIMENTO]'))
    .toHaveText("Próxima do vencimento");

  const marca = await page.locator(".hero > .brand").boundingBox();
  const apresentacao = await page.locator(".hero > .brand + div").boundingBox();
  expect(marca).not.toBeNull();
  expect(apresentacao).not.toBeNull();
  expect(Math.abs(marca!.x - apresentacao!.x)).toBeLessThanOrEqual(1);

  const filtros = await page.locator(".public-list form").boundingBox();
  const resultados = await page.locator(".public-list .table-wrap").boundingBox();
  expect(filtros).not.toBeNull();
  expect(resultados).not.toBeNull();
  expect(resultados!.y - (filtros!.y + filtros!.height)).toBeGreaterThanOrEqual(24);
});

test("navegacao interna omite simbolo da marca e mantem o texto alinhado", async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 800 });
  await mockApi(page);
  await page.goto("/");

  await expect(page.locator("aside > .brand > span")).toHaveCount(0);
  const marca = await page.locator("aside > .brand > div").boundingBox();
  const primeiroItem = await page.locator("aside nav button").first().boundingBox();
  expect(marca).not.toBeNull();
  expect(primeiroItem).not.toBeNull();
  expect(Math.abs(marca!.x - primeiroItem!.x)).toBeLessThanOrEqual(1);
});

test("botao de filtro interno usa o estilo da acao primaria", async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 800 });
  await mockApi(page);
  await page.goto("/");
  await page.locator("aside nav button").nth(1).click();

  const formulario = page.locator(".process-layout .span form");
  const botao = formulario.getByRole("button", { name: "Filtrar", exact: true });
  const controle = formulario.getByLabel("Filtrar por vigência");

  await expect(botao).toHaveClass(/primary/);
  await expect(botao).toHaveCSS("background-color", "rgb(27, 80, 57)");
  const botaoBox = await botao.boundingBox();
  const controleBox = await controle.boundingBox();
  expect(botaoBox).not.toBeNull();
  expect(controleBox).not.toBeNull();
  expect(botaoBox!.height).toBeGreaterThanOrEqual(controleBox!.height);
});

test("acoes de administracao comunicam hierarquia e risco", async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 900 });
  await mockApi(page);
  await page.goto("/");
  await page.locator("aside nav button").nth(6).click();

  const redefinir = page.getByRole("button", { name: "Redefinir", exact: true });
  const linhaUsuario = page.locator(".user-row").first();
  const detalhes = linhaUsuario.getByRole("button", { name: /Ver detalhes de/ });
  const desativar = linhaUsuario.getByRole("button", { name: "Desativar", exact: true });

  await expect(redefinir).toHaveClass(/primary/);
  await expect(redefinir).toHaveCSS("background-color", "rgb(27, 80, 57)");
  await expect(detalhes).toHaveClass(/secondary-action/);
  await expect(detalhes).toHaveCSS("color", "rgb(27, 80, 57)");
  await expect(desativar).toHaveClass(/danger-action/);
  await expect(desativar).toHaveCSS("color", "rgb(139, 47, 40)");

  for (const botao of [redefinir, detalhes, desativar]) {
    const caixa = await botao.boundingBox();
    expect(caixa).not.toBeNull();
    expect(caixa!.height).toBeGreaterThanOrEqual(42);
  }
});

test("valores de dominio usam rotulos humanos sem alterar os codigos enviados", async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 900 });
  await mockApi(page);
  await page.goto("/");

  await expect(page.locator("aside .profile small")).toHaveText("Administrador DIPAC");
  await expect(page.getByLabel("Tipo do instrumento").locator("option[value=CONTRATO_GESTAO]"))
    .toHaveText("Contrato de gestão");
  await expect(page.getByLabel("Status do processo").locator("option[value=EM_FORMALIZACAO]"))
    .toHaveText("Em formalização");

  await page.getByRole("button", { name: "Processos Administrativos", exact: true }).click();
  await expect(page.getByLabel("Filtrar por tipo").locator("option[value=CONVENIO]"))
    .toHaveText("Convênio");
  await expect(page.getByLabel("Filtrar por vigência").locator("option[value=NAO_INFORMADA]"))
    .toHaveText("Não informada");
  await expect(page.locator(".process-card .badge").first()).toHaveText("Em vigência");

  await page.getByRole("button", { name: "Documentos", exact: true }).click();
  await expect(page.getByLabel("Tipo de proprietário").locator("option[value=PROCESSO]"))
    .toHaveText("Processo administrativo");
  await expect(page.getByLabel("Categoria").locator("option[value=ADMINISTRATIVO]"))
    .toHaveText("Administrativo");

  await page.getByRole("button", { name: "Alterações contratuais", exact: true }).click();
  await expect(page.getByLabel("Instrumento Contratual").locator('option[value="77"]'))
    .toContainText("Convênio");

  await page.getByRole("button", { name: "Relatórios", exact: true }).click();
  await expect(page.locator("option[value=ACORDO_COOPERACAO_TECNICA]").first())
    .toHaveText("Acordo de cooperação técnica");
  await expect(page.locator(".report-actions")).toContainText("Histórico de tramitações");
  await expect(page.locator(".report-metadata")).toContainText("Tipo de instrumento: Convênio");
  await expect(page.locator(".report-metadata")).toContainText("Status do processo: Em vigência");

  await page.getByRole("button", { name: "Notificações", exact: true }).click();
  await expect(page.locator(".notification strong").first()).toHaveText("Chegada de tramitação");

  await page.getByRole("button", { name: "Administração", exact: true }).click();
  await expect(page.getByLabel("Perfil").first().locator("option[value=OPERADOR_DIPAC]"))
    .toHaveText("Operador DIPAC");

  await page.getByRole("button", { name: "Registros de Auditoria", exact: true }).click();
  const registro = page.locator(".audit-panel tbody tr").first();
  await expect(registro).toContainText("Atualização de instrumento contratual");
  await expect(registro).toContainText("Instrumento contratual");
  await expect(registro.locator(".badge")).toHaveText("Sucesso");
});

test("nome extenso de setor preserva palavras inteiras no grafico desktop", async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 800 });
  await mockApi(page);
  await page.goto("/");

  const label = page.locator(".bar > span", { hasText: "SETOR COM DENOMINACAO MUITO EXTENSA" });
  await expect(label).toHaveCSS("overflow-wrap", "normal");

  const medida = await label.evaluate(element => {
    const style = getComputedStyle(element);
    const canvas = document.createElement("canvas");
    const context = canvas.getContext("2d")!;
    context.font = style.font;
    return {
      larguraDisponivel: element.getBoundingClientRect().width,
      maiorPalavra: context.measureText("DENOMINACAO").width
    };
  });
  expect(medida.larguraDisponivel).toBeGreaterThanOrEqual(medida.maiorPalavra);
});
