package com.mercury.auth.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger logger = LoggerFactory.getLogger(RedisConfig.class);

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
            logger.info("Configuring Redis in Sentinel mode with master: {}", sentinelMaster);
            RedisSentinelConfiguration sentinelConfig = new RedisSentinelConfiguration();
            sentinelConfig.setMaster(sentinelMaster);
            
            // Parse sentinel nodes (format: host1:port1,host2:port2,host3:port3)
            String[] nodes = sentinelNodes.split(",");
            Set<RedisNode> sentinelNodesSet = new HashSet<>();
            for (String node : nodes) {
                String trimmedNode = node.trim();
                if (trimmedNode.isEmpty()) {
                    continue;
                }
                
                String[] parts = trimmedNode.split(":");
                if (parts.length == 2) {
                    try {
                        String nodeHost = parts[0].trim();
                        int nodePort = Integer.parseInt(parts[1].trim());
                        sentinelNodesSet.add(new RedisNode(nodeHost, nodePort));
                        logger.debug("Added sentinel node: {}:{}", nodeHost, nodePort);
                    } catch (NumberFormatException e) {
                        logger.warn("Invalid port number in sentinel node '{}': {}. Skipping this node.", trimmedNode, e.getMessage());
                    }
                } else {
                    logger.warn("Invalid sentinel node format '{}'. Expected format is 'host:port'. Skipping this node.", trimmedNode);
                }
            }
            
            if (sentinelNodesSet.isEmpty()) {
                throw new IllegalStateException("No valid sentinel nodes configured. Please check spring.redis.sentinel.nodes configuration.");
            }
            
            sentinelConfig.setSentinels(sentinelNodesSet);
            sentinelConfig.setDatabase(database);
            
            if (StringUtils.hasText(password)) {
                sentinelConfig.setPassword(password);
            }
            
            factory = new LettuceConnectionFactory(sentinelConfig);
            logger.info("Redis Sentinel configuration complete with {} sentinel nodes", sentinelNodesSet.size());
        } else {
            // Standalone mode
            logger.info("Configuring Redis in standalone mode: {}:{}", host, port);
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
