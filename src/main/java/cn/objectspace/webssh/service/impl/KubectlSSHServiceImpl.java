package cn.objectspace.webssh.service.impl;

import cn.objectspace.utils.IOUtil;
import cn.objectspace.webssh.constant.ConstantPool;
import cn.objectspace.webssh.pojo.K8sClusterInfo;
import cn.objectspace.webssh.pojo.K8sConnectInfo;
import cn.objectspace.webssh.pojo.K8sCommandInfo;
import cn.objectspace.webssh.service.WebSSHService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Maps;
import io.fabric8.kubernetes.client.Callback;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.ExecListener;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import io.fabric8.kubernetes.client.utils.BlockingInputStreamPumper;
import okhttp3.Response;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import javax.annotation.PreDestroy;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static cn.objectspace.webssh.constant.ConstantPool.*;

/**
 * @author xiejiajun
 */
@Service("kubectlService")
public class KubectlSSHServiceImpl implements WebSSHService {
    private static final Logger logger = LoggerFactory.getLogger(KubectlSSHServiceImpl.class);

    /**
     * 存放Kubuctl连接信息的map
     */
    private Map<String, K8sConnectInfo> connectInfoMap;

    private Map<Long, KubernetesClient> k8sClusterClientMap;

    private ExecutorService executorService;


    @Override
    public void initConnection(WebSocketSession session) {
        connectInfoMap = Maps.newConcurrentMap();
        k8sClusterClientMap = Maps.newConcurrentMap();
        executorService = Executors.newCachedThreadPool();

        K8sConnectInfo connectInfo = K8sConnectInfo.builder()
                .webSocketSession(session)
                .build();
        String socketSessionKey = String.valueOf(session.getAttributes().get(ConstantPool.USER_UUID_KEY));
        connectInfoMap.put(socketSessionKey, connectInfo);

    }

    @Override
    public void recvHandle(String buffer, WebSocketSession session) {
        ObjectMapper objectMapper = new ObjectMapper();
        K8sCommandInfo commandInfo;
        try {
            commandInfo = objectMapper.readValue(buffer, K8sCommandInfo.class);
        } catch (IOException e) {
            logger.error("Json转换异常");
            logger.error("异常信息:{}", e.getMessage());
            return;
        }
        String socketId = String.valueOf(session.getAttributes().get(ConstantPool.USER_UUID_KEY));
        String operateType = commandInfo.getOperate().toLowerCase();
        K8sConnectInfo connectInfo;
        switch (operateType) {
            case WEBSSH_OPERATE_CONNECT:
                connectInfo = connectInfoMap.get(socketId);
                executorService.execute(() -> {
                    try {
                        establishK8sConnection(connectInfo, commandInfo, session);
                    } catch (Exception e) {
                        logger.error("k8s集群连接失败", e);
                        sendMessage(session, "Error: " + e.getMessage());
                        KubectlSSHServiceImpl.this.close(session);
                    }
                });
                break;
            case WEBSSH_OPERATE_COMMAND:
                String command = commandInfo.getCommand();
                connectInfo = connectInfoMap.get(socketId);
                if (connectInfo == null) {
                    break;
                }
                try {
                    ExecWatch execWatcher = connectInfo.getK8sExecutorWatcher();
                    if (execWatcher == null) {
                        break;
                    }
                    execWatcher.resize(commandInfo.getCols(), commandInfo.getRows());
                    this.sendCommand(execWatcher, command, session);
                } catch (Exception e) {
                    logger.error("命令执行失败", e);
                    sendMessage(session, "ERROR: " + e.getMessage());
                    this.close(session);

                }
                break;
            case WEBSSH_OPERATE_HEARTBEAT:
                connectInfo = connectInfoMap.get(socketId);
                if (connectInfo == null) {
                    break;
                }
                if (this.heartBeat(connectInfo.getK8sExecutorWatcher())) {
                    sendMessage(session, "Heartbeat healthy");
                }
                break;
            default:
                logger.error("不支持的操作");
                close(session);
        }


    }

