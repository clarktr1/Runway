import "server-only";
import { getSessionToken } from "@/app/lib/session";

const API_URL = process.env.RUNWAY_API_URL ?? "http://localhost:8080";

export class ApiError extends Error {
  status: number;
  fieldErrors: Record<string, string>;

  constructor(status: number, message: string, fieldErrors: Record<string, string> = {}) {
    super(message);
    this.status = status;
    this.fieldErrors = fieldErrors;
  }
}

type ApiFetchOptions = {
  method?: string;
  body?: unknown;
  auth?: boolean;
};

export async function apiFetch<T>(path: string, options: ApiFetchOptions = {}): Promise<T> {
  const { method = "GET", body, auth = true } = options;
  const headers: Record<string, string> = { "Content-Type": "application/json" };

  if (auth) {
    const token = await getSessionToken();
    if (token) {
      headers["Authorization"] = `Bearer ${token}`;
    }
  }

  const response = await fetch(`${API_URL}${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
    cache: "no-store",
  });

  if (response.status === 204) {
    return undefined as T;
  }

  const payload = await response.json().catch(() => null);

  if (!response.ok) {
    const knownKeys = new Set(["type", "title", "status", "detail", "instance"]);
    const fieldErrors: Record<string, string> = {};
    if (payload && typeof payload === "object") {
      for (const [key, value] of Object.entries(payload)) {
        if (!knownKeys.has(key) && typeof value === "string") {
          fieldErrors[key] = value;
        }
      }
    }
    const detail = typeof payload?.detail === "string" ? payload.detail : "Something went wrong";
    throw new ApiError(response.status, detail, fieldErrors);
  }

  return payload as T;
}
