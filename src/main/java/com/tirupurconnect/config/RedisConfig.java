package com.tirupurconnect.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.util.List;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class RedisConfig {

    private final AppProperties props;

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
        StringRedisTemplate template = new StringRedisTemplate(factory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new StringRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    /**
     * Creates Redis Streams and consumer groups on startup.
     * Uses XGROUP CREATE with MKSTREAM — idempotent, safe to run multiple times.
     */
    @Bean
    public StreamConsumerGroupInitializer streamConsumerGroupInitializer(StringRedisTemplate redisTemplate) {
        return new StreamConsumerGroupInitializer(redisTemplate, props);
    }

    @Slf4j
    @RequiredArgsConstructor
    public static class StreamConsumerGroupInitializer {

        private final StringRedisTemplate redisTemplate;
        private final AppProperties props;

        // Called by Spring post-bean-init via @PostConstruct in a component,
        // or invoked directly. We expose this as a bean so it runs at startup.
        @jakarta.annotation.PostConstruct
        public void init() {
            AppProperties.Redis.Streams streams = props.getRedis().getStreams();

            List<StreamGroupPair> pairs = List.of(
                new StreamGroupPair(streams.getInquiryCreated(),  "vitality-service"),
                new StreamGroupPair(streams.getInquiryCreated(),  "notification-service"),
                new StreamGroupPair(streams.getInquiryCreated(),  "analytics-service"),
                new StreamGroupPair(streams.getSupplierUpdated(), "search-service"),
                new StreamGroupPair(streams.getVitalityUpdated(), "search-service"),
                new StreamGroupPair(streams.getVitalityUpdated(), "notification-service")
            );

            for (StreamGroupPair pair : pairs) {
                try {
                    redisTemplate.opsForStream().createGroup(
                        pair.stream(),
                        ReadOffset.from("0"),
                        pair.group()
                    );
                    log.info("Stream group ready: stream={} group={}", pair.stream(), pair.group());
                } catch (Exception e) {
                    // Group already exists — BUSYGROUP error is expected on restart
                    if (e.getMessage() != null && e.getMessage().contains("BUSYGROUP")) {
                        log.debug("Consumer group already exists: stream={} group={}", pair.stream(), pair.group());
                    } else {
                        log.warn("Could not create consumer group: stream={} group={} error={}",
                            pair.stream(), pair.group(), e.getMessage());
                    }
                }
            }
        }

        private record StreamGroupPair(String stream, String group) {}
    }
}
