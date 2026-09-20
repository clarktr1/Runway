"use client";

import { useActionState, useState } from "react";
import type { Job, JobType, RemoteHost } from "@/app/lib/types";
import type { JobFormState } from "@/app/lib/actions/jobs";
import { ScheduleField } from "@/app/components/ScheduleField";

type JobFormProps = {
  action: (state: JobFormState, formData: FormData) => Promise<JobFormState>;
  submitLabel: string;
  initialValues?: Job;
  confirmMessage?: string;
  remoteHosts?: RemoteHost[];
};

export function JobForm({ action, submitLabel, initialValues, confirmMessage, remoteHosts = [] }: JobFormProps) {
  const [state, formAction, pending] = useActionState<JobFormState, FormData>(action, undefined);
  const [type, setType] = useState<JobType>(initialValues?.type ?? "SHELL");

  const command =
    initialValues?.type === "SHELL" || initialValues?.type === "SSH_COMMAND"
      ? String(initialValues.configuration.command ?? "")
      : "";
  const method = initialValues?.type === "HTTP" ? String(initialValues.configuration.method ?? "GET") : "GET";
  const url = initialValues?.type === "HTTP" ? String(initialValues.configuration.url ?? "") : "";
  const remoteHostId =
    initialValues?.type === "SSH_COMMAND" ? String(initialValues.configuration.remoteHostId ?? "") : "";

  const retryPolicy = initialValues?.retryPolicy ?? {};
  const maxAttempts = Number(retryPolicy.maxAttempts ?? 1);
  const retryStrategy = String(retryPolicy.strategy ?? "FIXED");
  const initialDelaySeconds = Number(retryPolicy.initialDelaySeconds ?? 30);
  const maxDelaySeconds = Number(retryPolicy.maxDelaySeconds ?? 600);

  return (
    <form
      action={formAction}
      onSubmit={(event) => {
        if (confirmMessage && !window.confirm(confirmMessage)) {
          event.preventDefault();
        }
      }}
      className="flex max-w-xl flex-col gap-4"
    >
      {state?.error && <p className="text-sm text-red-600">{state.error}</p>}

      <label className="flex flex-col gap-1">
        <span className="text-sm font-medium">Name</span>
        <input
          name="name"
          defaultValue={initialValues?.name}
          required
          className="rounded-md border border-foreground/15 bg-transparent px-3 py-2 focus:border-primary focus:outline-none"
        />
        {state?.fieldErrors?.name && <span className="text-sm text-red-600">{state.fieldErrors.name}</span>}
      </label>

      <label className="flex flex-col gap-1">
        <span className="text-sm font-medium">Description</span>
        <input
          name="description"
          defaultValue={initialValues?.description ?? ""}
          className="rounded-md border border-foreground/15 bg-transparent px-3 py-2 focus:border-primary focus:outline-none"
        />
      </label>

      <label className="flex flex-col gap-1">
        <span className="text-sm font-medium">Type</span>
        <select
          name="type"
          value={type}
          onChange={(event) => setType(event.target.value as JobType)}
          className="rounded-md border border-foreground/15 bg-transparent px-3 py-2 focus:border-primary focus:outline-none"
        >
          <option value="SHELL">Shell Command</option>
          <option value="HTTP">HTTP Request</option>
          <option value="SSH_COMMAND">SSH Command</option>
        </select>
      </label>

      {type === "SHELL" && (
        <label className="flex flex-col gap-1">
          <span className="text-sm font-medium">Command</span>
          <input
            name="command"
            defaultValue={command}
            required
            placeholder="/opt/scripts/backup.sh"
            className="rounded-md border border-foreground/15 bg-transparent px-3 py-2 font-mono text-sm focus:border-primary focus:outline-none"
          />
          {state?.fieldErrors?.["configuration.command"] && (
            <span className="text-sm text-red-600">{state.fieldErrors["configuration.command"]}</span>
          )}
        </label>
      )}

      {type === "HTTP" && (
        <div className="flex gap-3">
          <label className="flex w-32 flex-col gap-1">
            <span className="text-sm font-medium">Method</span>
            <select
              name="method"
              defaultValue={method}
              className="rounded-md border border-foreground/15 bg-transparent px-3 py-2 focus:border-primary focus:outline-none"
            >
              <option value="GET">GET</option>
              <option value="POST">POST</option>
              <option value="PUT">PUT</option>
              <option value="DELETE">DELETE</option>
            </select>
          </label>
          <label className="flex flex-1 flex-col gap-1">
            <span className="text-sm font-medium">URL</span>
            <input
              name="url"
              defaultValue={url}
              required
              placeholder="https://example.com/api/reindex"
              className="rounded-md border border-foreground/15 bg-transparent px-3 py-2 focus:border-primary focus:outline-none"
            />
            {state?.fieldErrors?.["configuration.url"] && (
              <span className="text-sm text-red-600">{state.fieldErrors["configuration.url"]}</span>
            )}
          </label>
        </div>
      )}

      {type === "SSH_COMMAND" && (
        <>
          <label className="flex flex-col gap-1">
            <span className="text-sm font-medium">Remote Host</span>
            <select
              name="remoteHostId"
              defaultValue={remoteHostId}
              required
              className="rounded-md border border-foreground/15 bg-transparent px-3 py-2 focus:border-primary focus:outline-none"
            >
              <option value="" disabled>
                Select a remote host
              </option>
              {remoteHosts.map((host) => (
                <option key={host.id} value={host.id}>
                  {host.name} ({host.hostname})
                </option>
              ))}
            </select>
            {state?.fieldErrors?.["configuration.remoteHostId"] && (
              <span className="text-sm text-red-600">{state.fieldErrors["configuration.remoteHostId"]}</span>
            )}
            {remoteHosts.length === 0 && (
              <span className="text-sm text-foreground/60">
                No remote hosts yet — add one from the Account page first.
              </span>
            )}
          </label>
          <label className="flex flex-col gap-1">
            <span className="text-sm font-medium">Command</span>
            <textarea
              name="command"
              defaultValue={command}
              required
              rows={3}
              placeholder="/opt/deploy.sh"
              className="rounded-md border border-foreground/15 bg-transparent px-3 py-2 font-mono text-sm focus:border-primary focus:outline-none"
            />
            {state?.fieldErrors?.["configuration.command"] && (
              <span className="text-sm text-red-600">{state.fieldErrors["configuration.command"]}</span>
            )}
          </label>
        </>
      )}

      <ScheduleField initialValue={initialValues?.cronExpression ?? ""} />

      <div className="flex gap-3">
        <label className="flex flex-1 flex-col gap-1">
          <span className="text-sm font-medium">Timeout (seconds)</span>
          <input
            name="timeoutSeconds"
            type="number"
            min={1}
            defaultValue={initialValues?.timeoutSeconds ?? ""}
            className="rounded-md border border-foreground/15 bg-transparent px-3 py-2 focus:border-primary focus:outline-none"
          />
        </label>
        <label className="flex flex-1 flex-col gap-1">
          <span className="text-sm font-medium">Max Concurrency</span>
          <input
            name="maxConcurrency"
            type="number"
            min={1}
            defaultValue={initialValues?.maxConcurrency ?? 1}
            required
            className="rounded-md border border-foreground/15 bg-transparent px-3 py-2 focus:border-primary focus:outline-none"
          />
        </label>
      </div>

      <fieldset className="flex flex-col gap-3 rounded-md border border-foreground/15 p-3">
        <legend className="px-1 text-sm font-medium">Retry Policy</legend>
        <div className="flex gap-3">
          <label className="flex flex-1 flex-col gap-1">
            <span className="text-sm font-medium">Max Attempts</span>
            <input
              name="retryMaxAttempts"
              type="number"
              min={1}
              defaultValue={maxAttempts}
              className="rounded-md border border-foreground/15 bg-transparent px-3 py-2 focus:border-primary focus:outline-none"
            />
          </label>
          <label className="flex flex-1 flex-col gap-1">
            <span className="text-sm font-medium">Strategy</span>
            <select
              name="retryStrategy"
              defaultValue={retryStrategy}
              className="rounded-md border border-foreground/15 bg-transparent px-3 py-2 focus:border-primary focus:outline-none"
            >
              <option value="FIXED">Fixed</option>
              <option value="EXPONENTIAL">Exponential Backoff</option>
            </select>
          </label>
        </div>
        <div className="flex gap-3">
          <label className="flex flex-1 flex-col gap-1">
            <span className="text-sm font-medium">Initial Delay (seconds)</span>
            <input
              name="retryInitialDelaySeconds"
              type="number"
              min={0}
              defaultValue={initialDelaySeconds}
              className="rounded-md border border-foreground/15 bg-transparent px-3 py-2 focus:border-primary focus:outline-none"
            />
          </label>
          <label className="flex flex-1 flex-col gap-1">
            <span className="text-sm font-medium">Max Delay (seconds)</span>
            <input
              name="retryMaxDelaySeconds"
              type="number"
              min={0}
              defaultValue={maxDelaySeconds}
              className="rounded-md border border-foreground/15 bg-transparent px-3 py-2 focus:border-primary focus:outline-none"
            />
          </label>
        </div>
      </fieldset>

      <label className="flex items-center gap-2">
        <input
          type="checkbox"
          name="enabled"
          defaultChecked={initialValues?.enabled ?? true}
          className="h-4 w-4"
        />
        <span className="text-sm font-medium">Enabled</span>
      </label>

      <button
        type="submit"
        disabled={pending}
        className="mt-2 w-fit rounded-md bg-primary px-4 py-2 text-sm font-medium text-background hover:bg-primary-dark disabled:opacity-50"
      >
        {pending ? "Saving…" : submitLabel}
      </button>
    </form>
  );
}
