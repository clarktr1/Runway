import type { RemoteHost, SshCredential } from "@/app/lib/types";
import {
  createRemoteHostAction,
  deleteRemoteHostAction,
  repinRemoteHostAction,
  testRemoteHostConnectionAction,
} from "@/app/lib/actions/remoteHosts";
import { DeleteJobButton } from "@/app/components/DeleteJobButton";
import { RemoteHostForm } from "@/app/components/RemoteHostForm";
import { TestConnectionButton } from "@/app/components/TestConnectionButton";

export function RemoteHostsSection({
  hosts,
  credentials,
}: {
  hosts: RemoteHost[];
  credentials: SshCredential[];
}) {
  return (
    <div className="flex flex-col gap-4">
      {hosts.length === 0 ? (
        <p className="text-sm text-foreground/60">No remote hosts yet.</p>
      ) : (
        <ul className="flex flex-col gap-3">
          {hosts.map((host) => {
            const boundDelete = deleteRemoteHostAction.bind(null, host.id);
            return (
              <li key={host.id} className="flex flex-col gap-2 rounded-md border border-foreground/15 px-3 py-2">
                <div className="flex items-center justify-between">
                  <div className="flex flex-col">
                    <span className="text-sm font-medium">{host.name}</span>
                    <span className="text-xs text-foreground/60">
                      {host.username}@{host.hostname}:{host.port} · {host.sshCredentialName}
                    </span>
                  </div>
                  <DeleteJobButton
                    action={boundDelete}
                    confirmMessage={`Delete the remote host "${host.name}"? This can't be undone.`}
                  />
                </div>

                {host.pinnedHostKeyFingerprint ? (
                  <p className="font-mono text-xs text-foreground/60">
                    Pinned host key: {host.pinnedHostKeyFingerprint}
                  </p>
                ) : (
                  <p className="text-xs text-amber-600">
                    Not verified yet — jobs against this host will fail until you test the connection.
                  </p>
                )}

                <div className="flex items-center gap-3">
                  <TestConnectionButton hostId={host.id} action={testRemoteHostConnectionAction} />
                  {host.pinnedHostKeyFingerprint && (
                    <TestConnectionButton
                      hostId={host.id}
                      action={repinRemoteHostAction}
                      label="Re-pin"
                      confirmMessage="This host's key has changed since it was last verified. Only re-pin if you intended to rotate or replace this server."
                    />
                  )}
                </div>
              </li>
            );
          })}
        </ul>
      )}
      <RemoteHostForm action={createRemoteHostAction} credentials={credentials} submitLabel="Add Remote Host" />
    </div>
  );
}
