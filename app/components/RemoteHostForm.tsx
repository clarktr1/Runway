"use client";

import { useActionState } from "react";
import type { RemoteHostFormState } from "@/app/lib/actions/remoteHosts";
import type { RemoteHost, SshCredential } from "@/app/lib/types";

type RemoteHostFormProps = {
  action: (state: RemoteHostFormState, formData: FormData) => Promise<RemoteHostFormState>;
  credentials: SshCredential[];
  submitLabel: string;
  initialValues?: RemoteHost;
};

export function RemoteHostForm({ action, credentials, submitLabel, initialValues }: RemoteHostFormProps) {
  const [state, formAction, pending] = useActionState<RemoteHostFormState, FormData>(action, undefined);

  return (
    <form action={formAction} className="flex max-w-sm flex-col gap-4">
      {state?.error && <p className="text-sm text-red-600">{state.error}</p>}

      <label className="flex flex-col gap-1">
        <span className="text-sm font-medium">Name</span>
        <input
          name="name"
          defaultValue={initialValues?.name}
          required
          placeholder="Web Server 1"
          className="rounded-md border border-foreground/15 bg-transparent px-3 py-2 focus:border-primary focus:outline-none"
        />
        {state?.fieldErrors?.name && <span className="text-sm text-red-600">{state.fieldErrors.name}</span>}
      </label>

      <div className="flex gap-3">
        <label className="flex flex-1 flex-col gap-1">
          <span className="text-sm font-medium">Hostname</span>
          <input
            name="hostname"
            defaultValue={initialValues?.hostname}
            required
            placeholder="web1.internal"
            className="rounded-md border border-foreground/15 bg-transparent px-3 py-2 focus:border-primary focus:outline-none"
          />
          {state?.fieldErrors?.hostname && (
            <span className="text-sm text-red-600">{state.fieldErrors.hostname}</span>
          )}
        </label>
        <label className="flex w-24 flex-col gap-1">
          <span className="text-sm font-medium">Port</span>
          <input
            name="port"
            type="number"
            min={1}
            defaultValue={initialValues?.port ?? 22}
            required
            className="rounded-md border border-foreground/15 bg-transparent px-3 py-2 focus:border-primary focus:outline-none"
          />
        </label>
      </div>

      <label className="flex flex-col gap-1">
        <span className="text-sm font-medium">Username</span>
        <input
          name="username"
          defaultValue={initialValues?.username}
          required
          placeholder="deploy"
          className="rounded-md border border-foreground/15 bg-transparent px-3 py-2 focus:border-primary focus:outline-none"
        />
        {state?.fieldErrors?.username && (
          <span className="text-sm text-red-600">{state.fieldErrors.username}</span>
        )}
      </label>

      <label className="flex flex-col gap-1">
        <span className="text-sm font-medium">SSH Key</span>
        <select
          name="sshCredentialId"
          defaultValue={initialValues?.sshCredentialId ?? ""}
          required
          className="rounded-md border border-foreground/15 bg-transparent px-3 py-2 focus:border-primary focus:outline-none"
        >
          <option value="" disabled>
            Select an SSH key
          </option>
          {credentials.map((credential) => (
            <option key={credential.id} value={credential.id}>
              {credential.name}
            </option>
          ))}
        </select>
        {state?.fieldErrors?.sshCredentialId && (
          <span className="text-sm text-red-600">{state.fieldErrors.sshCredentialId}</span>
        )}
      </label>

      <button
        type="submit"
        disabled={pending || credentials.length === 0}
        className="mt-2 w-fit rounded-md bg-primary px-4 py-2 text-sm font-medium text-background hover:bg-primary-dark disabled:opacity-50"
      >
        {pending ? "Saving…" : submitLabel}
      </button>
      {credentials.length === 0 && (
        <p className="text-xs text-foreground/60">Add an SSH key above before adding a remote host.</p>
      )}
    </form>
  );
}
