import Link from "next/link";
import { apiFetch } from "@/app/lib/api";
import type { Execution, Job, Page } from "@/app/lib/types";
import { ExecutionsTable } from "@/app/components/ExecutionsTable";

export default async function DashboardPage() {
  const [jobs, executions] = await Promise.all([
    apiFetch<Job[]>("/api/jobs"),
    apiFetch<Page<Execution>>("/api/executions?size=5"),
  ]);

  return (
    <div className="flex max-w-3xl flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold">Overview</h1>
        <p className="mt-1 text-foreground/60">Queueing and worker metrics land here in later phases.</p>
      </div>

      <div className="rounded-lg border border-foreground/10 bg-surface p-6">
        <div className="text-sm text-foreground/60">Jobs</div>
        <div className="text-3xl font-semibold">{jobs.length}</div>
      </div>

      <div>
        <div className="mb-3 flex items-center justify-between">
          <h2 className="text-lg font-semibold">Recent Jobs</h2>
          <Link href="/jobs/new" className="text-sm text-primary-dark underline">
            Create Job
          </Link>
        </div>

        {jobs.length === 0 ? (
          <p className="text-foreground/60">
            No jobs yet.{" "}
            <Link href="/jobs/new" className="text-primary-dark underline">
              Create your first one
            </Link>
            .
          </p>
        ) : (
          <ul className="divide-y divide-foreground/10 rounded-lg border border-foreground/10">
            {jobs.slice(0, 5).map((job) => (
              <li key={job.id} className="flex items-center justify-between px-4 py-3">
                <Link href={`/jobs/${job.id}`} className="font-medium underline">
                  {job.name}
                </Link>
                <span className="text-sm text-foreground/60">
                  {job.enabled ? "Enabled" : "Disabled"}
                </span>
              </li>
            ))}
          </ul>
        )}
      </div>

      <div>
        <div className="mb-3 flex items-center justify-between">
          <h2 className="text-lg font-semibold">Recent Executions</h2>
          <Link href="/executions" className="text-sm text-primary-dark underline">
            View All
          </Link>
        </div>
        <ExecutionsTable executions={executions.content} showJob />
      </div>
    </div>
  );
}
