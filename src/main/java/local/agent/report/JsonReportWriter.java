package local.agent.report;

import local.agent.analysis.Finding;
import local.agent.analysis.ProjectProfile;

public final class JsonReportWriter {
    public String render(ProjectProfile profile) {
        var out = new StringBuilder();
        out.append("{\n")
                .append("  \"schemaVersion\": 1,\n")
                .append("  \"project\": ").append(quote(profile.name())).append(",\n")
                .append("  \"root\": ").append(quote(profile.root().toString())).append(",\n")
                .append("  \"ecosystem\": ").append(quote(profile.ecosystem())).append(",\n")
                .append("  \"healthScore\": ").append(profile.healthScore()).append(",\n")
                .append("  \"scoreWeights\": {\"high\": ").append(profile.scoreWeights().high())
                .append(", \"medium\": ").append(profile.scoreWeights().medium())
                .append(", \"low\": ").append(profile.scoreWeights().low()).append("},\n")
                .append("  \"metrics\": {\n")
                .append("    \"files\": ").append(profile.fileCount()).append(",\n")
                .append("    \"sources\": ").append(profile.sourceFileCount()).append(",\n")
                .append("    \"tests\": ").append(profile.testFileCount()).append(",\n")
                .append("    \"todos\": ").append(profile.todoCount()).append("\n")
                .append("  },\n")
                .append("  \"capabilities\": {\n")
                .append("    \"readme\": ").append(profile.hasReadme()).append(",\n")
                .append("    \"buildFile\": ").append(profile.hasBuildFile()).append(",\n")
                .append("    \"gitIgnore\": ").append(profile.hasGitIgnore()).append("\n")
                .append("  },\n")
                .append("  \"findings\": [");
        for (int i = 0; i < profile.findings().size(); i++) {
            Finding f = profile.findings().get(i);
            if (i > 0) out.append(',');
            out.append("\n    {")
                    .append("\"ruleId\": ").append(quote(f.ruleId())).append(", ")
                    .append("\"severity\": ").append(quote(f.severity().name())).append(", ")
                    .append("\"category\": ").append(quote(f.category())).append(", ")
                    .append("\"message\": ").append(quote(f.message())).append(", ")
                    .append("\"evidence\": ").append(quote(f.evidence())).append(", ")
                    .append("\"action\": ").append(quote(f.action())).append('}');
        }
        if (!profile.findings().isEmpty()) out.append('\n').append("  ");
        return out.append("]\n}\n").toString();
    }

    static String quote(String value) {
        var out = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        return out.append('"').toString();
    }
}
