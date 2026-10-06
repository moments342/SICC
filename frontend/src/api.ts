import { rejectStoredSession } from "./session";

const API = import.meta.env.VITE_API_URL ?? "";
const MAX_PAGE_SIZE = 100;

type PageResponse<T> = {
  content: T[];
  totalPages: number;
  number: number;
};

export class ApiError extends Error {
  readonly fields: Record<string, string>;
  constructor(message: string, status: number) {
    super(message);
    // ApiExceptionHandler joins Bean Validation errors as "field: message; ...".
    this.fields = status === 400 ? Object.fromEntries(message.split("; ").flatMap(part => {
      const match = part.match(/^([a-zA-Z][\w.[\]]*): (.+)$/);
      return match ? [[match[1], match[2]]] : [];
    })) : {};
  }
}

export async function request<T>(
  path: string,
  options: RequestInit = {},
  token?: string
): Promise<T> {
  const response = await fetchApi(path, {
    ...options,
    headers: {
      ...(options.body instanceof FormData ? {} : { "Content-Type": "application/json" }),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options.headers
    }
  }, token);
  if (!response.ok) {
    const body = await response.json().catch(() => null);
    throw new ApiError(body?.mensagem || "O serviço não respondeu como esperado. Tente novamente.", response.status);
  }
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}

export function upload<T>(path: string, data: FormData, token: string): Promise<T> {
  return request<T>(path, { method: "POST", body: data }, token);
}

export async function requestAllPages<T>(path: string, token?: string, signal?: AbortSignal): Promise<T[]> {
  const url = new URL(path, "http://sicc.local");
  const content: T[] = [];
  let pageNumber = 0;
  let totalPages = 1;

  do {
    url.searchParams.set("page", String(pageNumber));
    url.searchParams.set("size", String(MAX_PAGE_SIZE));
    const page = await request<PageResponse<T>>(`${url.pathname}${url.search}`, { signal }, token);
    content.push(...page.content);
    totalPages = page.totalPages;
    pageNumber += 1;
  } while (pageNumber < totalPages);

  return content;
}

export async function download(path: string, token: string) {
  const response = await fetchApi(path, {
    headers: { Authorization: `Bearer ${token}` }
  }, token);
  if (!response.ok) {
    throw new Error("Não foi possível baixar o arquivo.");
  }
  const disposition = response.headers.get("content-disposition") ?? "";
  const filename = disposition.match(/filename="?([^";]+)"?/i)?.[1] ?? "arquivo";
  const url = URL.createObjectURL(await response.blob());
  const anchor = document.createElement("a");
  anchor.href = url; anchor.download = filename; anchor.click();
  URL.revokeObjectURL(url);
}

async function fetchApi(path: string, options: RequestInit, token?: string) {
  let response: Response;
  try { response = await fetch(`${API}${path}`, options); }
  catch (error) {
    if ((error as Error).name === "AbortError") throw error;
    throw new Error("Não foi possível conectar ao SICC. Verifique sua conexão e tente novamente.");
  }
  if (response.status === 401 && token) rejectStoredSession(token);
  return response;
}
