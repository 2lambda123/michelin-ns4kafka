package com.michelin.ns4kafka.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.michelin.ns4kafka.integration.TopicTest.BearerAccessRefreshToken;
import com.michelin.ns4kafka.model.AccessControlEntry;
import com.michelin.ns4kafka.model.AccessControlEntry.AccessControlEntrySpec;
import com.michelin.ns4kafka.model.AccessControlEntry.Permission;
import com.michelin.ns4kafka.model.AccessControlEntry.ResourcePatternType;
import com.michelin.ns4kafka.model.AccessControlEntry.ResourceType;
import com.michelin.ns4kafka.model.KafkaStream;
import com.michelin.ns4kafka.model.Metadata;
import com.michelin.ns4kafka.model.Namespace;
import com.michelin.ns4kafka.model.Namespace.NamespaceSpec;
import com.michelin.ns4kafka.model.RoleBinding;
import com.michelin.ns4kafka.model.RoleBinding.Role;
import com.michelin.ns4kafka.model.RoleBinding.RoleBindingSpec;
import com.michelin.ns4kafka.model.RoleBinding.Subject;
import com.michelin.ns4kafka.model.RoleBinding.SubjectType;
import com.michelin.ns4kafka.model.RoleBinding.Verb;
import com.michelin.ns4kafka.service.executor.AccessControlEntryAsyncExecutor;
import com.michelin.ns4kafka.validation.TopicValidator;
import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.security.authentication.UsernamePasswordCredentials;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.common.acl.AccessControlEntryFilter;
import org.apache.kafka.common.acl.AclBinding;
import org.apache.kafka.common.acl.AclBindingFilter;
import org.apache.kafka.common.acl.AclOperation;
import org.apache.kafka.common.acl.AclPermissionType;
import org.apache.kafka.common.resource.PatternType;
import org.apache.kafka.common.resource.ResourcePattern;
import org.apache.kafka.common.resource.ResourcePatternFilter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@MicronautTest
@Property(name = "micronaut.security.gitlab.enabled", value = "false")
class AccessControlListTest extends AbstractIntegrationTest {
    @Inject
    @Client("/")
    HttpClient client;

    @Inject
    List<AccessControlEntryAsyncExecutor> accessControlEntryAsyncExecutorList;

    private String token;

