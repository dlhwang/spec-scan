package holdout.archive;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/archive/assets")
class ArchiveController {
    private final ArchiveService archive;
    ArchiveController(ArchiveService archive) { this.archive = archive; }
    @GetMapping("/{token}") Object fetch(String token) { return archive.requireAsset(token); }
    @GetMapping Object index() { return archive.index(); }
    @PostMapping Object deposit(String caption) { return archive.deposit(caption); }
    @PutMapping("/{token}") Object retitle(String token, String caption) { return archive.retitle(token, caption); }
    @DeleteMapping("/{token}") void withdraw(String token) { archive.withdraw(token); }
    @GetMapping("/filter") Object filter(String text) { return archive.filter(text); }
}
