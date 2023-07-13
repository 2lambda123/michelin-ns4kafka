package com.michelin.ns4kafka.services;

import com.michelin.ns4kafka.models.Namespace;
import com.michelin.ns4kafka.models.ObjectMeta;
import com.michelin.ns4kafka.models.Topic;
import com.michelin.ns4kafka.models.connector.Connector;
import com.michelin.ns4kafka.models.quota.ResourceQuota;
import com.michelin.ns4kafka.models.quota.ResourceQuotaResponse;
import com.michelin.ns4kafka.repositories.ResourceQuotaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.michelin.ns4kafka.models.quota.ResourceQuota.ResourceQuotaSpecKey.*;
import static org.apache.kafka.common.config.TopicConfig.RETENTION_BYTES_CONFIG;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResourceQuotaServiceTest {
    @InjectMocks
    ResourceQuotaService resourceQuotaService;

    @Mock
    ResourceQuotaRepository resourceQuotaRepository;

    @Mock
    TopicService topicService;

    @Mock
    ConnectorService connectorService;

    @Mock
    NamespaceService namespaceService;

    /**
     * Test get quota by namespace when it is defined
     */
    @Test
    void findByNamespace() {
        Namespace ns = Namespace.builder()
                .metadata(ObjectMeta.builder()
                        .name("namespace")
                        .cluster("local")
                        .build())
                .spec(Namespace.NamespaceSpec.builder()
                        .connectClusters(List.of("local-name"))
                        .build())
                .build();

        ResourceQuota resourceQuota = ResourceQuota.builder()
                .metadata(ObjectMeta.builder()
                        .cluster("local")
                        .name("test")
                        .build())
                .spec(Map.of(COUNT_TOPICS.toString(), "1"))
                .build();

        when(resourceQuotaRepository.findForNamespace("namespace"))
                .thenReturn(Optional.of(resourceQuota));

        Optional<ResourceQuota> resourceQuotaOptional = resourceQuotaService.findByNamespace(ns.getMetadata().getName());
        assertTrue(resourceQuotaOptional.isPresent());
        assertEquals("test", resourceQuotaOptional.get().getMetadata().getName());
    }

    /**
     * Test get quota by namespace when it is empty
     */
    @Test
    void findByNamespaceEmpty() {
        Namespace ns = Namespace.builder()
                .metadata(ObjectMeta.builder()
                        .name("namespace")
                        .cluster("local")
                        .build())
                .spec(Namespace.NamespaceSpec.builder()
                        .connectClusters(List.of("local-name"))
                        .build())
                .build();

        when(resourceQuotaRepository.findForNamespace("namespace"))
                .thenReturn(Optional.empty());

        Optional<ResourceQuota> resourceQuotaOptional = resourceQuotaService.findByNamespace(ns.getMetadata().getName());
        assertTrue(resourceQuotaOptional.isEmpty());
    }

    /**
     * Test get quota by name
     */
    @Test
    void findByName() {
        Namespace ns = Namespace.builder()
                .metadata(ObjectMeta.builder()
                        .name("namespace")
                        .cluster("local")
                        .build())
                .spec(Namespace.NamespaceSpec.builder()
                        .connectClusters(List.of("local-name"))
                        .build())
                .build();

        ResourceQuota resourceQuota = ResourceQuota.builder()
                .metadata(ObjectMeta.builder()
                        .cluster("local")
                        .name("test")
                        .build())
                .spec(Map.of(COUNT_TOPICS.toString(), "1"))
                .build();

        when(resourceQuotaRepository.findForNamespace("namespace"))
                .thenReturn(Optional.of(resourceQuota));

        Optional<ResourceQuota> resourceQuotaOptional = resourceQuotaService.findByName(ns.getMetadata().getName(), "test");
        assertTrue(resourceQuotaOptional.isPresent());
        assertEquals("test", resourceQuotaOptional.get().getMetadata().getName());
    }

    /**
     * Test get quota by wrong name
     */
    @Test
    void findByNameWrongName() {
        Namespace ns = Namespace.builder()
                .metadata(ObjectMeta.builder()
                        .name("namespace")
                        .cluster("local")
                        .build())
                .spec(Namespace.NamespaceSpec.builder()
                        .connectClusters(List.of("local-name"))
                        .build())
                .build();

        ResourceQuota resourceQuota = ResourceQuota.builder()
                .metadata(ObjectMeta.builder()
                        .cluster("local")
                        .name("test")
                        .build())
                .spec(Map.of(COUNT_TOPICS.toString(), "1"))
                .build();

        when(resourceQuotaRepository.findForNamespace("namespace"))
                .thenReturn(Optional.of(resourceQuota));

        Optional<ResourceQuota> resourceQuotaOptional = resourceQuotaService.findByName(ns.getMetadata().getName(), "wrong-name");
        assertTrue(resourceQuotaOptional.isEmpty());
    }

    /**
     * Test get quota when there is no quota defined on the namespace
     */
    @Test
    void findByNameEmpty() {
        Namespace ns = Namespace.builder()
                .metadata(ObjectMeta.builder()
                        .name("namespace")
                        .cluster("local")
                        .build())
                .spec(Namespace.NamespaceSpec.builder()
                        .connectClusters(List.of("local-name"))
                        .build())
                .build();

        when(resourceQuotaRepository.findForNamespace("namespace"))
                .thenReturn(Optional.empty());

        Optional<ResourceQuota> resourceQuotaOptional = resourceQuotaService.findByName(ns.getMetadata().getName(), "test");
        assertTrue(resourceQuotaOptional.isEmpty());
    }

    /**
     * Test create quota
     */
    @Test
    void create() {
        ResourceQuota resourceQuota = ResourceQuota.builder()
                .metadata(ObjectMeta.builder()
                        .cluster("local")
                        .name("test")
                        .build())
                .spec(Map.of(COUNT_TOPICS.toString(), "1"))
                .build();

        when(resourceQuotaRepository.create(resourceQuota))
                .thenReturn(resourceQuota);

        ResourceQuota createdResourceQuota = resourceQuotaService.create(resourceQuota);
        assertEquals(resourceQuota, createdResourceQuota);
        verify(resourceQuotaRepository, times(1)).create(resourceQuota);
    }

    /**
     * Test delete quota
     */
    @Test
    void delete() {
        ResourceQuota resourceQuota = ResourceQuota.builder()
                .metadata(ObjectMeta.builder()
                        .cluster("local")
                        .name("test")
                        .build())
                .spec(Map.of(COUNT_TOPICS.toString(), "1"))
                .build();

        doNothing().when(resourceQuotaRepository).delete(resourceQuota);

        resourceQuotaService.delete(resourceQuota);
        verify(resourceQuotaRepository, times(1)).delete(resourceQuota);
    }

    /**
     * Test successful validation when creating quota
     */
    @Test
    void validateNewQuotaAgainstCurrentResourceSuccess() {
        Namespace ns = Namespace.builder()
                .metadata(ObjectMeta.builder()
                        .name("namespace")
                        .cluster("local")
                        .build())
                .spec(Namespace.NamespaceSpec.builder()
                        .connectClusters(List.of("local-name"))
                        .build())
                .build();

        ResourceQuota resourceQuota = ResourceQuota.builder()
                .metadata(ObjectMeta.builder()
                        .cluster("local")
                        .name("test")
                        .build())
                .spec(Map.of(COUNT_TOPICS.toString(), "10"))
                .build();

        Topic topic1 = Topic.builder()
                .metadata(ObjectMeta.builder()
                        .name("topic")
                        .namespace("namespace")
                        .build())
                .build();

        Topic topic2 = Topic.builder()
                .metadata(ObjectMeta.builder()
                        .name("topic")
                        .namespace("namespace")
                        .build())
                .build();

        Topic topic3 = Topic.builder()
                .metadata(ObjectMeta.builder()
                        .name("topic")
                        .namespace("namespace")
                        .build())
                .build();

        when(topicService.findAllForNamespace(ns))
                .thenReturn(List.of(topic1, topic2, topic3));

        List<String> validationErrors = resourceQuotaService.validateNewResourceQuota(ns, resourceQuota);
        assertEquals(0, validationErrors.size());
    }

    /**
     * Test validation when creating quota on count/topics
     */
    @Test
    void validateNewQuotaAgainstCurrentResourceForCountTopics() {
        Namespace ns = Namespace.builder()
                .metadata(ObjectMeta.builder()
                        .name("namespace")
                        .cluster("local")
                        .build())
                .spec(Namespace.NamespaceSpec.builder()
                        .connectClusters(List.of("local-name"))
                        .build())
                .build();

        ResourceQuota resourceQuota = ResourceQuota.builder()
                .metadata(ObjectMeta.builder()
                        .cluster("local")
                        .name("test")
                        .build())
                .spec(Map.of(COUNT_TOPICS.toString(), "2"))
                .build();

        Topic topic1 = Topic.builder()
                .metadata(ObjectMeta.builder()
                        .name("topic")
                        .namespace("namespace")
                        .build())
                .build();

        Topic topic2 = Topic.builder()
                .metadata(ObjectMeta.builder()
                        .name("topic")
                        .namespace("namespace")
                        .build())
                .build();

        Topic topic3 = Topic.builder()
                .metadata(ObjectMeta.builder()
                        .name("topic")
                        .namespace("namespace")
                        .build())
                .build();

        when(topicService.findAllForNamespace(ns))
                .thenReturn(List.of(topic1, topic2, topic3));

        List<String> validationErrors = resourceQuotaService.validateNewResourceQuota(ns, resourceQuota);
        assertEquals(1, validationErrors.size());
        assertEquals("Quota already exceeded for count/topics: 3/2 (used/limit)", validationErrors.get(0));
    }

    /**
     * Test validation when creating quota on count/partitions
     */
    @Test
    void validateNewQuotaAgainstCurrentResourceForCountPartitions() {
        Namespace ns = Namespace.builder()
                .metadata(ObjectMeta.builder()
                        .name("namespace")
                        .cluster("local")
                        .build())
                .spec(Namespace.NamespaceSpec.builder()
                        .connectClusters(List.of("local-name"))
                        .build())
                .build();

        ResourceQuota resourceQuota = ResourceQuota.builder()
                .metadata(ObjectMeta.builder()
                        .cluster("local")
                        .name("test")
                        .build())
                .spec(Map.of(COUNT_PARTITIONS.toString(), "10"))
                .build();

        Topic topic1 = Topic.builder()
                .metadata(ObjectMeta.builder()
                        .name("topic")
                        .namespace("namespace")
                        .build())
                .spec(Topic.TopicSpec.builder()
                        .partitions(6)
                        .build())
                .build();

        Topic topic2 = Topic.builder()
                .metadata(ObjectMeta.builder()
                        .name("topic")
                        .namespace("namespace")
                        .build())
                .spec(Topic.TopicSpec.builder()
                        .partitions(3)
                        .build())
                .build();

        Topic topic3 = Topic.builder()
                .metadata(ObjectMeta.builder()
                        .name("topic")
                        .namespace("namespace")
                        .build())
                .spec(Topic.TopicSpec.builder()
                        .partitions(10)
                        .build())
                .build();

        when(topicService.findAllForNamespace(ns))
                .thenReturn(List.of(topic1, topic2, topic3));

        List<String> validationErrors = resourceQuotaService.validateNewResourceQuota(ns, resourceQuota);
        assertEquals(1, validationErrors.size());
        assertEquals("Quota already exceeded for count/partitions: 19/10 (used/limit)", validationErrors.get(0));
    }

    /**
     * Test format when creating quota on disk/topics
     */
    @Test
    void validateNewQuotaDiskTopicsFormat() {
        Namespace ns = Namespace.builder()
                .metadata(ObjectMeta.builder()
                        .name("namespace")
                        .cluster("local")
                        .build())
                .spec(Namespace.NamespaceSpec.builder()
                        .connectClusters(List.of("local-name"))
                        .build())
                .build();

        ResourceQuota resourceQuota = ResourceQuota.builder()
                .metadata(ObjectMeta.builder()
                        .cluster("local")
                        .name("test")
                        .build())
                .spec(Map.of(DISK_TOPICS.toString(), "10"))
                .build();

        List<String> validationErrors = resourceQuotaService.validateNewResourceQuota(ns, resourceQuota);
        assertEquals(1, validationErrors.size());
        assertEquals("Invalid value for disk/topics: value must end with either B, KiB, MiB or GiB", validationErrors.get(0));
    }

    /**
     * Test validation when creating quota on disk/topics
     */
    @Test
    void validateNewQuotaAgainstCurrentResourceForDiskTopics() {
        Namespace ns = Namespace.builder()
                .metadata(ObjectMeta.builder()
                        .name("namespace")
                        .cluster("local")
                        .build())
                .spec(Namespace.NamespaceSpec.builder()
                        .connectClusters(List.of("local-name"))
                        .build())
                .build();

        ResourceQuota resourceQuota = ResourceQuota.builder()
                .metadata(ObjectMeta.builder()
                        .cluster("local")
                        .name("test")
                        .build())
                .spec(Map.of(DISK_TOPICS.toString(), "5000B"))
                .build();

        Topic topic1 = Topic.builder()
                .metadata(ObjectMeta.builder()
                        .name("topic")
                        .namespace("namespace")
                        .build())
                .spec(Topic.TopicSpec.builder()
                        .partitions(6)
                        .configs(Map.of("retention.bytes", "1000"))
                        .build())
                .build();

        Topic topic2 = Topic.builder()
                .metadata(ObjectMeta.builder()
                        .name("topic")
                        .namespace("namespace")
                        .build())
                .spec(Topic.TopicSpec.builder()
                        .partitions(3)
                        .configs(Map.of("retention.bytes", "1000"))
                        .build())
                .build();

        when(topicService.findAllForNamespace(ns))
                .thenReturn(List.of(topic1, topic2));

        List<String> validationErrors = resourceQuotaService.validateNewResourceQuota(ns, resourceQuota);
        assertEquals(1, validationErrors.size());
        assertEquals("Quota already exceeded for disk/topics: 8.79KiB/5000B (used/limit)", validationErrors.get(0));
    }

    /**
     * Test validation when creating quota on count/connectors
     */
    @Test
    void validateNewQuotaAgainstCurrentResourceForCountConnectors() {
        Namespace ns = Namespace.builder()
                .metadata(ObjectMeta.builder()
                        .name("namespace")
                        .cluster("local")
                        .build())
                .spec(Namespace.NamespaceSpec.builder()
                        .connectClusters(List.of("local-name"))
                        .build())
                .build();

        ResourceQuota resourceQuota = ResourceQuota.builder()
                .metadata(ObjectMeta.builder()
                        .cluster("local")
                        .name("test")
                        .build())
                .spec(Map.of(COUNT_CONNECTORS.toString(), "1"))
                .build();

        when(connectorService.findAllForNamespace(ns))
                .thenReturn(List.of(
                        Connector.builder().metadata(ObjectMeta.builder().name("connect1").build()).build(),
                        Connector.builder().metadata(ObjectMeta.builder().name("connect2").build()).build()));

        List<String> validationErrors = resourceQuotaService.validateNewResourceQuota(ns, resourceQuota);
        assertEquals(1, validationErrors.size());
        assertEquals("Quota already exceeded for count/connectors: 2/1 (used/limit)", validationErrors.get(0));
    }

    /**
     * Test validation errors when creating quota on user/consumer_byte_rate with string instead of number
     */
    @Test
    void validateUserQuotaFormatError() {
        Namespace ns = Namespace.builder()
                .metadata(ObjectMeta.builder()
                        .name("namespace")
                        .cluster("local")
                        .build())
                .build();

        ResourceQuota resourceQuota = ResourceQuota.builder()
                .metadata(ObjectMeta.builder()
                        .cluster("local")
                        .name("test")
                        .build())
                .spec(Map.of(USER_PRODUCER_BYTE_RATE.toString(), "producer", USER_CONSUMER_BYTE_RATE.toString(), "consumer"))
                .build();

        List<String> validationErrors = resourceQuotaService.validateNewResourceQuota(ns, resourceQuota);

        assertEquals(2, validationErrors.size());
        assertEquals("Number expected for user/producer_byte_rate (producer given)", validationErrors.get(0));
        assertEquals("Number expected for user/consumer_byte_rate (consumer given)", validationErrors.get(1));
    }

    /**
     * Test validation when creating quota on user/consumer_byte_rate
     */
    @Test
    void validateUserQuotaFormatSuccess() {
        Namespace ns = Namespace.builder()
                .metadata(ObjectMeta.builder()
                        .name("namespace")
                        .cluster("local")
                        .build())
                .build();

        ResourceQuota resourceQuota = ResourceQuota.builder()
                .metadata(ObjectMeta.builder()
                        .cluster("local")
                        .name("test")
                        .build())
                .spec(Map.of(USER_PRODUCER_BYTE_RATE.toString(), "102400", USER_CONSUMER_BYTE_RATE.toString(), "102400"))
                .build();

        List<String> validationErrors = resourceQuotaService.validateNewResourceQuota(ns, resourceQuota);

        assertEquals(0, validationErrors.size());
    }

    /**
     * Test get current used resource for count topics
     */
    @Test
    void getCurrentUsedResourceForCountTopicsByNamespace() {
        Namespace ns = Namespace.builder()
                .metadata(ObjectMeta.builder()
                        .name("namespace")
                        .cluster("local")
                        .build())
                .spec(Namespace.NamespaceSpec.builder()
                        .connectClusters(List.of("local-name"))
                        .build())
                .build();

        Topic topic1 = Topic.builder()
                .metadata(ObjectMeta.builder()
                        .name("topic")
                        .namespace("namespace")
                        .build())
                .build();

        Topic topic2 = Topic.builder()
                .metadata(ObjectMeta.builder()
                        .name("topic")
                        .namespace("namespace")
                        .build())
                .build();

        Topic topic3 = Topic.builder()
                .metadata(ObjectMeta.builder()
                        .name("topic")
                        .namespace("namespace")
                        .build())
                .build();

        when(topicService.findAllForNamespace(ns))
                .thenReturn(List.of(topic1, topic2, topic3));

        long currentlyUsed = resourceQuotaService.getCurrentCountTopicsByNamespace(ns);
        assertEquals(3L, currentlyUsed);
    }

    /**

     * Test get current used resource for count partitions by namespace
     */
    @Test
    void getCurrentUsedResourceForCountPartitionsByNamespace() {
        Namespace ns = Namespace.builder()
                .metadata(ObjectMeta.builder()
                        .name("namespace")
                        .cluster("local")
                        .build())
                .spec(Namespace.NamespaceSpec.builder()
                        .connectClusters(List.of("local-name"))
                        .build())
                .build();

        Topic topic1 = Topic.builder()
                .metadata(ObjectMeta.builder()
                        .name("topic")
                        .namespace("namespace")
                        .build())
                .spec(Topic.TopicSpec.builder()
                        .partitions(6)
                        .build())
                .build();

        Topic topic2 = Topic.builder()
                .metadata(ObjectMeta.builder()
                        .name("topic")
                        .namespace("namespace")
                        .build())
                .spec(Topic.TopicSpec.builder()
                        .partitions(3)
                        .build())
                .build();

        Topic topic3 = Topic.builder()
                .metadata(ObjectMeta.builder()
                        .name("topic")
                        .namespace("namespace")
                        .build())
                .spec(Topic.TopicSpec.builder()
                        .partitions(10)
                        .build())
                .build();

        when(topicService.findAllForNamespace(ns))
                .thenReturn(List.of(topic1, topic2, topic3));

        long currentlyUsed = resourceQuotaService.getCurrentCountPartitionsByNamespace(ns);
        assertEquals(19L, currentlyUsed);
    }

    /**
     * Test get current used resource for count connectors by namespace
     */
    @Test
    void getCurrentUsedResourceForCountConnectorsByNamespace() {
        Namespace ns = Namespace.builder()
                .metadata(ObjectMeta.builder()
                        .name("namespace")
                        .cluster("local")
                        .build())
                .spec(Namespace.NamespaceSpec.builder()
                        .connectClusters(List.of("local-name"))
                        .build())
                .build();

        when(connectorService.findAllForNamespace(ns))
                .thenReturn(List.of(
                        Connector.builder().metadata(ObjectMeta.builder().name("connect1").build()).build(),
                        Connector.builder().metadata(ObjectMeta.builder().name("connect2").build()).build()));

        long currentlyUsed = resourceQuotaService.getCurrentCountConnectorsByNamespace(ns);
        assertEquals(2L, currentlyUsed);
    }

    /**
     * Test get current used resource for disk topics by namespace
     */
    @Test
    void getCurrentUsedResourceForDiskTopicsByNamespace() {
        Namespace ns = Namespace.builder()
                .metadata(ObjectMeta.builder()
                        .name("namespace")
                        .cluster("local")
                        .build())
                .spec(Namespace.NamespaceSpec.builder()
                        .connectClusters(List.of("local-name"))
                        .build())
                .build();

        Topic topic1 = Topic.builder()
                .metadata(ObjectMeta.builder()
                        .name("topic")
                        .namespace("namespace")
                        .build())
                .spec(Topic.TopicSpec.builder()
                        .partitions(6)
                        .configs(Map.of("retention.bytes", "1000"))
                        .build())
                .build();

        Topic topic2 = Topic.builder()
                .metadata(ObjectMeta.builder()
                        .name("topic")
                        .namespace("namespace")
                        .build())
                .spec(Topic.TopicSpec.builder()
                        .partitions(3)
                        .configs(Map.of("retention.bytes", "50000"))
                        .build())
                .build();

        Topic topic3 = Topic.builder()
                .metadata(ObjectMeta.builder()
                        .name("topic")
                        .namespace("namespace")
                        .build())
                .spec(Topic.TopicSpec.builder()
                        .partitions(10)
                        .configs(Map.of("retention.bytes", "2500"))
                        .build())
                .build();

        when(topicService.findAllForNamespace(ns)).thenReturn(List.of(topic1, topic2, topic3));

        long currentlyUsed = resourceQuotaService.getCurrentDiskTopicsByNamespace(ns);
        assertEquals(181000L, currentlyUsed);
    }

    /**
     * Test quota validation on topics
     */
    @Test
    void validateTopicQuota() {
        Namespace ns = Namespace.builder()
                .metadata(ObjectMeta.builder()
                        .name("namespace")
                        .cluster("local")
                        .build())
                .spec(Namespace.NamespaceSpec.builder()
                        .connectClusters(List.of("local-name"))
                        .build())
                .build();

        ResourceQuota resourceQuota = ResourceQuota.builder()
                .metadata(ObjectMeta.builder()
                        .cluster("local")
                        .name("test")
                        .build())
                .spec(Map.of(COUNT_TOPICS.toString(), "4"))
                .spec(Map.of(COUNT_PARTITIONS.toString(), "25"))
                .spec(Map.of(DISK_TOPICS.toString(), "2GiB"))
                .build();

        Topic newTopic = Topic.builder()
                .metadata(ObjectMeta.builder()
                        .name("topic")
                        .namespace("namespace")
                        .build())
                .spec(Topic.TopicSpec.builder()
                        .partitions(6)
                        .configs(Map.of(RETENTION_BYTES_CONFIG, "1000"))
                        .build())
                .build();


        Topic topic1 = Topic.builder()
                .metadata(ObjectMeta.builder()
                        .name("topic")
                        .namespace("namespace")
                        .build())
                .spec(Topic.TopicSpec.builder()
                        .partitions(6)
                        .configs(Map.of(RETENTION_BYTES_CONFIG, "1000"))
                        .build())
                .build();

        Topic topic2 = Topic.builder()
                .metadata(ObjectMeta.builder()
                        .name("topic")
                        .namespace("namespace")
                        .build())
                .spec(Topic.TopicSpec.builder()
                        .partitions(3)
                        .configs(Map.of(RETENTION_BYTES_CONFIG, "1000"))
                        .build())
                .build();

        Topic topic3 = Topic.builder()
                .metadata(ObjectMeta.builder()
                        .name("topic")
                        .namespace("namespace")
                        .build())
                .spec(Topic.TopicSpec.builder()
                        .partitions(10)
                        .configs(Map.of(RETENTION_BYTES_CONFIG, "1000"))
                        .build())
                .build();

        when(resourceQuotaRepository.findForNamespace("namespace"))
                .thenReturn(Optional.of(resourceQuota));
        when(topicService.findAllForNamespace(ns))
                .thenReturn(List.of(topic1, topic2, topic3));

        List<String> validationErrors = resourceQuotaService.validateTopicQuota(ns, Optional.empty(), newTopic);
        assertEquals(0, validationErrors.size());
    }

    /**
     * Test quota validation on topics when there is no quota defined
     */
    @Test
    void validateTopicQuotaNoQuota() {
        Namespace ns = Namespace.builder()
                .metadata(ObjectMeta.builder()
                        .name("namespace")
                        .cluster("local")
                        .build())
                .spec(Namespace.NamespaceSpec.builder()
                        .connectClusters(List.of("local-name"))
                        .build())
                .build();

        Topic newTopic = Topic.builder()
                .metadata(ObjectMeta.builder()
                        .name("topic")
                        .namespace("namespace")
                        .build())
                .spec(Topic.TopicSpec.builder()
                        .partitions(6)
                        .build())
                .build();

        when(resourceQuotaRepository.findForNamespace("namespace"))
                .thenReturn(Optional.empty());

        List<String> validationErrors = resourceQuotaService.validateTopicQuota(ns, Optional.empty(), newTopic);
        assertEquals(0, validationErrors.size());
    }

    /**
     * Test quota validation on topics when quota is being exceeded
     */
    @Test
    void validateTopicQuotaExceed() {
        Namespace ns = Namespace.builder()
                .metadata(ObjectMeta.builder()
                        .name("namespace")
                        .cluster("local")
                        .build())
                .spec(Namespace.NamespaceSpec.builder()
                        .connectClusters(List.of("local-name"))
                        .build())
                .build();

        ResourceQuota resourceQuota = ResourceQuota.builder()
                .metadata(ObjectMeta.builder()
                        .cluster("local")
                        .name("test")
                        .build())
                .spec(Map.of(COUNT_TOPICS.toString(), "3", COUNT_PARTITIONS.toString(), "20", DISK_TOPICS.toString(), "20KiB"))
                .build();

        Topic newTopic = Topic.builder()
                .metadata(ObjectMeta.builder()
                        .name("topic")
                        .namespace("namespace")
                        .build())
                .spec(Topic.TopicSpec.builder()
                        .partitions(6)
                        .configs(Map.of(RETENTION_BYTES_CONFIG, "1000"))
                        .build())
                .build();


        Topic topic1 = Topic.builder()
                .metadata(ObjectMeta.builder()
                        .name("topic")
                        .namespace("namespace")
                        .build())
                .spec(Topic.TopicSpec.builder()
                        .partitions(6)
                        .configs(Map.of(RETENTION_BYTES_CONFIG, "1000"))
                        .build())
                .build();

        Topic topic2 = Topic.builder()
                .metadata(ObjectMeta.builder()
                        .name("topic")
                        .namespace("namespace")
                        .build())
                .spec(Topic.TopicSpec.builder()
                        .partitions(3)
                        .configs(Map.of(RETENTION_BYTES_CONFIG, "1000"))
                        .build())
                .build();

        Topic topic3 = Topic.builder()
                .metadata(ObjectMeta.builder()
                        .name("topic")
                        .namespace("namespace")
                        .build())
                .spec(Topic.TopicSpec.builder()
                        .partitions(10)
                        .configs(Map.of(RETENTION_BYTES_CONFIG, "1000"))
                        .build())
                .build();

        when(resourceQuotaRepository.findForNamespace("namespace"))
                .thenReturn(Optional.of(resourceQuota));
        when(topicService.findAllForNamespace(ns))
                .thenReturn(List.of(topic1, topic2, topic3));

        List<String> validationErrors = resourceQuotaService.validateTopicQuota(ns, Optional.empty(), newTopic);
        assertEquals(3, validationErrors.size());
        assertEquals("Exceeding quota for count/topics: 3/3 (used/limit). Cannot add 1 topic.", validationErrors.get(0));
        assertEquals("Exceeding quota for count/partitions: 19/20 (used/limit). Cannot add 6 partition(s).", validationErrors.get(1));
        assertEquals("Exceeding quota for disk/topics: 18.555KiB/20.0KiB (used/limit). Cannot add 5.86KiB of data.", validationErrors.get(2));
    }

    /**
     * Test quota validation on topic update when quota is being exceeded
     */
    @Test
    void validateUpdateTopicQuotaExceed() {
        Namespace ns = Namespace.builder()
                .metadata(ObjectMeta.builder()
                        .name("namespace")
                        .cluster("local")
                        .build())
                .spec(Namespace.NamespaceSpec.builder()
                        .connectClusters(List.of("local-name"))
                        .build())
                .build();

        ResourceQuota resourceQuota = ResourceQuota.builder()
                .metadata(ObjectMeta.builder()
                        .cluster("local")
                        .name("test")
                        .build())
                .spec(Map.of(COUNT_TOPICS.toString(), "3", COUNT_PARTITIONS.toString(), "20", DISK_TOPICS.toString(), "20KiB"))
                .build();

        Topic newTopic = Topic.builder()
                .metadata(ObjectMeta.builder()
                        .name("topic")
                        .namespace("namespace")
                        .build())
                .spec(Topic.TopicSpec.builder()
                        .partitions(6)
                        .configs(Map.of(RETENTION_BYTES_CONFIG, "1500"))
                        .build())
                .build();

        Topic topic1 = Topic.builder()
                .metadata(ObjectMeta.builder()
                        .name("topic")
                        .namespace("namespace")
                        .build())
                .spec(Topic.TopicSpec.builder()
                        .partitions(6)
                        .configs(Map.of(RETENTION_BYTES_CONFIG, "1000"))
                        .build())
                .build();

        Topic topic2 = Topic.builder()
                .metadata(ObjectMeta.builder()
                        .name("topic")
                        .namespace("namespace")
                        .build())
                .spec(Topic.TopicSpec.builder()
                        .partitions(3)
                        .configs(Map.of(RETENTION_BYTES_CONFIG, "1000"))
                        .build())
                .build();

        Topic topic3 = Topic.builder()
                .metadata(ObjectMeta.builder()
                        .name("topic")
                        .namespace("namespace")
                        .build())
                .spec(Topic.TopicSpec.builder()
                        .partitions(10)
                        .configs(Map.of(RETENTION_BYTES_CONFIG, "1000"))
                        .build())
                .build();

        when(resourceQuotaRepository.findForNamespace("namespace"))
                .thenReturn(Optional.of(resourceQuota));
        when(topicService.findAllForNamespace(ns))
                .thenReturn(List.of(topic1, topic2, topic3));

        List<String> validationErrors = resourceQuotaService.validateTopicQuota(ns, Optional.of(topic1), newTopic);
        assertEquals(1, validationErrors.size());
        assertEquals("Exceeding quota for disk/topics: 18.555KiB/20.0KiB (used/limit). Cannot add 2.93KiB of data.", validationErrors.get(0));
    }

    /**
     * Test quota validation on connectors
     */
    @Test
    void validateConnectorQuota() {
        Namespace ns = Namespace.builder()
                .metadata(ObjectMeta.builder()
                        .name("namespace")
                        .cluster("local")
                        .build())
                .spec(Namespace.NamespaceSpec.builder()
                        .connectClusters(List.of("local-name"))
                        .build())
                .build();

        ResourceQuota resourceQuota = ResourceQuota.builder()
                .metadata(ObjectMeta.builder()
                        .cluster("local")
                        .name("test")
                        .build())
                .spec(Map.of(COUNT_CONNECTORS.toString(), "3"))
                .build();

        when(resourceQuotaRepository.findForNamespace("namespace"))
                .thenReturn(Optional.of(resourceQuota));
        when(connectorService.findAllForNamespace(ns))
                .thenReturn(List.of(
                        Connector.builder().metadata(ObjectMeta.builder().name("connect1").build()).build(),
                        Connector.builder().metadata(ObjectMeta.builder().name("connect2").build()).build()));

        List<String> validationErrors = resourceQuotaService.validateConnectorQuota(ns);
        assertEquals(0, validationErrors.size());
    }

    /**
     * Test quota validation on connectors when there is no quota defined
     */
    @Test
    void validateConnectorQuotaNoQuota() {
        Namespace ns = Namespace.builder()
                .metadata(ObjectMeta.builder()
                        .name("namespace")
                        .cluster("local")
                        .build())
                .spec(Namespace.NamespaceSpec.builder()
                        .connectClusters(List.of("local-name"))
                        .build())
                .build();

        when(resourceQuotaRepository.findForNamespace("namespace"))
                .thenReturn(Optional.empty());

        List<String> validationErrors = resourceQuotaService.validateConnectorQuota(ns);
        assertEquals(0, validationErrors.size());
    }

    /**
     * Test quota validation on connectors when quota is being exceeded
     */
    @Test
    void validateConnectorQuotaExceed() {
        Namespace ns = Namespace.builder()
                .metadata(ObjectMeta.builder()
                        .name("namespace")
                        .cluster("local")
                        .build())
                .spec(Namespace.NamespaceSpec.builder()
                        .connectClusters(List.of("local-name"))
                        .build())
                .build();

        ResourceQuota resourceQuota = ResourceQuota.builder()
                .metadata(ObjectMeta.builder()
                        .cluster("local")
                        .name("test")
                        .build())
                .spec(Map.of(COUNT_CONNECTORS.toString(), "2"))
                .build();

        when(resourceQuotaRepository.findForNamespace("namespace"))
                .thenReturn(Optional.of(resourceQuota));
        when(connectorService.findAllForNamespace(ns))
                .thenReturn(List.of(
                        Connector.builder().metadata(ObjectMeta.builder().name("connect1").build()).build(),
                        Connector.builder().metadata(ObjectMeta.builder().name("connect2").build()).build()));

        List<String> validationErrors = resourceQuotaService.validateConnectorQuota(ns);
        assertEquals(1, validationErrors.size());
        assertEquals("Exceeding quota for count/connectors: 2/2 (used/limit). Cannot add 1 connector.", validationErrors.get(0));
    }

    /**
     * Validate get current used resources by quota for a namespace
     */
    @Test
    void getUsedResourcesByQuotaByNamespace() {
        Namespace ns = Namespace.builder()
                .metadata(ObjectMeta.builder()
                        .name("namespace")
                        .cluster("local")
                        .build())
                .spec(Namespace.NamespaceSpec.builder()
                        .connectClusters(List.of("local-name"))
                        .build())
                .build();

        ResourceQuota resourceQuota = ResourceQuota.builder()
                .metadata(ObjectMeta.builder()
                        .cluster("local")
                        .name("test")
                        .build())
                .spec(Map.of(COUNT_TOPICS.toString(), "3",
                        COUNT_PARTITIONS.toString(), "20",
                        COUNT_CONNECTORS.toString(), "2",
                        DISK_TOPICS.toString(), "60KiB"))
                .build();

        Topic topic1 = Topic.builder()
                .metadata(ObjectMeta.builder()
                        .name("topic")
                        .namespace("namespace")
                        .build())
                .spec(Topic.TopicSpec.builder()
                        .partitions(6)
                        .configs(Map.of("retention.bytes", "1000"))
                        .build())
                .build();

        Topic topic2 = Topic.builder()
                .metadata(ObjectMeta.builder()
                        .name("topic")
                        .namespace("namespace")
                        .build())
                .spec(Topic.TopicSpec.builder()
                        .partitions(3)
                        .configs(Map.of("retention.bytes", "2000"))
                        .build())
                .build();

        Topic topic3 = Topic.builder()
                .metadata(ObjectMeta.builder()
                        .name("topic")
                        .namespace("namespace")
                        .build())
                .spec(Topic.TopicSpec.builder()
                        .partitions(10)
                        .configs(Map.of("retention.bytes", "4000"))
                        .build())
                .build();

        when(topicService.findAllForNamespace(ns))
                .thenReturn(List.of(topic1, topic2, topic3));
        when(connectorService.findAllForNamespace(ns))
                .thenReturn(List.of(
                        Connector.builder().metadata(ObjectMeta.builder().name("connect1").build()).build(),
                        Connector.builder().metadata(ObjectMeta.builder().name("connect2").build()).build()));

        ResourceQuotaResponse response = resourceQuotaService.getUsedResourcesByQuotaByNamespace(ns, Optional.of(resourceQuota));
        assertEquals(resourceQuota.getMetadata(), response.getMetadata());
        assertEquals("3/3", response.getSpec().getCountTopic());
        assertEquals("19/20", response.getSpec().getCountPartition());
        assertEquals("2/2", response.getSpec().getCountConnector());
        assertEquals("50.782KiB/60KiB", response.getSpec().getDiskTopic());
    }

    /**
     * Validate get current used resources by quota for a namespace when there is no quota applied
     */
    @Test
    void getCurrentResourcesQuotasByNamespaceNoQuota() {
        Namespace ns = Namespace.builder()
                .metadata(ObjectMeta.builder()
                        .name("namespace")
                        .cluster("local")
                        .build())
                .spec(Namespace.NamespaceSpec.builder()
                        .connectClusters(List.of("local-name"))
                        .build())
                .build();

        Topic topic1 = Topic.builder()
                .metadata(ObjectMeta.builder()
                        .name("topic")
                        .namespace("namespace")
                        .build())
                .spec(Topic.TopicSpec.builder()
                        .partitions(6)
                        .configs(Map.of("retention.bytes", "1000"))
                        .build())
                .build();

        Topic topic2 = Topic.builder()
                .metadata(ObjectMeta.builder()
                        .name("topic")
                        .namespace("namespace")
                        .build())
                .spec(Topic.TopicSpec.builder()
                        .partitions(3)
                        .configs(Map.of("retention.bytes", "2000"))
                        .build())
                .build();

        Topic topic3 = Topic.builder()
                .metadata(ObjectMeta.builder()
                        .name("topic")
                        .namespace("namespace")
                        .build())
                .spec(Topic.TopicSpec.builder()
                        .partitions(10)
                        .configs(Map.of("retention.bytes", "4000"))
                        .build())
                .build();

        when(topicService.findAllForNamespace(ns))
                .thenReturn(List.of(topic1, topic2, topic3));
        when(connectorService.findAllForNamespace(ns))
                .thenReturn(List.of(
                        Connector.builder().metadata(ObjectMeta.builder().name("connect1").build()).build(),
                        Connector.builder().metadata(ObjectMeta.builder().name("connect2").build()).build()));

        ResourceQuotaResponse response = resourceQuotaService.getUsedResourcesByQuotaByNamespace(ns, Optional.empty());
        assertEquals("namespace", response.getMetadata().getNamespace());
        assertEquals("local", response.getMetadata().getCluster());
        assertEquals("3", response.getSpec().getCountTopic());
        assertEquals("19", response.getSpec().getCountPartition());
        assertEquals("2", response.getSpec().getCountConnector());
        assertEquals("50.782KiB", response.getSpec().getDiskTopic());
    }

    /**
     * Validate get current used resources by quota for all namespaces
     */
    @Test
    void getCurrentResourcesQuotasAllNamespaces() {
        Namespace ns1 = Namespace.builder()
                .metadata(ObjectMeta.builder()
                        .name("namespace")
                        .cluster("local")
                        .build())
                .spec(Namespace.NamespaceSpec.builder()
                        .connectClusters(List.of("local-name"))
                        .build())
                .build();

        Namespace ns2 = Namespace.builder()
                .metadata(ObjectMeta.builder()
                        .name("namespace2")
                        .cluster("local")
                        .build())
                .spec(Namespace.NamespaceSpec.builder()
                        .connectClusters(List.of("local-name"))
                        .build())
                .build();

        Namespace ns3 = Namespace.builder()
                .metadata(ObjectMeta.builder()
                        .name("namespace3")
                        .cluster("local")
                        .build())
                .spec(Namespace.NamespaceSpec.builder()
                        .connectClusters(List.of("local-name"))
                        .build())
                .build();

        Namespace ns4 = Namespace.builder()
                .metadata(ObjectMeta.builder()
                        .name("namespace4")
                        .cluster("local")
                        .build())
                .spec(Namespace.NamespaceSpec.builder()
                        .connectClusters(List.of("local-name"))
                        .build())
                .build();

        ResourceQuota resourceQuota = ResourceQuota.builder()
                .metadata(ObjectMeta.builder()
                        .cluster("local")
                        .name("test")
                        .build())
                .spec(Map.of(COUNT_TOPICS.toString(), "3",
                        COUNT_PARTITIONS.toString(), "20",
                        COUNT_CONNECTORS.toString(), "2",
                        DISK_TOPICS.toString(), "60KiB"))
                .build();

        Topic topic1 = Topic.builder()
                .metadata(ObjectMeta.builder()
                        .name("topic")
                        .namespace("namespace")
                        .build())
                .spec(Topic.TopicSpec.builder()
                        .partitions(6)
                        .configs(Map.of("retention.bytes", "1000"))
                        .build())
                .build();

        Topic topic2 = Topic.builder()
                .metadata(ObjectMeta.builder()
                        .name("topic")
                        .namespace("namespace2")
                        .build())
                .spec(Topic.TopicSpec.builder()
                        .partitions(3)
                        .configs(Map.of("retention.bytes", "2000"))
                        .build())
                .build();

        Topic topic3 = Topic.builder()
                .metadata(ObjectMeta.builder()
                        .name("topic")
                        .namespace("namespace3")
                        .build())
                .spec(Topic.TopicSpec.builder()
                        .partitions(10)
                        .configs(Map.of("retention.bytes", "4000"))
                        .build())
                .build();

        when(namespaceService.listAll())
                .thenReturn(List.of(ns1, ns2, ns3, ns4));
        when(topicService.findAllForNamespace(ns1))
                .thenReturn(List.of(topic1));
        when(topicService.findAllForNamespace(ns2))
                .thenReturn(List.of(topic2));
        when(topicService.findAllForNamespace(ns3))
                .thenReturn(List.of(topic3));
        when(topicService.findAllForNamespace(ns4))
                .thenReturn(List.of());
        when(connectorService.findAllForNamespace(any()))
                .thenReturn(List.of(
                        Connector.builder().metadata(ObjectMeta.builder().name("connect1").build()).build(),
                        Connector.builder().metadata(ObjectMeta.builder().name("connect2").build()).build()));
        when(resourceQuotaRepository.findForNamespace(any()))
                .thenReturn(Optional.of(resourceQuota));

        List<ResourceQuotaResponse> response = resourceQuotaService.getUsedResourcesByQuotaForAllNamespaces();
        assertEquals(4, response.size());
    }
}
