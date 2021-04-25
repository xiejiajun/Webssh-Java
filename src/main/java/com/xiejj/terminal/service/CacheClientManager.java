package com.xiejj.terminal.service;

import com.xiejj.terminal.protocol.ClusterInfo;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;

/**
 * @author xiejiajun
 */
@Slf4j
@Component("cacheClientManager")
public class CacheClientManager implements ClientManager {

    private final ClientCache<String, KubernetesClient> clientCache = new ClientCache<>(1000);


    /**
     * 获取K8s客户端
     * @param clusterName
     * @return
     */
    @Override
    public KubernetesClient getOrCreateK8sClient(String clusterName) throws Exception {
        ClusterInfo clusterInfo = this.getK8sClusterInfo(clusterName);
        this.destroyOldClient(clusterInfo);
        String clientKey = this.clientKey(clusterInfo);
        try {
            return this.clientCache.get(clientKey, () -> {
                Config clientConfig = this.buildAuthConfig(clusterInfo);
                // TODO 这里构建的客户端的OkHttpClient的连接池默认大小只有5个()
                //  需要更大并发支持的话可参考io.fabric8.kubernetes.client.utils包里面的
                //  HttpClientUtils.createHttpClient(io.fabric8.kubernetes.client.Config)方法以及
                //  okhttp3.OkHttpClient.Builder.connectionPool方法和okhttp3.ConnectionPool手动构造一个
                //  拥有更多连接的OkHttpClient，然后通过DefaultKubernetesClient(okhttp3.OkHttpClient, Config)
                //  来创建可以支持更高并发的K8s客户端
                KubernetesClient client = new DefaultKubernetesClient(clientConfig);
                return Optional.of(client);
            });
        } catch (Exception e) {
            log.error("构建K8s集群客户端出错", e);
            this.clientCache.remove(clientKey);
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
            // TODO 需要特别注意：fabric8拿到CA数据后会自动调用CertUtils.getInputStreamFromDataOrFile进行一次Base64解码
            //  所以clientConfig.setCaCertData传入的字符串必须是Base64编码的CA证书数据
            //  官方的Java/Go SDK都不会对CA证书数据进行自动Base64解码，这一点fabric8和官方库不一样，要特别注意
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
        KubernetesClient client = this.clientCache.get(oldVersionKey);
        if (Objects.isNull(client)) {
            return;
        }
        client.close();
        clientCache.remove(oldVersionKey);
    }

    @Override
    public void close() {
        this.clientCache.destroy();
    }
}
