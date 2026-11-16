package com.bharathi.vulntrack.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.bharathi.vulntrack.domain.Finding;
import com.bharathi.vulntrack.domain.FindingStatus;

public interface FindingRepository extends JpaRepository<Finding, Long>, JpaSpecificationExecutor<Finding> {

    Optional<Finding> findByDependencyIdAndCveId(Long dependencyId, String cveId);
    List<Finding> findByDependencyApplicationIdAndStatusIn(Long applicationId, Collection<FindingStatus> statuses);

    @Override
    @EntityGraph(attributePaths = {"dependency", "dependency.application", "cve"})
    Page<Finding> findAll(Specification<Finding> spec, Pageable pageable);
}