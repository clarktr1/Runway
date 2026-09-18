import Link from "next/link";
import { verifySession } from "@/app/lib/dal";
import { logoutAction } from "@/app/lib/actions/auth";

export default async function AppLayout({ children }: { children: React.ReactNode }) {
  const session = await verifySession();

  return (
    <div className="flex min-h-screen flex-col">
      <header className="flex items-center justify-between border-b border-foreground/10 bg-surface px-6 py-4">
        <div className="flex items-center gap-6">
          <span className="font-semibold text-primary-dark">Runway</span>
          <nav className="flex gap-4 text-sm">
            <Link href="/dashboard">Dashboard</Link>
            <Link href="/jobs">Jobs</Link>
            <Link href="/executions">Executions</Link>
            <Link href="/workers">Workers</Link>
          </nav>
        </div>
        <div className="flex items-center gap-4 text-sm text-foreground/60">
          <span>{session.email}</span>
          <form action={logoutAction}>
            <button type="submit" className="underline">
              Log out
            </button>
          </form>
        </div>
      </header>
      <main className="flex-1 px-6 py-8">{children}</main>
    </div>
  );
}
