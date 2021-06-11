package com.michelin.ns4kafka.integration;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.michelin.ns4kafka.models.*;
import com.michelin.ns4kafka.models.AccessControlEntry.AccessControlEntrySpec;
import com.michelin.ns4kafka.models.AccessControlEntry.Permission;
import com.michelin.ns4kafka.models.AccessControlEntry.ResourcePatternType;
import com.michelin.ns4kafka.models.AccessControlEntry.ResourceType;
import com.michelin.ns4kafka.models.Namespace.NamespaceSpec;
import com.michelin.ns4kafka.models.RoleBinding.*;
import com.michelin.ns4kafka.models.Topic.TopicSpec;
import com.michelin.ns4kafka.validation.TopicValidator;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.PropertySource;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.RxHttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.security.authentication.UsernamePasswordCredentials;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Collection;
import java.util.List;
import java.util.Map;


@Testcontainers
public class NamespaceReadAccessToTopic {

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:6.2.0"))
            .withEmbeddedZookeeper()
            .withExposedPorts(9093);


    static RxHttpClient client;
    static EmbeddedServer server;
    static ApplicationContext context;

    @BeforeAll
    public static void initUnitTest() throws InterruptedException {
        kafka.start();
        server = ApplicationContext.run(EmbeddedServer.class, PropertySource.of(
                "test", Map.of(
                        "kafka.bootstrap.servers", kafka.getBootstrapServers(),
                        "ns4kafka.managed-clusters.test-cluster.config.bootstrap.servers", kafka.getBootstrapServers(),
                        "kafka.embedded.enabled", "false"
                )), "test");

        client = RxHttpClient.create(server.getURL());
    }

