"use client";

import { useState, useTransition } from "react";
import type { TestConnectionResult } from "@/app/lib/types";

export function TestConnectionButton({
  hostId,
  action,
  label = "Test Connection",
  confirmMessage,
}: {
  hostId: string;
  action: (id: string) => Promise<TestConnectionResult>;
  label?: string;
  confirmMessage?: string;
}) {
  const [isPending, startTransition] = useTransition();
  const [result, setResult] = useState<TestConnectionResult | null>(null);

  return (
    <div className="flex flex-col gap-1">
      <button
        type="button"
        disabled={isPending}
        onClick={() => {
          if (confirmMessage && !window.confirm(confirmMessage)) {
            return;
          }
          startTransition(async () => {
            setResult(await action(hostId));
          });
        }}
        className="w-fit rounded-md border border-foreground/15 px-3 py-1.5 text-sm font-medium hover:bg-foreground/5 disabled:opacity-50"
      >
        {isPending ? "Connecting…" : label}
      </button>
      {result && (
        <p className={`text-xs ${result.success ? "text-green-600" : "text-red-600"}`}>
          {result.success
            ? `${result.newlyPinned ? "Pinned" : "Verified"} host key: ${result.hostKeyFingerprint}`
            : (result.errorMessage ?? "Connection failed.")}
        </p>
      )}
    </div>
  );
}
