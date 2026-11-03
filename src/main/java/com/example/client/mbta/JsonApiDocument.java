package com.example.client.mbta;

import java.util.List;

/**
 * A JSON:API document whose primary data is a list: {@code {"data": [...], "included": [...]}}.
 * {@code included} holds related resources requested with {@code include=...} (e.g. a station's platforms).
 */
public record JsonApiDocument<A>(List<JsonApiResource<A>> data, List<JsonApiResource<A>> included) {
    public List<JsonApiResource<A>> included() {
        return included == null ? List.of() : included;
    }
}
