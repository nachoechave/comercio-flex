package com.comercioflex.shipping;

import static org.assertj.core.api.Assertions.*;

import com.comercioflex.shipping.application.InternalShippingProvider;
import com.comercioflex.shipping.domain.ShippingModels.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ShippingCoreTests {
  @Test
  void relativeTrackingUrlIsRejectedAsControlledShippingError() {
    var service = new com.comercioflex.shipping.application.ShippingService(null, null, null, null);
    assertThatThrownBy(
            () ->
                service.update(
                    UUID.randomUUID(),
                    new UpdateShipment(Status.PENDING, null, null, "/tracking/123", null, 0)))
        .isInstanceOf(com.comercioflex.shipping.application.ShippingException.class);
  }

  @Test
  void nullMethodsAndRulesAreInvalid() {
    try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
      var validator = factory.getValidator();
      assertThat(validator.validate(new Settings(null, 0, java.util.Arrays.asList((Method) null))))
          .isNotEmpty();
      assertThat(
              validator.validate(
                  new Settings(
                      null,
                      0,
                      List.of(
                          method(
                              MethodType.FIXED_RATE, "0", java.util.Arrays.asList((Rule) null))))))
          .isNotEmpty();
    }
  }

  private final InternalShippingProvider provider = new InternalShippingProvider();

  private Method method(MethodType type, String price, List<Rule> rules) {
    return new Method(
        UUID.randomUUID(),
        "Entrega",
        "Descripción",
        type,
        new BigDecimal(price),
        true,
        "Calle 123",
        "De 9 a 18",
        rules);
  }

  @ParameterizedTest
  @CsvSource({"49999.99,3000.00", "50000.00,0.00", "50000.01,0.00"})
  void thresholdIsInclusiveAndEvaluatedBeforeTransferDiscount(String subtotal, String cost) {
    var settings =
        new Settings(
            new BigDecimal("50000"), 0, List.of(method(MethodType.FIXED_RATE, "3000", List.of())));
    var quote =
        provider
            .quote(settings, new BigDecimal(subtotal), new BigDecimal("1000"), null, null)
            .getFirst();
    assertThat(quote.shippingAmount()).isEqualByComparingTo(cost);
    assertThat(quote.total())
        .isEqualByComparingTo(
            new BigDecimal(subtotal).subtract(new BigDecimal("1000")).add(new BigDecimal(cost)));
  }

  @Test
  void pickupZeroAndFixedRateRemainIndependent() {
    var quotes =
        provider.quote(
            new Settings(
                null,
                0,
                List.of(
                    method(MethodType.PICKUP, "0", List.of()),
                    method(MethodType.FIXED_RATE, "4000", List.of()))),
            new BigDecimal("45000"),
            BigDecimal.ZERO,
            null,
            null);
    assertThat(quotes).hasSize(2);
    assertThat(quotes.getFirst().total()).isEqualByComparingTo("45000");
    assertThat(quotes.getLast().total()).isEqualByComparingTo("49000");
  }

  @Test
  void localityIsNormalizedAndUnknownLocalityIsUnavailable() {
    var settings =
        new Settings(
            null,
            0,
            List.of(
                method(
                    MethodType.LOCATION_RATE,
                    "0",
                    List.of(new Rule("  LA   PLÁTA ", new BigDecimal("4000"))))));
    assertThat(provider.quote(settings, BigDecimal.TEN, BigDecimal.ZERO, "la plata", null))
        .hasSize(1);
    assertThat(provider.quote(settings, BigDecimal.TEN, BigDecimal.ZERO, "Berisso", null))
        .isEmpty();
  }

  @Test
  void postalCodeMatchesExactlyAndInactiveMethodsAreUnavailable() {
    var m =
        method(MethodType.POSTAL_CODE_RATE, "0", List.of(new Rule("1925", new BigDecimal("3000"))));
    var settings = new Settings(null, 0, List.of(m));
    assertThat(provider.quote(settings, BigDecimal.TEN, BigDecimal.ZERO, null, "1925")).hasSize(1);
    assertThat(provider.quote(settings, BigDecimal.TEN, BigDecimal.ZERO, null, "192")).isEmpty();
    var inactive =
        new Method(m.id(), m.name(), null, m.type(), m.price(), false, null, null, m.rules());
    assertThat(
            provider.quote(
                new Settings(null, 0, List.of(inactive)),
                BigDecimal.TEN,
                BigDecimal.ZERO,
                null,
                "1925"))
        .isEmpty();
  }

  @Test
  void stateMachineRejectsBackwardsAndTerminalTransitions() {
    assertThat(Status.PENDING.allows(Status.PREPARING)).isTrue();
    assertThat(Status.PREPARING.allows(Status.SHIPPED)).isTrue();
    assertThat(Status.SHIPPED.allows(Status.DELIVERED)).isTrue();
    assertThat(Status.SHIPPED.allows(Status.SHIPPED)).isTrue();
    assertThat(Status.PENDING.allows(Status.DELIVERED)).isFalse();
    assertThat(Status.DELIVERED.allows(Status.PREPARING)).isFalse();
    assertThat(Status.CANCELLED.allows(Status.SHIPPED)).isFalse();
    assertThat(Status.SHIPPED.allows(Status.CANCELLED)).isFalse();
  }
}
