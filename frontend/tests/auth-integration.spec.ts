import { expect, test, type Page } from "@playwright/test";

test.describe.configure({ mode: "serial" });

const PDF_VALIDO_BASE64 =
  "JVBERi0xLjYKJfbk/N8KMSAwIG9iago8PAovVHlwZSAvQ2F0YWxvZwovVmVyc2lvbiAvMS42Ci9QYWdlcyAzIDAgUgo+PgplbmRvYmoKNSAwIG9iago8PAovTGVuZ3RoIDExOQovVHlwZSAvT2JqU3RtCi9OIDMKL0ZpbHRlciAvRmxhdGVEZWNvZGUKL0ZpcnN0IDE0Cj4+CnN0cmVhbQ0KeJwzUjBQMFYwMlYwUTAzVbCx0Q/JLMlJVdAoSy0qTszXNdRUsLMDC1cWpCroBySmpxYr6HtnphQrRJsAtQYpxCroO+eX5pUoGGKoVND3TU3JTHTKr1CINtAzUABhM0MjIGluCSJjQcqKUoF6jcFGAfUDAJFBJTUNCmVuZHN0cmVhbQplbmRvYmoKNiAwIG9iago8PAovTGVuZ3RoIDMyCi9Sb290IDEgMCBSCi9JbmZvIDIgMCBSCi9JRCBbPDY5OEZBMEE2NjNFOEQ2OThFOUE3NjQwODQ1MzE2QTlDM0VGQzhBMUMxNDY5QzAwRkFCRjMyODZBNzAyRDFFMDU+IDw2OThGQTBBNjYzRThENjk4RTlBNzY0MDg0NTMxNkE5QzNFRkM4QTFDMTQ2OUMwMEZBQkYzMjg2QTcwMkQxRTA1Pl0KL1R5cGUgL1hSZWYKL1NpemUgNwovSW5kZXggWzAgN10KL1cgWzEgMiAxXQovRmlsdGVyIC9GbGF0ZURlY29kZQo+PgpzdHJlYW0NCnicY2Bg+M/IwM/AxMAKwoxAzMTI4MfAyKjDAAAd8QGlDQplbmRzdHJlYW0KZW5kb2JqCnN0YXJ0eHJlZgozMDAKJSVFT0YK";

async function entrarComoAdministrador(page: Page) {
  await page.goto("/");
  await page.getByLabel("Login").fill("admin");
  await page.getByLabel("Senha").fill("Permanente456!");
  await page.getByRole("button", { name: "Entrar no SICC" }).click();
  await expect(page.getByRole("heading", { name: "Visão geral" })).toBeVisible();
}

test("primeiro Administrador DIPAC troca a senha temporária antes de acessar o sistema", async ({ page }) => {
  await page.goto("/");
  await page.getByLabel("Login").fill("admin");
  await page.getByLabel("Senha").fill("Temporaria123!");
  await page.getByRole("button", { name: "Entrar no SICC" }).click();

  await expect(page.getByRole("heading", { name: "Crie sua senha permanente" })).toBeVisible();
  await expect(page.getByRole("button", { name: "Processos Administrativos" })).toHaveCount(0);
  await page.getByLabel("Senha temporária").fill("Temporaria123!");
  await page.getByLabel("Nova senha").fill("Permanente456!");
  await page.getByRole("button", { name: "Definir senha" }).click();

  await expect(page.getByRole("button", { name: "Entrar no SICC" })).toBeVisible();
  await page.getByLabel("Login").fill("admin");
  await page.getByLabel("Senha").fill("Permanente456!");
  await page.getByRole("button", { name: "Entrar no SICC" }).click();

  await expect(page.getByRole("heading", { name: "Visão geral" })).toBeVisible();
  await expect(page.getByRole("button", { name: "Administração" })).toBeVisible();
});

