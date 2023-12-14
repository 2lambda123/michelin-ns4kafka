package com.michelin.ns4kafka.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.michelin.ns4kafka.integration.TopicTest.BearerAccessRefreshToken;
import com.michelin.ns4kafka.models.AccessControlEntry;
import com.michelin.ns4kafka.models.Namespace;
import com.michelin.ns4kafka.models.ObjectMeta;
import com.michelin.ns4kafka.models.RoleBinding;
import com.michelin.ns4kafka.models.schema.Schema;
import com.michelin.ns4kafka.models.schema.SchemaCompatibilityState;
import com.michelin.ns4kafka.models.schema.SchemaList;
import com.michelin.ns4kafka.services.clients.schema.entities.SchemaCompatibilityResponse;
import com.michelin.ns4kafka.services.clients.schema.entities.SchemaResponse;
import io.confluent.kafka.schemaregistry.client.rest.entities.requests.RegisterSchemaRequest;
import io.confluent.kafka.schemaregistry.client.rest.entities.requests.RegisterSchemaResponse;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Property;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.security.authentication.UsernamePasswordCredentials;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@MicronautTest
@Property(name = "micronaut.security.gitlab.enabled", value = "false")
class SchemaTest extends AbstractIntegrationSchemaRegistryTest {
    @Inject
    private ApplicationContext applicationContext;

    @Inject
    @Client("/")
    private HttpClient ns4KafkaClient;

    private HttpClient schemaRegistryClient;

    private String token;

    @BeforeAll
    void init() {
        schemaRegistryClient = applicationContext.createBean(HttpClient.class, schemaRegistryContainer.getUrl());

        Namespace namespace = Namespace.builder()
            .metadata(ObjectMeta.builder()
                .name("ns1")
                .cluster("test-cluster")
                .build())
            .spec(Namespace.NamespaceSpec.builder()
                .kafkaUser("user1")
                .build())
            .build();

        RoleBinding roleBinding = RoleBinding.builder()
            .metadata(ObjectMeta.builder()
                .name("ns1-rb")
                .namespace("ns1")
                .build())
            .spec(RoleBinding.RoleBindingSpec.builder()
                .role(RoleBinding.Role.builder()
                    .resourceTypes(List.of("topics", "acls"))
                    .verbs(List.of(RoleBinding.Verb.POST, RoleBinding.Verb.GET))
                    .build())
                .subject(RoleBinding.Subject.builder()
                    .subjectName("group1")
                    .subjectType(RoleBinding.SubjectType.GROUP)
                    .build())
                .build())
            .build();


        UsernamePasswordCredentials credentials = new UsernamePasswordCredentials("admin", "admin");
        HttpResponse<BearerAccessRefreshToken> response = ns4KafkaClient.toBlocking()
            .exchange(HttpRequest.POST("/login", credentials), BearerAccessRefreshToken.class);

        token = response.getBody().get().getAccessToken();

        ns4KafkaClient.toBlocking()
            .exchange(HttpRequest.create(HttpMethod.POST, "/api/namespaces").bearerAuth(token).body(namespace));
        ns4KafkaClient.toBlocking().exchange(
            HttpRequest.create(HttpMethod.POST, "/api/namespaces/ns1/role-bindings").bearerAuth(token)
                .body(roleBinding));

        AccessControlEntry aclSchema = AccessControlEntry.builder()
            .metadata(ObjectMeta.builder()
                .name("ns1-acl")
                .namespace("ns1")
                .build())
            .spec(AccessControlEntry.AccessControlEntrySpec.builder()
                .resourceType(AccessControlEntry.ResourceType.TOPIC)
                .resource("ns1-")
                .resourcePatternType(AccessControlEntry.ResourcePatternType.PREFIXED)
                .permission(AccessControlEntry.Permission.OWNER)
                .grantedTo("ns1")
                .build())
            .build();

        ns4KafkaClient.toBlocking().exchange(
            HttpRequest.create(HttpMethod.POST, "/api/namespaces/ns1/acls").bearerAuth(token).body(aclSchema));
    }

