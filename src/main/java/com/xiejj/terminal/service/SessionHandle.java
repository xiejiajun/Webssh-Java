package com.xiejj.terminal.service;

import com.google.common.collect.Maps;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import lombok.Builder;
import lombok.Data;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.socket.WebSocketSession;

import java.io.Closeable;
import java.util.Map;

/**
 * WebSocket Session实体
 * @author xiejiajun
 */
@Data
@Builder
public class SessionHandle implements Closeable {

    private String sessionId;

    private WebSocketSession webSocketSession;

    private KubernetesClient k8sClient;

    private ExecWatch curTtyWatcher;

    /**
     * 最后一次访问时间
     */
    private long lastAccessTime;

    /**
     * 会话超时时间
     */
    private long sessionTimeoutMillis;

    private final Map<String, ExecWatch> podTtyWatchers = Maps.newConcurrentMap();

    public ExecWatch getTtyWatcher() {
        return this.curTtyWatcher;
    }

    public ExecWatch switchTtyWatcher(String ttyKey, ExecWatchCreator creator) {
        if (StringUtils.isBlank(ttyKey)) {
            return this.curTtyWatcher;
        }
        if (creator == null) {
            return this.curTtyWatcher;
        }
        ExecWatch ttyWatcher = this.podTtyWatchers.get(ttyKey);
        if (ttyWatcher != null) {
            this.curTtyWatcher = ttyWatcher;
            return this.curTtyWatcher;
        }
        ExecWatch newTtyWatcher = creator.create();
        if (newTtyWatcher != null) {
            this.curTtyWatcher = newTtyWatcher;
            this.podTtyWatchers.put(ttyKey, newTtyWatcher);
        }
        return this.curTtyWatcher;
    }


    public void refreshLastAccessTime() {
        this.lastAccessTime = System.currentTimeMillis();
    }

    public boolean isExpireSession() {
        if (this.sessionTimeoutMillis <= 0) {
            return false;
        }
        return System.currentTimeMillis() - this.lastAccessTime > this.sessionTimeoutMillis;
    }

    private void clearTtys() {
        if (podTtyWatchers.size() <= 0) {
            return;
        }
        for (Map.Entry<String, ExecWatch> entry : podTtyWatchers.entrySet()) {
            try {
                IOUtils.closeQuietly(entry.getValue());
            } catch (Exception ignore) {
            }
        }
    }

    @Override
    public void close() {
        this.clearTtys();
        IOUtils.closeQuietly(webSocketSession);
    }


    public interface ExecWatchCreator {
        /**
         * 创建ExecWatch实例
         * @return
         */
        ExecWatch create();
    }
}
