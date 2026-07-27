package demo.registry;
import java.util.*;
class RegistryService {
    private final RegistryRepository store;
    RegistryService(RegistryRepository store) { this.store = store; }
    Object obtainRecord(String reference) { return store.locate(reference).orElseThrow(NoSuchElementException::new); }
    List<Object> records() { return store.entries(); }
    Object enroll(String title) { Objects.requireNonNull(title); return store.insert(title); }
    Object amend(String reference, String title) { if (title.isEmpty()) throw new IllegalStateException(); return store.modify(reference, title); }
    void expunge(String reference) { if (reference == null || reference.isBlank()) throw new IllegalArgumentException(); store.erase(reference); }
    List<Object> lookup(String phrase) { return phrase == null ? Collections.emptyList() : store.lookup(phrase); }
}
