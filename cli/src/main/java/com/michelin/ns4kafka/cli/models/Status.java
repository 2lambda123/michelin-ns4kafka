package com.michelin.ns4kafka.cli.models;

import io.micronaut.core.annotation.Introspected;
import lombok.*;

import javax.validation.Valid;
import java.util.List;

@Introspected
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
public class Status {
    private final String apiVersion = "v1";
    private final String kind = "Status";

    @Valid
    private ObjectMeta metadata;

    private StatusPhase status;

    private String message;
    private String reason;

    private StatusDetails details;

    private int code;


    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    public static class StatusDetails {
        private String name;
        private String kind;
        private List<StatusCause> causes;
    }

    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    public static class StatusCause {
        private String message;
        private String reason;
        private String field;
    }

    public enum StatusPhase {
        Success,
        Failed
    }

}
