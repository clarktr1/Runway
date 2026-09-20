package com.runway.api.ssh;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.util.Base64;
import net.schmizz.sshj.common.Buffer;

public final class SshFingerprint {

    private SshFingerprint() {
    }

    public static String sha256(PublicKey key) {
        try {
            byte[] wireFormat = new Buffer.PlainBuffer().putPublicKey(key).getCompactData();
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] digest = sha256.digest(wireFormat);
            String base64 = Base64.getEncoder().withoutPadding().encodeToString(digest);
            return "SHA256:" + base64;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
