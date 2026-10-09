package com.zhiwei.superemployee.app.knowledge;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
/**
 * @author yangdb
 */

class KnowledgeDocumentTextExtractorTest {
    private final KnowledgeDocumentTextExtractor extractor = new KnowledgeDocumentTextExtractor();

    @Test
    void extractsMarkdownText() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "runbook.md", "text/markdown",
                "# Redis 超时\n\n检查连接池配置。".getBytes());
        KnowledgeDocumentTextExtractor.ExtractedText text = extractor.extract(file);
        assertTrue(text.hasText());
        assertTrue(text.text().contains("Redis"));
        assertFalse(text.truncated());
    }
}
