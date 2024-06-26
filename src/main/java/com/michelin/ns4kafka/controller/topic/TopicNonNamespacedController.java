package com.michelin.ns4kafka.controller.topic;

import com.michelin.ns4kafka.controller.generic.NonNamespacedResourceController;
import com.michelin.ns4kafka.model.Topic;
import com.michelin.ns4kafka.security.ResourceBasedSecurityRule;
import com.michelin.ns4kafka.service.TopicService;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import java.util.List;

/**
 * Non namespaced controller for topics.
 */
@Tag(name = "Topics", description = "Manage the topics.")
@Controller(value = "/api/topics")
@RolesAllowed(ResourceBasedSecurityRule.IS_ADMIN)
public class TopicNonNamespacedController extends NonNamespacedResourceController {
    @Inject
    TopicService topicService;

    /**
     * List topics.
     *
     * @return A list of topics
     */
    @Get
    public List<Topic> listAll() {
        return topicService.findAll();
    }
}
