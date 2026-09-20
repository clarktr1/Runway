"use client";

export function RetryExecutionButton({ action }: { action: () => Promise<void> }) {
  return (
    <form action={action}>
      <button
        type="submit"
        className="rounded-md bg-primary px-4 py-2 text-sm font-medium text-background hover:bg-primary-dark"
      >
        Retry
      </button>
    </form>
  );
}
