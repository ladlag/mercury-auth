# Redis Sentinel Configuration Example

This document provides example configurations for using Redis Sentinel mode with mercury-auth.

## Problem

When deploying Redis in Sentinel mode (for high availability), the application needs to connect to Redis Sentinels instead of directly to a Redis server. The error "Unable connect to xxxx" occurs when trying to use standalone Redis configuration with a Sentinel deployment.

## Solution

Spring Boot 2.7+ has built-in support for both standalone and sentinel Redis modes through auto-configuration. Simply configure the appropriate properties in `application.yml` or via environment variables - **no custom code needed**.

## Configuration Examples

### Option 1: Standalone Mode (Default)

```yaml
spring:
  redis:
    host: localhost
    port: 6379
    database: 0
    password: your-redis-password  # Optional, only if Redis requires authentication
```

### Option 2: Sentinel Mode

```yaml
spring:
  redis:
    database: 0
    password: your-redis-password  # Password for Redis master/slaves (optional, only if Redis requires authentication)
    sentinel:
      master: mymaster  # The master name configured in your sentinel
      nodes: sentinel1:26379,sentinel2:26379,sentinel3:26379  # Comma-separated list of sentinel nodes
      password: your-sentinel-password  # Password for Sentinel nodes (optional, only if Sentinel authentication enabled)
```

**Note**: When sentinel configuration is provided, the standalone `host` and `port` properties are automatically ignored by Spring Boot.

### Option 3: Using Environment Variables (Recommended for Production)

Set the following environment variables:

```bash
# For Sentinel mode
export REDIS_SENTINEL_MASTER=mymaster
export REDIS_SENTINEL_NODES=sentinel1:26379,sentinel2:26379,sentinel3:26379
export REDIS_PASSWORD=your-redis-password  # Password for Redis master/slaves (optional)
export REDIS_SENTINEL_PASSWORD=your-sentinel-password  # Password for Sentinel nodes (optional)
export REDIS_DATABASE=0
```

Spring Boot will automatically detect these and configure Redis Sentinel mode.

### Example for Container/Kubernetes Deployment

If you're deploying in a container cloud environment with Redis Sentinel:

```yaml
# docker-compose.yml or Kubernetes ConfigMap
environment:
  - REDIS_SENTINEL_MASTER=mymaster
  - REDIS_SENTINEL_NODES=redis-sentinel-0.redis-sentinel:26379,redis-sentinel-1.redis-sentinel:26379,redis-sentinel-2.redis-sentinel:26379
  - REDIS_PASSWORD=${REDIS_PASSWORD}  # Password for Redis master/slaves
  - REDIS_SENTINEL_PASSWORD=${REDIS_SENTINEL_PASSWORD}  # Password for Sentinel nodes (if needed)
  - REDIS_DATABASE=0
```

## How It Works

Spring Boot's `spring-boot-starter-data-redis` automatically configures Redis based on your properties:
- If `spring.redis.sentinel.master` is set, it uses **Sentinel mode**
- Otherwise, it uses **Standalone mode** with `spring.redis.host` and `spring.redis.port`

No custom `@Configuration` class is needed - Spring Boot handles everything automatically!

### Understanding Password Configuration

There are two separate password configurations when using Sentinel mode:

1. **`spring.redis.password`** (or `REDIS_PASSWORD` env var): 
   - This is the password for the actual Redis master and slave instances
   - Required if your Redis servers have `requirepass` configured
   - Used to authenticate with Redis after Sentinel provides the master/slave addresses

2. **`spring.redis.sentinel.password`** (or `REDIS_SENTINEL_PASSWORD` env var):
   - This is the password for the Sentinel nodes themselves
   - Required if your Sentinel nodes have `requirepass` configured (less common)
   - Used to authenticate with Sentinel nodes to query master/slave information

**Most deployments only need `spring.redis.password`** as Sentinel nodes typically don't require authentication.

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
# Look for successful connection messages like:
YYYY-MM-DD HH:mm:ss.SSS  INFO --- [main] o.s.d.r.c.l.LettuceConnectionFactory     : Initializing Lettuce connection factory
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

### Switching Between Standalone and Sentinel Mode

To switch from Sentinel to Standalone mode, simply remove the sentinel configuration:

```yaml
spring:
  redis:
    host: localhost
    port: 6379
    database: 0
    # Don't set sentinel properties for standalone mode
```

Or unset the environment variables:
```bash
unset REDIS_SENTINEL_MASTER
unset REDIS_SENTINEL_NODES
```

## Additional Resources

- [Redis Sentinel Documentation](https://redis.io/topics/sentinel)
- [Spring Boot Redis Configuration Properties](https://docs.spring.io/spring-boot/docs/current/reference/html/application-properties.html#appendix.application-properties.data)
- [Spring Data Redis - Sentinel Support](https://docs.spring.io/spring-data/redis/docs/current/reference/html/#redis:sentinel)
- [Lettuce Redis Client](https://lettuce.io/)
