package cn.objectspace.webssh.constant;

/**
* @Description: 常量池
* @Author: NoCortY
* @Date: 2020/3/8
*/
public class ConstantPool {
    /**
     * 随机生成uuid的key名
     */
    public static final String USER_UUID_KEY = "user_uuid";
    /**
     * 发送指令：连接
     */
    public static final String WEBSSH_OPERATE_CONNECT = "connect";
    /**
     * 发送指令：命令
     */
    public static final String WEBSSH_OPERATE_COMMAND = "command";
    /**
     * 发送指令：心跳
     */
    public static final String WEBSSH_OPERATE_HEARTBEAT = "heartbeat";


    public static final String DEFAULT_K8S_CLUSTER_ACCESS_TOKEN = "eyJhbGciOiJSUzI1NiIsImtpZCI6InplbENnUUladzlzQnY3UHZwLWRieT" +
            "NJZU9IQjczZUZtVGE3bjR2aWttc2cifQ.eyJpc3MiOiJrdWJlcm5ldGVzL3NlcnZ" +
            "pY2VhY2NvdW50Iiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9uYW1lc3B" +
            "hY2UiOiJkZWZhdWx0Iiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9zZWN" +
            "yZXQubmFtZSI6ImRlZmF1bHQtdG9rZW4taHJidmsiLCJrdWJlcm5ldGVzLmlvL3N" +
            "lcnZpY2VhY2NvdW50L3NlcnZpY2UtYWNjb3VudC5uYW1lIjoiZGVmYXVsdCIsImt" +
            "1YmVybmV0ZXMuaW8vc2VydmljZWFjY291bnQvc2VydmljZS1hY2NvdW50LnVpZCI" +
            "6Ijc4NWFhYWY4LTY5ZTYtNGE4OS04OWMzLWRiOWFjYmNjYzVhYiIsInN1YiI6InN" +
            "5c3RlbTpzZXJ2aWNlYWNjb3VudDpkZWZhdWx0OmRlZmF1bHQifQ.YkXTjsZldp_l" +
            "Wn6rQlqtlzJ59MqzZlcnsnR1-0Kh_dIXuQkVjcC5fPP8XbtoatrzjlA0oXO0WUgH" +
            "WyWh8KLdG7s_Y2MKl2VfpOndcAfvdLWi-0jF1TrQz-CabYygUAv_DpzsFtQLWs9M" +
            "JLLO_lwVCTLLc5dWSKx0Bkm_zPZV4T7SmrhYMOjNuPXAAOej-aep9cM8WNZE2UA8" +
            "Vq5LW-j4w0z8IlzLLVyGnsH8Z4NSzZm1scLqYkFQUuvoYFuWGpa4TM74jalSXtF1" +
            "v5fbTzLy4A_8veS-gPpsIkMc8F-pRPWQiYwUyZw87MZhIS72nA9c1ikAU7uIXW2xeG2ZOwtfoA";
    public static final String DEFAULT_K8S_CLUSTER_API_SERVER_URL = "https://192.168.64.2:8443";
    public static final String DEFAULT_K8S_NAMESPACE = "default";
    public static final String DEFAULT_K8S_POD = "nginx-minikube-7fd68cf764-xpq87";
}
