package com.architecture.flighthotelquery.config;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.AbstractValueAdaptingCache;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * Two-level cache: L1 Caffeine (in-process, TTL 10 s) in front of L2 Redis (distributed, TTL 10 min).
 * Read path: L1 -> L2 (promotes into L1) -> loader. Write path: writes through both levels.
 * Redis failures degrade to L1 + loader instead of failing the request.
 */
@Configuration
@EnableCaching
public class TwoLevelCacheConfig {

    public static final String BUDGET_CACHE = "budgetEstimates";

    @Bean
    @Primary
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration l2Config = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(10))
            .disableCachingNullValues()
            .prefixCacheNameWith("travel:")
            .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                new JdkSerializationRedisSerializer(getClass().getClassLoader())));
        RedisCacheManager l2 = RedisCacheManager.builder(connectionFactory).cacheDefaults(l2Config).build();
        l2.afterPropertiesSet();
        return new TwoLevelCacheManager(l2, Duration.ofSeconds(10), 10_000);
    }

    public static class TwoLevelCacheManager implements CacheManager {
        private final RedisCacheManager l2Manager;
        private final Duration l1Ttl;
        private final long l1MaxSize;
        private final Map<String, Cache> caches = new ConcurrentHashMap<>();

        public TwoLevelCacheManager(RedisCacheManager l2Manager, Duration l1Ttl, long l1MaxSize) {
            this.l2Manager = l2Manager;
            this.l1Ttl = l1Ttl;
            this.l1MaxSize = l1MaxSize;
        }

        @Override
        public Cache getCache(String name) {
            return caches.computeIfAbsent(name, n -> new TwoLevelCache(n,
                new CaffeineCache(n, Caffeine.newBuilder().expireAfterWrite(l1Ttl).maximumSize(l1MaxSize).build(), false),
                l2Manager.getCache(n)));
        }

        @Override
        public Collection<String> getCacheNames() {
            return caches.keySet();
        }
    }

    public static class TwoLevelCache extends AbstractValueAdaptingCache {
        private static final Logger log = LoggerFactory.getLogger(TwoLevelCache.class);
        private final String name;
        private final Cache l1;
        private final Cache l2;

        public TwoLevelCache(String name, Cache l1, Cache l2) {
            super(false);
            this.name = name;
            this.l1 = l1;
            this.l2 = l2;
        }

        @Override
        protected Object lookup(Object key) {
            ValueWrapper w = l1.get(key);
            if (w != null) {
                return w.get();
            }
            try {
                w = l2.get(key);
            } catch (RuntimeException e) {
                log.warn("L2 cache read failed for {}: {}", name, e.toString());
                return null;
            }
            if (w != null && w.get() != null) {
                l1.put(key, w.get());
                return w.get();
            }
            return null;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Object getNativeCache() {
            return this;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T get(Object key, Callable<T> valueLoader) {
            Object hit = lookup(key);
            if (hit != null) {
                return (T) fromStoreValue(hit);
            }
            synchronized (this) {
                hit = lookup(key);
                if (hit != null) {
                    return (T) fromStoreValue(hit);
                }
                try {
                    T loaded = valueLoader.call();
                    put(key, loaded);
                    return loaded;
                } catch (Exception e) {
                    throw new ValueRetrievalException(key, valueLoader, e);
                }
            }
        }

        @Override
        public void put(Object key, Object value) {
            try {
                l2.put(key, value);
            } catch (RuntimeException e) {
                log.warn("L2 cache write failed for {}: {}", name, e.toString());
            }
            l1.put(key, value);
        }

        @Override
        public void evict(Object key) {
            try {
                l2.evict(key);
            } catch (RuntimeException e) {
                log.warn("L2 cache evict failed for {}: {}", name, e.toString());
            }
            l1.evict(key);
        }

        @Override
        public void clear() {
            try {
                l2.clear();
            } catch (RuntimeException e) {
                log.warn("L2 cache clear failed for {}: {}", name, e.toString());
            }
            l1.clear();
        }
    }
}
