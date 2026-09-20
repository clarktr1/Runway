package com.runway.api.ssh;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.runway.api.common.BadRequestException;
import org.junit.jupiter.api.Test;

class SshKeyParserTest {

    private static final String ED25519_PRIVATE_KEY =
            """
            -----BEGIN OPENSSH PRIVATE KEY-----
            b3BlbnNzaC1rZXktdjEAAAAABG5vbmUAAAAEbm9uZQAAAAAAAAABAAAAMwAAAAtzc2gtZW
            QyNTUxOQAAACBhcQqEXXrBSLaF6QmSVbHtU7gu/aub9/ik7pivAwCiBgAAAJhstTV+bLU1
            fgAAAAtzc2gtZWQyNTUxOQAAACBhcQqEXXrBSLaF6QmSVbHtU7gu/aub9/ik7pivAwCiBg
            AAAED+G5biVjxsRt2rGCaDzNCtrNXgCw3RvgTwAeJuXTIeEWFxCoRdesFItoXpCZJVse1T
            uC79q5v3+KTumK8DAKIGAAAAD3J1bndheS10ZXN0LWtleQECAwQFBg==
            -----END OPENSSH PRIVATE KEY-----
            """;

    @Test
    void parsesAnUnencryptedEd25519Key() {
        SshKeyParser.ParsedKey parsed = SshKeyParser.parse(ED25519_PRIVATE_KEY, null);

        assertThat(parsed.fingerprint()).startsWith("SHA256:");
        assertThat(parsed.publicKeyPreview()).startsWith("ssh-ed25519 ");
        assertThat(parsed.keyProvider()).isNotNull();
    }

    @Test
    void isDeterministicAcrossParses() {
        SshKeyParser.ParsedKey first = SshKeyParser.parse(ED25519_PRIVATE_KEY, null);
        SshKeyParser.ParsedKey second = SshKeyParser.parse(ED25519_PRIVATE_KEY, null);

        assertThat(first.fingerprint()).isEqualTo(second.fingerprint());
    }

    @Test
    void rejectsGarbageInput() {
        assertThatThrownBy(() -> SshKeyParser.parse("not a real key", null))
                .isInstanceOf(BadRequestException.class);
    }
}
