package com.zhiwei.superemployee.app.web;

import com.zhiwei.superemployee.app.system.ServerFilesystemService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 服务器本机文件系统浏览，供运维任务选择代码工作区及 Git 客户端定位仓库。
 * <p>
 * 路径前缀 {@code /api/system}。根目录与列表接口权限：管理员，或 {@code OPS_DASHBOARD} / {@code OPS_GIT_CLIENT}。
 * </p>
 * @author yangdb
 */
@RestController
@RequestMapping("/api/system")
public class SystemWorkspaceController {
    private final ServerFilesystemService filesystem;

    /**
     * @param filesystem 本机文件系统服务
     */
    public SystemWorkspaceController(ServerFilesystemService filesystem) {
        this.filesystem = filesystem;
    }

    /**
     * 列出本机可浏览的文件系统根（盘符/挂载点等）。
     *
     * @return 根目录列表
     */
    @GetMapping("/filesystem/roots")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('OPS_DASHBOARD') or hasAuthority('OPS_GIT_CLIENT')")
    public Object roots() {
        return filesystem.roots();
    }

    /**
     * 列出指定路径下的文件与子目录；路径非法或不可读时返回 400。
     *
     * @param path 可选绝对路径，空则由服务层取默认起点
     * @return 目录条目列表，或错误正文
     */
    @GetMapping("/filesystem")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('OPS_DASHBOARD') or hasAuthority('OPS_GIT_CLIENT')")
    public ResponseEntity<?> list(@RequestParam(value = "path", required = false) String path) {
        try {
            return ResponseEntity.ok(filesystem.list(path));
        }
        catch (IllegalArgumentException | IllegalStateException ex) {
            Map<String, String> error = new LinkedHashMap<>();
            error.put("message", ex.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
}
