package com.michelin.ns4kafka.repository.kafka;

import com.michelin.ns4kafka.model.connect.cluster.ConnectCluster;
import com.michelin.ns4kafka.repository.ConnectClusterRepository;
import io.micronaut.configuration.kafka.annotation.KafkaClient;
import io.micronaut.configuration.kafka.annotation.KafkaListener;
import io.micronaut.configuration.kafka.annotation.OffsetReset;
import io.micronaut.configuration.kafka.annotation.OffsetStrategy;
import io.micronaut.configuration.kafka.annotation.Topic;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.Producer;

/**
 * Kafka Connect Cluster repository.
 */
@Singleton
@KafkaListener(
    offsetReset = OffsetReset.EARLIEST,
    groupId = "${ns4kafka.store.kafka.group-id}",
    offsetStrategy = OffsetStrategy.DISABLED
)
public class KafkaConnectClusterRepository extends KafkaStore<ConnectCluster> implements ConnectClusterRepository {
    public KafkaConnectClusterRepository(
        @Value("${ns4kafka.store.kafka.topics.prefix}.connect-workers") String kafkaTopic,
        @KafkaClient("connect-workers") Producer<String, ConnectCluster> kafkaProducer) {
        super(kafkaTopic, kafkaProducer);
    }

    @Override
    public List<ConnectCluster> findAll() {
        return new ArrayList<>(getKafkaStore().values());
    }

    @Override
    public List<ConnectCluster> findAllForCluster(String cluster) {
        return getKafkaStore().values().stream()
            .filter(connectCluster -> connectCluster.getMetadata().getCluster().equals(cluster))
            .toList();
    }

    @Override
    public ConnectCluster create(ConnectCluster connectCluster) {
        return this.produce(getMessageKey(connectCluster), connectCluster);
    }

    @Override
    public void delete(ConnectCluster connectCluster) {
        this.produce(getMessageKey(connectCluster), null);
    }

    @Override
    @Topic(value = "${ns4kafka.store.kafka.topics.prefix}.connect-workers")
    void receive(ConsumerRecord<String, ConnectCluster> message) {
        super.receive(message);
    }

    @Override
    String getMessageKey(ConnectCluster connectCluster) {
        return connectCluster.getMetadata().getNamespace() + "/" + connectCluster.getMetadata().getName();
    }
}
