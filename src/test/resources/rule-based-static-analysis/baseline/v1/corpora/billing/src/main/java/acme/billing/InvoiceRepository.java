package acme.billing;
import java.util.*;
interface InvoiceRepository {
    Optional<Object> byNumber(String number); List<Object> all(); Object create(long amount);
    Object change(String number, long amount); void remove(String number); List<Object> olderThan(int days);
}
