package com.runway.api.ssh;

import java.security.PublicKey;
import java.util.Collections;
import java.util.List;
import net.schmizz.sshj.common.KeyType;
import net.schmizz.sshj.transport.verification.HostKeyVerifier;

/**
 * Trust-on-first-use verifier: with a null {@code expectedFingerprint} it accepts whatever host key is
 * presented (capture mode, used only for an explicit "Test Connection"/pin action). With a non-null
 * {@code expectedFingerprint} it fails closed on any mismatch. Either way, the presented fingerprint is
 * recorded so the caller can display it or persist it as a new pin.
 */
public class PinnedHostKeyVerifier implements HostKeyVerifier {

    private final String expectedFingerprint;
    private String presentedFingerprint;
    private String presentedAlgorithm;

    public PinnedHostKeyVerifier(String expectedFingerprint) {
        this.expectedFingerprint = expectedFingerprint;
    }

    @Override
    public boolean verify(String hostname, int port, PublicKey key) {
        this.presentedFingerprint = SshFingerprint.sha256(key);
        this.presentedAlgorithm = KeyType.fromKey(key).toString();
        return expectedFingerprint == null || expectedFingerprint.equals(presentedFingerprint);
    }

    @Override
    public List<String> findExistingAlgorithms(String hostname, int port) {
        return Collections.emptyList();
    }

    public String getPresentedFingerprint() {
        return presentedFingerprint;
    }

    public String getPresentedAlgorithm() {
        return presentedAlgorithm;
    }

    public boolean isNewPin() {
        return expectedFingerprint == null;
    }
}
