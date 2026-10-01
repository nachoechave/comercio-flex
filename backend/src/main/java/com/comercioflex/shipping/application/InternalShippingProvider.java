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
      String province,
      String postalCode) {
    List<Quote> result = new ArrayList<>();
    for (Method m : settings.methods()) {
      if (!m.active() || m.type() == MethodType.CARRIER) continue;
      BigDecimal price =
          switch (m.type()) {
            case PICKUP, FIXED_RATE -> m.price();
            case LOCATION_RATE -> locationPrice(m.rules(), city, province);
            case POSTAL_CODE_RATE ->
                m.rules().stream()
                    .filter(r -> normalize(r.destination()).equals(normalize(postalCode)))
                    .map(Rule::price)
                    .findFirst()
                    .orElse(null);
            case CARRIER -> null;
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

  private BigDecimal locationPrice(List<Rule> rules, String city, String province) {
    String normalizedCity = normalize(city);
    String normalizedProvince = normalize(province);

    BigDecimal locality =
        rules.stream()
            .filter(r -> !normalize(r.destination()).startsWith("PROVINCIA:"))
            .filter(r -> !normalize(r.destination()).equals("RESTO_ARGENTINA"))
            .filter(r -> normalize(r.destination()).equals(normalizedCity))
            .map(Rule::price)
            .findFirst()
            .orElse(null);
    if (locality != null) return locality;

    BigDecimal provincePrice =
        rules.stream()
            .filter(r -> normalize(r.destination()).startsWith("PROVINCIA:"))
            .filter(
                r ->
                    normalize(r.destination().substring(r.destination().indexOf(':') + 1))
                        .equals(normalizedProvince))
            .map(Rule::price)
            .findFirst()
            .orElse(null);
    if (provincePrice != null) return provincePrice;

    return rules.stream()
        .filter(r -> normalize(r.destination()).equals("RESTO_ARGENTINA"))
        .map(Rule::price)
        .findFirst()
        .orElse(null);
  }
}
