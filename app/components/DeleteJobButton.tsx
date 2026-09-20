"use client";

export function DeleteJobButton({
  action,
  confirmMessage = "Delete this job? This can't be undone.",
}: {
  action: () => Promise<void>;
  confirmMessage?: string;
}) {
  return (
    <form
      action={action}
      onSubmit={(event) => {
        if (!window.confirm(confirmMessage)) {
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
