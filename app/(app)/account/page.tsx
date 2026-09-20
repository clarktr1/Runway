import { apiFetch } from "@/app/lib/api";
import type { Account, RemoteHost, SshCredential } from "@/app/lib/types";
import { AccountEmailForm } from "@/app/components/AccountEmailForm";
import { AccountPasswordForm } from "@/app/components/AccountPasswordForm";
import { SshCredentialsSection } from "@/app/components/SshCredentialsSection";
import { RemoteHostsSection } from "@/app/components/RemoteHostsSection";

export default async function AccountPage() {
  const [account, credentials, hosts] = await Promise.all([
    apiFetch<Account>("/api/account"),
    apiFetch<SshCredential[]>("/api/ssh-credentials"),
    apiFetch<RemoteHost[]>("/api/remote-hosts"),
  ]);

  return (
    <div className="flex max-w-xl flex-col gap-10">
      <div>
        <h1 className="text-2xl font-semibold">Account</h1>
        <p className="text-sm text-foreground/60">Manage your login, SSH keys, and remote hosts.</p>
      </div>

      <section className="flex flex-col gap-4">
        <h2 className="text-lg font-semibold">Email</h2>
        <AccountEmailForm currentEmail={account.email} />
      </section>

      <section className="flex flex-col gap-4">
        <h2 className="text-lg font-semibold">Password</h2>
        <AccountPasswordForm />
      </section>

      <section className="flex flex-col gap-4">
        <h2 className="text-lg font-semibold">SSH Keys</h2>
        <SshCredentialsSection credentials={credentials} />
      </section>

      <section className="flex flex-col gap-4">
        <h2 className="text-lg font-semibold">Remote Hosts</h2>
        <RemoteHostsSection hosts={hosts} credentials={credentials} />
      </section>
    </div>
  );
}
