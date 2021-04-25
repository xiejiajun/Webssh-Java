package com.xiejj.terminal.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.collect.Lists;
import io.fabric8.kubernetes.api.model.ExecConfig;
import io.fabric8.kubernetes.api.model.ExecEnvVar;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.utils.IOHelpers;
import io.fabric8.kubernetes.client.utils.Serialization;
import io.fabric8.kubernetes.client.utils.Utils;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author xiejiajun
 */
@Data
@Slf4j
@Builder
public class ClusterInfo {
    /**
     * 集群 名称
     */
    private String name;
    /**
     * 集群 CA 根证书(必须是Base64编码的数据)
     */
    private String caData;
    /**
     * 集群更改版本号
     */
    private int versionId;

    /**
     * 集群凭证
     */
    private String token;

    /**
     * 集群 api 访问地址
     */
    public String apiServerAddress;



    public Config buildExecConfig() throws IOException, InterruptedException {
//        String caData = "-----BEGIN CERTIFICATE-----\n" +
//                "-----END CERTIFICATE-----";
//        String masterUrl = "https://aws.eks.xxx";
//        Config clientConfig = Config.empty();
//        clientConfig.setMasterUrl(masterUrl);
//        clientConfig.setCaCertData(caData);
//
//        ExecConfig execConfig = new ExecConfig();
//        execConfig.setCommand("aws");
//        execConfig.setArgs(Lists.newArrayList("--region", "us-west-1", "eks", "get-token", "--cluster-name", "my-eks-cluster"));
//        execConfig.setApiVersion("client.authentication.k8s.io/v1alpha1");
//        ClusterInfo.ExecCredential ec = getExecCredentialFromExecConfig(execConfig, null);
//        if (ec != null && ec.status != null && ec.status.token != null) {
//            clientConfig.setOauthToken(ec.status.token);
//        } else {
//            log.warn("No token returned");
//        }
//
//        return clientConfig;
        // 下面这一行用于本地测试
        return ExecConfiger.getExecConfig();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class ExecCredential {
        public String kind;
        public String apiVersion;
        public ExecCredentialSpec spec;
        public ExecCredentialStatus status;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class ExecCredentialSpec {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class ExecCredentialStatus {
        public String token;
        // TODO clientCertificateData, clientKeyData, expirationTimestamp
    }

    protected static ExecCredential getExecCredentialFromExecConfig(ExecConfig exec, File configFile) throws IOException, InterruptedException {
        String apiVersion = exec.getApiVersion();
        if ("client.authentication.k8s.io/v1alpha1".equals(apiVersion) || "client.authentication.k8s.io/v1beta1".equals(apiVersion)) {
            List<ExecEnvVar> env = exec.getEnv();
            // TODO check behavior of tty & stdin
            ProcessBuilder pb = new ProcessBuilder(getAuthenticatorCommandFromExecConfig(exec, configFile, Utils.getSystemPathVariable()));
            pb.redirectErrorStream(true);
            if (env != null) {
                Map<String, String> environment = pb.environment();
                env.forEach(var -> environment.put(var.getName(), var.getValue()));
            }
            Process p = pb.start();
            String output;
            try (InputStream is = p.getInputStream()) {
                output = IOHelpers.readFully(is);
            }
            if (p.waitFor() != 0) {
                log.warn(output);
            }
            ExecCredential ec = Serialization.unmarshal(output, ExecCredential.class);
            if (!apiVersion.equals(ec.apiVersion)) {
                log.warn("Wrong apiVersion {} vs. {}", ec.apiVersion, apiVersion);
            } else {
                return ec;
            }
        } else { // TODO v1beta1?
            log.warn("Unsupported apiVersion: {}", apiVersion);
        }
        return null;
    }

    protected static List<String> getAuthenticatorCommandFromExecConfig(ExecConfig exec, File configFile, String systemPathValue) {
        String command = exec.getCommand();
        if (command.contains(File.separator) && !command.startsWith(File.separator) && configFile != null) {
            // Appears to be a relative path; normalize. Spec is vague about how to detect this situation.
            command = Paths.get(configFile.getAbsolutePath()).resolveSibling(command).normalize().toString();
        }
        List<String> argv = new ArrayList<>(Utils.getCommandPlatformPrefix());
        command = getCommandWithFullyQualifiedPath(command, systemPathValue);
        List<String> args = exec.getArgs();
        if (args != null) {
            argv.add(command + " " + String.join(" ", args));
        }
        return argv;
    }

    protected static String getCommandWithFullyQualifiedPath(String command, String pathValue) {
        String[] pathParts = pathValue.split(File.pathSeparator);

        // Iterate through path in order to find executable file
        for (String pathPart : pathParts) {
            File commandFile = new File(pathPart + File.separator + command);
            if (commandFile.exists()) {
                return commandFile.getAbsolutePath();
            }
        }

        return command;

    }

}
