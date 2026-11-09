package io.cloudflight.platform.spring.caching.migration

import mu.KotlinLogging
import org.springframework.beans.factory.InitializingBean
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript

/**
 * Flushes the Redis database once if the data in it was written with an older, incompatible serialization
 * format of the platform (i.e. Jackson 2 cache values and Spring Boot 3 sessions before the Spring Boot 4 upgrade).
 *
 * The currently used format is stored under [MARKER_KEY]. Comparing the marker, flushing and re-setting the marker
 * happens within a single Lua script, so it is atomic even if multiple instances start up at the same time,
 * and only the first one performs the flush.
 *
 * Disable with `cloudflight.cache.redis.flush-on-serialization-change=false`.
 */
class RedisSerializationMigration(private val connectionFactory: RedisConnectionFactory) : InitializingBean {

    override fun afterPropertiesSet() {
        try {
            val template = StringRedisTemplate(connectionFactory)
            val flushed = template.execute(FLUSH_SCRIPT, listOf(MARKER_KEY), SERIALIZATION_VERSION)
            if (flushed == 1L) {
                LOG.warn {
                    "Flushed the Redis database as it did not contain data in serialization format version " +
                            "$SERIALIZATION_VERSION. All caches, sessions and locks have been removed."
                }
            }
        } catch (e: Exception) {
            // don't prevent the startup if Redis is not reachable, the flush is retried on the next startup
            // as the marker is not set. Until then, EvictCacheErrorHandler and SafeRedisSessionSerializer
            // drop entries that can't be deserialized.
            LOG.error(e) { "Could not check the serialization format version of the Redis database" }
        }
    }

    companion object {
        const val MARKER_KEY = "cloudflight:platform:redis-serialization-version"

        /**
         * Increase this whenever the serialization of cache values or sessions changes incompatibly.
         * Version 2: Spring Boot 4 (Jackson 3, Spring Session/Security 7)
         */
        const val SERIALIZATION_VERSION = "2"

        private val FLUSH_SCRIPT = DefaultRedisScript(
            """
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return 0
            end
            redis.call('FLUSHDB')
            redis.call('SET', KEYS[1], ARGV[1])
            return 1
            """.trimIndent(),
            Long::class.java
        )

        private val LOG = KotlinLogging.logger { }
    }
}
