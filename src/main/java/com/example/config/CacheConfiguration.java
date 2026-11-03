package com.example.config;

import com.example.service.CachedVehicleFeed;
import com.example.service.MbtaService;
import com.github.benmanes.caffeine.jcache.configuration.CaffeineConfiguration;
import java.util.OptionalLong;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.cache.autoconfigure.JCacheManagerCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Caffeine caches (through JCache), one per kind of upstream data, sized and timed for how often it changes:
 * <ul>
 *   <li>reference data (MBTA routes, stations): {@code application.cache.caffeine.*}, 1 hour by default</li>
 *   <li>realtime data (GTFS-Realtime feed, predictions, alerts): {@code application.cache.realtime-time-to-live-seconds},
 *       15 s by default, matching how often MBTA regenerates it</li>
 * </ul>
 * Caching also keeps us within MBTA's rate limit (20 requests/minute without an API key).
 */
@Configuration
@EnableCaching
public class CacheConfiguration {

    private final ApplicationProperties.Cache properties;

    public CacheConfiguration(ApplicationProperties applicationProperties) {
        this.properties = applicationProperties.getCache();
    }

    @Bean
    public JCacheManagerCustomizer cacheManagerCustomizer() {
        long referenceTtl = properties.getCaffeine().getTimeToLiveSeconds();
        long referenceSize = properties.getCaffeine().getMaxEntries();
        long realtimeTtl = properties.getRealtimeTimeToLiveSeconds();
        return cm -> {
            createCache(cm, MbtaService.ROUTES_CACHE, configuration(referenceSize, referenceTtl));
            createCache(cm, MbtaService.STATIONS_CACHE, configuration(referenceSize, referenceTtl));
            createCache(cm, MbtaService.PREDICTIONS_CACHE, configuration(500, realtimeTtl));
            createCache(cm, MbtaService.ALERTS_CACHE, configuration(100, realtimeTtl));
            // one entry (the latest feed): protects the public feed from bursts of our own traffic
            createCache(cm, CachedVehicleFeed.CACHE, configuration(1, realtimeTtl));
        };
    }

    private static javax.cache.configuration.Configuration<Object, Object> configuration(long maxEntries, long timeToLiveSeconds) {
        CaffeineConfiguration<Object, Object> configuration = new CaffeineConfiguration<>();
        configuration.setMaximumSize(OptionalLong.of(maxEntries));
        configuration.setExpireAfterWrite(OptionalLong.of(TimeUnit.SECONDS.toNanos(timeToLiveSeconds)));
        configuration.setStatisticsEnabled(true); // feeds the cache.gets hit/miss metrics
        return configuration;
    }

    private static void createCache(
        javax.cache.CacheManager cm,
        String cacheName,
        javax.cache.configuration.Configuration<Object, Object> configuration
    ) {
        javax.cache.Cache<Object, Object> cache = cm.getCache(cacheName);
        if (cache != null) {
            cache.clear();
        } else {
            cm.createCache(cacheName, configuration);
        }
    }
}
