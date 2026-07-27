package logistics.shipping;
import java.util.*;
class ParcelService {
    private final ParcelRepository depot;
    ParcelService(ParcelRepository depot) { this.depot = depot; }
    Object requireParcel(String tracking) { return depot.findTracking(tracking).orElseThrow(IllegalArgumentException::new); }
    List<Object> queue() { return depot.pending(); }
    Object dispatch(double weight) { if (weight <= 0 || weight > 100) throw new IllegalArgumentException(); return depot.send(weight); }
    Object redirect(String tracking, String address) { if (address == null || address.isBlank()) throw new IllegalArgumentException(); return depot.redirect(tracking, address); }
    void cancel(String tracking) { Objects.requireNonNull(tracking); depot.cancel(tracking); }
    List<Object> byRegion(String zone) { return zone == null ? List.of() : depot.region(zone); }
}
