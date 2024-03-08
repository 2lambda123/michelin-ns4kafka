package com.michelin.ns4kafka.services.clients.schema.entities;

import lombok.Builder;
import org.apache.avro.data.Json;

/**
 * Update topic description input entity's attributes
 *
 * @param mutatedEntities   The updated entities

 */
@Builder
public record TopicDescriptionUpdateResponse(Json mutatedEntities) {

}
