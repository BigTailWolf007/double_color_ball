package com.dcb.common.config.event;

import org.springframework.context.ApplicationEvent;

/**
 * 配置变更事件
 */
public class ConfigChangedEvent extends ApplicationEvent {

    private final String configKey;

    public ConfigChangedEvent(Object source, String configKey) {
        super(source);
        this.configKey = configKey;
    }

    public String getConfigKey() {
        return configKey;
    }
}
