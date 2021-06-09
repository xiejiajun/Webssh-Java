package com.xiejj.terminal.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Maps;
import com.xiejj.terminal.constant.ConstantPool;
import com.xiejj.terminal.protocol.Message;
import com.xiejj.terminal.protocol.MessageOperate;
import com.xiejj.terminal.utils.MapUtils;
import com.xiejj.terminal.utils.ThreadUtils;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.ExecListener;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import javax.annotation.Nonnull;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;

/**
 * @author xiejiajun
 */
@Service
@Slf4j
public class TerminalService {

    private Map<String, SessionHandle> sessionHandleMap;

    private Map<MessageOperate, OperateHandler> handlerMap;

    private ExecutorService executorService;

    @Autowired
    @Qualifier("cacheClientManager")
    private ClientManager clientManager;

    @PostConstruct
    public void initialize() {
        sessionHandleMap = Maps.newConcurrentMap();
        executorService = ThreadUtils.newFixedThreadPool(100, "terminal-stream-thread");
        handlerMap = Maps.newHashMap();
        handlerMap.put(MessageOperate.ESTABLISH_CONNECT, this::handleConnect);
        handlerMap.put(MessageOperate.COMMAND, this::handleCommand);
        handlerMap.put(MessageOperate.HEARTBEAT, this::handleHeartBeat);
        handlerMap.put(MessageOperate.RESIZE_WINDOW, this::handleResizeWindow);
    }

    /**
     * 为当前Websocket Session绑定SessionHandle
     * @param session
     */
    public void bindSessionHandle(WebSocketSession session) {
        String socketSessionId = session.getId();
        SessionHandle sessionHandle = SessionHandle.builder()
                .webSocketSession(session)
                .sessionId(socketSessionId)
                .build();
        sessionHandleMap.put(socketSessionId, sessionHandle);
    }

    /**
     * 处理终端连接请求
     * @param message
     * @param sessionHandle
     */
    private void handleConnect(Message message, SessionHandle sessionHandle) {
        try {
            this.establishK8sConnection(sessionHandle, message);
            log.debug("{} establish k8s connection success", sessionHandle.getSessionId());
        } catch (Exception e) {
            log.error("建立K8s集群连接失败", e);
            Throwable cause = e.getCause();
            String msg = Objects.isNull(cause) ? e.getMessage() : cause.getMessage();
            this.sendMessage(sessionHandle, "建立K8s集群连接失败:" + msg);
            this.close(sessionHandle);
        }
    }

    /**
     * 处理命令执行请求
     * @param message
     * @param sessionHandle
     */
    private void handleCommand(Message message, SessionHandle sessionHandle) {
        String command = message.getCommand();
        try {
            ExecWatch execWatcher = sessionHandle.getTtyWatcher();
            if (execWatcher == null) {
                return;
            }
            this.sendCommand(execWatcher, command);
            log.debug("{} exec command success", sessionHandle.getSessionId());
        } catch (Exception e) {
            log.error("命令执行失败", e);
            sendMessage(sessionHandle, "命令执行失败：" + e.getMessage());
            this.close(sessionHandle);
        }
    }

    /**
     * 心跳检查
     * @param message
     * @param sessionHandle
     * @return
     */
    private void handleHeartBeat(Message message, SessionHandle sessionHandle) {
        try {
            this.sendCommand(sessionHandle.getTtyWatcher(), " ");
            sendMessage(sessionHandle, "Heartbeat healthy");
        } catch (IOException e) {
            log.error("心跳检查失败", e);
            this.close(sessionHandle);
        }
    }

    /**
     * 终端窗口大小调整
     * @param message
     * @param sessionHandle
     */
    private void handleResizeWindow(Message message, SessionHandle sessionHandle) {
        ExecWatch ttyWatcher = sessionHandle.getTtyWatcher();
        if (ttyWatcher == null) {
            return;
        }
        ttyWatcher.resize(message.getCols(), message.getRows());
    }

