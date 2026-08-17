import { expect, test, type Page } from "@playwright/test";

test.describe.configure({ mode: "serial" });

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
  await expect(linha).toContainText("EM FORMALIZACAO");
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
  const processoId = Number((await (await respostaProcesso).json()).id);

  await page.getByRole("button", { name: "Documentos" }).click();
  const cadastroDocumento = page.locator("form").filter({
    has: page.getByRole("button", { name: "Armazenar versão 1" })
  });
  await cadastroDocumento.getByLabel("ID do proprietário").fill(String(processoId));
  await cadastroDocumento.getByLabel("Categoria").selectOption("ASSINADO");
  await cadastroDocumento.getByLabel("Título").fill("Instrumento assinado CV-010");
  await cadastroDocumento.getByLabel("Arquivo").setInputFiles({
    name: "instrumento-cv-010.pdf",
    mimeType: "application/pdf",
    buffer: Buffer.from("%PDF-1.4\n%%EOF")
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
  await expect(resumo).toContainText("CONVENIO");
  await expect(resumo).toContainText("Maria Silva");
  await expect(resumo).toContainText("2099-12-31");
  await expect(resumo).toContainText("2099-06-30");
  await expect(resumo).toContainText(`Documento assinado #${documentoId}`);

  await page.getByRole("button", { name: "Sair" }).click();
  const consulta = page.locator("section.public-list");
  await consulta.getByLabel("Número").fill(numeroProcesso);
  await consulta.getByRole("button", { name: "Filtrar" }).click();
  const linha = consulta.locator("tbody tr").filter({ hasText: numeroProcesso });
  await expect(linha).toContainText("CONVENIO");
  await expect(linha).toContainText("Maria Silva");
  await expect(linha).toContainText("EM VIGENCIA");
  await expect(linha).toContainText("2099-12-31");
  await expect(linha).toContainText("2099-06-30");
  await expect(consulta.getByRole("columnheader")).toHaveCount(7);
});
