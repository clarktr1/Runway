"use client";

import { useState } from "react";
import cronstrue from "cronstrue";

const CUSTOM = "__custom__";

const SCHEDULE_PRESETS = [
  { label: "Manual only", value: "" },
  { label: "Every hour", value: "0 * * * *" },
  { label: "Every 6 hours", value: "0 */6 * * *" },
  { label: "Every day at 2:00 AM", value: "0 2 * * *" },
  { label: "Every Monday at 9:00 AM", value: "0 9 * * 1" },
  { label: "Custom…", value: CUSTOM },
];

function describeCron(expression: string) {
  try {
    return cronstrue.toString(expression);
  } catch {
    return "Unrecognized cron expression";
  }
}

export function ScheduleField({ initialValue }: { initialValue: string }) {
  const preset = SCHEDULE_PRESETS.find((p) => p.value === initialValue && p.value !== CUSTOM);
  const [mode, setMode] = useState(preset ? preset.value : initialValue ? CUSTOM : "");
  const [cronExpression, setCronExpression] = useState(initialValue);

  return (
    <div className="flex flex-col gap-1">
      <span className="text-sm font-medium">Schedule</span>
      <select
        value={mode}
        onChange={(event) => {
          const value = event.target.value;
          setMode(value);
          if (value !== CUSTOM) {
            setCronExpression(value);
          }
        }}
        className="rounded-md border border-foreground/15 bg-transparent px-3 py-2 focus:border-primary focus:outline-none"
      >
        {SCHEDULE_PRESETS.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>

      {mode === CUSTOM && (
        <input
          value={cronExpression}
          onChange={(event) => setCronExpression(event.target.value)}
          placeholder="0 2 * * *"
          className="mt-1 rounded-md border border-foreground/15 bg-transparent px-3 py-2 font-mono text-sm focus:border-primary focus:outline-none"
        />
      )}

      {cronExpression && <p className="mt-1 text-xs text-foreground/60">{describeCron(cronExpression)}</p>}

      <input type="hidden" name="cronExpression" value={cronExpression} />
    </div>
  );
}
