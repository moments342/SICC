export function formatarMes(mes: string) {
  return new Intl.DateTimeFormat("pt-BR", { month: "short", year: "numeric", timeZone: "UTC" })
    .format(new Date(`${mes}-01T00:00:00Z`));
}

export function formatarNumeroDias(dias: number) {
  return new Intl.NumberFormat("pt-BR", {
    minimumFractionDigits: 1,
    maximumFractionDigits: 1
  }).format(dias);
}

export function dataLocalAtual() {
  const agora = new Date();
  const local = new Date(agora.getTime() - agora.getTimezoneOffset() * 60_000);
  return local.toISOString().slice(0, 10);
}

export function formatarDataNegocio(data: string) {
  return new Intl.DateTimeFormat("pt-BR", { timeZone: "UTC" })
    .format(new Date(`${data}T00:00:00Z`));
}

export function formatarMomentoInsercao(instante: string) {
  return instante.replace("T", " ").slice(0, 16);
}

export function money(value: number) {
  return new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" }).format(value);
}
