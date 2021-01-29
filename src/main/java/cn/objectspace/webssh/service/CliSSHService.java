package cn.objectspace.webssh.service;

import cn.objectspace.webssh.pojo.SSHConnectInfo;
import cn.objectspace.webssh.pojo.WebSSHData;
import com.jcraft.jsch.*;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Properties;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author xiejiajun
 */
public class CliSSHService{

    private Logger logger = LoggerFactory.getLogger(CliSSHService.class);

    private ExecutorService executorService = Executors.newCachedThreadPool();

    private SSHConnectInfo sshConnectInfo;

    private volatile Boolean isAlive = true;



    /**
     * @Description: 初始化连接
     * @Param: [session]
     * @return: void
     * @Author: NoCortY
     * @Date: 2020/3/7
     */
    public void startTerminal(WebSSHData webSSHData) {
        JSch jSch = new JSch();
        sshConnectInfo = new SSHConnectInfo();
        sshConnectInfo.setjSch(jSch);

        final WebSSHData finalSSHData = webSSHData;
        try {
            connectToSSH(sshConnectInfo, finalSSHData);
        } catch (JSchException | IOException e) {
            logger.error("webssh连接异常");
            logger.error("异常信息:{}", e.getMessage());
            try {
                //发送错误信息
                sendMessage(("ERROR : "+e.getMessage()).getBytes());
            } catch (IOException ex) {
                logger.error("消息发送失败");
                logger.error("异常信息:{}", ex.getMessage());
            }
        }
        this.recvCommand();
    }

    /**
     * @Description: 处理客户端发送的数据
     * @Param: [buffer, session]
     * @return: void
     * @Author: NoCortY
     * @Date: 2020/3/7
     */
    public void recvCommand() {
        try {
            ChannelShell channel = (ChannelShell) sshConnectInfo.getChannel();
            if (channel != null) {
                // channel.setPtySize(100,50, 100, 60);
                OutputStream outputStream = channel.getOutputStream();
                while (isAlive) {
                    copy(System.in, outputStream);
                }
            }
        } catch (IOException e) {
            logger.error("clissh连接异常");
            logger.error("异常信息:{}", e.getMessage());
            try {
                //发送错误信息
                sendMessage(("ERROR : "+e.getMessage()).getBytes());
            } catch (IOException ex) {
                logger.error("消息发送失败");
                logger.error("异常信息:{}", ex.getMessage());
            }
        }
    }

    public void sendMessage(byte[] buffer) throws IOException {
        System.out.print(new String(buffer));
    }

    public void close() {
        if (sshConnectInfo != null) {
            //断开连接
            if (sshConnectInfo.getChannel() != null)
                sshConnectInfo.getChannel().disconnect();
        }

    }

    /**
     * @Description: 使用jsch连接终端
     * @Param: [cloudSSH, webSSHData, webSocketSession]
     * @return: void
     * @Author: NoCortY
     * @Date: 2020/3/7
     */
    private void connectToSSH(SSHConnectInfo sshConnectInfo, WebSSHData webSSHData) throws JSchException, IOException {
        Session session;
        Properties config = new Properties();
        config.put("StrictHostKeyChecking", "no");
        //获取jsch的会话
        session = sshConnectInfo.getjSch().getSession(webSSHData.getUsername(), webSSHData.getHost(), webSSHData.getPort());
        session.setConfig(config);
        //设置密码
        session.setPassword(webSSHData.getPassword());
        //连接  超时时间30s
        session.connect(30000);

        //开启shell通道
        Channel channels = session.openChannel("shell");
        ChannelShell channel = (ChannelShell) channels;
//        channel.setPtySize(webSSHData.getCols(),webSSHData.getRows(),webSSHData.getWidth(),webSSHData.getHeight());

        //通道连接 超时时间3s
        channel.connect(3000);

        //设置channel
        sshConnectInfo.setChannel(channel);

        //转发消息
        transToSSH(channel, "\r");

        executorService.execute(new Runnable() {
            @Override
            public void run() {
                InputStream inputStream = null;
                try {
                    //读取终端返回的信息流
                     inputStream = channel.getInputStream();
                    //循环读取
                    byte[] buffer = new byte[8192];
                    int i = 0;
                    //如果没有数据来，线程会一直阻塞在这个地方等待数据。
                    while ((i = inputStream.read(buffer)) != -1) {
                        sendMessage(Arrays.copyOfRange(buffer, 0, i));
                    }

                } catch (IOException e) {
                    logger.error("消息发送失败");
                    logger.error("异常信息:{}", e.getMessage());
                } finally {
                    isAlive = false;
                    //断开连接后关闭会话
                    session.disconnect();
                    channel.disconnect();
                    IOUtils.closeQuietly(inputStream);
                    executorService.shutdownNow();
                    logger.info("SSH通道已关闭...");
                }
            }
        });

    }

    /**
     * @Description: 将消息转发到终端
     * @Param: [channel, data]
     * @return: void
     * @Author: NoCortY
     * @Date: 2020/3/7
     */
    private void transToSSH(Channel channel, String command) throws IOException {
        if (channel != null) {
            OutputStream outputStream = channel.getOutputStream();
            outputStream.write(command.getBytes());
            outputStream.flush();
        }
    }

    private void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[4096];
        int bytesRead;
        // 没有输入的时候in.available()为0 ，就会退出这一层循环，从而不会走到in.read阻塞逻辑
        while (in.available() > 0 && isAlive) {
            bytesRead = in.read(buffer);
            if (bytesRead < 0) {
                break;
            }
            out.write(buffer, 0, bytesRead);
            out.flush();
        }
        out.flush();
    }


    public static void main(String[] args) {
        WebSSHData webSSHData = new WebSSHData();
        webSSHData.setCols(100);
        webSSHData.setRows(50);
        webSSHData.setWidth(100);
        webSSHData.setHeight(60);
        webSSHData.setHost("192.168.142.229");
        webSSHData.setPort(2222);
        webSSHData.setUsername("root");
        webSSHData.setPassword("root");

        CliSSHService cliSSHService = new CliSSHService();
        cliSSHService.startTerminal(webSSHData);


    }


}
