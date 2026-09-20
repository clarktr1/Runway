"use server";

import { revalidatePath } from "next/cache";
import { apiFetch, ApiError } from "@/app/lib/api";

export type SshCredentialFormState =
  | {
      error?: string;
      fieldErrors?: Record<string, string>;
    }
  | undefined;

export async function createSshCredentialAction(
  _prevState: SshCredentialFormState,
  formData: FormData,
): Promise<SshCredentialFormState> {
  const name = String(formData.get("name") ?? "");
  const privateKey = String(formData.get("privateKey") ?? "");
  const passphrase = String(formData.get("passphrase") ?? "");

  try {
    await apiFetch("/api/ssh-credentials", {
      method: "POST",
      body: { name, privateKey, passphrase: passphrase || null },
    });
  } catch (error) {
    if (error instanceof ApiError) {
      return { error: error.message, fieldErrors: error.fieldErrors };
    }
    return { error: "Something went wrong. Please try again." };
  }

  revalidatePath("/account");
}

export async function deleteSshCredentialAction(id: string) {
  await apiFetch(`/api/ssh-credentials/${id}`, { method: "DELETE" });
  revalidatePath("/account");
}