    /**
     * 和K8s集群建立连接
     * @param connectInfo
     * @param commandInfo
     * @param session
     */
    private void establishK8sConnection(K8sConnectInfo connectInfo, K8sCommandInfo commandInfo, WebSocketSession session) {
        Long k8sClusterId = commandInfo.getK8sClusterId();
        KubernetesClient k8sClient = null;
        ExecWatch k8sExecutorWatcher = null;
        try {
            k8sClient = this.getOrCreateK8sClient(k8sClusterId);
            connectInfo.setK8sClient(k8sClient);
            String namespace = commandInfo.getNamespace();
            String podName = commandInfo.getPodName();
            String container = commandInfo.getContainer();
            if (StringUtils.isBlank(namespace)) {
                namespace = DEFAULT_K8S_NAMESPACE;
            }
            if (StringUtils.isBlank(podName)) {
                podName = DEFAULT_K8S_POD;
            }

            k8sExecutorWatcher = newExecWatch(k8sClient, namespace, podName, container, session);
            connectInfo.setK8sExecutorWatcher(k8sExecutorWatcher);
            BlockingInputStreamPumper out = new BlockingInputStreamPumper(k8sExecutorWatcher.getOutput(), new TerminalOutputCallback(session));
            executorService.submit(out);
            BlockingInputStreamPumper err = new BlockingInputStreamPumper(k8sExecutorWatcher.getError(), new TerminalOutputCallback(session));
            executorService.submit(err);
            BlockingInputStreamPumper errChannel = new BlockingInputStreamPumper(k8sExecutorWatcher.getErrorChannel(), new TerminalOutputCallback(session));
            executorService.submit(errChannel);
            this.sendCommand(k8sExecutorWatcher, "\r", session);
            // TODO 是否需要再加一个检查连接的逻辑，移除失败的连接
        } catch (Exception e) {
            logger.error("建立连接失败", e);
            this.sendMessage(session, "建立连接失败" + e.getMessage() );
            IOUtil.closeQuietly(k8sExecutorWatcher);
            IOUtil.closeQuietly(k8sClient);
            this.close(session);
        }

    }

