package com.bharathi.vulntrack.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bharathi.vulntrack.domain.Application;

public interface ApplicationRepository extends JpaRepository<Application, Long> {

    Optional<Application> findByName(String name);

    boolean existsByName(String name);
}