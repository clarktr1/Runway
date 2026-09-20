package com.runway.api.worker;

import com.runway.api.credentials.CredentialEncryptionService;
import com.runway.api.credentials.SshCredential;
import com.runway.api.executions.CancellationToken;
import com.runway.api.executions.ExecutionService.LogLine;
import com.runway.api.executions.LogStream;
import com.runway.api.jobs.Job;
import com.runway.api.jobs.JobType;
import com.runway.api.remotehosts.RemoteHost;
import com.runway.api.remotehosts.RemoteHostRepository;
import com.runway.api.ssh.PinnedHostKeyVerifier;
import com.runway.api.ssh.SshKeyParser;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.connection.ConnectionException;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.userauth.keyprovider.KeyProvider;
import org.springframework.stereotype.Component;

/**
 * Unlike {@link ShellJobExecutor}, cancellation here is best-effort: closing the SSH client tears
 * down the local channel and stream reads, but does not guarantee the remote process is killed
 * (there is no equivalent of {@code Process#destroyForcibly()} across an SSH exec channel).
 */
@Component
public class SshJobExecutor implements JobExecutor {

    private static final long UNLIMITED_TIMEOUT_SECONDS = TimeUnit.DAYS.toSeconds(365);
    private static final int CONNECT_TIMEOUT_MS = 10_000;

    private final RemoteHostRepository remoteHostRepository;
    private final CredentialEncryptionService encryptionService;

    public SshJobExecutor(RemoteHostRepository remoteHostRepository, CredentialEncryptionService encryptionService) {
        this.remoteHostRepository = remoteHostRepository;
        this.encryptionService = encryptionService;
    }

    @Override
    public JobType supports() {
        return JobType.SSH_COMMAND;
    }

    @Override
    public Outcome execute(Job job, Consumer<LogLine> logSink, CancellationToken cancellationToken) {
        Map<String, Object> configuration = job.getConfiguration();
        UUID remoteHostId;
        try {
            remoteHostId = UUID.fromString(String.valueOf(configuration.get("remoteHostId")));
        } catch (IllegalArgumentException e) {
            return new Outcome(Result.FAILED, null, "configuration.remoteHostId is not a valid id");
        }
        String command = String.valueOf(configuration.get("command"));

        RemoteHost host = remoteHostRepository
                .findByIdAndOrganizationId(remoteHostId, job.getOrganization().getId())
                .orElse(null);
        if (host == null) {
            return new Outcome(Result.FAILED, null, "Remote host not found");
        }
        if (host.getPinnedHostKeyFingerprint() == null) {
            return new Outcome(
                    Result.FAILED, null, "Remote host has not been verified yet — use Test Connection first");
        }

        SshCredential credential = host.getSshCredential();
        String privateKey = encryptionService.decrypt(credential.getEncryptedPrivateKey());
        String passphrase = credential.getEncryptedPassphrase() == null
                ? null
                : encryptionService.decrypt(credential.getEncryptedPassphrase());

        long timeoutSeconds = job.getTimeoutSeconds() != null ? job.getTimeoutSeconds() : UNLIMITED_TIMEOUT_SECONDS;

        try (SSHClient client = new SSHClient()) {
            client.setConnectTimeout(CONNECT_TIMEOUT_MS);
            client.addHostKeyVerifier(new PinnedHostKeyVerifier(host.getPinnedHostKeyFingerprint()));
            client.connect(host.getHostname(), host.getPort());

            KeyProvider keyProvider = SshKeyParser.parse(privateKey, passphrase).keyProvider();
            client.authPublickey(host.getUsername(), keyProvider);

            cancellationToken.bind(() -> {
                try {
                    client.close();
                } catch (IOException ignored) {
                    // best-effort; the channel/session reads below unwind once the client closes
                }
            });

            try (Session session = client.startSession()) {
                return runCommand(session, command, timeoutSeconds, logSink, cancellationToken);
            }
        } catch (IOException e) {
            if (cancellationToken.isCancelled()) {
                return new Outcome(Result.CANCELLED, null, "Execution cancelled");
            }
            return new Outcome(Result.FAILED, null, "SSH connection failed: " + e.getMessage());
        }
    }

    private Outcome runCommand(
            Session session,
            String command,
            long timeoutSeconds,
            Consumer<LogLine> logSink,
            CancellationToken cancellationToken)
            throws IOException {
        Session.Command exec = session.exec(command);

        Thread stdout = streamReader(exec.getInputStream(), LogStream.STDOUT, logSink);
        Thread stderr = streamReader(exec.getErrorStream(), LogStream.STDERR, logSink);
        stdout.start();
        stderr.start();

        boolean timedOut = false;
        try {
            exec.join(timeoutSeconds, TimeUnit.SECONDS);
        } catch (ConnectionException e) {
            if (cancellationToken.isCancelled()) {
                timedOut = false;
            } else {
                timedOut = true;
                closeQuietly(exec);
            }
        }

        joinQuietly(stdout);
        joinQuietly(stderr);

        if (cancellationToken.isCancelled()) {
            return new Outcome(Result.CANCELLED, null, "Execution cancelled");
        }
        if (timedOut) {
            return new Outcome(Result.TIMEOUT, null, "Execution exceeded timeout of " + timeoutSeconds + "s");
        }

        Integer exitStatus = exec.getExitStatus();
        boolean success = exitStatus != null && exitStatus == 0;
        return new Outcome(
                success ? Result.SUCCESS : Result.FAILED,
                exitStatus,
                success ? null : "Remote command exited with code " + exitStatus);
    }

    private void closeQuietly(Session.Command exec) {
        try {
            exec.close();
        } catch (IOException ignored) {
            // already unwinding after a timeout
        }
    }

    private void joinQuietly(Thread thread) {
        try {
            thread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private Thread streamReader(InputStream stream, LogStream logStream, Consumer<LogLine> logSink) {
        Thread thread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logSink.accept(new LogLine(logStream, line, Instant.now()));
                }
            } catch (IOException ignored) {
                // stream closed once the channel exits
            }
        });
        thread.setDaemon(true);
        return thread;
    }
}
