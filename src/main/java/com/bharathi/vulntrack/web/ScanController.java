package com.bharathi.vulntrack.web;

import java.net.URI;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bharathi.vulntrack.ingest.ScanIngestionService;
import com.bharathi.vulntrack.ingest.ScanResult;
import com.bharathi.vulntrack.web.dto.ScanResponse;

@RestController
@RequestMapping("/api/scans")
public class ScanController {

    private final ScanIngestionService service;

    public ScanController(ScanIngestionService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ScanResult> submit(@RequestParam String application,
                                             @RequestBody String report) {
        ScanResult result = service.ingest(application, report);
        return ResponseEntity
                .created(URI.create("/api/scans/" + result.scanId()))
                .body(result);
    }

    @GetMapping("/{id}")
    public ScanResponse get(@PathVariable Long id) {
        return service.getScan(id);
    }
}