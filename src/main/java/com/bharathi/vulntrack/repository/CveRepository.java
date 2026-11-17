package com.bharathi.vulntrack.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bharathi.vulntrack.domain.Cve;

public interface CveRepository extends JpaRepository<Cve, String> {
    List<Cve> findTop20ByFetchedAtIsNullOrderByIdAsc();
}