    @BeforeAll
    void init() {
        Namespace ns1 = Namespace.builder()
            .metadata(Metadata.builder()
                .name("ns1")
                .cluster("test-cluster")
                .build())
            .spec(NamespaceSpec.builder()
                .kafkaUser("user1")
                .connectClusters(List.of("test-connect"))
                .topicValidator(TopicValidator.makeDefaultOneBroker())
                .build())
            .build();

        RoleBinding rb1 = RoleBinding.builder()
            .metadata(Metadata.builder()
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

        UsernamePasswordCredentials credentials = new UsernamePasswordCredentials("admin", "admin");
        HttpResponse<BearerAccessRefreshToken> response =
            client.toBlocking().exchange(HttpRequest.POST("/login", credentials), BearerAccessRefreshToken.class);

        token = response.getBody().get().getAccessToken();

        client.toBlocking()
            .exchange(HttpRequest.create(HttpMethod.POST, "/api/namespaces").bearerAuth(token).body(ns1));
        client.toBlocking().exchange(
            HttpRequest.create(HttpMethod.POST, "/api/namespaces/ns1/role-bindings").bearerAuth(token).body(rb1));
    }

    @Test
    void createTopicReadAcl() throws InterruptedException, ExecutionException {
        AccessControlEntry aclTopic = AccessControlEntry.builder()
            .metadata(Metadata.builder()
                .name("ns1-acl-topic")
                .namespace("ns1")
                .build())
            .spec(AccessControlEntrySpec.builder()
                .resourceType(ResourceType.TOPIC)
                .resource("ns1-")
                .resourcePatternType(ResourcePatternType.PREFIXED)
                .permission(Permission.READ)
                .grantedTo("ns1")
                .build())
            .build();

        client.toBlocking()
            .exchange(HttpRequest.create(HttpMethod.POST, "/api/namespaces/ns1/acls").bearerAuth(token).body(aclTopic));

        // Force ACLs synchronization
        accessControlEntryAsyncExecutorList.forEach(AccessControlEntryAsyncExecutor::run);

        Admin kafkaClient = getAdminClient();

        AclBindingFilter user1Filter = new AclBindingFilter(
            ResourcePatternFilter.ANY,
            new AccessControlEntryFilter("User:user1", null, AclOperation.ANY, AclPermissionType.ANY));
        Collection<AclBinding> results = kafkaClient.describeAcls(user1Filter).values().get();

        AclBinding expected = new AclBinding(
            new ResourcePattern(org.apache.kafka.common.resource.ResourceType.TOPIC, "ns1-", PatternType.PREFIXED),
            new org.apache.kafka.common.acl.AccessControlEntry("User:user1", "*", AclOperation.READ,
                AclPermissionType.ALLOW));

        assertEquals(1, results.size());
        assertEquals(expected, results.stream().findFirst().get());

        // DELETE the ACL and verify
        client.toBlocking().exchange(
            HttpRequest.create(HttpMethod.DELETE, "/api/namespaces/ns1/acls/ns1-acl-topic").bearerAuth(token));

        accessControlEntryAsyncExecutorList.forEach(AccessControlEntryAsyncExecutor::run);

        results = kafkaClient.describeAcls(user1Filter).values().get();

        assertTrue(results.isEmpty());
    }

    @Test
    void createPublicTopicReadAcl() throws InterruptedException, ExecutionException {
        AccessControlEntry aclTopicOwner = AccessControlEntry.builder()
            .metadata(Metadata.builder()
                .name("ns1-acl-topic-owner")
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

        client.toBlocking().exchange(
            HttpRequest.create(HttpMethod.POST, "/api/namespaces/ns1/acls").bearerAuth(token).body(aclTopicOwner));

        // Force ACLs synchronization
        accessControlEntryAsyncExecutorList.forEach(AccessControlEntryAsyncExecutor::run);

        AccessControlEntry aclTopicPublic = AccessControlEntry.builder()
            .metadata(Metadata.builder()
                .name("ns1-public-acl-topic")
                .namespace("ns1")
                .build())
            .spec(AccessControlEntrySpec.builder()
                .resourceType(ResourceType.TOPIC)
                .resource("ns1-")
                .resourcePatternType(ResourcePatternType.PREFIXED)
                .permission(Permission.READ)
                .grantedTo("*")
                .build())
            .build();

        client.toBlocking().exchange(
            HttpRequest.create(HttpMethod.POST, "/api/namespaces/ns1/acls").bearerAuth(token).body(aclTopicPublic));

        // Force ACLs synchronization
        accessControlEntryAsyncExecutorList.forEach(AccessControlEntryAsyncExecutor::run);

        Admin kafkaClient = getAdminClient();
        AclBindingFilter publicFilter = new AclBindingFilter(ResourcePatternFilter.ANY,
            new AccessControlEntryFilter("User:*", null, AclOperation.ANY, AclPermissionType.ANY));
        Collection<AclBinding> results = kafkaClient.describeAcls(publicFilter).values().get();

        AclBinding expected = new AclBinding(
            new ResourcePattern(org.apache.kafka.common.resource.ResourceType.TOPIC, "ns1-", PatternType.PREFIXED),
            new org.apache.kafka.common.acl.AccessControlEntry("User:*", "*", AclOperation.READ,
                AclPermissionType.ALLOW));

        assertEquals(1, results.size());
        assertEquals(expected, results.stream().findFirst().get());

        // DELETE the ACLs and verify
        client.toBlocking().exchange(
            HttpRequest.create(HttpMethod.DELETE, "/api/namespaces/ns1/acls/ns1-public-acl-topic").bearerAuth(token));
        client.toBlocking().exchange(
            HttpRequest.create(HttpMethod.DELETE, "/api/namespaces/ns1/acls/ns1-acl-topic-owner").bearerAuth(token));

        accessControlEntryAsyncExecutorList.forEach(AccessControlEntryAsyncExecutor::run);

        results = kafkaClient.describeAcls(publicFilter).values().get();

        assertTrue(results.isEmpty());
    }

    @Test
    void createTopicAclAlreadyExistsInBroker() throws InterruptedException, ExecutionException {
        AccessControlEntry aclTopic = AccessControlEntry.builder()
            .metadata(Metadata.builder()
                .name("ns1-acl-topic")
                .namespace("ns1")
                .build())
            .spec(AccessControlEntrySpec.builder()
                .resourceType(ResourceType.TOPIC)
                .resource("ns1-")
                .resourcePatternType(ResourcePatternType.PREFIXED)
                .permission(Permission.READ)
                .grantedTo("ns1")
                .build())
            .build();

        AclBinding aclBinding = new AclBinding(
            new ResourcePattern(org.apache.kafka.common.resource.ResourceType.TOPIC, "ns1-", PatternType.PREFIXED),
            new org.apache.kafka.common.acl.AccessControlEntry("User:user1", "*", AclOperation.READ,
                AclPermissionType.ALLOW));

        Admin kafkaClient = getAdminClient();
        kafkaClient.createAcls(Collections.singletonList(aclBinding));

        client.toBlocking()
            .exchange(HttpRequest.create(HttpMethod.POST, "/api/namespaces/ns1/acls").bearerAuth(token).body(aclTopic));

        // Force ACLs synchronization
        accessControlEntryAsyncExecutorList.forEach(AccessControlEntryAsyncExecutor::run);

        AclBindingFilter user1Filter = new AclBindingFilter(
            ResourcePatternFilter.ANY,
            new AccessControlEntryFilter("User:user1", null, AclOperation.ANY, AclPermissionType.ANY));
        Collection<AclBinding> results = kafkaClient.describeAcls(user1Filter).values().get();

        assertEquals(1, results.size());
        assertEquals(aclBinding, results.stream().findFirst().get());

        // Remove ACL
        client.toBlocking().exchange(
            HttpRequest.create(HttpMethod.DELETE, "/api/namespaces/ns1/acls/ns1-acl-topic")
                .bearerAuth(token));

        accessControlEntryAsyncExecutorList.forEach(AccessControlEntryAsyncExecutor::run);

        results = kafkaClient.describeAcls(user1Filter).values().get();

        assertTrue(results.isEmpty());
    }

    @Test
    void createConnectOwnerAcl() throws InterruptedException, ExecutionException {
        AccessControlEntry aclTopic = AccessControlEntry.builder()
            .metadata(Metadata.builder()
                .name("ns1-acl-connect")
                .namespace("ns1")
                .build())
            .spec(AccessControlEntrySpec.builder()
                .resourceType(ResourceType.CONNECT)
                .resource("ns1-")
                .resourcePatternType(ResourcePatternType.PREFIXED)
                .permission(Permission.OWNER)
                .grantedTo("ns1")
                .build())
            .build();

        client.toBlocking()
            .exchange(HttpRequest.create(HttpMethod.POST, "/api/namespaces/ns1/acls").bearerAuth(token).body(aclTopic));

        //force ACL Sync
        accessControlEntryAsyncExecutorList.forEach(AccessControlEntryAsyncExecutor::run);

        Admin kafkaClient = getAdminClient();

        AclBindingFilter user1Filter = new AclBindingFilter(
            ResourcePatternFilter.ANY,
            new AccessControlEntryFilter("User:user1", null, AclOperation.ANY, AclPermissionType.ANY));
        Collection<AclBinding> results = kafkaClient.describeAcls(user1Filter).values().get();

        AclBinding expected = new AclBinding(
            new ResourcePattern(org.apache.kafka.common.resource.ResourceType.GROUP, "connect-ns1-",
                PatternType.PREFIXED),
            new org.apache.kafka.common.acl.AccessControlEntry("User:user1", "*", AclOperation.READ,
                AclPermissionType.ALLOW));

        assertEquals(1, results.size());
        assertEquals(expected, results.stream().findFirst().get());

        // DELETE the ACL and verify
        client.toBlocking().exchange(
            HttpRequest.create(HttpMethod.DELETE, "/api/namespaces/ns1/acls/ns1-acl-connect").bearerAuth(token));

        accessControlEntryAsyncExecutorList.forEach(AccessControlEntryAsyncExecutor::run);

        results = kafkaClient.describeAcls(user1Filter).values().get();

        assertTrue(results.isEmpty());
    }

    @Test
    void createStreamAcl() throws InterruptedException, ExecutionException {
        AccessControlEntry aclTopic = AccessControlEntry.builder()
            .metadata(Metadata.builder()
                .name("ns1-acl-topic")
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

        AccessControlEntry aclGroup = AccessControlEntry.builder()
            .metadata(Metadata.builder()
                .name("ns1-acl-group")
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

        client.toBlocking()
            .exchange(HttpRequest.create(HttpMethod.POST, "/api/namespaces/ns1/acls").bearerAuth(token).body(aclTopic));
        client.toBlocking()
            .exchange(HttpRequest.create(HttpMethod.POST, "/api/namespaces/ns1/acls").bearerAuth(token).body(aclGroup));

        //force ACL Sync
        accessControlEntryAsyncExecutorList.forEach(AccessControlEntryAsyncExecutor::run);

        Admin kafkaClient = getAdminClient();

        AclBindingFilter user1Filter = new AclBindingFilter(
            ResourcePatternFilter.ANY,
            new AccessControlEntryFilter("User:user1", null, AclOperation.ANY, AclPermissionType.ANY));
        Collection<AclBinding> results = kafkaClient.describeAcls(user1Filter).values().get();

        // Topic ns1- READ
        // Topic ns1- WRITE
        // Group ns1- READ

        AclBinding ac1 = new AclBinding(
            new ResourcePattern(org.apache.kafka.common.resource.ResourceType.TOPIC, "ns1-", PatternType.PREFIXED),
            new org.apache.kafka.common.acl.AccessControlEntry("User:user1", "*", AclOperation.READ,
                AclPermissionType.ALLOW));
        AclBinding ac2 = new AclBinding(
            new ResourcePattern(org.apache.kafka.common.resource.ResourceType.TOPIC, "ns1-", PatternType.PREFIXED),
            new org.apache.kafka.common.acl.AccessControlEntry("User:user1", "*", AclOperation.WRITE,
                AclPermissionType.ALLOW));
        AclBinding ac3 = new AclBinding(
            new ResourcePattern(org.apache.kafka.common.resource.ResourceType.GROUP, "ns1-", PatternType.PREFIXED),
            new org.apache.kafka.common.acl.AccessControlEntry("User:user1", "*", AclOperation.READ,
                AclPermissionType.ALLOW));
        AclBinding ac4 = new AclBinding(
            new ResourcePattern(org.apache.kafka.common.resource.ResourceType.TOPIC, "ns1-", PatternType.PREFIXED),
            new org.apache.kafka.common.acl.AccessControlEntry("User:user1", "*", AclOperation.DESCRIBE_CONFIGS,
                AclPermissionType.ALLOW));

        assertEquals(4, results.size());
        assertTrue(results.containsAll(List.of(ac1, ac2, ac3, ac4)));

        KafkaStream stream = KafkaStream.builder()
            .metadata(Metadata.builder()
                .name("ns1-stream1")
                .namespace("ns1")
                .build())
            .build();

        client.toBlocking().exchange(
            HttpRequest.create(HttpMethod.POST, "/api/namespaces/ns1/streams").bearerAuth(token).body(stream));

        // Force ACLs synchronization
        accessControlEntryAsyncExecutorList.forEach(AccessControlEntryAsyncExecutor::run);

        results = kafkaClient.describeAcls(user1Filter).values().get();

        // Topic ns1- READ
        // Topic ns1- WRITE
        // Group ns1- READ
        // Topic ns1-stream1 CREATE
        // Topic ns1-stream1 DELETE
        AclBinding ac5 = new AclBinding(
            new ResourcePattern(org.apache.kafka.common.resource.ResourceType.TOPIC, "ns1-stream1",
                PatternType.PREFIXED),
            new org.apache.kafka.common.acl.AccessControlEntry("User:user1", "*", AclOperation.CREATE,
                AclPermissionType.ALLOW));
        AclBinding ac6 = new AclBinding(
            new ResourcePattern(org.apache.kafka.common.resource.ResourceType.TOPIC, "ns1-stream1",
                PatternType.PREFIXED),
            new org.apache.kafka.common.acl.AccessControlEntry("User:user1", "*", AclOperation.DELETE,
                AclPermissionType.ALLOW));
        AclBinding ac7 = new AclBinding(
            new ResourcePattern(org.apache.kafka.common.resource.ResourceType.TRANSACTIONAL_ID, "ns1-stream1",
                PatternType.PREFIXED),
            new org.apache.kafka.common.acl.AccessControlEntry("User:user1", "*", AclOperation.WRITE,
                AclPermissionType.ALLOW));

        assertEquals(7, results.size());
        assertTrue(results.containsAll(List.of(ac1, ac2, ac3, ac4, ac5, ac6, ac7)));

        // DELETE the Stream & ACL and verify
        client.toBlocking().exchange(
            HttpRequest.create(HttpMethod.DELETE, "/api/namespaces/ns1/streams/ns1-stream1").bearerAuth(token));
        client.toBlocking().exchange(
            HttpRequest.create(HttpMethod.DELETE, "/api/namespaces/ns1/acls/ns1-acl-topic").bearerAuth(token));
        client.toBlocking().exchange(
            HttpRequest.create(HttpMethod.DELETE, "/api/namespaces/ns1/acls/ns1-acl-group").bearerAuth(token));

        accessControlEntryAsyncExecutorList.forEach(AccessControlEntryAsyncExecutor::run);

        results = kafkaClient.describeAcls(user1Filter).values().get();

        assertTrue(results.isEmpty());
    }

    @Test
    void shouldCreateTransactionalIdOwnerAcl() throws InterruptedException, ExecutionException {
        AccessControlEntry aclTopic = AccessControlEntry.builder()
            .metadata(Metadata.builder()
                .name("ns1-acl-transactional-id")
                .namespace("ns1")
                .build())
            .spec(AccessControlEntrySpec.builder()
                .resourceType(ResourceType.TRANSACTIONAL_ID)
                .resource("ns1-")
                .resourcePatternType(ResourcePatternType.PREFIXED)
                .permission(Permission.OWNER)
                .grantedTo("ns1")
                .build())
            .build();

        client.toBlocking()
            .exchange(HttpRequest.create(HttpMethod.POST, "/api/namespaces/ns1/acls").bearerAuth(token).body(aclTopic));

        // Force ACL Sync
        accessControlEntryAsyncExecutorList.forEach(AccessControlEntryAsyncExecutor::run);

        Admin kafkaClient = getAdminClient();

        AclBindingFilter user1Filter = new AclBindingFilter(ResourcePatternFilter.ANY,
            new AccessControlEntryFilter("User:user1", null, AclOperation.ANY, AclPermissionType.ANY));
        Collection<AclBinding> results = kafkaClient.describeAcls(user1Filter).values().get();

        AclBinding expected = new AclBinding(
            new ResourcePattern(org.apache.kafka.common.resource.ResourceType.TRANSACTIONAL_ID, "ns1-",
                PatternType.PREFIXED),
            new org.apache.kafka.common.acl.AccessControlEntry("User:user1", "*", AclOperation.WRITE,
                AclPermissionType.ALLOW));

        AclBinding expected2 = new AclBinding(
            new ResourcePattern(org.apache.kafka.common.resource.ResourceType.TRANSACTIONAL_ID, "ns1-",
                PatternType.PREFIXED),
            new org.apache.kafka.common.acl.AccessControlEntry("User:user1", "*", AclOperation.DESCRIBE,
                AclPermissionType.ALLOW));

        assertEquals(2, results.size());
        assertTrue(results.containsAll(List.of(expected, expected2)));

        // DELETE the ACL and verify
        client.toBlocking().exchange(
            HttpRequest.create(HttpMethod.DELETE, "/api/namespaces/ns1/acls/ns1-acl-transactional-id")
                .bearerAuth(token));

        accessControlEntryAsyncExecutorList.forEach(AccessControlEntryAsyncExecutor::run);

        results = kafkaClient.describeAcls(user1Filter).values().get();

        assertTrue(results.isEmpty());
    }
}
