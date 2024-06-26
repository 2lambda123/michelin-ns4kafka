package com.michelin.ns4kafka.repository;

import com.michelin.ns4kafka.model.Topic;
import java.util.List;

/**
 * Topic repository.
 */
public interface TopicRepository {
    /**
     * Find all topics.
     *
     * @return The list of topics
     */
    List<Topic> findAll();

    /**
     * Find all topics by cluster.
     *
     * @param cluster The cluster
     * @return The list of topics
     */
    List<Topic> findAllForCluster(String cluster);

    /**
     * Create a given topic.
     *
     * @param topic The topic to create
     * @return The created topic
     */
    Topic create(Topic topic);

    /**
     * Delete a given topic.
     *
     * @param topic The topic to delete
     */
    void delete(Topic topic);
}
