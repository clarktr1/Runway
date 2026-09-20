"use client";

import { useActionState } from "react";
import { updateEmailAction } from "@/app/lib/actions/account";

export function AccountEmailForm({ currentEmail }: { currentEmail: string }) {
  const [state, formAction, pending] = useActionState(updateEmailAction, undefined);

  return (
    <form action={formAction} className="flex max-w-sm flex-col gap-4">
      {state?.error && <p className="text-sm text-red-600">{state.error}</p>}
      {state?.success && <p className="text-sm text-green-600">{state.success}</p>}

      <label className="flex flex-col gap-1">
        <span className="text-sm font-medium">Email</span>
        <input
          name="email"
          type="email"
          defaultValue={currentEmail}
          required
          className="rounded-md border border-foreground/15 bg-transparent px-3 py-2 focus:border-primary focus:outline-none"
        />
        {state?.fieldErrors?.email && <span className="text-sm text-red-600">{state.fieldErrors.email}</span>}
      </label>

      <label className="flex flex-col gap-1">
        <span className="text-sm font-medium">Current Password</span>
        <input
          name="currentPassword"
          type="password"
          required
          className="rounded-md border border-foreground/15 bg-transparent px-3 py-2 focus:border-primary focus:outline-none"
        />
      </label>

      <button
        type="submit"
        disabled={pending}
        className="mt-2 w-fit rounded-md bg-primary px-4 py-2 text-sm font-medium text-background hover:bg-primary-dark disabled:opacity-50"
      >
        {pending ? "Saving…" : "Update Email"}
      </button>
    </form>
  );
}