test("cadastro interno de processo fica imediatamente localizável na consulta pública", async ({ page }) => {
  const numero = "23005.000006/2026-10";
  await entrarComoAdministrador(page);

  await page.getByRole("button", { name: "Processos Administrativos" }).click();
  const cadastro = page.locator("form").filter({
    has: page.getByRole("button", { name: "Cadastrar processo" })
  });
  await cadastro.getByLabel("Número", { exact: true }).fill(numero);
  await cadastro.getByLabel("Origem", { exact: true }).fill("DIPAC");
  await cadastro.getByLabel("Número do projeto").fill("P-006");
  await cadastro.getByLabel("Responsável DIPAC").selectOption("1");
  await cadastro.getByRole("button", { name: "Cadastrar processo" }).click();
  await expect(page.getByText("Processo Administrativo criado.")).toBeVisible();
  await expect(page.getByText(numero)).toBeVisible();

  await page.getByRole("button", { name: "Sair" }).click();
  const consulta = page.locator("section.public-list");
  await consulta.getByLabel("Número").fill(numero);
  await consulta.getByRole("button", { name: "Filtrar" }).click();

  const linha = consulta.locator("tbody tr").filter({ hasText: numero });
  await expect(linha).toBeVisible();
  await expect(linha).toContainText("Ainda não formalizado");
  await expect(linha).toContainText("Em formalização");
});

test("formaliza instrumento com PDF assinado e publica somente a allowlist", async ({ page }) => {
  const numeroProcesso = "23005.000010/2026-10";
  await entrarComoAdministrador(page);

  await page.getByRole("button", { name: "Processos Administrativos" }).click();
  const cadastroProcesso = page.locator("form").filter({
    has: page.getByRole("button", { name: "Cadastrar processo" })
  });
  await cadastroProcesso.getByLabel("Número", { exact: true }).fill(numeroProcesso);
  await cadastroProcesso.getByLabel("Origem", { exact: true }).fill("DIPAC");
  const respostaProcesso = page.waitForResponse(resposta =>
    resposta.url().endsWith("/api/v1/processos")
    && resposta.request().method() === "POST");
  await cadastroProcesso.getByRole("button", { name: "Cadastrar processo" }).click();
  await respostaProcesso;

  await page.getByRole("button", { name: "Documentos" }).click();
  const cadastroDocumento = page.locator("form").filter({
    has: page.getByRole("button", { name: "Armazenar versão 1" })
  });
  await cadastroDocumento.getByLabel("Objeto proprietário").selectOption({
    label: `${numeroProcesso} · DIPAC · Em formalização`
  });
  await cadastroDocumento.getByLabel("Categoria").selectOption("ASSINADO");
  await cadastroDocumento.getByLabel("Título").fill("Instrumento assinado CV-010");
  await cadastroDocumento.getByLabel("Arquivo").setInputFiles({
    name: "instrumento-cv-010.pdf",
    mimeType: "application/pdf",
    buffer: Buffer.from(PDF_VALIDO_BASE64, "base64")
  });
  const respostaDocumento = page.waitForResponse(resposta =>
    resposta.url().endsWith("/api/v1/documentos")
    && resposta.request().method() === "POST");
  await cadastroDocumento.getByRole("button", { name: "Armazenar versão 1" }).click();
  const documentoId = Number((await (await respostaDocumento).json()).id);

  await page.getByRole("button", { name: "Processos Administrativos" }).click();
  await page.getByRole("button", { name: new RegExp(numeroProcesso) }).click();
  const formalizacao = page.locator("form").filter({
    has: page.getByRole("button", { name: "Formalizar" })
  });
  await formalizacao.getByLabel("Número").fill("CV-010/2026");
  await formalizacao.getByLabel("Tipo").selectOption("CONVENIO");
  await formalizacao.getByLabel("Objeto").fill("Cooperação institucional");
  await formalizacao.getByLabel("Natureza").fill("Administrativa");
  await formalizacao.getByLabel("Coordenador").fill("Maria Silva");
  await formalizacao.getByLabel("Partícipes, um por linha").fill("UFGD\nFundação");
  await formalizacao.getByLabel("Valor").fill("150000");
  await formalizacao.getByLabel("Vigência contratual").fill("2099-12-31");
  await formalizacao.getByLabel("Vigência TED").fill("2099-06-30");
  await formalizacao.getByLabel("Data de formalização").fill("2026-08-01");
  await formalizacao.getByLabel("Documento assinado PDF").selectOption(String(documentoId));
  await formalizacao.getByRole("button", { name: "Formalizar" }).click();

  const resumo = page.locator("section.panel").filter({ hasText: "Instrumento Contratual · CV-010/2026" });
  await expect(resumo).toContainText("Convênio");
  await expect(resumo).toContainText("Maria Silva");
  await expect(resumo).toContainText("2099-12-31");
  await expect(resumo).toContainText("2099-06-30");
  await expect(resumo).toContainText(`Documento assinado #${documentoId}`);

  await page.getByRole("button", { name: "Sair" }).click();
  const consulta = page.locator("section.public-list");
  await consulta.getByLabel("Número").fill(numeroProcesso);
  await consulta.getByRole("button", { name: "Filtrar" }).click();
  const linha = consulta.locator("tbody tr").filter({ hasText: numeroProcesso });
  await expect(linha).toContainText("Convênio");
  await expect(linha).toContainText("Maria Silva");
  await expect(linha).toContainText("Em vigência");
  await expect(linha).toContainText("2099-12-31");
  await expect(linha).toContainText("2099-06-30");
  await expect(consulta.getByRole("columnheader")).toHaveCount(7);
});

