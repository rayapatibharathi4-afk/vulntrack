package com.bharathi.vulntrack.nvd;

/** NVD could not be reached or kept failing. The caller should try again later. */
public class NvdUnavailableException extends RuntimeException {
    public NvdUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}