package evaluation.beta;

import java.util.Optional;

final class RegistryLookupService {
    String obtainRecord(Optional<String> source) {
        return source.orElseThrow(IllegalStateException::new);
    }
}
