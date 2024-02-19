package com.michelin.ns4kafka.utils.exceptions;

import java.io.Serial;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Resource validation exception.
 */
@Getter
@AllArgsConstructor
public class ResourceValidationException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 32400191899153204L;

    private final String kind;

    private final String name;

    private final List<String> validationErrors;

    /**
     * Constructor.
     *
     * @param kind            The kind of the resource
     * @param name            The name of the resource
     * @param validationError The validation error
     */
    public ResourceValidationException(String kind, String name, String validationError) {
        this.kind = kind;
        this.name = name;
        this.validationErrors = List.of(validationError);
    }
}
