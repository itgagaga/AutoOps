package com.zhiwei.superemployee.core.workspace;

import java.util.Locale;
import java.util.Set;

/**
 * 运维任务后端工程画像。
 * @author yangdb
 */
public enum ProjectBackendKind {
    /** Java Maven（pom.xml + JAR 部署）。 */
    MAVEN,
    /** Java Maven WAR（pom.xml + Tomcat webapps 部署；适合前后端不分离老项目）。 */
    JAVA_WAR,
    /** Java Gradle（build.gradle + JAR 部署）。 */
    GRADLE,
    /** Node.js 后端（package.json + 进程重启）。 */
    NODE,
    /** Go（go.mod + 二进制替换）。 */
    GO,
    /** Python（requirements/pyproject + venv 重启）。 */
    PYTHON,
    /** Docker（Dockerfile/compose + 镜像 load）。 */
    DOCKER;

    public static ProjectBackendKind from(String raw) {
        if (raw == null || raw.isBlank()) {
            return MAVEN;
        }
        try {
            return ProjectBackendKind.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        }
        catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "不支持的后端工程类型：" + raw
                            + "（支持 MAVEN / JAVA_WAR / GRADLE / NODE / GO / PYTHON / DOCKER）");
        }
    }

    public String displayName() {
        return switch (this) {
            case MAVEN -> "Java Maven（JAR）";
            case JAVA_WAR -> "Java WAR（Tomcat）";
            case GRADLE -> "Java Gradle";
            case NODE -> "Node.js";
            case GO -> "Go";
            case PYTHON -> "Python";
            case DOCKER -> "Docker";
        };
    }

    /** 故障上下文 applicationType（兼容旧 JAVA）。 */
    public String applicationType() {
        return switch (this) {
            case MAVEN, JAVA_WAR, GRADLE -> "JAVA";
            case NODE -> "NODE";
            case GO -> "GO";
            case PYTHON -> "PYTHON";
            case DOCKER -> "DOCKER";
        };
    }

    public boolean isJavaJar() {
        return this == MAVEN || this == GRADLE;
    }

    public boolean isJavaWar() {
        return this == JAVA_WAR;
    }

    /** JAR 或 WAR 类 Java 制品部署。 */
    public boolean isJavaPackaged() {
        return isJavaJar() || isJavaWar();
    }

    public boolean usesMavenWorkspace() {
        return this == MAVEN || this == JAVA_WAR;
    }

    public boolean isPathReplace() {
        return this == NODE || this == GO || this == PYTHON;
    }

    public boolean isDocker() {
        return this == DOCKER;
    }

    public String defaultVerifyCommand() {
        return switch (this) {
            case MAVEN, JAVA_WAR -> "mvn test";
            case GRADLE -> "gradle test";
            case NODE -> "npm test";
            case GO -> "go test ./...";
            case PYTHON -> "pytest -q";
            case DOCKER -> "docker compose build";
        };
    }

    public Set<String> defaultAllowedCommands() {
        return switch (this) {
            case MAVEN, JAVA_WAR -> Set.of("mvn", "mvn.cmd");
            case GRADLE -> Set.of("gradle", "gradle.bat", "gradlew", "gradlew.bat", "./gradlew");
            case NODE -> Set.of("npm", "npm.cmd", "pnpm", "pnpm.cmd", "yarn", "yarn.cmd");
            case GO -> Set.of("go", "go.exe");
            case PYTHON -> Set.of("pytest", "pytest.exe", "python", "python.exe", "py");
            case DOCKER -> Set.of("docker", "docker.exe", "docker-compose", "docker-compose.exe");
        };
    }

    public String artifactLabel() {
        return switch (this) {
            case MAVEN, GRADLE -> "JAR";
            case JAVA_WAR -> "WAR";
            case GO -> "二进制";
            case NODE, PYTHON -> "发布包";
            case DOCKER -> "镜像/Compose";
        };
    }

    public String artifactExtension() {
        return isJavaWar() ? ".war" : isJavaJar() ? ".jar" : "";
    }
}
