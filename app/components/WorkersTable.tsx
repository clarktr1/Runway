"use client";

import { useEffect, useState } from "react";
import type { Worker } from "@/app/lib/types";

const STATUS_COLORS: Record<Worker["status"], string> = {
  STARTING: "text-foreground/60",
  HEALTHY: "text-green-600",
  BUSY: "text-blue-600",
  OFFLINE: "text-red-600",
};

export function WorkersTable({ initialWorkers }: { initialWorkers: Worker[] }) {
  const [workers, setWorkers] = useState(initialWorkers);

  useEffect(() => {
    const source = new EventSource("/api/workers/stream");

    source.addEventListener("worker", (event) => {
      const updated = JSON.parse(event.data) as Worker;
      setWorkers((prev) => {
        const index = prev.findIndex((worker) => worker.id === updated.id);
        if (index === -1) {
          return [updated, ...prev];
        }
        const next = [...prev];
        next[index] = updated;
        return next;
      });
    });

    return () => source.close();
  }, []);

  if (workers.length === 0) {
    return <p className="text-foreground/60">No workers registered yet.</p>;
  }

  return (
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
  );
}
