package com.michelin.ns4kafka.model;

import static com.michelin.ns4kafka.util.enumation.Kind.KAFKA_STREAM;

import io.micronaut.core.annotation.Introspected;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Kafka Stream.
 */
@Data
@Introspected
@EqualsAndHashCode(callSuper = true)
public class KafkaStream extends MetadataResource {
    /**
     * Constructor.
     *
     * @param metadata The metadata
     */
    @Builder
    public KafkaStream(Metadata metadata) {
        super("v1", KAFKA_STREAM, metadata);
    }
}
