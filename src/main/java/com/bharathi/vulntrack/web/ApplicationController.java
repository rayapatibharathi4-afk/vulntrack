package com.bharathi.vulntrack.web;

import java.net.URI;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bharathi.vulntrack.service.ApplicationService;
import com.bharathi.vulntrack.web.dto.ApplicationResponse;
import com.bharathi.vulntrack.web.dto.CreateApplicationRequest;
import com.bharathi.vulntrack.web.dto.PageResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/applications")
public class ApplicationController {

    private final ApplicationService service;

    public ApplicationController(ApplicationService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ApplicationResponse> create(@Valid @RequestBody CreateApplicationRequest request) {
        ApplicationResponse created = service.create(request);
        return ResponseEntity
                .created(URI.create("/api/applications/" + created.id()))
                .body(created);
    }

    @GetMapping
    public PageResponse<ApplicationResponse> list(
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return PageResponse.from(service.list(pageable));
    }
}