import type { SshCredential } from "@/app/lib/types";
import { deleteSshCredentialAction } from "@/app/lib/actions/sshCredentials";
import { DeleteJobButton } from "@/app/components/DeleteJobButton";
import { SshCredentialForm } from "@/app/components/SshCredentialForm";

export function SshCredentialsSection({ credentials }: { credentials: SshCredential[] }) {
  return (
    <div className="flex flex-col gap-4">
      {credentials.length === 0 ? (
        <p className="text-sm text-foreground/60">No SSH keys yet.</p>
      ) : (
        <ul className="flex flex-col gap-2">
          {credentials.map((credential) => {
            const boundDelete = deleteSshCredentialAction.bind(null, credential.id);
            return (
              <li
                key={credential.id}
                className="flex items-center justify-between rounded-md border border-foreground/15 px-3 py-2"
              >
                <div className="flex flex-col">
                  <span className="text-sm font-medium">{credential.name}</span>
                  <span className="font-mono text-xs text-foreground/60">{credential.keyFingerprint}</span>
                </div>
                <DeleteJobButton
                  action={boundDelete}
                  confirmMessage={`Delete the SSH key "${credential.name}"? This can't be undone.`}
                />
              </li>
            );
          })}
        </ul>
      )}
      <SshCredentialForm />
    </div>
  );
}
