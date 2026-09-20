"use client";

export function CancelExecutionButton({ action }: { action: () => Promise<void> }) {
  return (
    <form
      action={action}
      onSubmit={(event) => {
        if (!window.confirm("Cancel this execution?")) {
          event.preventDefault();
        }
      }}
    >
      <button type="submit" className="text-sm text-red-600 underline">
        Cancel
      </button>
    </form>
  );
}
