package com.dcb.common.config.service;

import com.dcb.common.config.entity.SysConfig;
import com.dcb.common.config.mapper.SysConfigMapper;
import com.dcb.common.config.event.ConfigChangedEvent;
import com.dcb.common.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 系统配置服务：内存缓存 + 热加载
 */
@Slf4j
@Service
public class ConfigService {

    private final SysConfigMapper sysConfigMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final CacheManager cacheManager;

    /** 内存缓存：configKey → SysConfig */
    private final ConcurrentHashMap<String, SysConfig> cache = new ConcurrentHashMap<>();

    public ConfigService(SysConfigMapper sysConfigMapper,
                         ApplicationEventPublisher eventPublisher,
                         CacheManager cacheManager) {
        this.sysConfigMapper = sysConfigMapper;
        this.eventPublisher = eventPublisher;
        this.cacheManager = cacheManager;
    }

    /**
     * 启动时从 DB 加载全部配置到内存
     */
    @PostConstruct
    public void init() {
        try {
            List<SysConfig> list = sysConfigMapper.selectList(null);
            for (SysConfig c : list) {
                cache.put(c.getConfigKey(), c);
            }
            log.info("系统配置加载完成，共 {} 项", cache.size());
        } catch (Exception e) {
            log.warn("系统配置表加载失败（请先执行 init.sql 或 migration.sql）：{}", e.getMessage());
        }
    }

    /**
     * 获取字符串配置
     */
    public String getString(String key) {
        SysConfig config = cache.get(key);
        if (config == null) {
            throw new BizException("配置项不存在：" + key);
        }
        return config.getConfigValue();
    }

    /**
     * 获取整数配置
     */
    public int getInt(String key) {
        String value = getString(key);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new BizException("配置 " + key + " 的值不是有效整数：" + value);
        }
    }

    /**
     * 获取 Cron 配置（同时校验格式合法性）
     */
    public String getCron(String key) {
        String value = getString(key);
        validateCron(value);
        return value;
    }

    /**
     * 更新配置（写 DB + 刷新缓存）
     */
    public SysConfig update(String key, String value) {
        SysConfig config = cache.get(key);
        if (config == null) {
            throw new BizException("配置项不存在：" + key);
        }

        // 值校验
        validateValue(config.getConfigType(), value);

        // 更新 DB
        config.setConfigValue(value);
        config.setUpdatedAt(LocalDateTime.now());
        sysConfigMapper.updateById(config);

        // 刷新缓存
        cache.put(key, config);

        log.info("配置已更新：{} = {}", key, value);
        // 推荐配置变更时清除推荐缓存
        if (key.startsWith("recommend.")) {
            org.springframework.cache.Cache cache = cacheManager.getCache("recommend");
            if (cache != null) { cache.clear(); log.info("推荐缓存已清除"); }
        }
        // 发布配置变更事件（SCHEDULE 组需通知定时任务重调度）
        eventPublisher.publishEvent(new ConfigChangedEvent(this, key));
        return config;
    }

    /**
     * 按分组返回所有配置
     */
    public Map<String, List<SysConfig>> getAllByGroup() {
        List<SysConfig> all = new ArrayList<>(cache.values());
        all.sort(Comparator.comparingInt(SysConfig::getSortOrder));

        return all.stream()
                .collect(Collectors.groupingBy(
                        SysConfig::getConfigGroup,
                        LinkedHashMap::new,
                        Collectors.toList()));
    }

    /**
     * 手动刷新全部缓存
     */
    public void refresh() {
        cache.clear();
        init();
        log.info("系统配置缓存已刷新");
    }

    /** 值校验 */
    private void validateValue(String type, String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new BizException("配置值不能为空");
        }
        switch (type.toUpperCase()) {
            case "INT":
                try { Integer.parseInt(value.trim()); } catch (NumberFormatException e) {
                    throw new BizException("请输入有效的整数");
                }
                break;
            case "CRON":
                validateCron(value.trim());
                break;
            default:
                break;
        }
    }

    /** Cron 表达式基本校验（6 段空格分隔） */
    private void validateCron(String cron) {
        if (cron == null || cron.trim().isEmpty()) {
            throw new BizException("Cron 表达式不能为空");
        }
        String[] parts = cron.trim().split("\\s+");
        if (parts.length != 6 && parts.length != 7) {
            throw new BizException("Cron 表达式格式错误，需要 6 或 7 个字段（空格分隔），如：0 30 21 ? * 3,5,1");
        }
    }
}
