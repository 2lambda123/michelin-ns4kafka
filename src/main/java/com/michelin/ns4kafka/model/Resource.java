package com.michelin.ns4kafka.model;

import com.michelin.ns4kafka.util.enumation.Kind;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Resource.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Resource {
    private String apiVersion;
    private Kind kind;
}
