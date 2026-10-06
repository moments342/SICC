export const pageLabels: Record<string, string> = {
  dashboard: "Visão geral",
  processos: "Processos Administrativos",
  documentos: "Documentos",
  alteracoes: "Alterações contratuais",
  relatorios: "Relatórios",
  notificacoes: "Notificações",
  administracao: "Administração",
  auditoria: "Registros de Auditoria"
};

export function allowedPage(page: string, profile: string) {
  if (!Object.hasOwn(pageLabels, page)) return "dashboard";
  if ((page === "administracao" || page === "auditoria") && profile !== "ADMINISTRADOR_DIPAC") return "dashboard";
  return page;
}

export function pageFromLocation(profile: string) {
  return allowedPage(window.location.hash.replace(/^#\/?/, ""), profile);
}