test("Processo Administrativo inativo preserva documentos para consulta e download", async ({ page }) => {
  const numeroProcesso = "23005.000010/2026-10";
  await entrarComoAdministrador(page);

  await page.getByRole("button", { name: "Processos Administrativos" }).click();
  await page.getByRole("button", { name: new RegExp(numeroProcesso) }).click();
  const administracao = page.locator("section.panel").filter({
    has: page.getByRole("heading", { name: `Editar Processo Administrativo · ${numeroProcesso}` })
  });
  await administracao.getByRole("button", { name: "Desativar" }).click();
  await expect(page.getByText(
    "Processo Administrativo desativado; o registro histórico foi preservado."
  )).toBeVisible();

  await page.getByRole("button", { name: "Documentos" }).click();
  const cadastro = page.locator("form").filter({
    has: page.getByRole("button", { name: "Armazenar versão 1" })
  });
  await cadastro.getByLabel("Tipo de proprietário").selectOption("INSTRUMENTO");
  await cadastro.getByLabel("Objeto proprietário").selectOption({
    label: `CV-010/2026 · Convênio · Processo ${numeroProcesso} · Inativo`
  });

  await expect(page.getByText(
    "O Processo Administrativo está inativo. Os documentos permanecem disponíveis somente para consulta e download."
  )).toBeVisible();
  await expect(cadastro.getByLabel("Título")).toBeDisabled();
  await expect(cadastro.getByLabel("Arquivo")).toBeDisabled();
  await expect(cadastro.getByRole("button", { name: "Armazenar versão 1" })).toBeDisabled();

  const novaVersao = page.locator("form").filter({
    has: page.getByRole("button", { name: "Adicionar versão" })
  });
  await expect(novaVersao.getByLabel("Documento para nova versão")).toBeDisabled();
  await expect(novaVersao.getByRole("button", { name: "Adicionar versão" })).toBeDisabled();

  const documento = page.locator("article.doc").filter({ hasText: "Instrumento assinado CV-010" });
  await expect(documento).toBeVisible();
  await expect(documento.getByRole("button", { name: "Desativar" })).toBeDisabled();
  const download = page.waitForEvent("download");
  await documento.getByRole("button", { name: "Baixar versão 1" }).click();
  expect((await download).suggestedFilename()).toBe("instrumento-cv-010.pdf");
});

test("gera, acompanha e baixa um relatório retido pela API real", async ({ page }) => {
  await entrarComoAdministrador(page);
  await page.getByRole("button", { name: "Relatórios" }).click();
  await page.getByLabel("origem").fill("DIPAC");

  const geracao = page.waitForResponse(resposta =>
    resposta.url().endsWith("/api/v1/relatorios")
    && resposta.request().method() === "POST");
  await page.locator(".report-actions").getByText("Consolidado", { exact: true }).locator("..")
    .getByRole("button", { name: "CSV" }).click();
  const relatorio = await (await geracao).json();

  await expect(page.getByText("Relatório gerado e retido para download.")).toBeVisible();
  const historico = page.locator("article.doc").filter({ hasText: relatorio.nomeArquivo });
  await expect(historico).toContainText("origem: DIPAC");
  await expect(historico).toContainText(relatorio.checksumSha256);

  const download = page.waitForEvent("download");
  await historico.getByRole("button", { name: "Baixar" }).click();
  const arquivo = await download;
  expect(arquivo.suggestedFilename()).toBe(relatorio.nomeArquivo);
  const stream = await arquivo.createReadStream();
  let conteudo = "";
  for await (const parte of stream) conteudo += parte.toString("utf8");
  expect(conteudo).toContain("Relatório consolidado");
  expect(conteudo).toContain("origem=DIPAC");
});
