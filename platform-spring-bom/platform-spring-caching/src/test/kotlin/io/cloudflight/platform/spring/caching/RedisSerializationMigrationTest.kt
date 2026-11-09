package io.cloudflight.platform.spring.caching

import io.cloudflight.platform.spring.caching.autoconfigure.RedisSerializationMigrationAutoConfiguration
import io.cloudflight.platform.spring.caching.migration.RedisSerializationMigration
import io.cloudflight.platform.spring.context.ApplicationContextProfiles
import io.cloudflight.platform.spring.test.testcontainers.redis.RedisContainer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName

@SpringBootTest(classes = [CachingTestApplication::class])
@ActiveProfiles(ApplicationContextProfiles.TEST_CONTAINER)
@Testcontainers
class RedisSerializationMigrationTest(
    @Autowired private val connectionFactory: RedisConnectionFactory,
    @Autowired private val redisTemplate: StringRedisTemplate
) {

    companion object {
        @Container
        @ServiceConnection("redis")
        val redis = RedisContainer(DockerImageName.parse("redis:7.0.11"))
    }

    @Test
    fun markerIsSetOnStartup() {
        assertThat(redisTemplate.opsForValue().get(RedisSerializationMigration.MARKER_KEY))
            .isEqualTo(RedisSerializationMigration.SERIALIZATION_VERSION)
    }

    @Test
    fun flushesDataOfOldSerializationVersion() {
        redisTemplate.opsForValue().set(RedisSerializationMigration.MARKER_KEY, "1")
        redisTemplate.opsForValue().set("myCache::oldEntry", "written with Spring Boot 3")
        redisTemplate.opsForValue().set("spring:session:sessions:123", "old session")

        RedisSerializationMigration(connectionFactory).afterPropertiesSet()

        assertThat(redisTemplate.keys("*")).containsExactly(RedisSerializationMigration.MARKER_KEY)
        assertThat(redisTemplate.opsForValue().get(RedisSerializationMigration.MARKER_KEY))
            .isEqualTo(RedisSerializationMigration.SERIALIZATION_VERSION)
    }

    @Test
    fun flushesDataWithoutMarker() {
        redisTemplate.delete(RedisSerializationMigration.MARKER_KEY)
        redisTemplate.opsForValue().set("myCache::oldEntry", "written with Spring Boot 3")

        RedisSerializationMigration(connectionFactory).afterPropertiesSet()

        assertThat(redisTemplate.hasKey("myCache::oldEntry")).isFalse()
        assertThat(redisTemplate.opsForValue().get(RedisSerializationMigration.MARKER_KEY))
            .isEqualTo(RedisSerializationMigration.SERIALIZATION_VERSION)
    }

    @Test
    fun keepsDataOfCurrentSerializationVersion() {
        redisTemplate.opsForValue().set(
            RedisSerializationMigration.MARKER_KEY,
            RedisSerializationMigration.SERIALIZATION_VERSION
        )
        redisTemplate.opsForValue().set("myCache::currentEntry", "written with Spring Boot 4")

        RedisSerializationMigration(connectionFactory).afterPropertiesSet()

        assertThat(redisTemplate.hasKey("myCache::currentEntry")).isTrue()
    }

    @Test
    fun canBeDisabled() {
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RedisSerializationMigrationAutoConfiguration::class.java))
            .withBean(RedisConnectionFactory::class.java, { mock(RedisConnectionFactory::class.java) })
            .withPropertyValues("cloudflight.cache.redis.flush-on-serialization-change=false")
            .run { context ->
                assertThat(context).doesNotHaveBean(RedisSerializationMigration::class.java)
            }
    }
}
