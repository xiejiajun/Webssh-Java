package com.xiejj.terminal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @author xiejiajun
 */
@SpringBootApplication(scanBasePackages = "com.xiejj.terminal")
public class TerminalLauncher {

    public static void main(String[] args) {
        SpringApplication.run(TerminalLauncher.class);
    }
}
