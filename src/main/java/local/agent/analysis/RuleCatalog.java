package local.agent.analysis;

import java.util.Set;

public final class RuleCatalog {
    public static final Set<String> KNOWN_IDS = Set.of(
            "docs.readme",
            "build.manifest",
            "build.lock",
            "vcs.gitignore",
            "legal.license",
            "security.sensitive-file",
            "tests.missing",
            "tests.ratio",
            "maintenance.todos",
            "scan.file-limit"
    );

    private RuleCatalog() { }
}
