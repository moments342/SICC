import type { Page } from "@playwright/test";

export async function openUserActions(page: Page, name?: string) {
  const manager = page.getByRole("dialog", { name: "Gerenciar usuário", exact: true });
  if (await page.getByRole("dialog", { name: "Redefinir senha temporária", exact: true }).isVisible()) await page.keyboard.press("Escape");
  if (await manager.isVisible()) {
    if (!name || (await manager.locator(".modal-heading p").innerText()).includes(name)) return;
    await page.keyboard.press("Escape");
  }
  const trigger = name ? page.getByRole("button", { name: `Gerenciar usuário ${name}`, exact: true })
    : page.getByRole("button", { name: /^Gerenciar usuário / }).first();
  await trigger.click(); await manager.getByLabel("Perfil", { exact: true }).waitFor();
}

export async function openInstrumentPicker(page: Page) {
  await closeModal(page);
  const toggle = page.getByRole("button", { name: "Trocar instrumento", exact: true });
  if (await toggle.isVisible()) await toggle.click();
}

export async function openMenu(page: Page) {
  await closeModal(page);
  const menu = page.getByRole("button", { name: /^Menu ·/ });
  if (await menu.isVisible() && await menu.getAttribute("aria-expanded") === "false") await menu.click();
}

export async function closeModal(page: Page) {
  for (let level = 0; level < 3; level++) {
    const close = page.locator("dialog[open] .modal-close").last();
    if (!await close.isVisible() || !await close.isEnabled()) break;
    await close.click();
  }
}

export async function answerConfirmation(page: Page, accept = true) {
  const dialog = page.getByRole("dialog").filter({ has: page.getByRole("button", { name: "Fechar confirmação", exact: true }) });
  await dialog.locator(accept ? ".confirmation-actions button:last-child" : ".confirmation-actions button:first-child").click();
}

export async function openProcessAction(page: Page, action: "edit" | "movement" | "formalize") {
  const titles = { edit: "Editar Processo Administrativo", movement: "Registrar movimentação do processo", formalize: "Formalizar Instrumento Contratual" };
  const triggers = { edit: "Editar processo", movement: "Nova movimentação", formalize: "Formalizar Instrumento Contratual" };
  if (await page.getByRole("dialog", { name: titles[action], exact: true }).isVisible()) return;
  await closeModal(page);
  await page.getByRole("button", { name: triggers[action], exact: true }).click();
}

export async function openAlteration(page: Page, action: "draft" | "edit" | "effectuate" | "other" | "movement") {
  const names = { draft: /^Preparar (Termo Aditivo|Apostilamento)$/, edit: "Editar rascunho",
    effectuate: /^Efetivar (Termo Aditivo|Apostilamento)$/, other: "Outras operações contratuais", movement: "Registrar movimentação" };
  if (await page.getByRole("dialog", { name: names[action], exact: true }).isVisible()) return;
  await closeModal(page);
  await page.getByRole("button", { name: action === "movement" ? "Nova movimentação" : names[action], exact: true }).click();
}

export async function openCreation(page: Page) {
  const trigger = page.getByRole("button", { name: "Novo Processo Administrativo", exact: true });
  if (await trigger.isVisible() && await trigger.getAttribute("aria-expanded") === "false") await trigger.click();
}

export async function openLogin(page: Page) {
  if (!await page.locator(".login-card").isVisible()) await page.getByRole("link", { name: "Acesso interno", exact: true }).click();
}

export async function openFilters(page: Page) {
  if (await page.getByRole("dialog").count()) return;
  const filters = page.locator("details.filter-disclosure:visible").first();
  if (await filters.count() && await filters.getAttribute("open") === null) await filters.locator(":scope > summary").click();
}
