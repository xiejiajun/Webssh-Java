package cn.objectspace.webssh.pojo;

import io.fabric8.kubernetes.client.Config;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.BooleanUtils;

/**
 * @author xiejiajun
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class K8sClusterInfo {
    private Long clusterId;
    private String accessToken;
    private String apiServerUrl;
    private Boolean enableSSL;
    private String caCertData;
    private String clientKeyData;
    private String clientKeyAlgo;

    public Config buildK8sConfig() {
        Config clientConfig = Config.empty();
        clientConfig.setOauthToken(accessToken);
        clientConfig.setMasterUrl(apiServerUrl);
        clientConfig.setTrustCerts(true);
        if (BooleanUtils.isTrue(enableSSL)) {
            clientConfig.setTrustCerts(false);
            clientConfig.setCaCertData(caCertData);
            clientConfig.setClientKeyData(clientKeyData);
            clientConfig.setClientKeyAlgo(clientKeyAlgo);
        }
        return clientConfig;
    }
}
