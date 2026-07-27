package demo.registry;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/registry/records")
class RegistryController {
    private final RegistryService registry;
    RegistryController(RegistryService registry) { this.registry = registry; }
    @GetMapping("/{reference}") Object obtain(String reference) { return registry.obtainRecord(reference); }
    @GetMapping Object enumerate() { return registry.records(); }
    @PostMapping Object enroll(String title) { return registry.enroll(title); }
    @PatchMapping("/{reference}") Object amend(String reference, String title) { return registry.amend(reference, title); }
    @DeleteMapping("/{reference}") void expunge(String reference) { registry.expunge(reference); }
    @GetMapping("/lookup") Object lookup(String phrase) { return registry.lookup(phrase); }
}
