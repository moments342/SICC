const API = import.meta.env.VITE_API_URL ?? "";

export type Session = {
  token: string;
  perfil: "ADMINISTRADOR_DIPAC" | "OPERADOR_DIPAC";
  trocaSenhaObrigatoria: boolean;
};

export async function request<T>(
  path: string,
  options: RequestInit = {},
  token?: string
): Promise<T> {
  const response = await fetch(`${API}${path}`, {
    ...options,
    headers: {
      ...(options.body instanceof FormData ? {} : { "Content-Type": "application/json" }),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options.headers
    }
  });
  if (!response.ok) {
    const body = await response.json().catch(() => ({ mensagem: response.statusText }));
    throw new Error(body.mensagem ?? "Não foi possível concluir a operação.");
  }
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}

export function upload<T>(path: string, data: FormData, token: string): Promise<T> {
  return request<T>(path, { method: "POST", body: data }, token);
}

export async function download(path: string, token: string) {
  const response = await fetch(`${API}${path}`, { headers: { Authorization: `Bearer ${token}` } });
  if (!response.ok) throw new Error("Não foi possível baixar o arquivo.");
  const disposition = response.headers.get("content-disposition") ?? "";
  const filename = disposition.match(/filename="?([^";]+)"?/i)?.[1] ?? "arquivo";
  const url = URL.createObjectURL(await response.blob());
  const anchor = document.createElement("a");
  anchor.href = url; anchor.download = filename; anchor.click();
  URL.revokeObjectURL(url);
}
