package com.bharathi.vulntrack.ingest;

import java.net.URLDecoder;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.util.Optional;

import com.bharathi.vulntrack.domain.Ecosystem;

/** Ecosystem + package name + version, parsed from a package URL (purl). */
public record PackageCoordinates(Ecosystem ecosystem, String name, String version) {

    /**
     * pkg:maven/org.apache.logging.log4j/log4j-core@2.14.1  -> MAVEN, org.apache.logging.log4j:log4j-core, 2.14.1
     * pkg:npm/%40angular/core@12.0.0                       -> NPM, @angular/core, 12.0.0
     */
    public static Optional<PackageCoordinates> fromPurl(String purl) {
        if (purl == null || !purl.startsWith("pkg:")) {
            return Optional.empty();
        }
        int slash = purl.indexOf('/');
        int at = purl.lastIndexOf('@');
        if (slash < 0 || at < slash) {
            return Optional.empty();
        }
        String type = purl.substring("pkg:".length(), slash);
        String path = URLDecoder.decode(purl.substring(slash + 1, at), UTF_8);
        String version = purl.substring(at + 1).split("[?#]", 2)[0];
        if (path.isBlank() || version.isBlank()) {
            return Optional.empty();
        }
        return switch (type) {
            case "maven" -> Optional.of(new PackageCoordinates(Ecosystem.MAVEN, path.replace('/', ':'), version));
            case "npm" -> Optional.of(new PackageCoordinates(Ecosystem.NPM, path, version));
            default -> Optional.empty();
        };
    }
}