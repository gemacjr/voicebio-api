package com.example.client.mbta;

import java.util.Map;
import java.util.Optional;

/**
 * One JSON:API resource object (https://jsonapi.org/format/#document-resource-objects): {@code
 * {"id": ..., "type": ..., "attributes": {...}, "relationships": {...}}}.
 *
 * @param <A> the record the {@code attributes} member is read into
 */
public record JsonApiResource<A>(
    String id, String type, A attributes, Map<String, JsonApiRelationship> relationships) {
  /**
   * Id of the single resource a relationship points to, e.g. {@code relatedId("parent_station")}.
   */
  public Optional<String> relatedId(String relationship) {
    return relationships == null || !relationships.containsKey(relationship)
        ? Optional.empty()
        : relationships.get(relationship).firstId();
  }
}
