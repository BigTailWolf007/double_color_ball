package com.dcb.common.config.controller;

import com.dcb.common.config.entity.SysConfig;
import com.dcb.common.config.service.ConfigService;
import com.dcb.common.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 系统配置接口
 */
@Slf4j
@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
public class ConfigController {

    private final ConfigService configService;
    private final CacheManager cacheManager;

    /** 按分组返回所有配置 */
    @GetMapping("/list")
    public Result<Map<String, List<SysConfig>>> list() {
        return Result.success(configService.getAllByGroup());
    }

    /** 修改单个配置 */
    @PutMapping("/{key}")
    public Result<SysConfig> update(@PathVariable String key, @RequestBody Map<String, String> body) {
        String value = body.get("value");
        SysConfig config = configService.update(key, value);
        // 兜底：推荐配置变更时手动清除推荐缓存
        if (key.startsWith("recommend.")) {
            Cache cache = cacheManager.getCache("recommend");
            if (cache != null) { cache.clear(); log.info("推荐缓存已清除(controller)"); }
        }
        return Result.success(config);
    }

    /** 手动刷新缓存 */
    @PostMapping("/refresh")
    public Result<Void> refresh() {
        configService.refresh();
        return Result.success();
    }
}
