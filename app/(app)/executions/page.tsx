import { apiFetch } from "@/app/lib/api";
import type { Execution, ExecutionStatus, Page } from "@/app/lib/types";
import { ExecutionsTable } from "@/app/components/ExecutionsTable";

const STATUS_OPTIONS: ExecutionStatus[] = ["QUEUED", "RUNNING", "SUCCESS", "FAILED"];

export default async function ExecutionsPage({
  searchParams,
}: {
  searchParams: Promise<{ status?: string }>;
}) {
  const { status } = await searchParams;
  const query = status ? `?status=${status}` : "";
  const executions = await apiFetch<Page<Execution>>(`/api/executions${query}`);

  return (
    <div className="flex max-w-4xl flex-col gap-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold">Executions</h1>
        <form className="flex items-center gap-2 text-sm">
          <label htmlFor="status" className="text-foreground/60">
            Status
          </label>
          <select
            id="status"
            name="status"
            defaultValue={status ?? ""}
            className="rounded-md border border-foreground/15 bg-transparent px-3 py-1.5"
          >
            <option value="">All</option>
            {STATUS_OPTIONS.map((option) => (
              <option key={option} value={option}>
                {option}
              </option>
            ))}
          </select>
          <button type="submit" className="rounded-md border border-foreground/15 px-3 py-1.5">
            Filter
          </button>
        </form>
      </div>

      <ExecutionsTable executions={executions.content} showJob />
    </div>
  );
}
