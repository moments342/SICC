import { expect, test } from "@playwright/test";

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
