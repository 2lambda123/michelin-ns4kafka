package com.michelin.ns4kafka.repositories.kafka;

/**
 * Kafka Store Exception.
 */
public class KafkaStoreException extends RuntimeException {

    public KafkaStoreException(String message, Throwable cause) {
        super(message, cause);
    }

    public KafkaStoreException(String message) {
        super(message);
    }
}
