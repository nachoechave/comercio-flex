package com.comercioflex.shipping.api;

import com.comercioflex.order.api.CreateGuestOrderItemRequest;
import com.comercioflex.order.domain.OrderPaymentMethod;
import com.comercioflex.shipping.application.ShippingQuoteService;
import com.comercioflex.shipping.domain.ShippingModels.Quote;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stores/{storeSlug}/shipping")
public class ShippingQuoteController {
  private final ShippingQuoteService quotes;

  public ShippingQuoteController(ShippingQuoteService quotes) {
    this.quotes = quotes;
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
            quotes.quote(
                body.items().stream()
                    .map(
                        item ->
                            new ShippingQuoteService.Item(
                                item.variantId(), item.decimalQuantity()))
                    .toList(),
                body.paymentMethod(),
                body.city(),
                body.postalCode()));
  }
}
