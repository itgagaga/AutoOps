package com.zhiwei.superemployee.core.workspace;

import java.util.Locale;

/**
 * 运维任务端侧/前端工程画像。
 * @author yangdb
 */
public enum ProjectClientKind {
    /** 无端侧工程，仅后端闭环。 */
    NONE,
    /** 常规 Web 前端（package.json，默认 npm run build）。 */
    WEB_NPM,
    /** uni-app（package.json，默认 npm run build:mp-weixin）。 */
    UNI_APP,
    /** 微信原生小程序（project.config.json / app.json，弱结构校验）。 */
    MP_WEIXIN_NATIVE;

    public static ProjectClientKind from(String raw) {
        if (raw == null || raw.isBlank()) {
            return NONE;
        }
        try {
            return ProjectClientKind.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        }
        catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "不支持的端侧工程类型：" + raw + "（支持 NONE / WEB_NPM / UNI_APP / MP_WEIXIN_NATIVE）");
        }
    }

    public boolean requiresClientWorkspace() {
        return this != NONE;
    }

    public boolean usesNodePackageJson() {
        return this == WEB_NPM || this == UNI_APP;
    }

    public String defaultVerifyCommand() {
        return switch (this) {
            case WEB_NPM -> "npm run build";
            case UNI_APP -> "npm run build:mp-weixin";
            case MP_WEIXIN_NATIVE, NONE -> "";
        };
    }

    public String displayName() {
        return switch (this) {
            case NONE -> "无端侧";
            case WEB_NPM -> "Web（npm）";
            case UNI_APP -> "uni-app";
            case MP_WEIXIN_NATIVE -> "微信原生小程序";
        };
    }
}
