package local.agent.verification;

import java.nio.file.Path;
import java.util.List;

@FunctionalInterface
public interface BuildCommandProvider {
    List<String> detect(Path project);
}
