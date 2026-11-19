package com.bharathi.vulntrack.service;

/** A request that is well formed but breaks a business rule, such as accepting a risk that expires in the past. */
public class InvalidRequestException extends RuntimeException {
    public InvalidRequestException(String message) {
        super(message);
    }
}
