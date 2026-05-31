package com.savi.jobprocessor.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static java.util.Collections.singletonList;

/**

 * ────────────────────────────────────────────────────────────────
 * BUG FIX: Race condition between INCR and EXPIRE
 * ────────────────────────────────────────────────────────────────
 *
 * The original code:
 *   Long count = redisTemplate.opsForValue().increment(key);  // INCR
 *   if (count == 1) {
 *       redisTemplate.expire(key, WINDOW);                    // EXPIRE
 *   }
 *
 * The problem:
 * INCR and EXPIRE are two separate Redis commands. Between them, a thread
 * (or process) could crash. If the app crashes after INCR but before EXPIRE,
 * the key has no TTL — it lives FOREVER. The IP is permanently rate-limited
 * until someone manually deletes the Redis key. This is a data leak.
 *
 * It's a subtle bug: happens rarely, but when it does, users are locked out
 * indefinitely with no way to recover except manual Redis intervention.
 *
 * The fix: use a Lua script executed atomically on the Redis server.
 * Redis executes Lua scripts as a single atomic operation — no other
 * command can interleave between INCR and EXPIRE within the script.
 * This eliminates the race window entirely.
 *
 * The Lua script:
 *   local count = redis.call('INCR', KEYS[1])
 *   if count == 1 then
 *       redis.call('EXPIRE', KEYS[1], ARGV[1])
 *   end
 *   return count
*/
@Service
public class RateLimiterService {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterService.class);

    private static final int MAX_REQUEST = 5;
    private static final Duration WINDOW  = Duration.ofMinutes(1);

    /**
     *Redis is single-threaded for command execution.
     * A Lua script runs as one atomic unit — no commands from other clients
     * can execute between lines of the script.
     * This is the Redis-native way to implement multi-step atomic operations.
     */
    private static final String RATE_LIMIT_SCRIPT =
            "local count = redis.call('INCR', KEYS[1])\n" +
                    "if count == 1 then\n" +
                    "    redis.call('EXPIRE', KEYS[1], ARGV[1])\n" +
                    "end\n" +
                    "return count";

    private final StringRedisTemplate redisTemplate;

    public RateLimiterService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }


    public void validateCreateJobRequest(String clientIp) {
        String key = "rate:job:create:" + clientIp;

        // Execute the Lua script atomically.
        // execute() returns Object; the Lua script returns a long (Redis integer reply).
        Long count = redisTemplate.execute(
                new DefaultRedisScript<>(RATE_LIMIT_SCRIPT, Long.class),
                singletonList(key),
                String.valueOf(WINDOW.getSeconds())  // ARGV[1] = TTL in seconds
        );

        log.debug("Rate limit check: ip={} count={}/{}", clientIp, count, MAX_REQUEST);

        if (count != null && count > MAX_REQUEST) {
            log.warn("Rate limit exceeded: ip={} count={}", clientIp, count);
            throw new RateLimitExceededException(
                    "Too many job creation requests. Max " + MAX_REQUEST +
                            " per minute. Please try again later."
            );
        }
    }
}