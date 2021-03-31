package com.michelin.ns4kafka.controllers;

import com.michelin.ns4kafka.models.Namespace;
import com.michelin.ns4kafka.models.ObjectMeta;
import com.michelin.ns4kafka.models.Topic;
import com.michelin.ns4kafka.models.Namespace.NamespaceSpec;
import com.michelin.ns4kafka.models.Topic.TopicSpec;
import com.michelin.ns4kafka.services.NamespaceService;
import com.michelin.ns4kafka.services.TopicService;
import com.michelin.ns4kafka.validation.TopicValidator;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class TopicControllerTest {

    @Mock
    NamespaceService namespaceService;
    @Mock
    TopicService topicService;

    @InjectMocks
    TopicController topicController;

    //Current Namespace
    Namespace ns = Namespace.builder()
        .spec(NamespaceSpec.builder()
            .topicValidator(TopicValidator.makeDefault())
            .build())
        .metadata(ObjectMeta.builder()
            .name("test")
            .cluster("local")
            .build())
        .build();

    @BeforeEach
    void init() {
        //Given
        //find the namespace attached to the resource
        when(namespaceService.findByName("test"))
                .thenReturn(Optional.of(ns));
    }

    @Test
    public void ListEmptyTopics() {
        when(topicService.findAllForNamespace(ns))
                .thenReturn(List.of());

        List<Topic> actual = topicController.list("test");
        Assertions.assertEquals(0, actual.size());
    }

    @Test
    public void ListMultipleTopics() {
        when(topicService.findAllForNamespace(ns))
                .thenReturn(List.of(
                        Topic.builder().metadata(ObjectMeta.builder().name("topic1").build()).build(),
                        Topic.builder().metadata(ObjectMeta.builder().name("topic2").build()).build()
                ));

        List<Topic> actual = topicController.list("test");

        Assertions.assertEquals(2, actual.size());
        Assertions.assertEquals("topic1", actual.get(0).getMetadata().getName());
        Assertions.assertEquals("topic2", actual.get(1).getMetadata().getName());
    }

    @Test
    public void GetEmptyTopic() {
        when(topicService.findByName(ns, "topic.notfound"))
                .thenReturn(Optional.empty());

        Optional<Topic> actual = topicController.getTopic("test", "topic.notfound");

        Assertions.assertTrue(actual.isEmpty());
    }

    @Test
    public void GetTopic() {
        when(topicService.findByName(ns, "topic.found"))
                .thenReturn(Optional.of(
                        Topic.builder().metadata(ObjectMeta.builder().name("topic.found").build()).build()
                ));

        Optional<Topic> actual = topicController.getTopic("test", "topic.found");

        Assertions.assertTrue(actual.isPresent());
        Assertions.assertEquals("topic.found", actual.get().getMetadata().getName());
    }

    @Test
    public void DeleteTopic() throws InterruptedException, ExecutionException, TimeoutException {
        //Given
        Optional<Topic> toDelete = Optional.of(
                Topic.builder().metadata(ObjectMeta.builder().name("topic.delete").build()).build());
        when(topicService.findByName(ns, "topic.delete"))
                .thenReturn(toDelete);
        when(topicService.isNamespaceOwnerOfTopic("test","topic.delete"))
                .thenReturn(true);
        doNothing().when(topicService).delete(toDelete.get());


        //When
        HttpResponse<Void> actual = topicController.deleteTopic("test", "topic.delete");

        //Then
        Assertions.assertEquals(HttpStatus.NO_CONTENT, actual.getStatus());
    }

    @Test
    public void DeleteTopicUnauthorized() throws InterruptedException, ExecutionException, TimeoutException {
        //Given
        when(topicService.isNamespaceOwnerOfTopic("test", "topic.delete"))
                .thenReturn(false);

        //When
        HttpResponse<Void> actual = topicController.deleteTopic("test", "topic.delete");

        //Then
        Assertions.assertEquals(HttpStatus.UNAUTHORIZED, actual.getStatus());

    }


    @Test
    public void CreateNewTopicFailValidation() {
        Topic topic = Topic.builder()
                .metadata(ObjectMeta.builder()
                    .name("topic")
                    .build())
                .spec(TopicSpec.builder()
                        .partitions(0)
                        .build())
                .build();
        when(topicService.findByName(any(), anyString()))
            .thenReturn(Optional.empty());
        when(topicService.isNamespaceOwnerOfTopic(anyString(), anyString()))
                .thenReturn(true);

        Assertions.assertThrows(ResourceValidationException.class,() -> {
                topicController.apply("test", topic);
            });

        /*Topic topic = Topic.builder()
                .metadata(ObjectMeta.builder().name("test.topic2").build())
                .spec(Topic.TopicSpec.builder()
                        .replicationFactor(1)
                        .partitions(3)
                        .build())
                .build();
        ResourceValidationException actual = Assertions.assertThrows(ResourceValidationException.class,
                () -> topicController.apply("test",topic));
        Assertions.assertEquals(1, actual.getValidationErrors().size());
        Assertions.assertLinesMatch(List.of(".*replication\\.factor.*"), actual.getValidationErrors());
         */
    }
}
