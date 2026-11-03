import { expect, test, type Page } from "@playwright/test";
import { answerConfirmation, openUserActions, closeModal } from "./navigation-helpers";

async function setup(page: Page) {
  let users = [
    { id: 1, nome: "Maria Oliveira", login: "maria", email: "maria@ufgd.example", perfil: "OPERADOR_DIPAC", ativo: true, senhaTemporaria: false },
    { id: 2, nome: "João da Silva", login: "joao", email: "joao@ufgd.example", perfil: "ADMINISTRADOR_DIPAC", ativo: true, senhaTemporaria: true },
    { id: 3, nome: "Ana Costa", login: "ana", email: "ana@ufgd.example", perfil: "OPERADOR_DIPAC", ativo: false, senhaTemporaria: false }
  ];
  const mutations: string[] = [];
  let failProfile = false, failList = false, failDetail = false;
  await page.addInitScript(() => localStorage.setItem("sicc-session", JSON.stringify({ token: "admin-directory-fixture", perfil: "ADMINISTRADOR_DIPAC", trocaSenhaObrigatoria: false })));
  await page.route("**/api/v1/**", route => {
    const request = route.request(), url = new URL(request.url()), path = url.pathname;
    if (request.method() !== "GET") mutations.push(request.method() + " " + path + url.search);
    if (path === "/api/v1/admin/usuarios") return failList ? route.fulfill({ status: 503, json: { mensagem: "Lista indisponível." } }) : route.fulfill({ json: users });
    const match = path.match(/^\/api\/v1\/admin\/usuarios\/(\d+)(?:\/(perfil|ativo|senha))?$/);
    if (match) {
      const user = users.find(user => user.id === Number(match[1]));
      if (failDetail && request.method() === "GET") return route.fulfill({ status: 503, json: { mensagem: "Detalhe indisponível." } });
      if (match[2] === "perfil") {
        if (failProfile) return route.fulfill({ status: 503, json: { mensagem: "Não foi possível alterar o perfil." } });
        user!.perfil = url.searchParams.get("perfil")!;
      }
      if (match[2] === "ativo") user!.ativo = url.searchParams.get("ativo") === "true";
      return route.fulfill({ json: user });
    }
    return route.fulfill({ json: [] });
  });
  return { mutations, setFailProfile: (value: boolean) => { failProfile = value; }, setFailList: (value: boolean) => { failList = value; }, setFailDetail: (value: boolean) => { failDetail = value; }, empty: () => { users = []; } };
}

for (const width of [2172, 1440, 768, 390]) test(`usuários: busca, filtros e gerenciamento por teclado em ${width}px`, async ({ page }, info) => {
  await page.setViewportSize({ width, height: 1000 }); const fixture = await setup(page); await page.goto("/#/administracao");
  const section = page.locator(".admin-users"), search = section.getByRole("searchbox", { name: "Buscar usuário", exact: true });
  await expect(section.locator(".user-row")).toHaveCount(3);
  await expect(section.getByRole("button", { name: /Redefinir senha/ })).toHaveCount(0);
  await page.screenshot({ path: info.outputPath(`users-directory-${width}.png`), fullPage: true });
  await search.fill("joao"); await expect(section.locator(".user-row")).toHaveCount(1); await expect(section.locator(".user-row")).toContainText("João da Silva");
  await search.fill("maria@"); await expect(section.locator(".user-row")).toContainText("Maria Oliveira");
  await section.getByLabel("Situação do acesso", { exact: true }).selectOption("INATIVOS");
  await expect(section).toContainText("Nenhum usuário corresponde aos filtros.");
  await section.getByRole("button", { name: "Limpar filtros", exact: true }).click(); await expect(search).toBeFocused();
  await expect(section.locator(".user-row")).toHaveCount(3);
  const trigger = section.getByRole("button", { name: "Gerenciar usuário Maria Oliveira", exact: true });
  await trigger.focus(); await page.keyboard.press("Enter");
  const manager = page.getByRole("dialog", { name: "Gerenciar usuário", exact: true });
  await expect(manager.getByLabel("Perfil", { exact: true })).toHaveValue("OPERADOR_DIPAC");
  await manager.getByLabel("Perfil", { exact: true }).selectOption("ADMINISTRADOR_DIPAC");
  expect(fixture.mutations).toEqual([]);
  await manager.getByRole("button", { name: "Salvar perfil", exact: true }).click(); await answerConfirmation(page, false);
  await expect(manager.getByLabel("Perfil", { exact: true })).toHaveValue("OPERADOR_DIPAC");
  await page.screenshot({ path: info.outputPath(`users-management-${width}.png`) });
  await manager.getByRole("button", { name: "Redefinir senha de Maria Oliveira", exact: true }).click();
  await expect(page.getByRole("dialog", { name: "Redefinir senha temporária", exact: true })).toContainText("Maria Oliveira · @maria");
  await expect(page.getByRole("combobox", { name: "Usuário Interno" })).toHaveCount(0);
  await page.keyboard.press("Escape"); await expect(manager).toBeVisible();
  await page.keyboard.press("Escape"); await expect(trigger).toBeFocused();
  expect(fixture.mutations).toEqual([]);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
});

