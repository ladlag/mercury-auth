package com.mercury.auth;

import com.mercury.auth.config.RedisConfig;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

public class RedisConfigTest {

    @Test
    void standaloneMode_whenNoSentinelConfigured() {
        RedisConfig config = new RedisConfig();
        ReflectionTestUtils.setField(config, "host", "localhost");
        ReflectionTestUtils.setField(config, "port", 6379);
        ReflectionTestUtils.setField(config, "database", 0);
        ReflectionTestUtils.setField(config, "password", null);
        ReflectionTestUtils.setField(config, "sentinelMaster", null);
        ReflectionTestUtils.setField(config, "sentinelNodes", null);

        RedisConnectionFactory factory = config.redisConnectionFactory();

        assertThat(factory).isInstanceOf(LettuceConnectionFactory.class);
        LettuceConnectionFactory lettuceFactory = (LettuceConnectionFactory) factory;
        assertThat(lettuceFactory.getHostName()).isEqualTo("localhost");
        assertThat(lettuceFactory.getPort()).isEqualTo(6379);
        assertThat(lettuceFactory.getDatabase()).isEqualTo(0);
    }

    @Test
    void sentinelMode_whenSentinelConfigured() {
        RedisConfig config = new RedisConfig();
        ReflectionTestUtils.setField(config, "host", "localhost");
        ReflectionTestUtils.setField(config, "port", 6379);
        ReflectionTestUtils.setField(config, "database", 1);
        ReflectionTestUtils.setField(config, "password", null);
        ReflectionTestUtils.setField(config, "sentinelMaster", "mymaster");
        ReflectionTestUtils.setField(config, "sentinelNodes", "sentinel1:26379,sentinel2:26379");

        RedisConnectionFactory factory = config.redisConnectionFactory();

        assertThat(factory).isInstanceOf(LettuceConnectionFactory.class);
        LettuceConnectionFactory lettuceFactory = (LettuceConnectionFactory) factory;
        // In sentinel mode, hostname should not be the standalone host
        assertThat(lettuceFactory.getDatabase()).isEqualTo(1);
    }

    @Test
    void standaloneMode_whenEmptySentinelConfigured() {
        RedisConfig config = new RedisConfig();
        ReflectionTestUtils.setField(config, "host", "redis-host");
        ReflectionTestUtils.setField(config, "port", 6380);
        ReflectionTestUtils.setField(config, "database", 2);
        ReflectionTestUtils.setField(config, "password", null);
        ReflectionTestUtils.setField(config, "sentinelMaster", "");
        ReflectionTestUtils.setField(config, "sentinelNodes", "");

        RedisConnectionFactory factory = config.redisConnectionFactory();

        assertThat(factory).isInstanceOf(LettuceConnectionFactory.class);
        LettuceConnectionFactory lettuceFactory = (LettuceConnectionFactory) factory;
        assertThat(lettuceFactory.getHostName()).isEqualTo("redis-host");
        assertThat(lettuceFactory.getPort()).isEqualTo(6380);
        assertThat(lettuceFactory.getDatabase()).isEqualTo(2);
    }
}
