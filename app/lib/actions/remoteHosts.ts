"use server";

import { revalidatePath } from "next/cache";
import { apiFetch, ApiError } from "@/app/lib/api";
import type { TestConnectionResult } from "@/app/lib/types";

export type RemoteHostFormState =
  | {
      error?: string;
      fieldErrors?: Record<string, string>;
    }
  | undefined;

function buildRemoteHostRequest(formData: FormData) {
  return {
    name: String(formData.get("name") ?? ""),
    hostname: String(formData.get("hostname") ?? ""),
    port: Number(formData.get("port") ?? "22"),
    username: String(formData.get("username") ?? ""),
    sshCredentialId: String(formData.get("sshCredentialId") ?? ""),
  };
}

export async function createRemoteHostAction(
  _prevState: RemoteHostFormState,
  formData: FormData,
): Promise<RemoteHostFormState> {
  try {
    await apiFetch("/api/remote-hosts", { method: "POST", body: buildRemoteHostRequest(formData) });
  } catch (error) {
    if (error instanceof ApiError) {
      return { error: error.message, fieldErrors: error.fieldErrors };
    }
    return { error: "Something went wrong. Please try again." };
  }

  revalidatePath("/account");
}

export async function updateRemoteHostAction(
  id: string,
  _prevState: RemoteHostFormState,
  formData: FormData,
): Promise<RemoteHostFormState> {
  try {
    await apiFetch(`/api/remote-hosts/${id}`, { method: "PATCH", body: buildRemoteHostRequest(formData) });
  } catch (error) {
    if (error instanceof ApiError) {
      return { error: error.message, fieldErrors: error.fieldErrors };
    }
    return { error: "Something went wrong. Please try again." };
  }

  revalidatePath("/account");
}

export async function deleteRemoteHostAction(id: string) {
  await apiFetch(`/api/remote-hosts/${id}`, { method: "DELETE" });
  revalidatePath("/account");
}

export async function testRemoteHostConnectionAction(id: string): Promise<TestConnectionResult> {
  const result = await apiFetch<TestConnectionResult>(`/api/remote-hosts/${id}/test-connection`, {
    method: "POST",
  });
  revalidatePath("/account");
  return result;
}

export async function repinRemoteHostAction(id: string): Promise<TestConnectionResult> {
  const result = await apiFetch<TestConnectionResult>(`/api/remote-hosts/${id}/repin`, { method: "POST" });
  revalidatePath("/account");
  return result;
}