test("perfil só é gravado após salvar e confirmar; falha preserva a escolha", async ({ page }) => {
  const fixture = await setup(page); await page.goto("/#/administracao"); await openUserActions(page, "Maria Oliveira");
  const manager = page.getByRole("dialog", { name: "Gerenciar usuário", exact: true });
  fixture.setFailProfile(true); await manager.getByLabel("Perfil", { exact: true }).selectOption("ADMINISTRADOR_DIPAC");
  await manager.getByRole("button", { name: "Salvar perfil", exact: true }).click(); await answerConfirmation(page);
  await expect(manager.getByRole("alert").filter({ hasText: "Não foi possível alterar o perfil." })).toBeVisible();
  await expect(manager.getByLabel("Perfil", { exact: true })).toHaveValue("ADMINISTRADOR_DIPAC");
  fixture.setFailProfile(false); await manager.getByRole("button", { name: "Salvar perfil", exact: true }).click(); await answerConfirmation(page);
  await expect(manager).toBeHidden();
  await expect(page.locator(".user-row").filter({ hasText: "@maria" })).toContainText("Administrador DIPAC");
  await expect(page.getByRole("button", { name: "Gerenciar usuário Maria Oliveira", exact: true })).toBeFocused();
  expect(fixture.mutations).toEqual(["PATCH /api/v1/admin/usuarios/1/perfil?perfil=ADMINISTRADOR_DIPAC", "PATCH /api/v1/admin/usuarios/1/perfil?perfil=ADMINISTRADOR_DIPAC"]);
});

test("desativar confirma o usuário e atualiza o filtro sem perder foco", async ({ page }) => {
  const fixture = await setup(page); await page.goto("/#/administracao"); await page.getByLabel("Situação do acesso").selectOption("ATIVOS");
  await openUserActions(page, "Maria Oliveira"); const manager = page.getByRole("dialog", { name: "Gerenciar usuário", exact: true });
  await manager.getByRole("button", { name: "Desativar acesso", exact: true }).click(); await answerConfirmation(page, false); expect(fixture.mutations).toEqual([]);
  await manager.getByRole("button", { name: "Desativar acesso", exact: true }).click(); await answerConfirmation(page);
  await expect(page.locator(".user-row")).toHaveCount(1); await expect(page.getByRole("searchbox", { name: "Buscar usuário" })).toBeFocused();
  await page.getByLabel("Situação do acesso").selectOption("INATIVOS"); await expect(page.locator(".user-row").filter({ hasText: "@maria" })).toContainText("Inativo");
});

test("falha de lista não parece base vazia; tentativa e criação continuam disponíveis", async ({ page }) => {
  const fixture = await setup(page); fixture.setFailList(true); await page.goto("/#/administracao");
  const section = page.locator(".admin-users"); await expect(section.getByRole("alert")).toContainText("Lista indisponível.");
  await expect(section).not.toContainText("Nenhum usuário cadastrado."); fixture.setFailList(false); fixture.empty();
  await section.getByRole("button", { name: "Tentar novamente", exact: true }).click(); await expect(section).toContainText("Nenhum usuário cadastrado.");
  await section.getByRole("button", { name: "Criar usuário", exact: true }).click(); await page.getByRole("dialog", { name: "Criar usuário", exact: true }).getByLabel("Nome", { exact: true }).fill("Preenchimento conservado");
  await closeModal(page); await section.getByRole("button", { name: "Criar usuário", exact: true }).click(); await expect(page.getByRole("dialog", { name: "Criar usuário", exact: true }).getByLabel("Nome", { exact: true })).toHaveValue("Preenchimento conservado");
});

test("falha ao consultar usuário não mostra operações com dados incompletos", async ({ page }) => {
  const fixture = await setup(page); fixture.setFailDetail(true); await page.goto("/#/administracao");
  await page.getByRole("button", { name: "Gerenciar usuário Maria Oliveira", exact: true }).click();
  const manager = page.getByRole("dialog", { name: "Gerenciar usuário", exact: true }); await expect(manager.getByRole("alert")).toContainText("Detalhe indisponível.");
  await expect(manager.getByRole("button", { name: "Salvar perfil", exact: true })).toHaveCount(0);
  fixture.setFailDetail(false); await manager.getByRole("button", { name: "Tentar novamente", exact: true }).click(); await expect(manager.getByLabel("Perfil", { exact: true })).toHaveValue("OPERADOR_DIPAC");
});
