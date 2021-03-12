package com.xiejj.terminal.service;

import io.fabric8.kubernetes.client.KubernetesClient;

/**
 * @author xiejiajun
 */
public interface ClientManager {

    /**
     * 获取客户端
     * @param clusterName
     * @return
     * @throws Exception
     */
    KubernetesClient getOrCreateK8sClient(String clusterName) throws Exception;


    /**
     * 清理资源
     */
    void close();
}
