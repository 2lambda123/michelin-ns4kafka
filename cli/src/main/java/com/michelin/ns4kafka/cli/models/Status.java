package com.michelin.ns4kafka.cli.models;

import io.micronaut.core.annotation.Introspected;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Introspected
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
public class Status {
    private final String apiVersion = "v1";
    private final String kind = "Status";

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
        private List<String> causes;
    }

    public enum StatusPhase {
        Success,
        Failed
    }
}
