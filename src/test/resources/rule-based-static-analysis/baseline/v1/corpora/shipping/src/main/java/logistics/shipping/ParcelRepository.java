package logistics.shipping;
import java.util.*;
interface ParcelRepository {
    Optional<Object> findTracking(String tracking); List<Object> pending(); Object send(double weight);
    Object redirect(String tracking, String address); void cancel(String tracking); List<Object> region(String zone);
}
