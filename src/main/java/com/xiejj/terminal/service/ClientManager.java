package com.xiejj.terminal.service;

import com.google.common.collect.Maps;
import com.xiejj.terminal.protocol.ClusterInfo;
import com.xiejj.terminal.utils.MapUtils;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

/**
 * @author xiejiajun
 */
@Slf4j
@Component
public class ClientManager {

    /**
     * TODO 最好改为LRU Cache实现，防止创建太多客户端
     * K8S客户端缓存
     */
    private final Map<String, KubernetesClient> clientMap = Maps.newConcurrentMap();


    /**
     * 获取K8s客户端
     * @param clusterName
     * @return
     */
    public KubernetesClient getOrCreateK8sClient(String clusterName) throws Exception {
        KubernetesClient k8sClient = null;
        ClusterInfo clusterInfo = this.getK8sClusterInfo(clusterName);
        this.destroyOldClient(clusterInfo);
        String clientKey = this.clientKey(clusterInfo);
        try {
            if (clientKey != null) {
                k8sClient = this.clientMap.get(clientKey);
            }
            if (k8sClient != null) {
                return k8sClient;
            }

            Config clientConfig = this.buildAuthConfig(clusterInfo);
            k8sClient = new DefaultKubernetesClient(clientConfig);
            this.clientMap.put(clientKey, k8sClient);
            return k8sClient;
        } catch (Exception e) {
            log.error("构建K8s集群客户端出错", e);
            IOUtils.closeQuietly(k8sClient);
            this.clientMap.remove(clientKey);
            throw e;
        }

    }

    /**
     * 构建K8s客户端认证配置
     * @param clusterInfo
     * @return
     */
    private Config buildAuthConfig(ClusterInfo clusterInfo) {
        Config config = null;
        try {
            // 优先使用命令创建
            config = clusterInfo.buildExecConfig();
        } catch (Exception ignore) {

        }
        if (config != null) {
            return config;
        }
        config = Config.empty();
        config.setOauthToken(clusterInfo.getToken());
        config.setMasterUrl(clusterInfo.getApiServerAddress());
        config.setTrustCerts(true);
        if (StringUtils.isNotBlank(clusterInfo.getCaData())) {
            config.setTrustCerts(false);
            config.setCaCertData(clusterInfo.getCaData());
        }
        return config;
    }

    /**
     * 拉取集群认证信息
     * @param clusterName
     * @return
     */
    private ClusterInfo getK8sClusterInfo(String clusterName) {
        if (Objects.isNull(clusterName)) {
            throw new RuntimeException("K8s集群名称不能为空");
        }
        ClusterInfo clusterInfo = this.getCluster(clusterName);
        if (Objects.isNull(clusterInfo)) {
            throw new RuntimeException(String.format("未找到名为: %s的K8s集群信息", clusterName));
        }
        if (StringUtils.isBlank(clusterInfo.getName())) {
            throw new RuntimeException("集群名称未返回");
        }
        if (StringUtils.isBlank(clusterInfo.getApiServerAddress())) {
            throw new RuntimeException("集群ApiServer地址为空");
        }
        if (StringUtils.isBlank(clusterInfo.getToken())) {
            throw new RuntimeException("集群Token为空");
        }
        return clusterInfo;
    }

    private ClusterInfo getCluster(String clusterName) {
        return ClusterInfo.builder()
                .apiServerAddress("www")
                .name("my-k8s-cluster")
                .caData("wwwwww")
                .token("wwww")
                .build();
    }

    /**
     * 生成客户端缓存Key
     * @param clusterInfo
     * @return
     */
    private String clientKey(ClusterInfo clusterInfo) {
        return String.format("%s_%s", clusterInfo.getName(), clusterInfo.getVersionId());
    }

    /**
     * 销毁过期版本K8S客户端
     * @param clusterInfo
     */
    private void destroyOldClient(ClusterInfo clusterInfo) {
        if (clusterInfo.getVersionId() <= 0) {
            return;
        }
        String oldVersionKey = String.format("%s_%s", clusterInfo.getName(), (clusterInfo.getVersionId() - 1));
        KubernetesClient client = this.clientMap.get(oldVersionKey);
        if (Objects.isNull(client)) {
            return;
        }
        client.close();
        clientMap.remove(oldVersionKey);
    }

    @PreDestroy
    public void close() {
        if (MapUtils.isEmpty(this.clientMap)) {
            return;
        }
        Iterator<Map.Entry<String, KubernetesClient>> clientIterator = this.clientMap.entrySet().iterator();
        while (clientIterator.hasNext()) {
            KubernetesClient k8sClient = clientIterator.next().getValue();
            IOUtils.closeQuietly(k8sClient);
            clientIterator.remove();
        }
    }

}
