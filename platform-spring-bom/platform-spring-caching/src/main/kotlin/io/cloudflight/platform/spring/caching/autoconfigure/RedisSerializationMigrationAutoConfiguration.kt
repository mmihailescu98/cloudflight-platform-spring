package io.cloudflight.platform.spring.caching.autoconfigure

import io.cloudflight.platform.spring.caching.migration.RedisSerializationMigration
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.RedisOperations

/**
 * Registers the [RedisSerializationMigration]. The cache manager, the [org.springframework.data.redis.core.RedisTemplate]
 * and the session serializer of the platform depend on it, so the flush happens before any of them accesses Redis.
 */
@AutoConfiguration(after = [DataRedisAutoConfiguration::class])
@ConditionalOnClass(RedisOperations::class)
@ConditionalOnBean(RedisConnectionFactory::class)
@ConditionalOnProperty(
    prefix = "cloudflight.cache.redis",
    name = ["flush-on-serialization-change"],
    havingValue = "true",
    matchIfMissing = true
)
class RedisSerializationMigrationAutoConfiguration {

    @Bean
    fun redisSerializationMigration(connectionFactory: RedisConnectionFactory): RedisSerializationMigration {
        return RedisSerializationMigration(connectionFactory)
    }
}
