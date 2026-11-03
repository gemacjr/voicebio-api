package com.example.service;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.IntegrationTest;
import com.example.client.transit.GtfsRealtimeClient;
import com.example.transit.GtfsFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** The feed is downloaded once per cache lifetime, however many requests ask for it. */
@IntegrationTest
class CachedVehicleFeedIT {

  @Autowired private CachedVehicleFeed feed;

  @Autowired private CacheManager cacheManager;

  @MockitoBean private GtfsRealtimeClient client;

  @BeforeEach
  void clearCache() {
    cacheManager.getCache(CachedVehicleFeed.CACHE).clear();
    when(client.vehiclePositions()).thenReturn(GtfsFixtures.vehiclePositions());
  }

  @Test
  void repeatedReadsHitTheUpstreamOnce() {
    feed.vehiclePositions();
    feed.vehiclePositions();
    feed.vehiclePositions();

    verify(client, times(1)).vehiclePositions();
  }
}
