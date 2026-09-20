"use client";

import { useActionState } from "react";
import { createSshCredentialAction } from "@/app/lib/actions/sshCredentials";

export function SshCredentialForm() {
  const [state, formAction, pending] = useActionState(createSshCredentialAction, undefined);

  return (
    <form action={formAction} className="flex max-w-sm flex-col gap-4">
      {state?.error && <p className="text-sm text-red-600">{state.error}</p>}

      <label className="flex flex-col gap-1">
        <span className="text-sm font-medium">Name</span>
        <input
          name="name"
          required
          placeholder="Deploy Key"
          className="rounded-md border border-foreground/15 bg-transparent px-3 py-2 focus:border-primary focus:outline-none"
        />
        {state?.fieldErrors?.name && <span className="text-sm text-red-600">{state.fieldErrors.name}</span>}
      </label>

      <label className="flex flex-col gap-1">
        <span className="text-sm font-medium">Private Key</span>
        <textarea
          name="privateKey"
          required
          rows={6}
          placeholder="-----BEGIN OPENSSH PRIVATE KEY-----"
          className="rounded-md border border-foreground/15 bg-transparent px-3 py-2 font-mono text-xs focus:border-primary focus:outline-none"
        />
        {state?.fieldErrors?.privateKey && (
          <span className="text-sm text-red-600">{state.fieldErrors.privateKey}</span>
        )}
      </label>

      <label className="flex flex-col gap-1">
        <span className="text-sm font-medium">Passphrase (if the key is encrypted)</span>
        <input
          name="passphrase"
          type="password"
          className="rounded-md border border-foreground/15 bg-transparent px-3 py-2 focus:border-primary focus:outline-none"
        />
      </label>

      <p className="text-xs text-foreground/60">
        The key is encrypted at rest and is never shown again after saving.
      </p>

      <button
        type="submit"
        disabled={pending}
        className="mt-2 w-fit rounded-md bg-primary px-4 py-2 text-sm font-medium text-background hover:bg-primary-dark disabled:opacity-50"
      >
        {pending ? "Saving…" : "Add SSH Key"}
      </button>
    </form>
  );
}
