package com.ns4kafka;

import com.michelin.ns4kafka.models.Namespace;
import com.michelin.ns4kafka.models.Topic;
import com.michelin.ns4kafka.repositories.NamespaceRepository;
import com.michelin.ns4kafka.repositories.TopicRepository;
import com.michelin.ns4kafka.repositories.kafka.KafkaStoreException;
import io.micronaut.configuration.kafka.config.AbstractKafkaConfiguration;
import io.micronaut.context.ApplicationContext;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.util.Collections;
import java.util.Map;

@MicronautTest()
public class KafkaTest {

    @Inject
    NamespaceRepository namespaceRepository;

    @Inject
    EmbeddedServer server;

    @Test
    public void Test(){
        namespaceRepository.createNamespace(
                Namespace.builder()
                        .cluster("cloud")
                        .defaulKafkatUser("test_user")
                        .diskQuota(99)
                        .name("ns01")
                        .build()
        );
        Assertions.assertEquals(1, namespaceRepository.findAllForCluster("cloud").size());
    }
}