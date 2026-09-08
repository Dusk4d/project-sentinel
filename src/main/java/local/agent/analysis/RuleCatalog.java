package local.agent.analysis;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class RuleCatalog {
    public static final String DOCS_README = "docs.readme";
    public static final String DOCS_README_EMPTY = "docs.readme-empty";
    public static final String BUILD_MANIFEST = "build.manifest";
    public static final String BUILD_MANIFEST_EMPTY = "build.manifest-empty";
    public static final String BUILD_LOCK = "build.lock";
    public static final String AUTOMATION_CI = "automation.ci";
    public static final String VCS_GITIGNORE = "vcs.gitignore";
    public static final String LEGAL_LICENSE = "legal.license";
    public static final String SECURITY_SENSITIVE_FILE = "security.sensitive-file";
    public static final String TESTS_MISSING = "tests.missing";
    public static final String TESTS_RATIO = "tests.ratio";
    public static final String MAINTENANCE_TODOS = "maintenance.todos";
    public static final String SCAN_FILE_LIMIT = "scan.file-limit";

    public static final List<RuleDefinition> DEFINITIONS = List.of(
            new RuleDefinition(DOCS_README, "文档", "项目根目录缺少 README*"),
            new RuleDefinition(DOCS_README_EMPTY, "文档", "项目根目录存在 README* 但内容为空"),
            new RuleDefinition(BUILD_MANIFEST, "可复现性", "缺少可识别的构建清单"),
            new RuleDefinition(BUILD_MANIFEST_EMPTY, "可复现性", "存在受支持的构建清单但内容为空"),
            new RuleDefinition(BUILD_LOCK, "可复现性", "已识别技术栈但未锁定构建工具或依赖"),
            new RuleDefinition(AUTOMATION_CI, "自动化", "存在构建清单但未发现主流 CI 配置"),
            new RuleDefinition(VCS_GITIGNORE, "版本控制", "项目根目录缺少 .gitignore"),
            new RuleDefinition(LEGAL_LICENSE, "合规", "项目根目录缺少 LICENSE 或 COPYING"),
            new RuleDefinition(SECURITY_SENSITIVE_FILE, "安全", "发现可能包含凭据的敏感文件名"),
            new RuleDefinition(TESTS_MISSING, "测试", "存在源码但没有识别到测试文件"),
            new RuleDefinition(TESTS_RATIO, "测试", "测试文件数少于源码文件数的五分之一"),
            new RuleDefinition(MAINTENANCE_TODOS, "维护", "TODO/FIXME/HACK 数量超过配置阈值；未超过时输出 INFO"),
            new RuleDefinition(SCAN_FILE_LIMIT, "规模", "确认符合条件的文件数超过配置上限")
    );
    public static final Set<String> KNOWN_IDS = DEFINITIONS.stream().map(RuleDefinition::id)
            .collect(Collectors.toUnmodifiableSet());

    private RuleCatalog() { }
}
