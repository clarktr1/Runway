import { apiFetch } from "@/app/lib/api";
import type { RemoteHost } from "@/app/lib/types";
import { JobForm } from "@/app/components/JobForm";
import { createJobAction } from "@/app/lib/actions/jobs";

export default async function NewJobPage() {
  const remoteHosts = await apiFetch<RemoteHost[]>("/api/remote-hosts");

  return (
    <div className="flex max-w-xl flex-col gap-6">
      <h1 className="text-2xl font-semibold">Create Job</h1>
      <JobForm
        action={createJobAction}
        submitLabel="Create Job"
        confirmMessage="Create this job?"
        remoteHosts={remoteHosts}
      />
    </div>
  );
}
