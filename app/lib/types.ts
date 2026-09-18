export type JobType = "SHELL" | "HTTP";

export type Job = {
  id: string;
  name: string;
  description: string | null;
  type: JobType;
  configuration: Record<string, unknown>;
  enabled: boolean;
  timeoutSeconds: number | null;
  maxConcurrency: number;
  retryPolicy: Record<string, unknown>;
  cronExpression: string | null;
  nextRunAt: string | null;
  createdAt: string;
  updatedAt: string;
};

export type TriggerType = "SCHEDULED" | "MANUAL" | "RETRY";

export type ExecutionStatus =
  | "QUEUED"
  | "RUNNING"
  | "SUCCESS"
  | "FAILED"
  | "TIMEOUT"
  | "CANCELLED"
  | "RETRYING";

export type Execution = {
  id: string;
  jobId: string;
  jobName: string;
  workerId: string | null;
  triggerType: TriggerType;
  status: ExecutionStatus;
  attempt: number;
  queuedAt: string;
  startedAt: string | null;
  completedAt: string | null;
  exitCode: number | null;
  errorMessage: string | null;
  durationMs: number | null;
  nextAttemptAt: string | null;
};

export type WorkerStatus = "STARTING" | "HEALTHY" | "BUSY" | "OFFLINE";

export type Worker = {
  id: string;
  status: WorkerStatus;
  startedAt: string;
  lastHeartbeatAt: string;
};

export type LogStream = "STDOUT" | "STDERR" | "SYSTEM";

export type ExecutionLog = {
  stream: LogStream;
  message: string;
  createdAt: string;
};

export type Page<T> = {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
};
