package local.agent.analysis;

public record ScoreWeights(int high, int medium, int low) {
    public static final ScoreWeights DEFAULT = new ScoreWeights(25, 12, 5);

    public ScoreWeights {
        validate("score.high", high);
        validate("score.medium", medium);
        validate("score.low", low);
        if (high < medium || medium < low)
            throw new IllegalArgumentException("评分权重必须满足 high >= medium >= low");
    }

    public int deduction(Severity severity) {
        return switch (severity) {
            case HIGH -> high;
            case MEDIUM -> medium;
            case LOW -> low;
            case INFO -> 0;
        };
    }

    private static void validate(String name, int value) {
        if (value < 0 || value > 100) throw new IllegalArgumentException(name + " 必须在 0 到 100 之间");
    }
}
