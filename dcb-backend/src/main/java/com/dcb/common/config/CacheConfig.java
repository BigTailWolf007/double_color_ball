package com.dcb.common.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * 缓存配置：为不同业务定义独立的缓存策略
 */
@Configuration
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(Arrays.asList(
                // 推荐号码缓存：50条，10分钟无访问过期
                new CaffeineCache("recommend",
                        Caffeine.newBuilder()
                                .maximumSize(50)
                                .expireAfterAccess(10, TimeUnit.MINUTES)
                                .build()),
                // 冷热号分析缓存：1条，1小时后自动过期（开奖数据每天最多更新2次）
                new CaffeineCache("lotteryAnalysis",
                        Caffeine.newBuilder()
                                .maximumSize(1)
                                .expireAfterWrite(1, TimeUnit.HOURS)
                                .build())
        ));
        return manager;
    }
}
