import Link from "next/link";
import type { Execution } from "@/app/lib/types";

function formatTimestamp(value: string) {
  return new Date(value).toLocaleString();
}

export function ExecutionsTable({
  executions,
  showJob = false,
}: {
  executions: Execution[];
  showJob?: boolean;
}) {
  if (executions.length === 0) {
    return <p className="text-foreground/60">No executions yet.</p>;
  }

  return (
    <table className="w-full border-collapse text-left text-sm">
      <thead>
        <tr className="border-b border-foreground/10 text-foreground/60">
          {showJob && <th className="py-2 pr-4 font-medium">Job</th>}
          <th className="py-2 pr-4 font-medium">Status</th>
          <th className="py-2 pr-4 font-medium">Trigger</th>
          <th className="py-2 pr-4 font-medium">Queued</th>
          <th className="py-2 pr-4 font-medium">Attempt</th>
        </tr>
      </thead>
      <tbody>
        {executions.map((execution) => (
          <tr key={execution.id} className="border-b border-foreground/5">
            {showJob && (
              <td className="py-3 pr-4">
                <Link href={`/jobs/${execution.jobId}`} className="font-medium underline">
                  {execution.jobName}
                </Link>
              </td>
            )}
            <td className="py-3 pr-4">{execution.status}</td>
            <td className="py-3 pr-4">{execution.triggerType}</td>
            <td className="py-3 pr-4">{formatTimestamp(execution.queuedAt)}</td>
            <td className="py-3 pr-4">{execution.attempt}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
