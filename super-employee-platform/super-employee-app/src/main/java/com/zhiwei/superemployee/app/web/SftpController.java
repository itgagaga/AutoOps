package com.zhiwei.superemployee.app.web;

import com.zhiwei.superemployee.app.audit.AuditService;
import com.zhiwei.superemployee.app.resource.ManagedHost;
import com.zhiwei.superemployee.app.ssh.ManagedSshConnector;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.sftp.FileAttributes;
import net.schmizz.sshj.sftp.FileMode;
import net.schmizz.sshj.sftp.RemoteResourceInfo;
import net.schmizz.sshj.sftp.SFTPClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 浏览器侧 SFTP 文件浏览/上传/下载，用于手动部署与运维传文件。
 * <p>
 * 路径前缀 {@code /api/ssh/sftp}。菜单权限：管理员，或 {@code OPS_SSH_CLIENT} / {@code RESOURCE_HOST}。
 * 仅允许对资源中心已启用主机操作；写操作写审计。路径均经规范化，禁止越级。
 * </p>
 * @author yangdb
 */
@RestController
@RequestMapping("/api/ssh/sftp")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('OPS_SSH_CLIENT') or hasAuthority('RESOURCE_HOST')")
public class SftpController {
    private final ManagedSshConnector connector;
    private final AuditService audit;

    /**
     * @param connector SSH/SFTP 连接器
     * @param audit     审计服务
     */
    public SftpController(ManagedSshConnector connector, AuditService audit) {
        this.connector = connector;
        this.audit = audit;
    }

    /**
     * 列出远端目录条目（目录优先、名称排序），含大小/修改时间/权限八进制。
     *
     * @param hostId 主机主键（须已启用）
     * @param path   远端路径，空则根目录 {@code /}
     * @return 含 host 信息、path、parentPath、entries 的视图
     */
    @GetMapping("/{hostId}/list")
    public Map<String, Object> list(@PathVariable String hostId,
                                    @RequestParam(value = "path", required = false) String path) {
        String remotePath = normalize(path);
        ManagedHost host = connector.requireEnabledHost(hostId);
        try (SSHClient ssh = connector.open(host); SFTPClient sftp = ssh.newSFTPClient()) {
            List<Map<String, Object>> entries = new ArrayList<>();
            for (RemoteResourceInfo item : sftp.ls(remotePath)) {
                String name = item.getName();
                if (".".equals(name) || "..".equals(name)) {
                    continue;
                }
                FileAttributes attrs = item.getAttributes();
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("name", name);
                row.put("path", join(remotePath, name));
                row.put("directory", item.isDirectory());
                row.put("size", attrs == null ? 0L : attrs.getSize());
                row.put("mtime", attrs == null ? 0L : attrs.getMtime() * 1000L);
                row.put("permissions", attrs == null || attrs.getMode() == null
                        ? "" : Integer.toOctalString(attrs.getMode().getMask() & 0777));
                entries.add(row);
            }
            entries.sort(Comparator
                    .comparing((Map<String, Object> row) -> !(Boolean) row.get("directory"))
                    .thenComparing(row -> String.valueOf(row.get("name")).toLowerCase()));
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("hostId", hostId);
            result.put("hostName", host.getName());
            result.put("path", remotePath);
            result.put("parentPath", parentOf(remotePath));
            result.put("entries", entries);
            return result;
        }
        catch (IOException ex) {
            throw new IllegalStateException("SFTP 列目录失败：" + ex.getMessage(), ex);
        }
    }

    /**
     * 在远端创建目录（含中间路径）；禁止创建根目录。
     *
     * @param hostId 主机主键
     * @param body   含 path 字段
     * @return path 与 ok=true
     */
    @PostMapping("/{hostId}/mkdir")
    public Map<String, Object> mkdir(@PathVariable String hostId, @RequestBody Map<String, String> body) {
        String remotePath = normalize(body.get("path"));
        if ("/".equals(remotePath)) {
            throw new IllegalArgumentException("不能创建根目录");
        }
        try (SSHClient ssh = connector.open(hostId); SFTPClient sftp = ssh.newSFTPClient()) {
            sftp.mkdirs(remotePath);
            audit.record("SFTP_MKDIR", "HOST", hostId, remotePath);
            return Map.of("path", remotePath, "ok", true);
        }
        catch (IOException ex) {
            throw new IllegalStateException("创建目录失败：" + ex.getMessage(), ex);
        }
    }

