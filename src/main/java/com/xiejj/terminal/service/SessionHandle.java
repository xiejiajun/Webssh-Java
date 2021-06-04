package com.xiejj.terminal.service;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import lombok.Builder;
import lombok.Data;
import org.apache.commons.io.IOUtils;
import org.springframework.web.socket.WebSocketSession;

import java.io.Closeable;

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

    private ExecWatch ttyWatcher;

    /**
     * 重置终端,支持在同一个websocket会话中切换容器时自动清理前面的连接
     */
    public void resetTerminal() {
        IOUtils.closeQuietly(this.ttyWatcher);
        this.ttyWatcher = null;
    }

    @Override
    public void close() {
        IOUtils.closeQuietly(ttyWatcher);
        IOUtils.closeQuietly(webSocketSession);
    }
}
