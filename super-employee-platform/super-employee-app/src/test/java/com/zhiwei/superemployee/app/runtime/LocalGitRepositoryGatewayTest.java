package com.zhiwei.superemployee.app.runtime;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link LocalGitRepositoryGateway}：合并前脏工作区过滤。
 */
class LocalGitRepositoryGatewayTest {

    @Test
    void ignoresIdeAndOpsUntrackedFiles() {
        String porcelain = """
                ?? .cursorindexingignore
                ?? .specstory/
                ?? .zhiwei-deploy/
                """;
        assertThat(LocalGitRepositoryGateway.blockingWorktreeChanges(porcelain)).isBlank();
    }

    @Test
    void stillBlocksTrackedSourceChanges() {
        String porcelain = """
                ?? .specstory/
                 M src/main/java/Demo.java
                ?? .zhiwei-deploy/
                """;
        assertThat(LocalGitRepositoryGateway.blockingWorktreeChanges(porcelain))
                .isEqualTo(" M src/main/java/Demo.java");
    }

    @Test
    void stillBlocksUntrackedSourceFiles() {
        assertThat(LocalGitRepositoryGateway.blockingWorktreeChanges("?? src/NewFix.java"))
                .isEqualTo("?? src/NewFix.java");
    }

    @Test
    void ignoresTrackedGitignoreAndDotfiles() {
        String porcelain = """
                 M .gitignore
                 M .cursorindexingignore
                ?? .specstory/
                """;
        assertThat(LocalGitRepositoryGateway.blockingWorktreeChanges(porcelain)).isBlank();
    }

    @Test
    void stillBlocksGithubWorkflowChanges() {
        assertThat(LocalGitRepositoryGateway.blockingWorktreeChanges(" M .github/workflows/ci.yml"))
                .isEqualTo(" M .github/workflows/ci.yml");
    }

    @Test
    void ignoresNestedBuildAndIdeNoise() {
        String porcelain = """
                ?? frontend/node_modules/lodash/index.js
                 M backend/target/classes/Demo.class
                ?? web/dist/assets/app.js
                 M app.iml
                ?? ~$手册.docx
                ?? venv/lib/python3.11/site.py
                ?? src/__pycache__/job.cpython-311.pyc
                 M frontend/.vscode/settings.json
                 M logs/app.log
                """;
        assertThat(LocalGitRepositoryGateway.blockingWorktreeChanges(porcelain)).isBlank();
    }

    @Test
    void stillBlocksTrackedRepairSource() {
        String porcelain = """
                 M .gitignore
                 M src/main/java/com/demo/opsapp/job/FaultInjectorJob.java
                ?? .specstory/
                """;
        assertThat(LocalGitRepositoryGateway.blockingWorktreeChanges(porcelain))
                .isEqualTo(" M src/main/java/com/demo/opsapp/job/FaultInjectorJob.java");
        assertThat(LocalGitRepositoryGateway.blockingWorktreePaths(porcelain))
                .containsExactly("src/main/java/com/demo/opsapp/job/FaultInjectorJob.java");
    }

    @Test
    void stillBlocksReadmeAndPom() {
        assertThat(LocalGitRepositoryGateway.blockingWorktreeChanges(" M README.md"))
                .isEqualTo(" M README.md");
        assertThat(LocalGitRepositoryGateway.blockingWorktreeChanges(" M pom.xml"))
                .isEqualTo(" M pom.xml");
    }
}
