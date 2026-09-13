import { JobForm } from "@/app/components/JobForm";
import { createJobAction } from "@/app/lib/actions/jobs";

export default function NewJobPage() {
  return (
    <div className="flex max-w-xl flex-col gap-6">
      <h1 className="text-2xl font-semibold">Create Job</h1>
      <JobForm action={createJobAction} submitLabel="Create Job" confirmMessage="Create this job?" />
    </div>
  );
}
