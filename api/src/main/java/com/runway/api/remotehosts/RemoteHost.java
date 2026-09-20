package com.runway.api.remotehosts;

import com.runway.api.auth.Organization;
import com.runway.api.credentials.SshCredential;
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
@Table(name = "remote_hosts")
public class RemoteHost {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String hostname;

    @Column(nullable = false)
    private int port;

    @Column(nullable = false)
    private String username;

    @ManyToOne(optional = false)
    @JoinColumn(name = "ssh_credential_id", nullable = false)
    private SshCredential sshCredential;

    @Column(name = "pinned_host_key_fingerprint")
    private String pinnedHostKeyFingerprint;

    @Column(name = "pinned_host_key_algorithm")
    private String pinnedHostKeyAlgorithm;

    @Column(name = "pinned_at")
    private Instant pinnedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected RemoteHost() {
    }

    public RemoteHost(
            Organization organization, String name, String hostname, int port, String username,
            SshCredential sshCredential) {
        this.organization = organization;
        this.name = name;
        this.hostname = hostname;
        this.port = port;
        this.username = username;
        this.sshCredential = sshCredential;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(String name, String hostname, int port, String username, SshCredential sshCredential) {
        boolean hostnameChanged = !this.hostname.equals(hostname);
        this.name = name;
        this.hostname = hostname;
        this.port = port;
        this.username = username;
        this.sshCredential = sshCredential;
        if (hostnameChanged) {
            this.pinnedHostKeyFingerprint = null;
            this.pinnedHostKeyAlgorithm = null;
            this.pinnedAt = null;
        }
        this.updatedAt = Instant.now();
    }

    public void pin(String fingerprint, String algorithm) {
        this.pinnedHostKeyFingerprint = fingerprint;
        this.pinnedHostKeyAlgorithm = algorithm;
        this.pinnedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Organization getOrganization() {
        return organization;
    }

    public String getName() {
        return name;
    }

    public String getHostname() {
        return hostname;
    }

    public int getPort() {
        return port;
    }

    public String getUsername() {
        return username;
    }

    public SshCredential getSshCredential() {
        return sshCredential;
    }

    public String getPinnedHostKeyFingerprint() {
        return pinnedHostKeyFingerprint;
    }

    public String getPinnedHostKeyAlgorithm() {
        return pinnedHostKeyAlgorithm;
    }

    public Instant getPinnedAt() {
        return pinnedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
