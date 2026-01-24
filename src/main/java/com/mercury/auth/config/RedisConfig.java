package com.mercury.auth.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisNode;
import org.springframework.data.redis.connection.RedisSentinelConfiguration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.Set;

@Configuration
public class RedisConfig {

    @Value("${spring.redis.host:localhost}")
    private String host;
    @Value("${spring.redis.port:6379}")
    private int port;
    @Value("${spring.redis.database:0}")
    private int database;
    @Value("${spring.redis.password:#{null}}")
    private String password;
    
    // Sentinel configuration
    @Value("${spring.redis.sentinel.master:#{null}}")
    private String sentinelMaster;
    @Value("${spring.redis.sentinel.nodes:#{null}}")
    private String sentinelNodes;

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        LettuceConnectionFactory factory;
        
        // Check if sentinel configuration is provided
        if (StringUtils.hasText(sentinelMaster) && StringUtils.hasText(sentinelNodes)) {
            // Sentinel mode
            RedisSentinelConfiguration sentinelConfig = new RedisSentinelConfiguration();
            sentinelConfig.setMaster(sentinelMaster);
            
            // Parse sentinel nodes (format: host1:port1,host2:port2,host3:port3)
            String[] nodes = sentinelNodes.split(",");
            Set<RedisNode> sentinelNodesSet = new HashSet<>();
            for (String node : nodes) {
                String[] parts = node.trim().split(":");
                if (parts.length == 2) {
                    sentinelNodesSet.add(new RedisNode(parts[0], Integer.parseInt(parts[1])));
                }
            }
            sentinelConfig.setSentinels(sentinelNodesSet);
            sentinelConfig.setDatabase(database);
            
            if (StringUtils.hasText(password)) {
                sentinelConfig.setPassword(password);
            }
            
            factory = new LettuceConnectionFactory(sentinelConfig);
        } else {
            // Standalone mode
            RedisStandaloneConfiguration standaloneConfig = new RedisStandaloneConfiguration();
            standaloneConfig.setHostName(host);
            standaloneConfig.setPort(port);
            standaloneConfig.setDatabase(database);
            
            if (StringUtils.hasText(password)) {
                standaloneConfig.setPassword(password);
            }
            
            factory = new LettuceConnectionFactory(standaloneConfig);
        }
        
        return factory;
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }
}
