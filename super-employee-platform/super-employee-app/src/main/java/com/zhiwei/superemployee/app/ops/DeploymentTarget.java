package com.zhiwei.superemployee.app.ops;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 自动部署绑定目标：远端部署目录 + 制品相对路径（JAR / 二进制 / 发布包名等）。
 * <p>
 * JSON 字段同时兼容 {@code jar}（历史）与 {@code artifact}（新）。
 * </p>
 * @author yangdb
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DeploymentTarget(String path, String jar) {

    @JsonCreator
    public static DeploymentTarget of(
            @JsonProperty("path") String path,
            @JsonProperty("jar") String jar,
            @JsonProperty("artifact") String artifact) {
        String value = blankToNull(artifact);
        if (value == null) {
            value = blankToNull(jar);
        }
        return new DeploymentTarget(path, value);
    }

    /** 与 {@link #jar()} 同义，便于新代码按制品语义调用。 */
    public String artifact() {
        return jar;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