    /**
     * 整文件上传到远端目录；可选 relativePath 保留相对子路径。
     *
     * @param hostId       主机主键
     * @param path         目标目录
     * @param relativePath 可选相对路径（禁止 {@code ..}）
     * @param file         上传文件
     * @return 远端 path、size、ok
     */
    @PostMapping(value = "/{hostId}/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> upload(@PathVariable String hostId,
                                      @RequestParam("path") String path,
                                      @RequestParam(value = "relativePath", required = false) String relativePath,
                                      @RequestPart("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择要上传的文件");
        }
        ResolvedRemote target = resolveRemote(path, relativePath, file.getOriginalFilename());
        Path tmp = null;
        try (SSHClient ssh = connector.open(hostId); SFTPClient sftp = ssh.newSFTPClient()) {
            ensureParentDirs(sftp, target.remoteDir());
            tmp = java.nio.file.Files.createTempFile("sftp-up-", "-" + target.filename().replaceAll("[^A-Za-z0-9._-]", "_"));
            file.transferTo(tmp);
            sftp.put(tmp.toString(), target.remoteFile());
            audit.record("SFTP_UPLOAD", "HOST", hostId, target.remoteFile() + " size=" + file.getSize());
            return Map.of("path", target.remoteFile(), "size", file.getSize(), "ok", true);
        }
        catch (IOException ex) {
            throw new IllegalStateException("上传失败：" + ex.getMessage(), ex);
        }
        finally {
            if (tmp != null) {
                try {
                    java.nio.file.Files.deleteIfExists(tmp);
                }
                catch (IOException ignored) {
                }
            }
        }
    }

    /**
     * 查询远端已上传字节数，供断点续传判断是否已完成或需从何处续写。
     *
     * @param hostId       主机主键
     * @param path         目标目录
     * @param relativePath 可选相对路径
     * @param fileName     文件名
     * @param totalSize    完整文件总字节数
     * @return exists / uploadedSize / complete 等
     */
    @GetMapping("/{hostId}/upload-status")
    public Map<String, Object> uploadStatus(@PathVariable String hostId,
                                            @RequestParam("path") String path,
                                            @RequestParam(value = "relativePath", required = false) String relativePath,
                                            @RequestParam("fileName") String fileName,
                                            @RequestParam("totalSize") long totalSize) {
        if (totalSize < 0) {
            throw new IllegalArgumentException("totalSize 非法");
        }
        ResolvedRemote target = resolveRemote(path, relativePath, fileName);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("path", target.remoteFile());
        result.put("totalSize", totalSize);
        try (SSHClient ssh = connector.open(hostId); SFTPClient sftp = ssh.newSFTPClient()) {
            FileAttributes attrs = sftp.statExistence(target.remoteFile());
            if (attrs == null) {
                result.put("exists", false);
                result.put("uploadedSize", 0L);
                result.put("complete", totalSize == 0);
                return result;
            }
            if (attrs.getType() == FileMode.Type.DIRECTORY) {
                throw new IllegalStateException("目标路径是目录，无法续传文件：" + target.remoteFile());
            }
            long uploaded = attrs.getSize();
            result.put("exists", true);
            result.put("uploadedSize", uploaded);
            result.put("complete", totalSize > 0 && uploaded == totalSize);
            return result;
        }
        catch (IOException ex) {
            throw new IllegalStateException("查询上传状态失败：" + ex.getMessage(), ex);
        }
    }

    /**
     * 分片上传：按 offset 写入远端文件。
     * offset=0 时截断重建；offset&gt;0 时从已有文件续写；写满 totalSize 后截断对齐并记审计。
     *
     * @param hostId       主机主键
     * @param path         目标目录
     * @param relativePath 可选相对路径
     * @param fileName     文件名
     * @param offset       本分片起始偏移
     * @param totalSize    完整文件总字节数
     * @param chunk        分片内容
     * @return 上传进度（uploadedSize/complete 等）
     */
    @PostMapping(value = "/{hostId}/upload-chunk", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> uploadChunk(@PathVariable String hostId,
                                           @RequestParam("path") String path,
                                           @RequestParam(value = "relativePath", required = false) String relativePath,
                                           @RequestParam("fileName") String fileName,
                                           @RequestParam("offset") long offset,
                                           @RequestParam("totalSize") long totalSize,
                                           @RequestPart("chunk") MultipartFile chunk) {
        if (chunk == null || chunk.isEmpty()) {
            throw new IllegalArgumentException("分片内容为空");
        }
        if (offset < 0 || totalSize < 0 || offset >= totalSize) {
            throw new IllegalArgumentException("offset/totalSize 非法");
        }
        long chunkSize = chunk.getSize();
        if (offset + chunkSize > totalSize) {
            throw new IllegalArgumentException("分片超出文件总大小");
        }
        ResolvedRemote target = resolveRemote(path, relativePath, fileName);
        byte[] data;
        try {
            data = chunk.getBytes();
        }
        catch (IOException ex) {
            throw new IllegalStateException("读取分片失败：" + ex.getMessage(), ex);
        }
        try (SSHClient ssh = connector.open(hostId); SFTPClient sftp = ssh.newSFTPClient()) {
            ensureParentDirs(sftp, target.remoteDir());
            java.util.Set<net.schmizz.sshj.sftp.OpenMode> modes = offset == 0
                    ? java.util.EnumSet.of(
                    net.schmizz.sshj.sftp.OpenMode.CREAT,
                    net.schmizz.sshj.sftp.OpenMode.TRUNC,
                    net.schmizz.sshj.sftp.OpenMode.WRITE)
                    : java.util.EnumSet.of(
                    net.schmizz.sshj.sftp.OpenMode.CREAT,
                    net.schmizz.sshj.sftp.OpenMode.WRITE);
            try (net.schmizz.sshj.sftp.RemoteFile remote = sftp.open(target.remoteFile(), modes)) {
                if (offset > 0) {
                    long current = remote.length();
                    if (current < offset) {
                        throw new IllegalStateException("远端文件不完整，无法从 offset=" + offset + " 续传（当前=" + current + "）");
                    }
                    if (current > offset) {
                        remote.setLength(offset);
                    }
                }
                remote.write(offset, data, 0, data.length);
                long written = offset + data.length;
                if (written >= totalSize) {
                    remote.setLength(totalSize);
                    audit.record("SFTP_UPLOAD", "HOST", hostId, target.remoteFile() + " size=" + totalSize + " resumed");
                }
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("path", target.remoteFile());
                result.put("offset", offset);
                result.put("uploadedSize", written);
                result.put("totalSize", totalSize);
                result.put("complete", written >= totalSize);
                result.put("ok", true);
                return result;
            }
        }
        catch (IOException ex) {
            throw new IllegalStateException("分片上传失败：" + ex.getMessage(), ex);
        }
    }

    /**
     * 下载远端文件（禁止目录；单文件上限 200MB）。
     *
     * @param hostId 主机主键
     * @param path   远端文件路径
     * @return 附件字节流
     */
    @GetMapping("/{hostId}/download")
    public ResponseEntity<byte[]> download(@PathVariable String hostId, @RequestParam("path") String path) {
        String remotePath = normalize(path);
        if ("/".equals(remotePath)) {
            throw new IllegalArgumentException("不能下载目录");
        }
        Path tmp = null;
        try (SSHClient ssh = connector.open(hostId); SFTPClient sftp = ssh.newSFTPClient()) {
            FileAttributes attrs = sftp.stat(remotePath);
            if (attrs.getType() == FileMode.Type.DIRECTORY) {
                throw new IllegalArgumentException("不能下载目录，请选择文件");
            }
            if (attrs.getSize() > 200L * 1024 * 1024) {
                throw new IllegalArgumentException("文件超过 200MB，请改用终端或其它方式传输");
            }
            tmp = java.nio.file.Files.createTempFile("sftp-dl-", ".bin");
            sftp.get(remotePath, tmp.toString());
            byte[] body = java.nio.file.Files.readAllBytes(tmp);
            String name = remotePath.substring(remotePath.lastIndexOf('/') + 1);
            audit.record("SFTP_DOWNLOAD", "HOST", hostId, remotePath);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename*=UTF-8''" + URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20"))
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(body);
        }
        catch (IOException ex) {
            throw new IllegalStateException("下载失败：" + ex.getMessage(), ex);
        }
        finally {
            if (tmp != null) {
                try {
                    java.nio.file.Files.deleteIfExists(tmp);
                }
                catch (IOException ignored) {
                }
            }
        }
    }

    /**
     * 删除远端文件或空目录；禁止删除根目录。
     *
     * @param hostId 主机主键
     * @param path   远端路径
     * @return path 与 ok=true
     */
    @DeleteMapping("/{hostId}/entry")
    public Map<String, Object> delete(@PathVariable String hostId, @RequestParam("path") String path) {
        String remotePath = normalize(path);
        if ("/".equals(remotePath)) {
            throw new IllegalArgumentException("不能删除根目录");
        }
        try (SSHClient ssh = connector.open(hostId); SFTPClient sftp = ssh.newSFTPClient()) {
            FileAttributes attrs = sftp.stat(remotePath);
            if (attrs.getType() == FileMode.Type.DIRECTORY) {
                sftp.rmdir(remotePath);
            } else {
                sftp.rm(remotePath);
            }
            audit.record("SFTP_DELETE", "HOST", hostId, remotePath);
            return Map.of("path", remotePath, "ok", true);
        }
        catch (IOException ex) {
            throw new IllegalStateException("删除失败：" + ex.getMessage(), ex);
        }
    }

    /**
     * 规范化远端绝对路径：统一斜杠、解析 {@code .}/{@code ..}，空路径视为根。
     *
     * @param path 原始路径
     * @return 规范化绝对路径
     */
    private static String normalize(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        String value = path.trim().replace('\\', '/');
        if (!value.startsWith("/")) {
            value = "/" + value;
        }
        String[] parts = value.split("/");
        List<String> stack = new ArrayList<>();
        for (String part : parts) {
            if (part.isBlank() || ".".equals(part)) {
                continue;
            }
            if ("..".equals(part)) {
                if (!stack.isEmpty()) {
                    stack.remove(stack.size() - 1);
                }
                continue;
            }
            stack.add(part);
        }
        if (stack.isEmpty()) {
            return "/";
        }
        return "/" + String.join("/", stack);
    }

    /**
     * 拼接目录与文件名。
     *
     * @param dir  目录路径
     * @param name 文件或子目录名
     * @return 拼接后的绝对路径
     */
    private static String join(String dir, String name) {
        if ("/".equals(dir)) {
            return "/" + name;
        }
        return dir.endsWith("/") ? dir + name : dir + "/" + name;
    }

    /**
     * 取路径的父目录；根目录返回 null。
     *
     * @param path 绝对路径
     * @return 父路径或 null
     */
    private static String parentOf(String path) {
        if (path == null || "/".equals(path)) {
            return null;
        }
        int idx = path.lastIndexOf('/');
        if (idx <= 0) {
            return "/";
        }
        return path.substring(0, idx);
    }

    /**
     * 清洗相对路径，禁止包含 {@code ..} 越级到上级目录。
     *
     * @param relativePath 原始相对路径
     * @return 清洗后的相对路径，空则 null
     */
    private static String sanitizeRelativePath(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return null;
        }
        String value = relativePath.trim().replace('\\', '/');
        while (value.startsWith("/")) {
            value = value.substring(1);
        }
        String[] parts = value.split("/");
        List<String> stack = new ArrayList<>();
        for (String part : parts) {
            if (part.isBlank() || ".".equals(part)) {
                continue;
            }
            if ("..".equals(part)) {
                throw new IllegalArgumentException("相对路径不能包含 ..");
            }
            stack.add(part);
        }
        return stack.isEmpty() ? null : String.join("/", stack);
    }

    /**
     * 根据目标目录、可选相对路径与原始文件名解析最终远端目录与文件路径。
     *
     * @param path         目标目录
     * @param relativePath 可选相对路径
     * @param originalName 上传原始文件名
     * @return 解析结果
     */
    private static ResolvedRemote resolveRemote(String path, String relativePath, String originalName) {
        String dir = normalize(path);
        String rel = sanitizeRelativePath(relativePath);
        String filename;
        String remoteDir = dir;
        if (rel != null) {
            int slash = rel.lastIndexOf('/');
            if (slash >= 0) {
                remoteDir = join(dir, rel.substring(0, slash));
                filename = rel.substring(slash + 1);
            } else {
                filename = rel;
            }
        } else {
            filename = originalName == null ? "upload.bin" : originalName.replace('\\', '/');
            int slash = filename.lastIndexOf('/');
            if (slash >= 0) {
                filename = filename.substring(slash + 1);
            }
        }
        if (filename.isBlank() || filename.contains("..")) {
            throw new IllegalArgumentException("非法文件名");
        }
        return new ResolvedRemote(remoteDir, filename, join(remoteDir, filename));
    }

    /**
     * 确保远端父目录存在（非根路径时 mkdirs）。
     *
     * @param sftp      SFTP 客户端
     * @param remoteDir 远端目录
     * @throws IOException 创建失败时抛出
     */
    private static void ensureParentDirs(SFTPClient sftp, String remoteDir) throws IOException {
        if (remoteDir != null && !remoteDir.isBlank() && !"/".equals(remoteDir)) {
            sftp.mkdirs(remoteDir);
        }
    }

    /**
     * 解析后的远端上传目标：目录、文件名与完整路径。
     *
     * @param remoteDir  远端目录
     * @param filename   文件名
     * @param remoteFile 完整远端文件路径
     */
    private record ResolvedRemote(String remoteDir, String filename, String remoteFile) {
    }
}
