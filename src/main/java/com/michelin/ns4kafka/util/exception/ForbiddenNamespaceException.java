package com.michelin.ns4kafka.util.exception;

/**
 * Exception thrown when a namespace is forbidden.
 */
public class ForbiddenNamespaceException extends RuntimeException {
    private static final String MESSAGE = "Accessing forbidden namespace \"%s\"";

    public ForbiddenNamespaceException(String namespace) {
        super(String.format(MESSAGE, namespace));
    }
}
