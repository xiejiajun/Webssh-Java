package com.xiejj.terminal.websocket;

import com.xiejj.terminal.service.TerminalService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;

/**
 * @author xiejiajun
 */
@Slf4j
@Component
public class TerminalWebsocketHandler implements WebSocketHandler  {

    @Autowired
    private TerminalService terminalService;

    @Override
    public void afterConnectionEstablished(WebSocketSession webSocketSession) throws Exception {
        this.terminalService.bindSessionHandle(webSocketSession);
    }

    @Override
    public void handleMessage(WebSocketSession webSocketSession, WebSocketMessage<?> webSocketMessage) throws Exception {
        if (webSocketMessage instanceof TextMessage) {
            this.terminalService.handleMessage(((TextMessage) webSocketMessage).getPayload(), webSocketSession);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession webSocketSession, Throwable throwable) throws Exception {
        log.error("数据传输错误", throwable);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession webSocketSession, CloseStatus closeStatus) throws Exception {
        this.terminalService.close(webSocketSession);
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }
}
