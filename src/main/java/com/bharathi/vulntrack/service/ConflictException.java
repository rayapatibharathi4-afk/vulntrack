package com.bharathi.vulntrack.service;

public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}