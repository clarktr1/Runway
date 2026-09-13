"use server";

import { revalidatePath } from "next/cache";
import { apiFetch } from "@/app/lib/api";

export async function runJobAction(jobId: string) {
  await apiFetch(`/api/jobs/${jobId}/run`, { method: "POST" });
  revalidatePath(`/jobs/${jobId}`);
  revalidatePath("/executions");
}
