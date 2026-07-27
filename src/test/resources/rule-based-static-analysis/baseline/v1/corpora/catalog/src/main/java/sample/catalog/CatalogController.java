package sample.catalog;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/catalog")
class CatalogController {
    private final CatalogService service;
    CatalogController(CatalogService service) { this.service = service; }
    @GetMapping("/{sku}") Object detail(String sku) { return service.requireProduct(sku); }
    @GetMapping Object browse() { return service.allProducts(); }
    @PostMapping Object register(String label) { return service.addProduct(label); }
    @PutMapping("/{sku}") Object revise(String sku, String label) { return service.renameProduct(sku, label); }
    @DeleteMapping("/{sku}") void retire(String sku) { service.removeProduct(sku); }
    @GetMapping("/search") Object search(String query) { return service.searchProducts(query); }
}
