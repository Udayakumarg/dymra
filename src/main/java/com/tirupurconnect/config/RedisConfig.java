package com.tirupurconnect.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.ReadOffset;
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

    // FIX #1 (pom): JavaTimeModule registered here — jackson-datatype-jsr310 must be on classpath
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    /**
     * FIX #12: Use ApplicationRunner instead of inner @Bean with @PostConstruct.
     * ApplicationRunner fires after full context is ready, avoiding proxy/init-order issues.
     *
     * FIX #13: Spring Data Redis StreamOperations.createGroup() with MKSTREAM.
     * The correct API is: opsForStream().createGroup(key, groupName, readOffset)
     * For MKSTREAM behaviour we catch the "no such key" error and create the stream
     * first with a dummy XADD that we immediately delete, or use the raw connection
     * to execute XGROUP CREATE ... MKSTREAM. We use raw execute here.
     */
    @Bean
    public ApplicationRunner streamGroupInitializer(StringRedisTemplate redisTemplate) {
        return args -> {
            AppProperties.Redis.Streams streams = props.getRedis().getStreams();

            record Pair(String stream, String group) {}
            List<Pair> pairs = List.of(
                new Pair(streams.getInquiryCreated(),  "vitality-service"),
                new Pair(streams.getInquiryCreated(),  "notification-service"),
                new Pair(streams.getInquiryCreated(),  "analytics-service"),
                new Pair(streams.getSupplierUpdated(), "search-service"),
                new Pair(streams.getVitalityUpdated(), "search-service"),
                new Pair(streams.getVitalityUpdated(), "notification-service")
            );

            for (Pair pair : pairs) {
                try {
                    // XGROUP CREATE <stream> <group> $ MKSTREAM
                    // Raw execution — works on all Redis 5+ versions
                    redisTemplate.execute((org.springframework.data.redis.connection.RedisConnection conn) -> {
                        try {
                            conn.streamCommands().xGroupCreate(
                                pair.stream().getBytes(),
                                pair.group(),
                                ReadOffset.latest(),
                                true   // makeStream = MKSTREAM
                            );
                        } catch (Exception inner) {
                            // BUSYGROUP = group already exists, which is fine
                            if (inner.getMessage() == null || !inner.getMessage().contains("BUSYGROUP")) {
                                log.warn("Stream group init warning: stream={} group={} msg={}",
                                    pair.stream(), pair.group(), inner.getMessage());
                            }
                        }
                        return null;
                    });
                    log.info("Stream group ready: stream={} group={}", pair.stream(), pair.group());
                } catch (Exception e) {
                    log.warn("Could not init stream group: stream={} group={} error={}",
                        pair.stream(), pair.group(), e.getMessage());
                }
            }
        };
    }
}
