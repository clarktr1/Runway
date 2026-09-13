"use client";

export function DeleteJobButton({ action }: { action: () => Promise<void> }) {
  return (
    <form
      action={action}
      onSubmit={(event) => {
        if (!window.confirm("Delete this job? This can't be undone.")) {
          event.preventDefault();
        }
      }}
    >
      <button type="submit" className="text-sm text-red-600 underline">
        Delete
      </button>
    </form>
  );
}
