package evaluation.alpha;

import java.util.Optional;

final class CatalogLookupService {
    String requireEntry(Optional<String> candidate) {
        return candidate.orElseThrow(IllegalArgumentException::new);
    }
}
