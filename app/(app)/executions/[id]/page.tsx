import { notFound } from "next/navigation";
import { apiFetch, ApiError } from "@/app/lib/api";
import type { Execution, ExecutionLog } from "@/app/lib/types";
import { ExecutionDetail } from "@/app/components/ExecutionDetail";
import { retryExecutionAction, cancelExecutionAction } from "@/app/lib/actions/executions";

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

  const boundRetry = retryExecutionAction.bind(null, execution.id, execution.jobId);
  const boundCancel = cancelExecutionAction.bind(null, execution.id, execution.jobId);

  return (
    <ExecutionDetail execution={execution} logs={logs} retryAction={boundRetry} cancelAction={boundCancel} />
  );
}
