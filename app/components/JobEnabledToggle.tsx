"use client";

import { useTransition } from "react";
import type { Job } from "@/app/lib/types";

export function JobEnabledToggle({
  job,
  action,
}: {
  job: Job;
  action: (job: Job) => Promise<void>;
}) {
  const [isPending, startTransition] = useTransition();

  return (
    <button
      type="button"
      role="switch"
      aria-checked={job.enabled}
      aria-label={job.enabled ? "Disable job" : "Enable job"}
      disabled={isPending}
      onClick={() => startTransition(() => action(job))}
      className={`relative h-5 w-9 shrink-0 rounded-full transition-colors disabled:opacity-50 ${
        job.enabled ? "bg-primary" : "bg-foreground/20"
      }`}
    >
      <span
        className={`absolute left-0.5 top-0.5 h-4 w-4 rounded-full bg-background transition-transform ${
          job.enabled ? "translate-x-4" : "translate-x-0"
        }`}
      />
    </button>
  );
}
