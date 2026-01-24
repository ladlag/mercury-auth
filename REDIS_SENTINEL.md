# Redis Sentinel Configuration Example

This document provides example configurations for using Redis Sentinel mode with mercury-auth.

## Problem

When deploying Redis in Sentinel mode (for high availability), the application needs to connect to Redis Sentinels instead of directly to a Redis server. The error "Unable connect to xxxx" occurs when trying to use standalone Redis configuration with a Sentinel deployment.

## Solution

The updated RedisConfig now supports both standalone and sentinel modes. To use Sentinel mode, configure the sentinel properties instead of the standalone host/port.

## Configuration Examples

### Option 1: Using application.yml

```yaml
spring:
  redis:
    database: 0
    password: your-redis-password  # Optional, only if Redis requires authentication
    sentinel:
      master: mymaster  # The master name configured in your sentinel
      nodes: sentinel1:26379,sentinel2:26379,sentinel3:26379  # Comma-separated list of sentinel nodes
```

**Note**: When sentinel configuration is provided, the standalone `host` and `port` properties are ignored.

### Option 2: Using Environment Variables (Recommended for Production)

Set the following environment variables:

```bash
# Sentinel configuration
export REDIS_SENTINEL_MASTER=mymaster
export REDIS_SENTINEL_NODES=sentinel1:26379,sentinel2:26379,sentinel3:26379
export REDIS_PASSWORD=your-redis-password  # Optional
export REDIS_DATABASE=0
```

### Example for Container/Kubernetes Deployment

If you're deploying in a container cloud environment with Redis Sentinel:

```yaml
# docker-compose.yml or Kubernetes ConfigMap
environment:
  - REDIS_SENTINEL_MASTER=mymaster
  - REDIS_SENTINEL_NODES=redis-sentinel-0.redis-sentinel:26379,redis-sentinel-1.redis-sentinel:26379,redis-sentinel-2.redis-sentinel:26379
  - REDIS_PASSWORD=${REDIS_PASSWORD}
  - REDIS_DATABASE=0
```

## Testing the Configuration

### 1. Verify Sentinel Nodes are Reachable

Test connectivity to your sentinel nodes:

```bash
# Using redis-cli
redis-cli -h sentinel1 -p 26379 ping
redis-cli -h sentinel2 -p 26379 ping
redis-cli -h sentinel3 -p 26379 ping
```

### 2. Verify Master Name

Check the master name configured in sentinels:

```bash
redis-cli -h sentinel1 -p 26379 sentinel masters
```

Look for the master name (e.g., "mymaster") in the output.

### 3. Test Application Connection

Start the application with sentinel configuration and check the logs for successful Redis connection:

```bash
# Look for successful connection messages
2026-01-24 07:00:00.000  INFO --- [main] o.s.d.r.c.l.LettuceConnectionFactory     : Connecting to Redis Sentinel
```

## Troubleshooting

### "Unable to connect" Error

If you still see connection errors:

1. **Verify sentinel nodes are accessible** from your application's network
2. **Check the master name** matches your sentinel configuration
3. **Verify password** (if configured) is correct
4. **Check firewall rules** allow traffic to sentinel ports (default: 26379)
5. **Verify Redis master is running** by querying sentinels:
   ```bash
   redis-cli -h sentinel1 -p 26379 sentinel get-master-addr-by-name mymaster
   ```

### Switching Back to Standalone Mode

To use standalone Redis instead of Sentinel, simply remove or comment out the sentinel configuration:

```yaml
spring:
  redis:
    host: localhost
    port: 6379
    database: 0
    # sentinel:  # Comment out or remove sentinel config
    #   master: mymaster
    #   nodes: sentinel1:26379,sentinel2:26379
```

## Additional Resources

- [Redis Sentinel Documentation](https://redis.io/topics/sentinel)
- [Spring Data Redis - Sentinel Support](https://docs.spring.io/spring-data/redis/docs/current/reference/html/#redis:sentinel)
- [Lettuce Redis Client](https://lettuce.io/)
