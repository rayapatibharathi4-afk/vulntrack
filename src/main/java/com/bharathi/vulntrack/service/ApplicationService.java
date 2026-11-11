package com.bharathi.vulntrack.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bharathi.vulntrack.domain.Application;
import com.bharathi.vulntrack.repository.ApplicationRepository;
import com.bharathi.vulntrack.web.dto.ApplicationResponse;
import com.bharathi.vulntrack.web.dto.CreateApplicationRequest;

@Service
public class ApplicationService {

    private final ApplicationRepository applications;

    public ApplicationService(ApplicationRepository applications) {
        this.applications = applications;
    }

    @Transactional
    public ApplicationResponse create(CreateApplicationRequest request) {
        if (applications.existsByName(request.name())) {
            throw new ConflictException("Application '" + request.name() + "' already exists");
        }
        Application saved = applications.save(
                new Application(request.name(), request.repoUrl(), request.ownerTeam()));
        return ApplicationResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public Page<ApplicationResponse> list(Pageable pageable) {
        return applications.findAll(pageable).map(ApplicationResponse::from);
    }
}