package cn.objectspace.test;

import cn.objectspace.webssh.pojo.K8sClusterInfo;

import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException, InterruptedException {
        K8sClusterInfo.buildExecConfig();
    }
}
