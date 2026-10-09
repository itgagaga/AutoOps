package com.zhiwei.superemployee.agent.starter;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(SpecialistAgentProperties.class)
@ComponentScan(basePackageClasses = SpecialistAgentAutoConfiguration.class)
public class SpecialistAgentAutoConfiguration {
}
