package com.runway.api.ssh;

import com.runway.api.common.BadRequestException;
import java.io.IOException;
import java.security.PublicKey;
import java.util.Base64;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.common.Buffer;
import net.schmizz.sshj.userauth.keyprovider.KeyProvider;
import net.schmizz.sshj.userauth.password.PasswordFinder;
import net.schmizz.sshj.userauth.password.PasswordUtils;

public final class SshKeyParser {

    private SshKeyParser() {
    }

    public static ParsedKey parse(String privateKeyPem, String passphrase) {
        try (SSHClient client = new SSHClient()) {
            PasswordFinder passwordFinder =
                    (passphrase == null || passphrase.isBlank())
                            ? null
                            : PasswordUtils.createOneOff(passphrase.toCharArray());
            KeyProvider keyProvider = client.loadKeys(privateKeyPem, null, passwordFinder);
            PublicKey publicKey = keyProvider.getPublic();
            String keyType = keyProvider.getType().toString();
            String fingerprint = SshFingerprint.sha256(publicKey);
            String wireFormat = Base64.getEncoder()
                    .encodeToString(new Buffer.PlainBuffer().putPublicKey(publicKey).getCompactData());
            String preview = keyType + " " + wireFormat;
            return new ParsedKey(keyProvider, fingerprint, preview);
        } catch (IOException e) {
            throw new BadRequestException("Unable to parse the provided SSH private key: " + e.getMessage());
        }
    }

    public record ParsedKey(KeyProvider keyProvider, String fingerprint, String publicKeyPreview) {
    }
}
