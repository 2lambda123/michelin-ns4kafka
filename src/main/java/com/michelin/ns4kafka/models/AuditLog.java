package com.michelin.ns4kafka.models;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.michelin.ns4kafka.utils.enums.ApplyStatus;
import com.michelin.ns4kafka.utils.enums.Kind;
import java.util.Date;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Audit log.
 */
@Data
@AllArgsConstructor
public class AuditLog {
    private String user;
    private boolean admin;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Date date;
    private Kind kind;
    private Metadata metadata;
    private ApplyStatus operation;
    private Object before;
    private Object after;
}
