import Link from "next/link";
import { apiFetch } from "@/app/lib/api";
import type { Job } from "@/app/lib/types";
import { JobEnabledToggle } from "@/app/components/JobEnabledToggle";
import { toggleJobEnabledAction } from "@/app/lib/actions/jobs";

export default async function JobsPage() {
  const jobs = await apiFetch<Job[]>("/api/jobs");

  return (
    <div className="flex max-w-4xl flex-col gap-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold">Jobs</h1>
        <Link
          href="/jobs/new"
          className="rounded-md bg-primary px-4 py-2 text-sm font-medium text-background"
        >
          Create Job
        </Link>
      </div>

      {jobs.length === 0 ? (
        <p className="text-foreground/60">No jobs yet.</p>
      ) : (
        <table className="w-full border-collapse text-left text-sm">
          <thead>
            <tr className="border-b border-foreground/10 text-foreground/60">
              <th className="py-2 pr-4 font-medium">Name</th>
              <th className="py-2 pr-4 font-medium">Type</th>
              <th className="py-2 pr-4 font-medium">Schedule</th>
              <th className="py-2 pr-4 font-medium">Next Run</th>
              <th className="py-2 pr-4 font-medium">Status</th>
            </tr>
          </thead>
          <tbody>
            {jobs.map((job) => (
              <tr key={job.id} className="border-b border-foreground/5">
                <td className="py-3 pr-4">
                  <Link href={`/jobs/${job.id}`} className="font-medium underline">
                    {job.name}
                  </Link>
                </td>
                <td className="py-3 pr-4">{job.type}</td>
                <td className="py-3 pr-4">{job.cronExpression ?? "Manual only"}</td>
                <td className="py-3 pr-4">
                  {job.enabled && job.nextRunAt ? new Date(job.nextRunAt).toLocaleString() : "—"}
                </td>
                <td className="py-3 pr-4">
                  <div className="flex items-center gap-2">
                    <JobEnabledToggle job={job} action={toggleJobEnabledAction} />
                    <span className="text-foreground/60">{job.enabled ? "Enabled" : "Disabled"}</span>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
