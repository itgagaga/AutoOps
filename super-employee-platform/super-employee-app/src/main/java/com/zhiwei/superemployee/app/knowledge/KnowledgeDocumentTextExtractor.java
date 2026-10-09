package com.zhiwei.superemployee.app.knowledge;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 从上传文档中提取纯文本，供知识库 AI 预生成使用。
 * <p>支持常见文本格式；DOCX 通过读取 {@code word/document.xml} 去标签提取，无需额外依赖。</p>
 * @author yangdb
 */
@Component
public class KnowledgeDocumentTextExtractor {
    private static final Set<String> TEXT_EXTENSIONS = Set.of(
            "txt", "md", "markdown", "log", "csv", "json", "yml", "yaml", "xml", "properties", "html", "htm", "conf", "ini"
    );
    private static final int MAX_BYTES = 2 * 1024 * 1024;
    private static final int MAX_CHARS = 24_000;

    /**
     * 提取上传文件文本，并按上限截断。
     *
     * @param file 上传文件，可为 {@code null}
     * @return 提取结果；未上传时返回空结果
     */
    public ExtractedText extract(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ExtractedText.empty();
        }
        String filename = file.getOriginalFilename() == null ? "upload.bin" : file.getOriginalFilename().trim();
        String ext = extension(filename);
        if (file.getSize() > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文档过大，请控制在 2MB 以内或先截取关键章节");
        }
        try {
            byte[] bytes = file.getBytes();
            String text;
            if ("docx".equals(ext)) {
                text = extractDocx(bytes);
            } else if (TEXT_EXTENSIONS.contains(ext)) {
                text = decodeText(bytes);
                if ("html".equals(ext) || "htm".equals(ext) || "xml".equals(ext)) {
                    text = stripTags(text);
                }
            } else if ("pdf".equals(ext) || "doc".equals(ext) || "xls".equals(ext) || "xlsx".equals(ext)
                    || "ppt".equals(ext) || "pptx".equals(ext)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "暂不支持直接解析 ." + ext + "，请另存为 .txt / .md / .docx 后再导入");
            } else {
                text = decodeText(bytes);
                if (looksBinary(text)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "无法识别的二进制文档，请使用 txt/md/docx/log/json/yaml 等文本格式");
                }
            }
            text = normalize(text);
            if (text.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "未能从文档中提取到有效文本");
            }
            boolean truncated = text.length() > MAX_CHARS;
            if (truncated) {
                text = text.substring(0, MAX_CHARS);
            }
            return new ExtractedText(filename, text, truncated);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "读取上传文档失败：" + ex.getMessage());
        }
    }

    private static String extractDocx(byte[] bytes) throws IOException {
        try (InputStream in = new ByteArrayInputStream(bytes); ZipInputStream zip = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if ("word/document.xml".equals(entry.getName())) {
                    String xml = new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                    return stripTags(xml.replace("</w:p>", "\n").replace("</w:tr>", "\n"));
                }
            }
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "DOCX 中未找到正文（word/document.xml）");
    }

    private static String decodeText(byte[] bytes) {
        for (Charset charset : new Charset[]{StandardCharsets.UTF_8, Charset.forName("GBK")}) {
            try {
                CharsetDecoder decoder = charset.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT);
                return decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString();
            } catch (Exception ignored) {
                // try next
            }
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static String stripTags(String html) {
        return html.replaceAll("(?is)<script[^>]*>.*?</script>", " ")
                .replaceAll("(?is)<style[^>]*>.*?</style>", " ")
                .replaceAll("(?s)<[^>]+>", " ");
    }

    private static String normalize(String text) {
        return text.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[\\t\\x0B\\f]+", " ")
                .replaceAll(" *\\n *", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private static boolean looksBinary(String text) {
        if (text.isEmpty()) {
            return true;
        }
        int sample = Math.min(text.length(), 800);
        int bad = 0;
        for (int i = 0; i < sample; i++) {
            char c = text.charAt(i);
            if (c == 0 || (c < 9) || (c > 13 && c < 32)) {
                bad++;
            }
        }
        return bad * 20 > sample;
    }

    private static String extension(String filename) {
        int idx = filename.lastIndexOf('.');
        if (idx < 0 || idx == filename.length() - 1) {
            return "";
        }
        return filename.substring(idx + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * 文档提取结果。
     *
     * @param fileName  原始文件名
     * @param text      提取文本
     * @param truncated 是否因长度被截断
     */
    public record ExtractedText(String fileName, String text, boolean truncated) {
        static ExtractedText empty() {
            return new ExtractedText("", "", false);
        }

        boolean hasText() {
            return text != null && !text.isBlank();
        }
    }
}
