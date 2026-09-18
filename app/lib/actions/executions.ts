"use server";

import { revalidatePath } from "next/cache";
import { apiFetch } from "@/app/lib/api";

export async function runJobAction(jobId: string) {
  await apiFetch(`/api/jobs/${jobId}/run`, { method: "POST" });
  revalidatePath(`/jobs/${jobId}`);
  revalidatePath("/executions");
}

export async function retryExecutionAction(executionId: string, jobId: string) {
  await apiFetch(`/api/executions/${executionId}/retry`, { method: "POST" });
  revalidatePath(`/executions/${executionId}`);
  revalidatePath(`/jobs/${jobId}`);
  revalidatePath("/executions");
}

export async function cancelExecutionAction(executionId: string, jobId: string) {
  await apiFetch(`/api/executions/${executionId}/cancel`, { method: "POST" });
  revalidatePath(`/executions/${executionId}`);
  revalidatePath(`/jobs/${jobId}`);
  revalidatePath("/executions");
}
