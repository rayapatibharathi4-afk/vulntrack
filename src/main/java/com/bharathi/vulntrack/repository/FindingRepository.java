package com.bharathi.vulntrack.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bharathi.vulntrack.domain.Finding;

public interface FindingRepository extends JpaRepository<Finding, Long> {

    Optional<Finding> findByDependencyIdAndCveId(Long dependencyId, String cveId);
}