package logistics.shipping;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/parcels")
class ParcelController {
    private final ParcelService delivery;
    ParcelController(ParcelService delivery) { this.delivery = delivery; }
    @GetMapping("/{tracking}") Object trace(String tracking) { return delivery.requireParcel(tracking); }
    @GetMapping Object queue() { return delivery.queue(); }
    @PostMapping Object dispatch(double weight) { return delivery.dispatch(weight); }
    @PatchMapping("/{tracking}") Object redirect(String tracking, String address) { return delivery.redirect(tracking, address); }
    @DeleteMapping("/{tracking}") void cancel(String tracking) { delivery.cancel(tracking); }
    @GetMapping("/region") Object region(String zone) { return delivery.byRegion(zone); }
}
