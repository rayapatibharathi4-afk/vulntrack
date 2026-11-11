package com.bharathi.vulntrack.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bharathi.vulntrack.domain.Cve;

public interface CveRepository extends JpaRepository<Cve, String> {
}