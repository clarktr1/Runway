package com.runway.api.credentials;

import com.runway.api.auth.Organization;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ssh_credentials")
public class SshCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(name = "created_by_user_id", nullable = false)
    private UUID createdByUserId;

    @Column(nullable = false)
    private String name;

    @Column(name = "encrypted_private_key", nullable = false)
    private String encryptedPrivateKey;

    @Column(name = "encrypted_passphrase")
    private String encryptedPassphrase;

    @Column(name = "key_fingerprint", nullable = false)
    private String keyFingerprint;

    @Column(name = "public_key_preview")
    private String publicKeyPreview;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SshCredential() {
    }

    public SshCredential(
            Organization organization,
            UUID createdByUserId,
            String name,
            String encryptedPrivateKey,
            String encryptedPassphrase,
            String keyFingerprint,
            String publicKeyPreview) {
        this.organization = organization;
        this.createdByUserId = createdByUserId;
        this.name = name;
        this.encryptedPrivateKey = encryptedPrivateKey;
        this.encryptedPassphrase = encryptedPassphrase;
        this.keyFingerprint = keyFingerprint;
        this.publicKeyPreview = publicKeyPreview;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public Organization getOrganization() {
        return organization;
    }

    public UUID getCreatedByUserId() {
        return createdByUserId;
    }

    public String getName() {
        return name;
    }

    public String getEncryptedPrivateKey() {
        return encryptedPrivateKey;
    }

    public String getEncryptedPassphrase() {
        return encryptedPassphrase;
    }

    public String getKeyFingerprint() {
        return keyFingerprint;
    }

    public String getPublicKeyPreview() {
        return publicKeyPreview;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
