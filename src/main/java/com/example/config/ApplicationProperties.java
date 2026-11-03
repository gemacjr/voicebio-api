package com.example.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.web.cors.CorsConfiguration;

/**
 * Properties specific to Voicebio Api, bound from the {@code application.*} namespace
 * in the {@code application*.yml} files.
 */
@ConfigurationProperties(prefix = "application", ignoreUnknownFields = false)
public class ApplicationProperties {

    private final CorsConfiguration cors = new CorsConfiguration();
    private final Cache cache = new Cache();
    private final Security security = new Security();
    private final ApiDocs apiDocs = new ApiDocs();
    private final Mbta mbta = new Mbta();

    public CorsConfiguration getCors() {
        return cors;
    }

    public Cache getCache() {
        return cache;
    }

    public Security getSecurity() {
        return security;
    }

    public ApiDocs getApiDocs() {
        return apiDocs;
    }

    public Mbta getMbta() {
        return mbta;
    }

    /** MBTA V3 API settings (base URL and timeouts are standard {@code spring.http.serviceclient.mbta.*}). */
    public static class Mbta {

        /** Optional API key, sent as {@code x-api-key}. Without one MBTA allows 20 requests/minute, with one 1000. */
        private String apiKey;

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }
    }


    public static class Cache {

        private final Caffeine caffeine = new Caffeine();

        /**
         * Lifetime of realtime caches (GTFS-Realtime feed, predictions, alerts). MBTA regenerates realtime data every
         * 10 to 15 seconds; reference data (routes, stations) uses {@code caffeine.time-to-live-seconds}.
         */
        private int realtimeTimeToLiveSeconds = 15;

        public Caffeine getCaffeine() {
            return caffeine;
        }

        public int getRealtimeTimeToLiveSeconds() {
            return realtimeTimeToLiveSeconds;
        }

        public void setRealtimeTimeToLiveSeconds(int realtimeTimeToLiveSeconds) {
            this.realtimeTimeToLiveSeconds = realtimeTimeToLiveSeconds;
        }

        public static class Caffeine {

            /** How long entries stay in a cache after being written. */
            private int timeToLiveSeconds = 3600;

            /** Maximum number of entries per cache. */
            private long maxEntries = 100;

            public int getTimeToLiveSeconds() {
                return timeToLiveSeconds;
            }

            public void setTimeToLiveSeconds(int timeToLiveSeconds) {
                this.timeToLiveSeconds = timeToLiveSeconds;
            }

            public long getMaxEntries() {
                return maxEntries;
            }

            public void setMaxEntries(long maxEntries) {
                this.maxEntries = maxEntries;
            }
        }
    }

    public static class Security {

        private final Jwt jwt = new Jwt();

        public Jwt getJwt() {
            return jwt;
        }

        public static class Jwt {

            /** Base64-encoded HMAC secret used to sign and verify JWTs. */
            private String base64Secret;

            public String getBase64Secret() {
                return base64Secret;
            }

            public void setBase64Secret(String base64Secret) {
                this.base64Secret = base64Secret;
            }
        }
    }

    public static class ApiDocs {

        private String title = "Voicebio API";
        private String description = "Voicebio API documentation";
        private String version = "0.0.1";

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }
    }
}
