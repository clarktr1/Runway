"use server";

import { redirect } from "next/navigation";
import { revalidatePath } from "next/cache";
import { apiFetch, ApiError } from "@/app/lib/api";
import type { Job } from "@/app/lib/types";

export type JobFormState =
  | {
      error?: string;
      fieldErrors?: Record<string, string>;
    }
  | undefined;

function buildJobRequest(formData: FormData) {
  const type = String(formData.get("type") ?? "SHELL");
  const configuration =
    type === "HTTP"
      ? { method: String(formData.get("method") ?? ""), url: String(formData.get("url") ?? "") }
      : { command: String(formData.get("command") ?? "") };

  const timeoutSeconds = String(formData.get("timeoutSeconds") ?? "").trim();
  const cronExpression = String(formData.get("cronExpression") ?? "").trim();

  const retryPolicy = {
    maxAttempts: Number(formData.get("retryMaxAttempts") ?? "1"),
    strategy: String(formData.get("retryStrategy") ?? "FIXED"),
    initialDelaySeconds: Number(formData.get("retryInitialDelaySeconds") ?? "30"),
    maxDelaySeconds: Number(formData.get("retryMaxDelaySeconds") ?? "600"),
  };

  return {
    name: String(formData.get("name") ?? ""),
    description: String(formData.get("description") ?? "") || null,
    type,
    configuration,
    enabled: formData.get("enabled") === "on",
    timeoutSeconds: timeoutSeconds ? Number(timeoutSeconds) : null,
    maxConcurrency: Number(formData.get("maxConcurrency") ?? "1"),
    retryPolicy,
    cronExpression: cronExpression || null,
  };
}

export async function createJobAction(
  _prevState: JobFormState,
  formData: FormData,
): Promise<JobFormState> {
  let jobId: string;
  try {
    const job = await apiFetch<{ id: string }>("/api/jobs", {
      method: "POST",
      body: buildJobRequest(formData),
    });
    jobId = job.id;
  } catch (error) {
    if (error instanceof ApiError) {
      return { error: error.message, fieldErrors: error.fieldErrors };
    }
    return { error: "Something went wrong. Please try again." };
  }

  redirect(`/jobs/${jobId}`);
}

export async function updateJobAction(
  id: string,
  _prevState: JobFormState,
  formData: FormData,
): Promise<JobFormState> {
  try {
    await apiFetch(`/api/jobs/${id}`, { method: "PATCH", body: buildJobRequest(formData) });
  } catch (error) {
    if (error instanceof ApiError) {
      return { error: error.message, fieldErrors: error.fieldErrors };
    }
    return { error: "Something went wrong. Please try again." };
  }

  redirect(`/jobs/${id}`);
}

export async function deleteJobAction(id: string) {
  await apiFetch(`/api/jobs/${id}`, { method: "DELETE" });
  redirect("/jobs");
}

export async function toggleJobEnabledAction(job: Job) {
  await apiFetch(`/api/jobs/${job.id}`, {
    method: "PATCH",
    body: {
      name: job.name,
      description: job.description,
      type: job.type,
      configuration: job.configuration,
      enabled: !job.enabled,
      timeoutSeconds: job.timeoutSeconds,
      maxConcurrency: job.maxConcurrency,
      retryPolicy: job.retryPolicy,
      cronExpression: job.cronExpression,
    },
  });
  revalidatePath("/jobs");
  revalidatePath(`/jobs/${job.id}`);
}
