package demo.dto;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;

// Validation Annotation 어노테이션 정의 (JavaParser 탐색용)
@Retention(RetentionPolicy.RUNTIME) @interface NotNull {}
@Retention(RetentionPolicy.RUNTIME) @interface Min { long value(); }
@Retention(RetentionPolicy.RUNTIME) @interface Size { int min(); int max(); }

public class SampleOrderRequest {

    // [패턴 3] VALIDATION_ANNOTATION → FIELD
    @NotNull
    @Size(min = 2, max = 100)
    private String orderTitle;

    // [패턴 3] VALIDATION_ANNOTATION → FIELD
    @Min(1)
    private int quantity;

    private List<Item> items;

    public String getOrderTitle() { return orderTitle; }
    public void setOrderTitle(String orderTitle) { this.orderTitle = orderTitle; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public List<Item> getItems() { return items; }
    public void setItems(List<Item> items) { this.items = items; }

    public static class Item {
        @NotNull
        private String itemName;

        @Min(1000)
        private long amount;

        public String getItemName() { return itemName; }
        public void setItemName(String itemName) { this.itemName = itemName; }

        public long getAmount() { return amount; }
        public void setAmount(long amount) { this.amount = amount; }
    }
}
