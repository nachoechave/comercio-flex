package com.comercioflex.shipping.application;
import java.math.BigDecimal;
import java.util.List;
import com.comercioflex.shipping.domain.ShippingModels.*;
/** Extension port. External providers and remote shipment operations belong to phase 2. */
public interface ShippingProvider {
 List<Quote> quote(Settings settings, BigDecimal listSubtotal, BigDecimal discountAmount,
                   String city, String postalCode);
}

