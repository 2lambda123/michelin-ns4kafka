package com.michelin.ns4kafka.properties;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.convert.format.MapFormat;
import io.micronaut.serde.annotation.Serdeable;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@Serdeable
@ConfigurationProperties("ns4kafka.store.kafka.topics")
public class KafkaStoreProperties {
    private String prefix;
    private int replicationFactor;

    @MapFormat(transformation = MapFormat.MapTransformation.FLAT)
    private Map<String, String> props;
}
