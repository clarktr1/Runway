"use server";

import { redirect } from "next/navigation";
import { apiFetch, ApiError } from "@/app/lib/api";
import { createSession, deleteSession } from "@/app/lib/session";

export type AuthFormState =
  | {
      error?: string;
      fieldErrors?: Record<string, string>;
    }
  | undefined;

type AuthResponse = { token: string };

export async function registerAction(
  _prevState: AuthFormState,
  formData: FormData,
): Promise<AuthFormState> {
  const name = String(formData.get("name") ?? "");
  const email = String(formData.get("email") ?? "");
  const password = String(formData.get("password") ?? "");

  let token: string;
  try {
    const response = await apiFetch<AuthResponse>("/api/auth/register", {
      method: "POST",
      body: { name, email, password },
      auth: false,
    });
    token = response.token;
  } catch (error) {
    if (error instanceof ApiError) {
      return { error: error.message, fieldErrors: error.fieldErrors };
    }
    return { error: "Something went wrong. Please try again." };
  }

  await createSession(token);
  redirect("/dashboard");
}

export async function loginAction(
  _prevState: AuthFormState,
  formData: FormData,
): Promise<AuthFormState> {
  const email = String(formData.get("email") ?? "");
  const password = String(formData.get("password") ?? "");

  let token: string;
  try {
    const response = await apiFetch<AuthResponse>("/api/auth/login", {
      method: "POST",
      body: { email, password },
      auth: false,
    });
    token = response.token;
  } catch (error) {
    if (error instanceof ApiError) {
      return { error: error.message, fieldErrors: error.fieldErrors };
    }
    return { error: "Something went wrong. Please try again." };
  }

  await createSession(token);
  redirect("/dashboard");
}

export async function logoutAction() {
  await deleteSession();
  redirect("/login");
}
