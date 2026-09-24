package com.comercioflex.shipping.application;

import com.comercioflex.shipping.domain.ShippingModels.*;
import java.math.BigDecimal;
import java.util.List;

/** Extension port. External providers and remote shipment operations belong to phase 2. */
public interface ShippingProvider {
  List<Quote> quote(
      Settings settings,
      BigDecimal listSubtotal,
      BigDecimal discountAmount,
      String city,
      String postalCode);
}
