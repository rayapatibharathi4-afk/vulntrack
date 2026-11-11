package com.bharathi.vulntrack.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bharathi.vulntrack.domain.Dependency;
import com.bharathi.vulntrack.domain.Ecosystem;

public interface DependencyRepository extends JpaRepository<Dependency, Long> {

    Optional<Dependency> findByApplicationIdAndEcosystemAndNameAndVersion(
            Long applicationId, Ecosystem ecosystem, String name, String version);
}