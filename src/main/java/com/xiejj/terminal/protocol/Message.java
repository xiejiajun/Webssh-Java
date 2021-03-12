package com.xiejj.terminal.protocol;

import lombok.Data;

/**
 * @author xiejiajun
 */
@Data
public class Message {
    private MessageOperate operate;
    private String command;
    private int cols = 150;
    private int rows = 50;

    private String k8sClusterName;
    private String namespace;
    private String podName;
    private String container;
}
