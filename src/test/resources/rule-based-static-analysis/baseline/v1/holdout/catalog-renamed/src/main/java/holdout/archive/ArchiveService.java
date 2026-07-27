package holdout.archive;
import java.util.*;
class ArchiveService {
    private final ArchiveRepository vault;
    ArchiveService(ArchiveRepository vault) { this.vault = vault; }
    Object requireAsset(String token) { Optional<Object> found = vault.resolve(token); if (found.isEmpty()) throw new IllegalArgumentException(); return found.get(); }
    List<Object> index() { return vault.contents(); }
    Object deposit(String caption) { if (caption == null || caption.trim().isEmpty()) throw new IllegalArgumentException(); return vault.store(caption); }
    Object retitle(String token, String caption) { if (caption.length() < 3) throw new IllegalArgumentException(); return vault.rename(token, caption); }
    void withdraw(String token) { if (token == null) throw new IllegalArgumentException(); vault.remove(token); }
    List<Object> filter(String text) { return text == null ? List.of() : vault.filter(text); }
}
