package com.bharathi.vulntrack.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bharathi.vulntrack.domain.Scan;

public interface ScanRepository extends JpaRepository<Scan, Long> {
}