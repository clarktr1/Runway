"use client";

import Link from "next/link";
import { useActionState } from "react";
import { loginAction } from "@/app/lib/actions/auth";

export default function LoginPage() {
  const [state, formAction, pending] = useActionState(loginAction, undefined);

  return (
    <div className="flex min-h-screen items-center justify-center px-6">
      <form action={formAction} className="flex w-full max-w-sm flex-col gap-4">
        <h1 className="text-2xl font-semibold">Log in to Runway</h1>

        {state?.error && <p className="text-sm text-red-600">{state.error}</p>}

        <label className="flex flex-col gap-1">
          <span className="text-sm font-medium">Email</span>
          <input
            name="email"
            type="email"
            required
            className="rounded-md border border-foreground/15 bg-transparent px-3 py-2 focus:border-primary focus:outline-none"
          />
        </label>

        <label className="flex flex-col gap-1">
          <span className="text-sm font-medium">Password</span>
          <input
            name="password"
            type="password"
            required
            className="rounded-md border border-foreground/15 bg-transparent px-3 py-2 focus:border-primary focus:outline-none"
          />
        </label>

        <button
          type="submit"
          disabled={pending}
          className="mt-2 rounded-md bg-primary px-4 py-2 text-sm font-medium text-background hover:bg-primary-dark disabled:opacity-50"
        >
          {pending ? "Logging in…" : "Log in"}
        </button>

        <p className="text-sm text-foreground/60">
          Don&apos;t have an account?{" "}
          <Link href="/register" className="text-primary-dark underline">
            Sign up
          </Link>
        </p>
      </form>
    </div>
  );
}
