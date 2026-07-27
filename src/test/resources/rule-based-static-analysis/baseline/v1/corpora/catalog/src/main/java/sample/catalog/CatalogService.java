package sample.catalog;
import java.util.*;
class CatalogService {
    private final CatalogRepository repository;
    CatalogService(CatalogRepository repository) { this.repository = repository; }
    Object requireProduct(String sku) { return repository.findBySku(sku).orElseThrow(IllegalArgumentException::new); }
    List<Object> allProducts() { return repository.findAll(); }
    Object addProduct(String label) { if (label == null || label.isBlank()) throw new IllegalArgumentException(); return repository.save(label); }
    Object renameProduct(String sku, String label) { if (label.length() < 3) throw new IllegalArgumentException(); return repository.update(sku, label); }
    void removeProduct(String sku) { if (sku == null) throw new IllegalArgumentException(); repository.delete(sku); }
    List<Object> searchProducts(String query) { return query == null ? List.of() : repository.search(query); }
}
