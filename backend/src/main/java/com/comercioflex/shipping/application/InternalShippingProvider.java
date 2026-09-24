package com.comercioflex.shipping.application;

import com.comercioflex.shipping.domain.ShippingModels.*;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class InternalShippingProvider implements ShippingProvider {
  public static String normalize(String value) {
    return value == null
        ? ""
        : Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .replaceAll("\\s+", " ")
            .toUpperCase(Locale.ROOT);
  }

  @Override
  public List<Quote> quote(
      Settings settings,
      BigDecimal listSubtotal,
      BigDecimal discount,
      String city,
      String postalCode) {
    List<Quote> result = new ArrayList<>();
    for (Method m : settings.methods()) {
      if (!m.active()) continue;
      BigDecimal price =
          switch (m.type()) {
            case PICKUP, FIXED_RATE -> m.price();
            case LOCATION_RATE, POSTAL_CODE_RATE ->
                m.rules().stream()
                    .filter(
                        r ->
                            normalize(r.destination())
                                .equals(
                                    normalize(
                                        m.type() == MethodType.LOCATION_RATE ? city : postalCode)))
                    .map(Rule::price)
                    .findFirst()
                    .orElse(null);
          };
      if (price == null) continue;
      boolean free =
          m.type() != MethodType.PICKUP
              && settings.freeShippingThreshold() != null
              && listSubtotal.compareTo(settings.freeShippingThreshold()) >= 0;
      if (free) price = BigDecimal.ZERO.setScale(2);
      BigDecimal net = listSubtotal.subtract(discount);
      if (net.add(price).compareTo(new BigDecimal("9999999999999.99")) > 0)
        throw new ShippingException("El total supera el máximo permitido.");
      result.add(
          new Quote(
              m.id(),
              m.name(),
              m.description(),
              m.type(),
              price,
              free,
              listSubtotal,
              discount,
              net,
              net.add(price),
              m.pickupAddress(),
              m.instructions()));
    }
    return List.copyOf(result);
  }
}
