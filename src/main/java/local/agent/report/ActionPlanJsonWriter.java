package local.agent.report;

import local.agent.analysis.ProjectProfile;
import local.agent.planning.ActionPlanner;

public final class ActionPlanJsonWriter {
    public String render(ProjectProfile profile) {
        var actions = new ActionPlanner().plan(profile);
        int totalGain = actions.stream().mapToInt(action -> action.potentialScoreGain()).sum();
        int recoverable = Math.min(100 - profile.healthScore(), totalGain);
        var out = new StringBuilder("{\n")
                .append("  \"schemaVersion\": 1,\n")
                .append("  \"project\": ").append(JsonReportWriter.quote(profile.name())).append(",\n")
                .append("  \"root\": ").append(JsonReportWriter.quote(profile.root().toString())).append(",\n")
                .append("  \"currentHealthScore\": ").append(profile.healthScore()).append(",\n")
                .append("  \"actionCount\": ").append(actions.size()).append(",\n")
                .append("  \"potentialScoreRecovery\": ").append(recoverable).append(",\n")
                .append("  \"projectedHealthScore\": ").append(profile.healthScore() + recoverable).append(",\n")
                .append("  \"actions\": [");
        for (int i = 0; i < actions.size(); i++) {
            var action = actions.get(i);
            if (i > 0) out.append(',');
            out.append("\n    {")
                    .append("\"rank\": ").append(action.rank()).append(", ")
                    .append("\"ruleId\": ").append(JsonReportWriter.quote(action.ruleId())).append(", ")
                    .append("\"severity\": ").append(JsonReportWriter.quote(action.severity().name())).append(", ")
                    .append("\"category\": ").append(JsonReportWriter.quote(action.category())).append(", ")
                    .append("\"action\": ").append(JsonReportWriter.quote(action.action())).append(", ")
                    .append("\"rationale\": ").append(JsonReportWriter.quote(action.rationale())).append(", ")
                    .append("\"potentialScoreGain\": ").append(action.potentialScoreGain())
                    .append('}');
        }
        if (!actions.isEmpty()) out.append('\n').append("  ");
        return out.append("]\n}\n").toString();
    }
}
