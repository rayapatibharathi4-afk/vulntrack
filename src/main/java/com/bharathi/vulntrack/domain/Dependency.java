package com.bharathi.vulntrack.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "dependency")
public class Dependency {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    @Enumerated(EnumType.STRING)
    @Column(name = "ecosystem", nullable = false, length = 10)
    private Ecosystem ecosystem;

    @Column(name = "name", nullable = false, length = 300)
    private String name;

    @Column(name = "version", nullable = false, length = 100)
    private String version;

    protected Dependency() {
        // required by JPA
    }

    public Dependency(Application application, Ecosystem ecosystem, String name, String version) {
        this.application = application;
        this.ecosystem = ecosystem;
        this.name = name;
        this.version = version;
    }

    public Long getId() { return id; }
    public Application getApplication() { return application; }
    public Ecosystem getEcosystem() { return ecosystem; }
    public String getName() { return name; }
    public String getVersion() { return version; }
}