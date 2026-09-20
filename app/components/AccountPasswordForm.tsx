"use client";

import { useActionState } from "react";
import { updatePasswordAction } from "@/app/lib/actions/account";

export function AccountPasswordForm() {
  const [state, formAction, pending] = useActionState(updatePasswordAction, undefined);

  return (
    <form action={formAction} className="flex max-w-sm flex-col gap-4">
      {state?.error && <p className="text-sm text-red-600">{state.error}</p>}
      {state?.success && <p className="text-sm text-green-600">{state.success}</p>}

      <label className="flex flex-col gap-1">
        <span className="text-sm font-medium">Current Password</span>
        <input
          name="currentPassword"
          type="password"
          required
          className="rounded-md border border-foreground/15 bg-transparent px-3 py-2 focus:border-primary focus:outline-none"
        />
      </label>

      <label className="flex flex-col gap-1">
        <span className="text-sm font-medium">New Password</span>
        <input
          name="newPassword"
          type="password"
          required
          minLength={8}
          className="rounded-md border border-foreground/15 bg-transparent px-3 py-2 focus:border-primary focus:outline-none"
        />
        {state?.fieldErrors?.newPassword && (
          <span className="text-sm text-red-600">{state.fieldErrors.newPassword}</span>
        )}
      </label>

      <button
        type="submit"
        disabled={pending}
        className="mt-2 w-fit rounded-md bg-primary px-4 py-2 text-sm font-medium text-background hover:bg-primary-dark disabled:opacity-50"
      >
        {pending ? "Saving…" : "Update Password"}
      </button>
    </form>
  );
}
