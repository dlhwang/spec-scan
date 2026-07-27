package demo.controller;

import demo.dto.SampleOrderRequest;
import demo.service.SampleUserService;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
import java.util.stream.Collectors;

@Retention(RetentionPolicy.RUNTIME) @interface RequestParam {}
@Retention(RetentionPolicy.RUNTIME) @interface RequestBody {}
@Retention(RetentionPolicy.RUNTIME) @interface NotNull {}
@Retention(RetentionPolicy.RUNTIME) @interface Min { long value(); }

public class SamplePatternController {

    private SampleUserService userService;

    public List<String> processOrder(
            // [패턴 3] VALIDATION_ANNOTATION → PARAMETER
            @RequestParam @NotNull @Min(1) Long userId,
            @RequestBody SampleOrderRequest orderRequest
    ) {
        // [패턴 1] BINARY_EXPR → IF → THROW
        if (orderRequest.getQuantity() <= 0) {
            throw new IllegalArgumentException("주문 수량은 1개 이상이어야 합니다.");
        }

        // [패턴 2] BOOLEAN_CALL → IF → THROW
        if (!userService.isUserActive(userId)) {
            throw new IllegalStateException("비활성화된 사용자입니다.");
        }

        // [패턴 4] LAMBDA → STREAM_FILTER
        return orderRequest.getItems().stream()
                .filter(item -> item.getAmount() >= 1000)
                .map(SampleOrderRequest.Item::getItemName)
                .collect(Collectors.toList());
    }
}
