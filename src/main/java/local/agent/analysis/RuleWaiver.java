package local.agent.analysis;

import java.time.LocalDate;

public record RuleWaiver(String ruleId, LocalDate expiresOn, String owner, String reason) {
    public RuleWaiver {
        if (ruleId == null || !ruleId.matches("[a-z][a-z0-9.-]*")) throw new IllegalArgumentException("无效豁免规则 ID: " + ruleId);
        if (expiresOn == null) throw new IllegalArgumentException("豁免必须提供到期日");
        if (owner == null || owner.isBlank()) throw new IllegalArgumentException("豁免必须提供负责人");
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("豁免必须提供原因");
    }

    public boolean activeOn(LocalDate date) { return !expiresOn.isBefore(date); }
}
