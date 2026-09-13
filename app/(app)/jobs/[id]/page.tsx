import { notFound } from "next/navigation";
import { apiFetch, ApiError } from "@/app/lib/api";
import type { Execution, Job, Page } from "@/app/lib/types";
import { JobForm } from "@/app/components/JobForm";
import { DeleteJobButton } from "@/app/components/DeleteJobButton";
import { RunJobButton } from "@/app/components/RunJobButton";
import { ExecutionsTable } from "@/app/components/ExecutionsTable";
import { updateJobAction, deleteJobAction } from "@/app/lib/actions/jobs";
import { runJobAction } from "@/app/lib/actions/executions";

export default async function JobDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;

  let job: Job;
  try {
    job = await apiFetch<Job>(`/api/jobs/${id}`);
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) {
      notFound();
    }
    throw error;
  }

  const executions = await apiFetch<Page<Execution>>(`/api/executions?jobId=${id}&size=10`);

  const boundUpdate = updateJobAction.bind(null, id);
  const boundDelete = deleteJobAction.bind(null, id);
  const boundRun = runJobAction.bind(null, id);

  return (
    <div className="flex max-w-xl flex-col gap-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold">{job.name}</h1>
        <div className="flex items-center gap-3">
          <RunJobButton action={boundRun} />
          <DeleteJobButton action={boundDelete} />
        </div>
      </div>

      {job.nextRunAt && (
        <p className="text-sm text-foreground/60">Next run: {new Date(job.nextRunAt).toLocaleString()}</p>
      )}

      <JobForm
        action={boundUpdate}
        submitLabel="Save Changes"
        initialValues={job}
        confirmMessage="Save changes to this job?"
      />

      <div>
        <h2 className="mb-3 text-lg font-semibold">Recent Executions</h2>
        <ExecutionsTable executions={executions.content} />
      </div>
    </div>
  );
}