    @Test
    void registerSchemaCompatibility() {
        // Register schema, first name is optional
        Schema schema = Schema.builder()
            .metadata(ObjectMeta.builder()
                .name("ns1-subject0-value")
                .build())
            .spec(Schema.SchemaSpec.builder()
                .schema("{\"namespace\":\"com.michelin.kafka.producer.showcase.avro\","
                    + "\"type\":\"record\",\"name\":\"PersonAvro\",\"fields\":[{\"name\":\"firstName\",\"type\":"
                    + "[\"null\",\"string\"],\"default\":null,\"doc\":\"First name of the person\"},"
                    + "{\"name\":\"lastName\",\"type\":[\"null\",\"string\"],\"default\":null,"
                    + "\"doc\":\"Last name of the person\"},{\"name\":\"dateOfBirth\",\"type\":"
                    + "[\"null\",{\"type\":\"long\",\"logicalType\":\"timestamp-millis\"}],"
                    + "\"default\":null,\"doc\":\"Date of birth of the person\"}]}")
                .build())
            .build();

        var createResponse =
            ns4KafkaClient.toBlocking().exchange(HttpRequest.create(HttpMethod.POST, "/api/namespaces/ns1/schemas")
                .bearerAuth(token)
                .body(schema), Schema.class);

        assertEquals("created", createResponse.header("X-Ns4kafka-Result"));

        SchemaResponse actual = schemaRegistryClient.toBlocking()
            .retrieve(HttpRequest.GET("/subjects/ns1-subject0-value/versions/latest"), SchemaResponse.class);

        Assertions.assertNotNull(actual.id());
        assertEquals(1, actual.version());
        assertEquals("ns1-subject0-value", actual.subject());

        // Set compat to forward
        ns4KafkaClient.toBlocking()
            .exchange(HttpRequest.create(HttpMethod.POST, "/api/namespaces/ns1/schemas/ns1-subject0-value/config")
                .bearerAuth(token)
                .body(Map.of("compatibility", Schema.Compatibility.FORWARD)), SchemaCompatibilityState.class);

        SchemaCompatibilityResponse updatedConfig = schemaRegistryClient.toBlocking()
            .retrieve(HttpRequest.GET("/config/ns1-subject0-value"),
                SchemaCompatibilityResponse.class);

        assertEquals(Schema.Compatibility.FORWARD, updatedConfig.compatibilityLevel());

        // Register compatible schema v2, removing optional "first name" field
        Schema incompatibleSchema = Schema.builder()
            .metadata(ObjectMeta.builder()
                .name("ns1-subject0-value")
                .build())
            .spec(Schema.SchemaSpec.builder()
                .schema("{\"namespace\":\"com.michelin.kafka.producer.showcase.avro\","
                    + "\"type\":\"record\",\"name\":\"PersonAvro\",\"fields\":"
                    + "[{\"name\":\"dateOfBirth\",\"type\":[\"null\",{\"type\":\"long\","
                    + "\"logicalType\":\"timestamp-millis\"}],\"default\":null,\"doc\":"
                    + "\"Date of birth of the person\"}]}")
                .build())
            .build();

        var createV2Response =
            ns4KafkaClient.toBlocking().exchange(HttpRequest.create(HttpMethod.POST, "/api/namespaces/ns1/schemas")
                .bearerAuth(token)
                .body(incompatibleSchema), Schema.class);

        assertEquals("changed", createV2Response.header("X-Ns4kafka-Result"));

        SchemaResponse actualV2 = schemaRegistryClient.toBlocking()
            .retrieve(HttpRequest.GET("/subjects/ns1-subject0-value/versions/latest"),
                SchemaResponse.class);

        Assertions.assertNotNull(actualV2.id());
        assertEquals(2, actualV2.version());
        assertEquals("ns1-subject0-value", actualV2.subject());
    }

