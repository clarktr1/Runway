import { apiFetch } from "@/app/lib/api";
import type { Worker } from "@/app/lib/types";
import { WorkersTable } from "@/app/components/WorkersTable";

export default async function WorkersPage() {
  const workers = await apiFetch<Worker[]>("/api/workers");

  return (
    <div className="flex max-w-3xl flex-col gap-6">
      <h1 className="text-2xl font-semibold">Workers</h1>
      <WorkersTable initialWorkers={workers} />
    </div>
  );
}
