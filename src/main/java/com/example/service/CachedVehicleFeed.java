package com.example.service;

import com.example.client.transit.GtfsRealtimeClient;
import com.google.transit.realtime.GtfsRealtime.FeedMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

/**
 * The latest GTFS-Realtime vehicle feed, fetched at most once per cache lifetime ({@code
 * application.cache.transit-feed-time-to-live-seconds}, 15 s by default).
 *
 * <p>A separate bean on purpose: {@code @Cacheable} works through a Spring proxy, so it would be
 * bypassed if {@link TransitService} called a cached method on itself.
 */
@Slf4j
@Component
public class CachedVehicleFeed {

  public static final String CACHE = "transitFeed";

  private final GtfsRealtimeClient client;

  public CachedVehicleFeed(GtfsRealtimeClient client) {
    this.client = client;
  }

  @Cacheable(cacheNames = CACHE, key = "'vehiclePositions'")
  public FeedMessage vehiclePositions() {
    FeedMessage feed = client.vehiclePositions();
    log.info(
        "Fetched GTFS-Realtime feed: {} entities, generated at {}",
        feed.getEntityCount(),
        feed.getHeader().getTimestamp());
    return feed;
  }
}