    @Test
    void registerSchemaIncompatibility() {
        // Register schema, first name is non-optional
        Schema schema = Schema.builder()
            .metadata(ObjectMeta.builder()
                .name("ns1-subject1-value")
                .build())
            .spec(Schema.SchemaSpec.builder()
                .schema(
                    "{\"namespace\":\"com.michelin.kafka.producer.showcase.avro\",\"type\":\"record\","
                        + "\"name\":\"PersonAvro\",\"fields\":[{\"name\":\"firstName\",\"type\":[\"string\"],"
                        + "\"doc\":\"First name of the person\"},"
                        + "{\"name\":\"lastName\",\"type\":[\"null\",\"string\"],\"default\":null,"
                        + "\"doc\":\"Last name of the person\"},{\"name\":\"dateOfBirth\",\"type\":[\"null\","
                        + "{\"type\":\"long\",\"logicalType\":\"timestamp-millis\"}],\"default\":null,"
                        + "\"doc\":\"Date of birth of the person\"}]}")
                .build())
            .build();

        var createResponse =
            ns4KafkaClient.toBlocking().exchange(HttpRequest.create(HttpMethod.POST, "/api/namespaces/ns1/schemas")
                .bearerAuth(token)
                .body(schema), Schema.class);

        assertEquals("created", createResponse.header("X-Ns4kafka-Result"));

        SchemaResponse actual = schemaRegistryClient.toBlocking()
            .retrieve(HttpRequest.GET("/subjects/ns1-subject1-value/versions/latest"),
                SchemaResponse.class);

        Assertions.assertNotNull(actual.id());
        assertEquals(1, actual.version());
        assertEquals("ns1-subject1-value", actual.subject());

        // Set compat to forward
        ns4KafkaClient.toBlocking()
            .exchange(HttpRequest.create(HttpMethod.POST, "/api/namespaces/ns1/schemas/ns1-subject1-value/config")
                .bearerAuth(token)
                .body(Map.of("compatibility", Schema.Compatibility.FORWARD)), SchemaCompatibilityState.class);

        SchemaCompatibilityResponse updatedConfig = schemaRegistryClient.toBlocking()
            .retrieve(HttpRequest.GET("/config/ns1-subject1-value"),
                SchemaCompatibilityResponse.class);

        assertEquals(Schema.Compatibility.FORWARD, updatedConfig.compatibilityLevel());

        // Register incompatible schema v2, removing non-optional "first name" field
        Schema incompatibleSchema = Schema.builder()
            .metadata(ObjectMeta.builder()
                .name("ns1-subject1-value")
                .build())
            .spec(Schema.SchemaSpec.builder()
                .schema(
                    "{\"namespace\":\"com.michelin.kafka.producer.showcase.avro\",\"type\":\"record\","
                        + "\"name\":\"PersonAvro\",\"fields\":["
                        + "{\"name\":\"lastName\",\"type\":[\"null\",\"string\"],\"default\":null,"
                        + "\"doc\":\"Last name of the person\"},{\"name\":\"dateOfBirth\",\"type\":[\"null\","
                        + "{\"type\":\"long\",\"logicalType\":\"timestamp-millis\"}],\"default\":null,"
                        + "\"doc\":\"Date of birth of the person\"}]}")
                .build())
            .build();

        HttpClientResponseException incompatibleActual = assertThrows(HttpClientResponseException.class,
            () -> ns4KafkaClient.toBlocking()
                .exchange(HttpRequest.create(HttpMethod.POST, "/api/namespaces/ns1/schemas")
                    .bearerAuth(token)
                    .body(incompatibleSchema)));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, incompatibleActual.getStatus());
        assertEquals("Invalid Schema ns1-subject1-value", incompatibleActual.getMessage());
    }

    @Test
    void registerSchemaWithReferences() {
        Schema schemaHeader = Schema.builder()
            .metadata(ObjectMeta.builder()
                .name("ns1-header-subject-value")
                .build())
            .spec(Schema.SchemaSpec.builder()
                .schema("{\"namespace\":\"com.michelin.kafka.producer.showcase.avro\",\"type\":\"record\","
                    + "\"name\":\"HeaderAvro\",\"fields\":[{\"name\":\"id\",\"type\":[\"null\",\"string\"],"
                    + "\"default\":null,\"doc\":\"ID of the header\"}]}")
                .build())
            .build();

        // Header created
        var headerCreateResponse =
            ns4KafkaClient.toBlocking().exchange(HttpRequest.create(HttpMethod.POST, "/api/namespaces/ns1/schemas")
                .bearerAuth(token)
                .body(schemaHeader), Schema.class);

        assertEquals("created", headerCreateResponse.header("X-Ns4kafka-Result"));

        SchemaResponse actualHeader = schemaRegistryClient.toBlocking()
            .retrieve(HttpRequest.GET("/subjects/ns1-header-subject-value/versions/latest"),
                SchemaResponse.class);

        Assertions.assertNotNull(actualHeader.id());
        assertEquals(1, actualHeader.version());
        assertEquals("ns1-header-subject-value", actualHeader.subject());

        // Person without refs not created
        Schema schemaPersonWithoutRefs = Schema.builder()
            .metadata(ObjectMeta.builder()
                .name("ns1-person-subject-value")
                .build())
            .spec(Schema.SchemaSpec.builder()
                .schema("{\"namespace\":\"com.michelin.kafka.producer.showcase.avro\",\"type\":\"record\","
                    + "\"name\":\"PersonAvro\","
                    + "\"fields\":[{\"name\":\"header\",\"type\":[\"null\","
                    + "\"com.michelin.kafka.producer.showcase.avro.HeaderAvro\"],"
                    + "\"default\":null,\"doc\":\"Header of the person\"},{\"name\":\"firstName\","
                    + "\"type\":[\"null\",\"string\"],"
                    + "\"default\":null,\"doc\":\"First name of the person\"},{\"name\":\"lastName\","
                    + "\"type\":[\"null\",\"string\"],"
                    + "\"default\":null,\"doc\":\"Last name of the person\"},{\"name\":\"dateOfBirth\","
                    + "\"type\":[\"null\","
                    + "{\"type\":\"long\",\"logicalType\":\"timestamp-millis\"}],\"default\":null"
                    + "\"doc\":\"Date of birth of the person\"}]}")
                .build())
            .build();

        HttpClientResponseException createException = assertThrows(HttpClientResponseException.class,
            () -> ns4KafkaClient.toBlocking()
                .exchange(HttpRequest.create(HttpMethod.POST, "/api/namespaces/ns1/schemas")
                    .bearerAuth(token)
                    .body(schemaPersonWithoutRefs)));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, createException.getStatus());
        assertEquals("Invalid Schema ns1-person-subject-value", createException.getMessage());

        // Person with refs created
        Schema schemaPersonWithRefs = Schema.builder()
            .metadata(ObjectMeta.builder()
                .name("ns1-person-subject-value")
                .build())
            .spec(Schema.SchemaSpec.builder()
                .schema("{\"namespace\":\"com.michelin.kafka.producer.showcase.avro\","
                    + "\"type\":\"record\",\"name\":\"PersonAvro\",\"fields\":[{\"name\":\"header\",\"type\":[\"null\","
                    + "\"com.michelin.kafka.producer.showcase.avro.HeaderAvro\"],"
                    + "\"default\":null,\"doc\":\"Header of the person\"},{\"name\":\"firstName\","
                    + "\"type\":[\"null\",\"string\"],\"default\":null,\"doc\":\"First name of the person\"},"
                    + "{\"name\":\"lastName\",\"type\":[\"null\",\"string\"],\"default\":null,\"doc\":"
                    + "\"Last name of the person\"},{\"name\":\"dateOfBirth\",\"type\":[\"null\",{\"type\":\"long\","
                    + "\"logicalType\":\"timestamp-millis\"}],\"default\":null,"
                    + "\"doc\":\"Date of birth of the person\"}]}")
                .references(List.of(Schema.SchemaSpec.Reference.builder()
                    .name("com.michelin.kafka.producer.showcase.avro.HeaderAvro")
                    .subject("ns1-header-subject-value")
                    .version(1)
                    .build()))
                .build())
            .build();

        var personCreateResponse =
            ns4KafkaClient.toBlocking().exchange(HttpRequest.create(HttpMethod.POST, "/api/namespaces/ns1/schemas")
                .bearerAuth(token)
                .body(schemaPersonWithRefs), Schema.class);

        assertEquals("created", personCreateResponse.header("X-Ns4kafka-Result"));

        SchemaResponse actualPerson = schemaRegistryClient.toBlocking()
            .retrieve(HttpRequest.GET("/subjects/ns1-person-subject-value/versions/latest"),
                SchemaResponse.class);

        Assertions.assertNotNull(actualPerson.id());
        assertEquals(1, actualPerson.version());
        assertEquals("ns1-person-subject-value", actualPerson.subject());

        var personUnchangedResponse =
                ns4KafkaClient.toBlocking().exchange(HttpRequest.create(HttpMethod.POST, "/api/namespaces/ns1/schemas")
                        .bearerAuth(token)
                        .body(schemaPersonWithRefs), Schema.class);

        assertEquals("unchanged", personUnchangedResponse.header("X-Ns4kafka-Result"));
    }

    @Test
    void registerSchemaWrongPrefix() {
        Schema wrongSchema = Schema.builder()
            .metadata(ObjectMeta.builder()
                .name("wrongprefix-subject")
                .build())
            .spec(Schema.SchemaSpec.builder()
                .schema(
                    "{\"namespace\":\"com.michelin.kafka.producer.showcase.avro\",\"type\":\"record\","
                        + "\"name\":\"PersonAvro\",\"fields\":[{\"name\":\"firstName\",\"type\":[\"null\",\"string\"],"
                        + "\"default\":null,\"doc\":\"First name of the person\"},"
                        + "{\"name\":\"lastName\",\"type\":[\"null\",\"string\"],\"default\":null,"
                        + "\"doc\":\"Last name of the person\"},{\"name\":\"dateOfBirth\",\"type\":[\"null\","
                        + "{\"type\":\"long\",\"logicalType\":\"timestamp-millis\"}],\"default\":null,"
                        + "\"doc\":\"Date of birth of the person\"}]}")
                .build())
            .build();

        HttpClientResponseException createException = assertThrows(HttpClientResponseException.class,
            () -> ns4KafkaClient.toBlocking()
                .exchange(HttpRequest.create(HttpMethod.POST, "/api/namespaces/ns1/schemas")
                    .bearerAuth(token)
                    .body(wrongSchema)));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, createException.getStatus());
        assertEquals("Invalid Schema wrongprefix-subject", createException.getMessage());

        // Get all schemas
        var getResponse =
            ns4KafkaClient.toBlocking().exchange(HttpRequest.create(HttpMethod.GET, "/api/namespaces/ns1/schemas")
                .bearerAuth(token), Argument.listOf(SchemaList.class));

        assertTrue(getResponse.getBody().isPresent());
        assertTrue(getResponse.getBody().get()
            .stream()
            .noneMatch(schemaList -> schemaList.getMetadata().getName().equals("wrongprefix-subject")));

        HttpClientResponseException getException = assertThrows(HttpClientResponseException.class,
            () -> schemaRegistryClient.toBlocking()
                .retrieve(HttpRequest.GET("/subjects/wrongprefix-subject/versions/latest"),
                    SchemaResponse.class));

        assertEquals(HttpStatus.NOT_FOUND, getException.getStatus());
    }

    @Test
    void registerSchema() {
        Schema schema = Schema.builder()
            .metadata(ObjectMeta.builder()
                .name("ns1-subject2-value")
                .build())
            .spec(Schema.SchemaSpec.builder()
                .schema(
                    "{\"namespace\":\"com.michelin.kafka.producer.showcase.avro\",\"type\":\"record\","
                        + "\"name\":\"PersonAvro\",\"fields\":[{\"name\":\"firstName\",\"type\":[\"null\",\"string\"],"
                        + "\"default\":null,\"doc\":\"First name of the person\"},"
                        + "{\"name\":\"lastName\",\"type\":[\"null\",\"string\"],\"default\":null,"
                        + "\"doc\":\"Last name of the person\"},{\"name\":\"dateOfBirth\",\"type\":[\"null\","
                        + "{\"type\":\"long\",\"logicalType\":\"timestamp-millis\"}],\"default\":null,"
                        + "\"doc\":\"Date of birth of the person\"}]}")
                .build())
            .build();

        // Apply schema
        var createResponse =
            ns4KafkaClient.toBlocking().exchange(HttpRequest.create(HttpMethod.POST, "/api/namespaces/ns1/schemas")
                .bearerAuth(token)
                .body(schema), Schema.class);

        assertEquals("created", createResponse.header("X-Ns4kafka-Result"));

        // Get all schemas
        var getResponse =
            ns4KafkaClient.toBlocking().exchange(HttpRequest.create(HttpMethod.GET, "/api/namespaces/ns1/schemas")
                .bearerAuth(token), Argument.listOf(SchemaList.class));

        assertTrue(getResponse.getBody().isPresent());
        assertTrue(getResponse.getBody().get()
            .stream()
            .anyMatch(schemaList -> schemaList.getMetadata().getName().equals("ns1-subject2-value")));

        // Delete schema
        var deleteResponse = ns4KafkaClient.toBlocking()
            .exchange(HttpRequest.create(HttpMethod.DELETE, "/api/namespaces/ns1/schemas/ns1-subject2-value")
                .bearerAuth(token), Schema.class);

        assertEquals(HttpStatus.NO_CONTENT, deleteResponse.getStatus());

        // Get all schemas
        var getResponseEmpty =
            ns4KafkaClient.toBlocking().exchange(HttpRequest.create(HttpMethod.GET, "/api/namespaces/ns1/schemas")
                .bearerAuth(token), Argument.listOf(SchemaList.class));

        assertTrue(getResponseEmpty.getBody().isPresent());
        assertTrue(getResponseEmpty.getBody().get()
            .stream()
            .noneMatch(schemaList -> schemaList.getMetadata().getName().equals("ns1-subject2-value")));

        HttpClientResponseException getException = assertThrows(HttpClientResponseException.class,
            () -> schemaRegistryClient.toBlocking()
                .retrieve(HttpRequest.GET("/subjects/ns1-subject2-value/versions/latest"),
                    SchemaResponse.class));

        assertEquals(HttpStatus.NOT_FOUND, getException.getStatus());
    }

    @Test
    void registerSameSchemaTwice() {
        Schema schema = Schema.builder()
            .metadata(ObjectMeta.builder()
                .name("ns1-subject3-value")
                .build())
            .spec(Schema.SchemaSpec.builder()
                .schema(
                    "{\"namespace\":\"com.michelin.kafka.producer.showcase.avro\",\"type\":\"record\","
                        + "\"name\":\"PersonAvro\",\"fields\":[{\"name\":\"firstName\",\"type\":[\"null\",\"string\"],"
                        + "\"default\":null,\"doc\":\"First name of the person\"},"
                        + "{\"name\":\"lastName\",\"type\":[\"null\",\"string\"],\"default\":null,"
                        + "\"doc\":\"Last name of the person\"},{\"name\":\"dateOfBirth\",\"type\":[\"null\","
                        + "{\"type\":\"long\",\"logicalType\":\"timestamp-millis\"}],\"default\":null,"
                        + "\"doc\":\"Date of birth of the person\"}]}")
                .build())
            .build();

        // Apply schema
        var createResponse =
                ns4KafkaClient.toBlocking().exchange(HttpRequest.create(HttpMethod.POST, "/api/namespaces/ns1/schemas")
                        .bearerAuth(token)
                        .body(schema), Schema.class);

        assertEquals("created", createResponse.header("X-Ns4kafka-Result"));

        // Get all schemas
        var getResponse =
                ns4KafkaClient.toBlocking().exchange(HttpRequest.create(HttpMethod.GET, "/api/namespaces/ns1/schemas")
                        .bearerAuth(token), Argument.listOf(SchemaList.class));

        assertTrue(getResponse.getBody().isPresent());
        assertTrue(getResponse.getBody().get()
                .stream()
                .anyMatch(schemaList -> schemaList.getMetadata().getName().equals("ns1-subject3-value")));

        // Apply the same schema with swapped fields
        Schema sameSchemaWithSwappedFields = Schema.builder()
            .metadata(ObjectMeta.builder()
                .name("ns1-subject3-value")
                .build())
            .spec(Schema.SchemaSpec.builder()
                .schema(
                    "{\"namespace\":\"com.michelin.kafka.producer.showcase.avro\",\"type\":\"record\","
                        + "\"name\":\"PersonAvro\",\"fields\":[ {\"name\":\"lastName\",\"type\":[\"null\",\"string\"],"
                        + "\"default\":null, \"doc\":\"Last name of the person\"},"
                        + "{\"name\":\"firstName\",\"type\":[\"null\",\"string\"], \"default\":null,"
                        + "\"doc\":\"First name of the person\"}, {\"name\":\"dateOfBirth\",\"type\":[\"null\","
                        + "{\"type\":\"long\",\"logicalType\":\"timestamp-millis\"}],\"default\":null,"
                        + "\"doc\":\"Date of birth of the person\"}]}")
                .build())
            .build();

        var createSwappedFieldsResponse =
                ns4KafkaClient.toBlocking().exchange(HttpRequest.create(HttpMethod.POST, "/api/namespaces/ns1/schemas")
                        .bearerAuth(token)
                        .body(sameSchemaWithSwappedFields), Schema.class);

        // Expects a new version
        assertEquals("changed", createSwappedFieldsResponse.header("X-Ns4kafka-Result"));

        // Get the latest version to check if we are on v2
        SchemaResponse schemaAfterApply = schemaRegistryClient.toBlocking()
                .retrieve(HttpRequest.GET("/subjects/ns1-subject3-value/versions/latest"),
                        SchemaResponse.class);

        Assertions.assertNotNull(schemaAfterApply.id());
        assertEquals(2, schemaAfterApply.version());
        assertEquals("ns1-subject3-value", schemaAfterApply.subject());

        // Apply again the schema with swapped fields
        var createAgainResponse =
                ns4KafkaClient.toBlocking().exchange(HttpRequest.create(HttpMethod.POST, "/api/namespaces/ns1/schemas")
                        .bearerAuth(token)
                        .body(sameSchemaWithSwappedFields), Schema.class);

        // Expects no new schema version but an unchanged status
        assertEquals("unchanged", createAgainResponse.header("X-Ns4kafka-Result"));

        // Apply again with SR client to be sure that
        RegisterSchemaRequest request = new RegisterSchemaRequest();
        request.setSchema(sameSchemaWithSwappedFields.getSpec().getSchema());

        schemaRegistryClient.toBlocking()
                .exchange(HttpRequest.create(HttpMethod.POST, "/subjects/ns1-subject3-value/versions")
                        .body(request), RegisterSchemaResponse.class);

        SchemaResponse schemaAfterPostOnRegistry = schemaRegistryClient.toBlocking()
                .retrieve(HttpRequest.GET("/subjects/ns1-subject3-value/versions/latest"),
                        SchemaResponse.class);

        Assertions.assertNotNull(schemaAfterPostOnRegistry.id());
        assertEquals(2, schemaAfterPostOnRegistry.version());
        assertEquals("ns1-subject3-value", schemaAfterPostOnRegistry.subject());
    }
}
