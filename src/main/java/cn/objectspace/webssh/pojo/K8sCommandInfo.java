package cn.objectspace.webssh.pojo;

import lombok.Data;

/**
 * @author xiejiajun
 */
@Data
public class K8sCommandInfo {
    private String operate;
    private String host;
    private Integer port = 22;
    private String username;
    private String password;
    private String command = "";
    private Long k8sClusterId;
    private String namespace;
    private String podName;
    private String container;

    private int cols = 80;
    private int rows = 24;
    private int width = 640;
    private int height = 480;

}
