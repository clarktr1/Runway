"use client";

import { useEffect, useRef, useState } from "react";
import type { DemoRunStatus, ExecutionLog, ExecutionStatus } from "@/app/lib/types";
import { STATUS_COLORS, STREAM_COLORS } from "@/app/components/ExecutionDetail";

const TERMINAL_STATUSES: ExecutionStatus[] = ["SUCCESS", "FAILED", "TIMEOUT", "CANCELLED"];

async function* readEvents(body: ReadableStream<Uint8Array>) {
  const reader = body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";

  for (;;) {
    const { done, value } = await reader.read();
    if (done) {
      return;
    }
    buffer += decoder.decode(value, { stream: true });

    let end: number;
    while ((end = buffer.indexOf("\n\n")) !== -1) {
      const block = buffer.slice(0, end);
      buffer = buffer.slice(end + 2);

      let event = "message";
      let data = "";
      for (const line of block.split("\n")) {
        if (line.startsWith("event:")) {
          event = line.slice("event:".length).trim();
        } else if (line.startsWith("data:")) {
          data += line.slice("data:".length).trim();
        }
      }
      if (data) {
        yield { event, data };
      }
    }
  }
}

export function DemoRunner({ id }: { id: string }) {
  const [status, setStatus] = useState<DemoRunStatus | null>(null);
  const [logs, setLogs] = useState<ExecutionLog[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [running, setRunning] = useState(false);
  const abortRef = useRef<AbortController | null>(null);

  useEffect(() => () => abortRef.current?.abort(), []);

  async function run() {
    const controller = new AbortController();
    abortRef.current = controller;
    setRunning(true);
    setError(null);
    setStatus(null);
    setLogs([]);

    let last: DemoRunStatus | null = null;
    try {
      const response = await fetch(`/api/demo/jobs/${id}/run`, { method: "POST", signal: controller.signal });
      if (!response.ok || !response.body) {
        const problem = await response.json().catch(() => null);
        setError(problem?.detail ?? "Couldn't start the demo. Please try again.");
        return;
      }

      for await (const { event, data } of readEvents(response.body)) {
        if (event === "log") {
          setLogs((previous) => [...previous, JSON.parse(data) as ExecutionLog]);
        } else if (event === "status") {
          last = JSON.parse(data) as DemoRunStatus;
          setStatus(last);
        }
      }
      if (!last || !TERMINAL_STATUSES.includes(last.status)) {
        setError("The demo stopped unexpectedly. Please try again.");
      }
    } catch {
      if (!controller.signal.aborted) {
        setError("Lost connection to the demo. Please try again.");
      }
    } finally {
      setRunning(false);
    }
  }

  return (
    <div className="flex flex-col gap-3">
      <div className="flex flex-wrap items-center gap-4">
        <button
          type="button"
          onClick={run}
          disabled={running}
          className="rounded-md bg-primary px-4 py-2 text-sm font-medium text-background hover:bg-primary-dark disabled:opacity-50"
        >
          {running ? "Running…" : status ? "Run again" : "Run"}
        </button>

        {status && (
          <p className="text-sm" aria-live="polite">
            <span className={`font-medium ${STATUS_COLORS[status.status]}`}>{status.status}</span>
            {status.maxAttempts > 1 && (
              <span className="text-foreground/60">
                {" "}
                · attempt {status.attempt} of {status.maxAttempts}
              </span>
            )}
            {status.status === "RETRYING" && status.retryInSeconds !== null && (
              <span className="text-foreground/60"> · trying again in {status.retryInSeconds}s</span>
            )}
            {TERMINAL_STATUSES.includes(status.status) && (
              <span className="text-foreground/60">
                {status.exitCode !== null && ` · exit code ${status.exitCode}`}
                {status.durationMs !== null && ` · ${(status.durationMs / 1000).toFixed(1)}s`}
              </span>
            )}
          </p>
        )}
      </div>

      {error && <p className="text-sm text-red-600">{error}</p>}

      {status?.errorMessage && status.status !== "RETRYING" && (
        <p className="rounded-md border border-red-600/30 bg-red-600/5 px-3 py-2 text-sm text-red-600">
          {status.errorMessage}
        </p>
      )}

      {(running || logs.length > 0) && (
        <pre className="max-h-96 overflow-auto rounded-md bg-black/90 p-4 text-sm whitespace-pre-wrap break-all">
          {logs.length === 0 ? (
            <div className="text-gray-400">Waiting for output…</div>
          ) : (
            logs.map((log, index) => (
              <div key={index} className={STREAM_COLORS[log.stream]}>
                {log.message}
              </div>
            ))
          )}
        </pre>
      )}
    </div>
  );
}
