package com.xiejj.terminal.service;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import lombok.Builder;
import lombok.Data;
import org.springframework.web.socket.WebSocketSession;

/**
 * WebSocket Session实体
 * @author xiejiajun
 */
@Data
@Builder
public class SessionHandle {

    private String sessionId;

    private WebSocketSession webSocketSession;

    private KubernetesClient k8sClient;

    private ExecWatch ttyWatcher;

    public void close() {
        if (ttyWatcher != null) {
            ttyWatcher.close();
        }
    }
}
