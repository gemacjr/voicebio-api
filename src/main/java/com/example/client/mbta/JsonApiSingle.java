package com.example.client.mbta;

/** A JSON:API document whose primary data is one resource: {@code {"data": {...}}}. */
public record JsonApiSingle<A>(JsonApiResource<A> data) {}
