import { notFound } from "next/navigation";
import Link from "next/link";
import { apiFetch, ApiError } from "@/app/lib/api";
import type { Execution, ExecutionLog } from "@/app/lib/types";

const STATUS_COLORS: Record<Execution["status"], string> = {
  QUEUED: "text-foreground/60",
  RUNNING: "text-blue-600",
  SUCCESS: "text-green-600",
  FAILED: "text-red-600",
};

const STREAM_COLORS: Record<ExecutionLog["stream"], string> = {
  STDOUT: "text-foreground",
  STDERR: "text-red-600",
  SYSTEM: "text-foreground/50",
};

export default async function ExecutionDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;

  let execution: Execution;
  try {
    execution = await apiFetch<Execution>(`/api/executions/${id}`);
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) {
      notFound();
    }
    throw error;
  }

  const logs = await apiFetch<ExecutionLog[]>(`/api/executions/${id}/logs`);

  return (
    <div className="flex max-w-3xl flex-col gap-6">
      <div>
        <Link href={`/jobs/${execution.jobId}`} className="text-sm text-foreground/60 underline">
          {execution.jobName}
        </Link>
        <h1 className={`text-2xl font-semibold ${STATUS_COLORS[execution.status]}`}>{execution.status}</h1>
      </div>

      <dl className="grid grid-cols-2 gap-x-6 gap-y-2 text-sm sm:grid-cols-3">
        <div>
          <dt className="text-foreground/60">Trigger</dt>
          <dd>{execution.triggerType}</dd>
        </div>
        <div>
          <dt className="text-foreground/60">Attempt</dt>
          <dd>{execution.attempt}</dd>
        </div>
        <div>
          <dt className="text-foreground/60">Worker</dt>
          <dd>{execution.workerId ?? "—"}</dd>
        </div>
        <div>
          <dt className="text-foreground/60">Queued</dt>
          <dd>{new Date(execution.queuedAt).toLocaleString()}</dd>
        </div>
        <div>
          <dt className="text-foreground/60">Started</dt>
          <dd>{execution.startedAt ? new Date(execution.startedAt).toLocaleString() : "—"}</dd>
        </div>
        <div>
          <dt className="text-foreground/60">Completed</dt>
          <dd>{execution.completedAt ? new Date(execution.completedAt).toLocaleString() : "—"}</dd>
        </div>
        <div>
          <dt className="text-foreground/60">Exit Code</dt>
          <dd>{execution.exitCode ?? "—"}</dd>
        </div>
        <div>
          <dt className="text-foreground/60">Duration</dt>
          <dd>{execution.durationMs !== null ? `${(execution.durationMs / 1000).toFixed(1)}s` : "—"}</dd>
        </div>
      </dl>

      {execution.errorMessage && (
        <p className="rounded-md border border-red-600/30 bg-red-600/5 px-3 py-2 text-sm text-red-600">
          {execution.errorMessage}
        </p>
      )}

      <div>
        <h2 className="mb-2 text-lg font-semibold">Output</h2>
        {logs.length === 0 ? (
          <p className="text-foreground/60">No output captured yet.</p>
        ) : (
          <pre className="overflow-x-auto rounded-md bg-black/90 p-4 text-sm">
            {logs.map((log, index) => (
              <div key={index} className={STREAM_COLORS[log.stream]}>
                {log.message}
              </div>
            ))}
          </pre>
        )}
      </div>
    </div>
  );
}
