package acme.billing;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/billing/invoices")
class InvoiceController {
    private final InvoiceService billing;
    InvoiceController(InvoiceService billing) { this.billing = billing; }
    @GetMapping("/{number}") Object read(String number) { return billing.requireInvoice(number); }
    @GetMapping Object history() { return billing.history(); }
    @PostMapping Object issue(long amount) { return billing.issue(amount); }
    @PutMapping("/{number}") Object adjust(String number, long amount) { return billing.adjust(number, amount); }
    @DeleteMapping("/{number}") void voidInvoice(String number) { billing.voidInvoice(number); }
    @GetMapping("/overdue") Object overdue(int days) { return billing.overdue(days); }
}
