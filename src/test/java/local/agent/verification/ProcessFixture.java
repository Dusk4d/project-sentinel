package local.agent.verification;

public final class ProcessFixture {
    public static void main(String[] args) throws Exception {
        switch (args[0]) {
            case "pass" -> System.out.println("fixture passed");
            case "fail" -> { System.err.println("fixture failed"); System.exit(7); }
            case "sleep" -> Thread.sleep(10_000);
            case "large" -> {
                for (int i = 0; i < 80_000; i++) System.out.print('x');
            }
            default -> throw new IllegalArgumentException(args[0]);
        }
    }
}
