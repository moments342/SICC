import type { Page } from "@playwright/test";

/** Fixtures de identificação e detalhe separadas, como o contrato do seletor. */
export async function mockInstrumentosAlteracao(page: Page, processos: {
  numero: string; instrumento?: { id: number; numero: string; tipo: string; [key: string]: unknown };
}[]) {
  const instrumentos = processos.flatMap(p => p.instrumento ? [{ ...p.instrumento, numeroProcesso: p.numero }] : []);
  await page.route("**/api/v1/alteracoes/instrumentos?*", route => route.fulfill({ json: {
    content: instrumentos.map(({ id, numero, tipo, numeroProcesso }) => ({ id, numero, tipo, numeroProcesso })),
    number: 0, size: 20, totalElements: instrumentos.length, totalPages: instrumentos.length ? 1 : 0
  } }));
  await page.route(/\/api\/v1\/alteracoes\/instrumentos\/\d+$/, route => {
    const id = Number(new URL(route.request().url()).pathname.split("/").at(-1));
    return route.fulfill({ json: instrumentos.find(i => i.id === id) });
  });
}
