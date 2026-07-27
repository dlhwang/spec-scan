package acme.billing;
import java.util.*;
class InvoiceService {
    private final InvoiceRepository ledger;
    InvoiceService(InvoiceRepository ledger) { this.ledger = ledger; }
    Object requireInvoice(String number) { return ledger.byNumber(number).orElseThrow(IllegalStateException::new); }
    List<Object> history() { return ledger.all(); }
    Object issue(long amount) { if (amount <= 0) throw new IllegalArgumentException(); return ledger.create(amount); }
    Object adjust(String number, long amount) { if (amount < 0) throw new IllegalArgumentException(); return ledger.change(number, amount); }
    void voidInvoice(String number) { Objects.requireNonNull(number); ledger.remove(number); }
    List<Object> overdue(int days) { if (days < 1) throw new IllegalArgumentException(); return ledger.olderThan(days); }
}
