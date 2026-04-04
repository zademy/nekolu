/*
 * Author: Zademy
 * Website: https://zademy.com
 * Last modified: 2026-04-04
 */

package com.zademy.nekolu.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Cache;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zademy.nekolu.dto.FileInfoResponse;
import com.zademy.nekolu.model.LogicalFileMetadata;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Caffeine cache configuration for file metadata.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager("files", "fileInfo", "stats");
        cacheManager.setAsyncCacheMode(true);
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .recordStats());
        return cacheManager;
    }

    @Bean("fileInfoNativeCache")
    public Cache<Long, FileInfoResponse> fileInfoNativeCache() {
        return Caffeine.newBuilder()
                .maximumSize(5000)
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .recordStats()
                .build();
    }

    @Bean("logicalMetadataNativeCache")
    public Cache<String, LogicalFileMetadata> logicalMetadataNativeCache() {
        return Caffeine.newBuilder()
                .maximumSize(5000)
                .expireAfterWrite(15, TimeUnit.MINUTES)
                .recordStats()
                .build();
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
