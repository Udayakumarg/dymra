package com.tirupurconnect.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app")
@Getter @Setter
public class AppProperties {

    private Jwt jwt = new Jwt();
    private Elasticsearch elasticsearch = new Elasticsearch();
    private Redis redis = new Redis();
    private Whatsapp whatsapp = new Whatsapp();
    private Outbox outbox = new Outbox();
    private Vitality vitality = new Vitality();

    @Getter @Setter
    public static class Jwt {
        private String secret;
        private long expiryMs = 86400000L;
    }

    @Getter @Setter
    public static class Elasticsearch {
        private String host = "localhost";
        private int port = 9200;
        private String username = "elastic";
        private String password = "changeme";
        private String indexName = "listings";
    }

    @Getter @Setter
    public static class Redis {
        private Streams streams = new Streams();
        private long idempotencyTtlHours = 48;

        @Getter @Setter
        public static class Streams {
            private String supplierUpdated = "stream:supplier.profile.updated";
            private String inquiryCreated  = "stream:inquiry.created";
            private String vitalityUpdated = "stream:vitality.score.updated";
            private String deadLetter      = "stream:dead-letter";
        }
    }

    @Getter @Setter
    public static class Whatsapp {
        private String provider = "mock";
        private String apiUrl;
        private String apiKey;
        private String sourceNumber;
    }

    @Getter @Setter
    public static class Outbox {
        private long schedulerDelayMs = 5000L;
        private int maxRetryAttempts = 3;
    }

    @Getter @Setter
    public static class Vitality {
        private int windowDays = 90;
        private Scores scores = new Scores();
        private Thresholds thresholds = new Thresholds();

        @Getter @Setter
        public static class Scores {
            private short waResponse        = 35;
            private short inquiryResponded  = 28;
            private short catalogueUpdated  = 20;
            private short phoneVerified     = 12;
            private short appLogin          = 5;
        }

        @Getter @Setter
        public static class Thresholds {
            private int active  = 65;
            private int dormant = 35;
            private int fading  = 10;
        }
    }
}