    /**
     * 处理websocket消息
     * @param messageBuffer
     * @param session
     */
    public void handleMessage(String messageBuffer, WebSocketSession session) {
        ObjectMapper objectMapper = new ObjectMapper();
        Message message;
        try {
            message = objectMapper.readValue(messageBuffer, Message.class);
        } catch (IOException e) {
            log.error("命令解析异常:{}", e.getMessage());
            this.sendMessage(session, "命令解析失败：" + e.getMessage());
            return;
        }
        try {
            String sessionId = session.getId();
            MessageOperate operateType = message.getOperate();

           SessionHandle sessionHandle = this.sessionHandleMap.get(sessionId);
            if (sessionHandle == null) {
                this.sendMessage(session, "invalid session");
                this.close(session);
                return;
            }
            OperateHandler handler = this.handlerMap.get(operateType);
            if (handler == null) {
                this.sendMessage(session, "不支持的操作：" + operateType);
                return;
            }
            // 并发处理命令
            executorService.execute(() -> {
                try {
                    handler.handle(message, sessionHandle);
                } catch (Exception e) {
                    log.error(e.getMessage());
                }
            });
        } catch (Exception e) {
            log.error("执行执行异常:{}", e.getMessage());
            this.sendMessage(session, "指令执行异常:" + e.getMessage());
        }
    }

    /**
     * 和K8s容器建立连接
     * @param sessionHandle
     * @param commandInfo
     */
    private void establishK8sConnection(SessionHandle sessionHandle, Message commandInfo) throws Exception {
        String clusterName = commandInfo.getK8sClusterName();
        if (StringUtils.isBlank(clusterName)) {
            this.sendMessage(sessionHandle, "K8s集群名称未指定");
            this.close(sessionHandle);
            return;
        }
        KubernetesClient k8sClient = this.clientManager.getOrCreateK8sClient(clusterName);
        sessionHandle.setK8sClient(k8sClient);
        String namespace = commandInfo.getNamespace();
        String podName = commandInfo.getPodName();
        String container = commandInfo.getContainer();
        if (StringUtils.isBlank(namespace)) {
            namespace = ConstantPool.DEFAULT_K8S_NAMESPACE;
        }
        if (StringUtils.isBlank(podName)) {
            throw new RuntimeException("容器信息未指定");
        }

        final String finalNamespace = namespace;
        ExecWatch ttyWatcher = sessionHandle.switchTtyWatcher(commandInfo.ttyKey(),
                () -> this.newExecWatch(k8sClient, finalNamespace, podName, container, sessionHandle));
        ttyWatcher.resize(commandInfo.getCols(), commandInfo.getRows());

    }

    /**
     * 通过WebSocket发送消息到前端
     * @param sessionHandle
     * @param message
     */
    private void sendMessage(SessionHandle sessionHandle, String message) {
        if (sessionHandle == null) {
            log.info("Session信息为空");
            return;
        }
        this.sendMessage(sessionHandle.getWebSocketSession(), message);
    }

    /**
     * 通过WebSocket发送消息到前端
     * @param session
     * @param message
     */
    private void sendMessage(WebSocketSession session, String message) {
        try {
            this.sendMessage(session, message.getBytes());
        } catch (IOException e) {
            log.error("WebSocket消息发送失败", e);
        }
    }

    /**
     * 通过WebSocket发送消息到前端
     * @param sessionHandle
     * @param buffer
     */
    private void sendMessage(SessionHandle sessionHandle, byte[] buffer) throws IOException {
        if (sessionHandle == null) {
            log.info("Session信息为空");
            return;
        }
        this.sendMessage(sessionHandle.getWebSocketSession(), buffer);
    }

    /**
     * 通过WebSocket发送消息到前端
     * @param session
     * @param buffer
     */
    public void sendMessage(WebSocketSession session, byte[] buffer) throws IOException {
        if (session == null || !session.isOpen()) {
            log.error("WebSocket为空或者已关闭");
            return;
        }
        session.sendMessage(new TextMessage(buffer));
    }

    /**
     * 销毁Session
     * @param sessionHandle
     */
    public void close(SessionHandle sessionHandle) {
        if (sessionHandle == null) {
            return;
        }
        String sessionId = sessionHandle.getSessionId();
        sessionHandle.close();
        this.sessionHandleMap.remove(sessionId);
    }

