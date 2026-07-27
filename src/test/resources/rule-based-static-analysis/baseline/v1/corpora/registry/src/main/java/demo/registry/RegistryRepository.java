package demo.registry;
import java.util.*;
interface RegistryRepository {
    Optional<Object> locate(String reference); List<Object> entries(); Object insert(String title);
    Object modify(String reference, String title); void erase(String reference); List<Object> lookup(String phrase);
}
