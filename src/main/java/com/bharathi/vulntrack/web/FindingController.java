package com.bharathi.vulntrack.web;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bharathi.vulntrack.domain.FindingStatus;
import com.bharathi.vulntrack.domain.Severity;
import com.bharathi.vulntrack.service.FindingFilter;
import com.bharathi.vulntrack.service.FindingService;
import com.bharathi.vulntrack.web.dto.FindingResponse;
import com.bharathi.vulntrack.web.dto.PageResponse;

@RestController
@RequestMapping("/api/findings")
public class FindingController {

    private final FindingService service;

    public FindingController(FindingService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<FindingResponse> search(
            @RequestParam(required = false) FindingStatus status,
            @RequestParam(required = false) Severity severity,
            @RequestParam(required = false) Long applicationId,
            @RequestParam(defaultValue = "false") boolean overdue,
            @PageableDefault(size = 20, sort = "slaDueAt", direction = Sort.Direction.ASC) Pageable pageable) {

        FindingFilter filter = new FindingFilter(status, severity, applicationId, overdue);
        return PageResponse.from(service.search(filter, pageable));
    }
}