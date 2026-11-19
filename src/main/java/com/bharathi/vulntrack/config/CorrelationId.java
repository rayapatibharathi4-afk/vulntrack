package com.bharathi.vulntrack.config;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * One id per request, carried through logs, HTTP responses and Kafka message headers,
 * so one scan can be followed from the POST to the consumer's log lines.
 */
public final class CorrelationId {

    public static final String HTTP_HEADER = "X-Correlation-Id";
    /** Key used in the logging MDC; the log pattern prints it as %X{correlationId}. */
    public static final String MDC_KEY = "correlationId";

    /** Accept only short, plain ids. Anything else is replaced, so clients cannot forge log lines. */
    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9-]{1,64}");

    private CorrelationId() {
    }

    /** Returns the client's id if it is safe to use, otherwise a new one. */
    public static String fromHeaderOrNew(String headerValue) {
        if (headerValue != null && VALID.matcher(headerValue).matches()) {
            return headerValue;
        }
        return UUID.randomUUID().toString();
    }
}
