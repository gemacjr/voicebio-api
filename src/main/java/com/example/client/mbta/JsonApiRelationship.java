package com.example.client.mbta;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.List;
import java.util.Optional;

/**
 * A JSON:API relationship. Its {@code data} is a single resource identifier for to-one relationships
 * ({@code parent_station}) and an array for to-many ({@code child_stops}); {@code ACCEPT_SINGLE_VALUE_AS_ARRAY}
 * reads both into one list, so callers never deal with the two shapes. {@code null} means "no related resource".
 */
public record JsonApiRelationship(@JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY) List<Identifier> data) {
    public record Identifier(String id, String type) {}

    public List<String> ids() {
        return data == null ? List.of() : data.stream().map(Identifier::id).toList();
    }

    public Optional<String> firstId() {
        return ids().stream().findFirst();
    }
}
