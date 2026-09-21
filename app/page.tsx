import Link from "next/link";
import { redirect } from "next/navigation";
import { getSessionToken } from "@/app/lib/session";

export default async function Home() {
  const token = await getSessionToken();
  if (token) {
    redirect("/dashboard");
  }

  return (
    <div className="flex min-h-screen items-center justify-center px-6">
      <div className="flex max-w-xl flex-col gap-6">
        <h1 className="text-4xl font-semibold text-primary-dark">Runway</h1>
        <p className="text-lg">
          A self-hosted job scheduler. Run shell commands and HTTP requests on a cron schedule or on demand,
          with retries, timeouts, and live execution logs.
        </p>
        <div className="flex flex-wrap items-center gap-4">
          <Link
            href="/demo"
            className="rounded-md bg-primary px-5 py-2.5 text-sm font-medium text-background hover:bg-primary-dark"
          >
            Try the demo
          </Link>
          <Link href="/login" className="text-sm underline">
            Log in
          </Link>
          <Link href="/register" className="text-sm underline">
            Create account
          </Link>
        </div>
        <p className="text-sm text-foreground/60">No account needed to try the demo.</p>
      </div>
    </div>
  );
}
