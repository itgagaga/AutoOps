package com.demo.opsapp.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 故意缺陷：配置了不存在的模板路径，读取时 FileNotFound / NoSuchFileException。
 */
@Service
public class InventoryService {

    private final Path templatePath;

    public InventoryService(@Value("${demo.inventory.template-path:config/missing-inventory-template.json}") String templatePath) {
        this.templatePath = Path.of(templatePath);
    }

    public String loadTemplate() throws IOException {
        // 修复：添加文件存在性检查，避免 NoSuchFileException
        if (!Files.exists(templatePath)) {
            return "{}"; // 默认空 JSON
        }
        return Files.readString(templatePath, StandardCharsets.UTF_8);
    }

    public int parseSkuCode(String sku) {
        // BUG: 空串或过短会 StringIndexOutOfBoundsException
        return Integer.parseInt(sku.substring(0, 4));
    }
}
