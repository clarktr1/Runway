package com.runway.api.testsupport;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.util.Arrays;
import org.apache.sshd.common.config.keys.AuthorizedKeyEntry;
import org.apache.sshd.common.config.keys.PublicKeyEntryResolver;
import org.apache.sshd.common.keyprovider.KeyPairProvider;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.shell.ProcessShellCommandFactory;

/**
 * An in-process SSH server backed by a real OS process per exec'd command, used to test
 * {@code SshJobExecutor} and the remote-host "Test Connection" flow without Docker or a real
 * remote box. The host key is generated fresh per instance; callers that need its fingerprint
 * should capture it by actually connecting (e.g. via {@code PinnedHostKeyVerifier} in capture
 * mode), the same way {@code RemoteHostService} establishes a pin — sshj's own fingerprint
 * utility can't serialize a JDK-native {@code Ed25519} key that never round-tripped through
 * sshj's wire-format decoder. The host key itself is RSA rather than Ed25519: both sshj and
 * sshd-core here expect the legacy {@code net.i2p.crypto.eddsa} EdDSA key types for signing/
 * serialization, not the JDK's native provider, so a JDK-generated Ed25519 host key fails to
 * sign the handshake. RSA has no such split and needs no extra dependency.
 */
public class EmbeddedSshServer implements AutoCloseable {

    private final SshServer sshServer;

    public EmbeddedSshServer(String authorizedUsername, String authorizedPublicKeyLine)
            throws NoSuchAlgorithmException, GeneralSecurityException, IOException {
        KeyPairGenerator hostKeyGenerator = KeyPairGenerator.getInstance("RSA");
        hostKeyGenerator.initialize(2048);
        KeyPair hostKeyPair = hostKeyGenerator.generateKeyPair();
        PublicKey authorizedKey = AuthorizedKeyEntry.parseAuthorizedKeyEntry(authorizedPublicKeyLine)
                .resolvePublicKey(null, PublicKeyEntryResolver.IGNORING);

        this.sshServer = SshServer.setUpDefaultServer();
        sshServer.setPort(0);
        sshServer.setKeyPairProvider(KeyPairProvider.wrap(hostKeyPair));
        sshServer.setPublickeyAuthenticator((username, key, session) -> username.equals(authorizedUsername)
                && Arrays.equals(key.getEncoded(), authorizedKey.getEncoded()));
        sshServer.setCommandFactory(ProcessShellCommandFactory.INSTANCE);
        sshServer.start();
    }

    public int getPort() {
        return sshServer.getPort();
    }

    public String getHostname() {
        return "localhost";
    }

    @Override
    public void close() throws IOException {
        sshServer.stop();
    }
}
