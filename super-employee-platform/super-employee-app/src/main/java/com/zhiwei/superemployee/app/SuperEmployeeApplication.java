package com.zhiwei.superemployee.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 「智维超级员工」Spring Boot 应用入口。
 * <p>
 * 扫描 {@code com.zhiwei.superemployee} 包下全部组件，并启用定时任务
 * （如 Agent 心跳巡检、会话恢复调度等）。
 * </p>
 * @author yangdb
 */
@SpringBootApplication(scanBasePackages = "com.zhiwei.superemployee")
@EnableScheduling
public class SuperEmployeeApplication {

    /**
     * 启动嵌入式 Web 容器并引导 Spring 应用上下文。
     *
     * @param args 命令行参数，透传给 Spring Boot
     */
    public static void main(String[] args) {
        SpringApplication.run(SuperEmployeeApplication.class, args);
    }
}
