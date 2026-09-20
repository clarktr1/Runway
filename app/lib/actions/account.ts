"use server";

import { revalidatePath } from "next/cache";
import { apiFetch, ApiError } from "@/app/lib/api";
import { createSession } from "@/app/lib/session";

export type AccountFormState =
  | {
      error?: string;
      fieldErrors?: Record<string, string>;
      success?: string;
    }
  | undefined;

export async function updateEmailAction(
  _prevState: AccountFormState,
  formData: FormData,
): Promise<AccountFormState> {
  const currentPassword = String(formData.get("currentPassword") ?? "");
  const email = String(formData.get("email") ?? "");

  try {
    await apiFetch("/api/account/email", { method: "PATCH", body: { currentPassword, email } });
  } catch (error) {
    if (error instanceof ApiError) {
      return { error: error.message, fieldErrors: error.fieldErrors };
    }
    return { error: "Something went wrong. Please try again." };
  }

  revalidatePath("/account");
  return { success: "Email updated." };
}

export async function updatePasswordAction(
  _prevState: AccountFormState,
  formData: FormData,
): Promise<AccountFormState> {
  const currentPassword = String(formData.get("currentPassword") ?? "");
  const newPassword = String(formData.get("newPassword") ?? "");

  let token: string;
  try {
    const response = await apiFetch<{ token: string }>("/api/account/password", {
      method: "PATCH",
      body: { currentPassword, newPassword },
    });
    token = response.token;
  } catch (error) {
    if (error instanceof ApiError) {
      return { error: error.message, fieldErrors: error.fieldErrors };
    }
    return { error: "Something went wrong. Please try again." };
  }

  await createSession(token);
  revalidatePath("/account");
  return { success: "Password updated." };
}
