package com.comercioflex.shipping.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.comercioflex.shipping.domain.ShippingModels.Method;
import com.comercioflex.shipping.domain.ShippingModels.MethodType;
import com.comercioflex.shipping.domain.ShippingModels.Quote;
import com.comercioflex.shipping.domain.ShippingModels.Selection;
import com.comercioflex.shipping.domain.ShippingModels.Settings;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ShippingServiceLockingTests {

  @Test
  void publicQuoteReadsSettingsWithoutLockingThem() {
    ShippingRepository repository = mock(ShippingRepository.class);
    ShippingProvider provider = mock(ShippingProvider.class);
    Settings settings = new Settings(null, 0, List.of());
    when(repository.settings(false)).thenReturn(settings);
    when(provider.quote(
            eq(settings), any(BigDecimal.class), any(BigDecimal.class), isNull(), isNull()))
        .thenReturn(List.of());

    ShippingService service = new ShippingService(repository, provider, null, null);
    service.quotes(new BigDecimal("1000.00"), BigDecimal.ZERO, null, null);

    verify(repository).settings(false);
    verify(repository, never()).settings(true);
  }

  @Test
  void authoritativeOrderSelectionKeepsShippingSettingsLocked() {
    ShippingRepository repository = mock(ShippingRepository.class);
    ShippingProvider provider = mock(ShippingProvider.class);
    UUID methodId = UUID.randomUUID();
    Settings settings =
        new Settings(
            null,
            4,
            List.of(
                new Method(
                    methodId,
                    "Retiro",
                    null,
                    MethodType.PICKUP,
                    BigDecimal.ZERO,
                    true,
                    "Calle 123",
                    null,
                    List.of())));
    Quote quote =
        new Quote(
            methodId,
            "Retiro",
            null,
            MethodType.PICKUP,
            new BigDecimal("0.00"),
            false,
            new BigDecimal("1000.00"),
            new BigDecimal("0.00"),
            new BigDecimal("1000.00"),
            new BigDecimal("1000.00"),
            "Calle 123",
            null);
    when(repository.settings(true)).thenReturn(settings);
    when(provider.quote(
            eq(settings), any(BigDecimal.class), any(BigDecimal.class), isNull(), isNull()))
        .thenReturn(List.of(quote));

    ShippingService service = new ShippingService(repository, provider, null, null);
    var snapshot =
        service.select(
            new Selection(methodId, null, new BigDecimal("1000.00")),
            new BigDecimal("1000.00"),
            BigDecimal.ZERO);

    assertThat(snapshot.methodId()).isEqualTo(methodId);
    verify(repository).settings(true);
    verify(repository, never()).settings(false);
  }
}