    @Test
    void unauthorizedModifications() throws InterruptedException {

        Namespace ns1 = Namespace.builder()
            .metadata(ObjectMeta.builder()
                      .name("ns1")
                      .cluster("test-cluster")
                      .build())
            .spec(NamespaceSpec.builder()
                  .kafkaUser("user1")
                  .connectClusters(List.of("test-connect"))
                  .topicValidator(TopicValidator.makeDefault())
                  .build())
            .build();

        RoleBinding rb1 = RoleBinding.builder()
            .metadata(ObjectMeta.builder()
                      .name("ns1-rb")
                      .namespace("ns1")
                      .build())
            .spec(RoleBindingSpec.builder()
                  .role(Role.builder()
                        .resourceTypes(List.of("topics", "acls"))
                        .verbs(List.of(Verb.POST, Verb.GET))
                        .build())
                  .subject(Subject.builder()
                           .subjectName("group1")
                           .subjectType(SubjectType.GROUP)
                           .build())
                  .build())
            .build();

        Namespace ns2 = Namespace.builder()
            .metadata(ObjectMeta.builder()
                      .name("ns2")
                      .cluster("test-cluster")
                      .build())
            .spec(NamespaceSpec.builder()
                  .kafkaUser("user2")
                  .connectClusters(List.of("test-connect"))
                  .topicValidator(TopicValidator.makeDefault())
                  .build())
            .build();

        RoleBinding rb2 = RoleBinding.builder()
            .metadata(ObjectMeta.builder()
                      .name("ns2-rb")
                      .namespace("ns2")
                      .build())
            .spec(RoleBindingSpec.builder()
                  .role(Role.builder()
                        .resourceTypes(List.of("topics", "acls"))
                        .verbs(List.of(Verb.POST, Verb.GET))
                        .build())
                  .subject(Subject.builder()
                           .subjectName("group2")
                           .subjectType(SubjectType.GROUP)
                           .build())
                  .build())
            .build();

        AccessControlEntry acl1ns1 = AccessControlEntry.builder()
            .metadata(ObjectMeta.builder()
                      .name("ns1-acl1")
                      .namespace("ns1")
                      .build())
            .spec(AccessControlEntrySpec.builder()
                  .resourceType(ResourceType.GROUP)
                  .resource("ns1-")
                  .resourcePatternType(ResourcePatternType.PREFIXED)
                  .permission(Permission.OWNER)
                  .grantedTo("ns1")
                  .build())
            .build();

        AccessControlEntry acl2ns1 = AccessControlEntry.builder()
            .metadata(ObjectMeta.builder()
                      .name("ns1-acl2")
                      .namespace("ns1")
                      .build())
            .spec(AccessControlEntrySpec.builder()
                  .resourceType(ResourceType.TOPIC)
                  .resource("ns1-")
                  .resourcePatternType(ResourcePatternType.PREFIXED)
                  .permission(Permission.OWNER)
                  .grantedTo("ns1")
                  .build())
            .build();

        AccessControlEntry acl1ns2 = AccessControlEntry.builder()
            .metadata(ObjectMeta.builder()
                      .name("ns2-acl1")
                      .namespace("ns2")
                      .build())
            .spec(AccessControlEntrySpec.builder()
                  .resourceType(ResourceType.GROUP)
                  .resource("ns2-")
                  .resourcePatternType(ResourcePatternType.PREFIXED)
                  .permission(Permission.OWNER)
                  .grantedTo("ns2")
                  .build())
            .build();

        AccessControlEntry acl2ns2 = AccessControlEntry.builder()
            .metadata(ObjectMeta.builder()
                      .name("ns2-acl2")
                      .namespace("ns2")
                      .build())
            .spec(AccessControlEntrySpec.builder()
                  .resourceType(ResourceType.TOPIC)
                  .resource("ns2-")
                  .resourcePatternType(ResourcePatternType.PREFIXED)
                  .permission(Permission.OWNER)
                  .grantedTo("ns2")
                  .build())
            .build();

        AccessControlEntry aclns1Tons2 = AccessControlEntry.builder()
            .metadata(ObjectMeta.builder()
                      .name("ns1-acltons2")
                      .namespace("ns1")
                      .build())
            .spec(AccessControlEntrySpec.builder()
                  .resourceType(ResourceType.TOPIC)
                  .resource("ns1-")
                  .resourcePatternType(ResourcePatternType.PREFIXED)
                  .permission(Permission.READ)
                  .grantedTo("ns2")
                  .build())
            .build();


        Topic t1 = Topic.builder()
            .metadata(ObjectMeta.builder()
                      .name("ns1-topic1")
                      .namespace("ns1")
                      .build())
            .spec(TopicSpec.builder()
                  .partitions(3)
                  .replicationFactor(3)
                  .configs(Map.of("cleanup.policy", "delete",
                                  "min.insync.replicas", "2",
                                  "retention.ms", "60000"))
                  .build())
            .build();

        while(!server.isRunning()){
            Thread.sleep(1000);
        }
        UsernamePasswordCredentials credentials = new UsernamePasswordCredentials("admin","admin");
        HttpResponse<BearerAccessRefreshToken> response = client.exchange(HttpRequest.POST("/login", credentials), BearerAccessRefreshToken.class).blockingFirst();
        String token = response.getBody().get().getAccessToken();



        try {
            client.toBlocking().exchange(HttpRequest.create(HttpMethod.POST, "api/namespaces").bearerAuth(token).body(ns1));
        }catch (Exception e){
            System.out.println(e);
            System.out.println(server.getURL()+":"+server.getHost());
        }
        client.exchange(HttpRequest.create(HttpMethod.POST,"api/namespaces/ns1/role-bindings").bearerAuth(token).body(rb1)).blockingFirst();
        client.exchange(HttpRequest.create(HttpMethod.POST,"api/namespaces").bearerAuth(token).body(ns2)).blockingFirst();
        client.exchange(HttpRequest.create(HttpMethod.POST,"api/namespaces/ns2/role-bindings").bearerAuth(token).body(rb2)).blockingFirst();

        client.exchange(HttpRequest.create(HttpMethod.POST,"api/namespaces/ns1/acls").bearerAuth(token).body(acl1ns1)).blockingFirst();
        client.exchange(HttpRequest.create(HttpMethod.POST,"api/namespaces/ns1/acls").bearerAuth(token).body(acl2ns1)).blockingFirst();

        client.exchange(HttpRequest.create(HttpMethod.POST,"api/namespaces/ns2/acls").bearerAuth(token).body(acl1ns2)).blockingFirst();
        client.exchange(HttpRequest.create(HttpMethod.POST,"api/namespaces/ns2/acls").bearerAuth(token).body(acl2ns2)).blockingFirst();

        client.exchange(HttpRequest.create(HttpMethod.POST,"api/namespaces/ns1/acls").bearerAuth(token).body(aclns1Tons2)).blockingFirst();

        Assertions.assertEquals(HttpStatus.OK, client.exchange(HttpRequest.create(HttpMethod.POST,"api/namespaces/ns1/topics").bearerAuth(token).body(t1)).blockingFirst().getStatus());
        Topic t1bis = Topic.builder()
            .metadata(t1.getMetadata())
            .spec(TopicSpec.builder()
                .partitions(3)
                .replicationFactor(3)
                .configs(Map.of("cleanup.policy", "delete",
                                "min.insync.replicas", "2",
                                "retention.ms", "90000"))
                .build())
            .build();

        HttpClientResponseException exception = Assertions.assertThrows(HttpClientResponseException.class,() -> client.exchange(HttpRequest.create(HttpMethod.POST,"api/namespaces/ns2/topics").bearerAuth(token).body(t1bis)).blockingFirst());
        Assertions.assertEquals(exception.getMessage(),  "Validation failed: [Invalid value ns1-topic1 for name: Namespace not OWNER of this topic]");

        // Compare spec of the topics and assure there is no change
        Assertions.assertEquals(t1.getSpec(),client.retrieve(HttpRequest.create(HttpMethod.GET,"api/namespaces/ns1/topics/ns1-topic1").bearerAuth(token), Topic.class ).blockingFirst().getSpec());


    }

    @NoArgsConstructor
    @AllArgsConstructor
    @Data
    public static class BearerAccessRefreshToken {
        private String username;
        private Collection<String> roles;

        @JsonProperty("access_token")
        private String accessToken;

        @JsonProperty("token_type")
        private String tokenType;

        @JsonProperty("expires_in")
        private Integer expiresIn;
    }


}
