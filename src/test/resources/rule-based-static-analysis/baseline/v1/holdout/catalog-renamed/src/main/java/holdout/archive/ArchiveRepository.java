package holdout.archive;
import java.util.*;
interface ArchiveRepository {
    Optional<Object> resolve(String token); List<Object> contents(); Object store(String caption);
    Object rename(String token, String caption); void remove(String token); List<Object> filter(String text);
}
