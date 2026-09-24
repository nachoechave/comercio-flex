package com.comercioflex.shipping.api;

import com.comercioflex.order.api.CreateGuestOrderItemRequest;
import com.comercioflex.order.application.GuestOrderService;
import com.comercioflex.order.application.OrderItemCommand;
import com.comercioflex.order.domain.OrderPaymentMethod;
import com.comercioflex.shipping.domain.ShippingModels.Quote;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/stores/{storeSlug}/shipping")
public class ShippingQuoteController {
  private final GuestOrderService orders;

  public ShippingQuoteController(GuestOrderService orders) {
    this.orders = orders;
  }

  public record Request(
      @NotEmpty @Size(max = 50) List<@NotNull @Valid CreateGuestOrderItemRequest> items,
      @NotNull OrderPaymentMethod paymentMethod,
      @Size(max = 160) String city,
      @Size(max = 20) String postalCode) {}

  @PostMapping("/quote")
  ResponseEntity<List<Quote>> quote(
      @Valid @RequestBody Request body, jakarta.servlet.http.HttpServletRequest request) {
    ShippingAccess.requireEcommerce(request);
    return ResponseEntity.ok()
        .cacheControl(org.springframework.http.CacheControl.noStore())
        .body(
            orders.quote(
                body.items().stream()
                    .map(i -> new OrderItemCommand(i.variantId(), i.decimalQuantity()))
                    .toList(),
                body.paymentMethod(),
                body.city(),
                body.postalCode()));
  }
}
