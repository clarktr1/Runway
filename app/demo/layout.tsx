import Link from "next/link";
import { getSessionToken } from "@/app/lib/session";

// The demo is public, so it can't share the (app) layout, which requires a session.
export default async function DemoLayout({ children }: { children: React.ReactNode }) {
  const hasSession = Boolean(await getSessionToken());

  return (
    <div className="flex min-h-screen flex-col">
      <header className="flex items-center justify-between border-b border-foreground/10 bg-surface px-6 py-4">
        <Link href="/" className="font-semibold text-primary-dark">
          Runway
        </Link>
        <nav className="flex gap-4 text-sm">
          {hasSession ? (
            <Link href="/dashboard">Dashboard</Link>
          ) : (
            <>
              <Link href="/login">Log in</Link>
              <Link href="/register">Create account</Link>
            </>
          )}
        </nav>
      </header>
      <main className="flex-1 px-6 py-8">{children}</main>
    </div>
  );
}
