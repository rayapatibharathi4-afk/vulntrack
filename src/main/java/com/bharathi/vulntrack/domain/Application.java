package com.bharathi.vulntrack.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "application")
public class Application {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, unique = true, length = 100)
    private String name;

    @Column(name = "repo_url", length = 500)
    private String repoUrl;

    @Column(name = "owner_team", length = 100)
    private String ownerTeam;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Application() {
        // required by JPA
    }

    public Application(String name, String repoUrl, String ownerTeam) {
        this.name = name;
        this.repoUrl = repoUrl;
        this.ownerTeam = ownerTeam;
    }

    @PrePersist
    @SuppressWarnings("unused") // called by Hibernate via reflection
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getRepoUrl() { return repoUrl; }
    public String getOwnerTeam() { return ownerTeam; }
    public Instant getCreatedAt() { return createdAt; }
}