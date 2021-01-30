package cn.objectspace.webssh.pojo;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import lombok.Builder;
import lombok.Data;
import org.springframework.web.socket.WebSocketSession;

/**
 * @author xiejiajun
 */
@Data
@Builder
public class K8sConnectInfo {

    private WebSocketSession webSocketSession;

    private KubernetesClient k8sClient;

    private ExecWatch k8sExecutorWatcher;
}
