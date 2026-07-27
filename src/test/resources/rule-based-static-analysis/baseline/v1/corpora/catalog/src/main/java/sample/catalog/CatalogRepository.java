package sample.catalog;
import java.util.*;
interface CatalogRepository {
    Optional<Object> findBySku(String sku); List<Object> findAll(); Object save(String label);
    Object update(String sku, String label); void delete(String sku); List<Object> search(String query);
}