    @Override
    public void sendMessage(WebSocketSession session, byte[] buffer) throws IOException {
        if (session == null || !session.isOpen()) {
            logger.error("WebSocket为null或者已经关闭");
            return;
        }
        session.sendMessage(new TextMessage(buffer));
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
            logger.error("WebSocket消息发送失败", e);
        }
    }
    @Override
    public void close(WebSocketSession session) {
        String socketId = String.valueOf(session.getAttributes().get(ConstantPool.USER_UUID_KEY));
        K8sConnectInfo connectInfo = connectInfoMap.get(socketId);
        this.connectInfoMap.remove(socketId);
        IOUtil.closeQuietly(session);
    }

    /**
     * 往容器发送待执行命令
     * @param execWatcher
     * @param command
     * @param session
     */
    private void sendCommand(ExecWatch execWatcher, String command, WebSocketSession session) {
        if (execWatcher != null) {
            try {
                OutputStream commandStream = execWatcher.getInput();
                commandStream.write(command.getBytes());
                commandStream.flush();
            } catch (IOException e) {
                logger.error("发送命令失败", e);
                if (session == null) {
                    return;
                }
                sendMessage(session, "往容器发送命令失败: " + e.getMessage());
            }
        }
    }

    /**
     * 心跳检查: 待测试
     * @param execWatch
     * @return
     */
    private boolean heartBeat(ExecWatch execWatch) {
        if (execWatch == null) {
            return false;
        }
        try {
            OutputStream commandStream = execWatch.getInput();
            commandStream.write("\r".getBytes());
            commandStream.flush();
            return true;
        } catch (IOException e) {
            logger.error("心跳检查失败", e);
            return false;
        }
    }

    private KubernetesClient getOrCreateK8sClient(Long k8sClusterId) {
        K8sClusterInfo clusterInfo = null;
        KubernetesClient k8sClient = null;
        try {
            if (k8sClusterId != null) {
                k8sClient = this.k8sClusterClientMap.get(k8sClusterId);
            }
            if (k8sClient != null) {
                return k8sClient;
            }
            clusterInfo = getK8sClusterInfo(k8sClusterId);
            if (clusterInfo == null) {
                clusterInfo = K8sClusterInfo.builder()
                        .accessToken(DEFAULT_K8S_CLUSTER_ACCESS_TOKEN)
                        .apiServerUrl(DEFAULT_K8S_CLUSTER_API_SERVER_URL)
                        .enableSSL(false)
                        .clusterId(-1L)
                        .build();
            }
            Config clientConfig = clusterInfo.buildK8sConfig();
            k8sClient = new DefaultKubernetesClient(clientConfig);
            this.k8sClusterClientMap.put(clusterInfo.getClusterId(), k8sClient);
            return k8sClient;
        } catch (Exception e) {
            logger.error("构建K8s集群客户端出错", e);
            if (clusterInfo == null) {
                throw new RuntimeException("K8s集群信息为空");
            }
            IOUtil.closeQuietly(k8sClient);
            this.k8sClusterClientMap.remove(clusterInfo.getClusterId());
            throw e;
        }

    }

    private K8sClusterInfo getK8sClusterInfo(Long k8sClusterId) {
        if (Objects.isNull(k8sClusterId)) {
            return null;
        }
        // TODO 从DB获取K8s集群信息
        return null;
    }

    private ExecWatch newExecWatch(KubernetesClient client, String namespace, String podName, String containerName, WebSocketSession session) {
        return client.pods().inNamespace(namespace).withName(podName).inContainer(containerName)
                .redirectingInput()
                .redirectingOutput()
                .redirectingError()
                .redirectingErrorChannel()
                .withTTY()
                .usingListener(new SimpleListener(session))
                .exec("/bin/bash");
    }

    private class SimpleListener implements ExecListener {
        private WebSocketSession session;
        private String socketId;

        public SimpleListener(WebSocketSession session) {
            this.session = session;
            this.socketId = String.valueOf(session.getAttributes().get(ConstantPool.USER_UUID_KEY));
        }


        @Override
        public void onOpen(Response response) {
            logger.info("{}: The shell will remain open for 10 seconds.", this.socketId);
            KubectlSSHServiceImpl.this.sendMessage(this.session, "The shell will remain open for 10 seconds.\n");
        }

        @Override
        public void onFailure(Throwable t, Response response) {
            String message = response != null ? response.message() : "";
            logger.error("{}: shell barfed, {}", this.socketId, message);
            sendMessage(session, "shell barfed, " + message);
            close(session);
        }

        @Override
        public void onClose(int code, String reason) {
            logger.info("The shell will now close, reason: {}", reason);
//            sendMessage(session, "The shell will now close.");
            close(session);
        }
    }

    private class TerminalOutputCallback implements Callback<byte[]> {
        private WebSocketSession session;

        public TerminalOutputCallback(WebSocketSession session) {
            this.session = session;
        }

        @Override
        public void call(byte[] data) {
            try {
                sendMessage(session, data);
            } catch (IOException e) {
                logger.error("WebSocket消息发送失败", e);
            }
        }
    }

    @PreDestroy
    public void free() {
        if (this.connectInfoMap != null) {
            Iterator<Map.Entry<String, K8sConnectInfo>> iterator = this.connectInfoMap.entrySet().iterator();
            while (iterator.hasNext()) {
                K8sConnectInfo connectInfo = iterator.next().getValue();
                if (connectInfo != null) {
                    if (connectInfo.getK8sExecutorWatcher() != null) {
                        sendCommand(connectInfo.getK8sExecutorWatcher(), "exit\n", null);
                    }
                }
                IOUtil.closeQuietly(connectInfo.getWebSocketSession());
                iterator.remove();
            }
        }
        if (this.k8sClusterClientMap != null) {
            Iterator<Map.Entry<Long, KubernetesClient>> clientIterator = this.k8sClusterClientMap.entrySet().iterator();
            while (clientIterator.hasNext()) {
                KubernetesClient k8sClient = clientIterator.next().getValue();
                IOUtil.closeQuietly(k8sClient);
                clientIterator.remove();
            }
        }
        this.executorService.shutdownNow();

    }

}
