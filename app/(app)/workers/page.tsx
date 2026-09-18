import { apiFetch } from "@/app/lib/api";
import type { Worker } from "@/app/lib/types";

const STATUS_COLORS: Record<Worker["status"], string> = {
  STARTING: "text-foreground/60",
  HEALTHY: "text-green-600",
  BUSY: "text-blue-600",
  OFFLINE: "text-red-600",
};

export default async function WorkersPage() {
  const workers = await apiFetch<Worker[]>("/api/workers");

  return (
    <div className="flex max-w-3xl flex-col gap-6">
      <h1 className="text-2xl font-semibold">Workers</h1>

      {workers.length === 0 ? (
        <p className="text-foreground/60">No workers registered yet.</p>
      ) : (
        <table className="w-full border-collapse text-left text-sm">
          <thead>
            <tr className="border-b border-foreground/10 text-foreground/60">
              <th className="py-2 pr-4 font-medium">Worker</th>
              <th className="py-2 pr-4 font-medium">Status</th>
              <th className="py-2 pr-4 font-medium">Started</th>
              <th className="py-2 pr-4 font-medium">Last Heartbeat</th>
            </tr>
          </thead>
          <tbody>
            {workers.map((worker) => (
              <tr key={worker.id} className="border-b border-foreground/5">
                <td className="py-3 pr-4 font-mono text-xs">{worker.id}</td>
                <td className={`py-3 pr-4 font-medium ${STATUS_COLORS[worker.status]}`}>{worker.status}</td>
                <td className="py-3 pr-4">{new Date(worker.startedAt).toLocaleString()}</td>
                <td className="py-3 pr-4">{new Date(worker.lastHeartbeatAt).toLocaleString()}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
