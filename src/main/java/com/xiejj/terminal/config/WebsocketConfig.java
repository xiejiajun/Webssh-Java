package com.xiejj.terminal.config;

import com.xiejj.terminal.config.interceptor.WebSocketInterceptor;
import com.xiejj.terminal.websocket.TerminalWebsocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * @author xiejiajun
 */
@Configuration
@EnableWebSocket
public class WebsocketConfig implements WebSocketConfigurer {

    @Autowired
    private TerminalWebsocketHandler terminalWebsocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry webSocketHandlerRegistry) {
        webSocketHandlerRegistry.addHandler(terminalWebsocketHandler, "/terminal")
                .addInterceptors(new WebSocketInterceptor())
                .setAllowedOrigins("*");
    }
}