    /**
     * 销毁Session
     * @param session
     */
    public void close(WebSocketSession session) {
        String sessionId = session.getId();
        IOUtils.closeQuietly(sessionHandleMap.get(sessionId));
        this.sessionHandleMap.remove(sessionId);
    }

    /**
     * 往容器发送待执行命令
     * @param execWatcher
     * @param command
     */
    private void sendCommand(ExecWatch execWatcher, String command) throws IOException {
        if (execWatcher != null) {
            OutputStream commandStream = execWatcher.getInput();
            commandStream.write(command.getBytes());
            commandStream.flush();
        }
    }

    /**
     * 构建终端Watcher
     * @param client
     * @param namespace
     * @param podName
     * @param containerName
     * @param sessionHandle
     * @return ExecWebSocketListener
     */
    private ExecWatch newExecWatch(KubernetesClient client, String namespace, String podName, String containerName, SessionHandle sessionHandle) {
        OutputStream terminalOutput = new TerminalOutputStream(sessionHandle);
        // writingErrorChannel会输出exit命令之后是否退出成功的消息，这里不需要
        // ExecWebSocketListener.onMessage里面streamID为3才会往errChannel写数据
        // [bash 标准输入/输出/错误输出-0/1/2](https://www.jianshu.com/p/cb3c5ad8dcc5): Shell只有0/1/2，这里的3是K8s自定义的？
        return client.pods().inNamespace(namespace).withName(podName).inContainer(containerName)
                .redirectingInput()
                .writingOutput(terminalOutput)
                .writingError(terminalOutput)
                .withTTY()
                .usingListener(new TerminalListener(sessionHandle))
                .exec("/bin/bash");
    }


    @PreDestroy
    public void destroy() {
        this.removeAllSessions();
        this.clientManager.close();
        this.executorService.shutdownNow();
    }

    /**
     * 清除所有Session
     */
    private void removeAllSessions() {
        if (MapUtils.isEmpty(this.sessionHandleMap)) {
            return;
        }
        Iterator<Map.Entry<String, SessionHandle>> iterator = this.sessionHandleMap.entrySet().iterator();
        while (iterator.hasNext()) {
            SessionHandle sessionHandle = iterator.next().getValue();
            IOUtils.closeQuietly(sessionHandle);
            iterator.remove();
        }
    }

    /**
     * 自定义重写了write(byte[] data)方法的OutputStream直接统一接收K8s Websocket的io.fabric8.kubernetes.client.dsl.internal
     *  .ExecWebSocketListener.onMessage接收到的响应消息，提高响应速度。
     *  （redirecting + 监听ExecWebSocketListener.getOutput / getError方式中间有一层转发和异步监听，效率比较低)
     */
    private class TerminalOutputStream extends OutputStream {
        private final SessionHandle sessionHandle;

        public TerminalOutputStream(@Nonnull SessionHandle sessionHandle) {
            this.sessionHandle = sessionHandle;
        }

        @Override
        public void write(int b) throws IOException {
            throw new IOException("not implements");
        }

        @Override
        public void write(@Nonnull byte[] buffer) throws IOException {
            TerminalService.this.sendMessage(this.sessionHandle, buffer);
        }
    }

    /**
     * 终端会话监听器
     */
    private class TerminalListener implements ExecListener {
        private final SessionHandle sessionHandle;
        private final String sessionId;

        public TerminalListener(SessionHandle sessionHandle) {
            this.sessionHandle = sessionHandle;
            this.sessionId = sessionHandle.getSessionId();
        }


        @Override
        public void onOpen(Response response) {
            log.info("{}: The shell will remain open.", this.sessionId);
        }

        @Override
        public void onFailure(Throwable t, Response response) {
            String message = response != null ? response.message() : "";
            log.error("{}: shell barfed, {}", this.sessionId, message);
            sendMessage(sessionHandle, "shell barfed, " + message);
            close(sessionHandle);
        }

        @Override
        public void onClose(int code, String reason) {
            log.info("{}: The shell will now close", this.sessionId);
            close(sessionHandle);
        }
    }


    interface OperateHandler{
        /**
         * 处理Websocket指令
         * @param message
         * @param sessionHandle
         */
        void handle(Message message, SessionHandle sessionHandle);
    }

}
