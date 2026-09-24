package com.comercioflex.shipping.application;

import com.comercioflex.order.application.InvalidGuestOrderException;
import com.comercioflex.order.application.OrderUnavailableException;
import com.comercioflex.order.domain.OrderPaymentMethod;
import com.comercioflex.shipping.domain.ShippingModels.Quote;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Advisory, read-only shipping quote pricing.
 *
 * <p>This path deliberately avoids SELECT ... FOR UPDATE. Order creation remains authoritative and
 * recalculates prices, availability and shipping while holding the existing transactional locks.
 */
@Service
public class ShippingQuoteService {
  private static final BigDecimal MAX_SUBTOTAL = new BigDecimal("9999999999999.99");

  private final JdbcTemplate jdbc;
  private final ShippingService shipping;

  public ShippingQuoteService(
      @Qualifier("tenantJdbcTemplate") JdbcTemplate jdbc, ShippingService shipping) {
    this.jdbc = jdbc;
    this.shipping = shipping;
  }

  public record Item(UUID variantId, BigDecimal quantity) {}

  private record VariantPrice(
      BigDecimal unitPrice,
      BigDecimal physicalQuantity,
      BigDecimal reservedQuantity,
      boolean sellable) {}

  private record PaymentPricing(boolean bankTransferEnabled, BigDecimal discountPercentage) {}

  public List<Quote> quote(
      List<Item> requestedItems, OrderPaymentMethod paymentMethod, String city, String postalCode) {
    if (paymentMethod == null
        || requestedItems == null
        || requestedItems.isEmpty()
        || requestedItems.size() > 50) {
      throw new InvalidGuestOrderException("Indicá productos y medio de pago.");
    }

    Set<UUID> variants = new HashSet<>();
    BigDecimal listSubtotal = BigDecimal.ZERO.setScale(2);

    for (Item item : requestedItems) {
      validateItem(item, variants);
      VariantPrice variant = findVariant(item.variantId());
      if (!variant.sellable()
          || variant
                  .physicalQuantity()
                  .subtract(variant.reservedQuantity())
                  .compareTo(item.quantity())
              < 0) {
        throw new OrderUnavailableException();
      }

      BigDecimal lineTotal =
          variant.unitPrice().multiply(item.quantity()).setScale(2, RoundingMode.HALF_UP);
      listSubtotal = listSubtotal.add(lineTotal);
      if (listSubtotal.compareTo(MAX_SUBTOTAL) > 0) {
        throw new InvalidGuestOrderException("El total del pedido supera el máximo permitido.");
      }
    }

    BigDecimal discountAmount = discount(listSubtotal, paymentMethod);
    return shipping.quotes(listSubtotal, discountAmount, city, postalCode);
  }

  private VariantPrice findVariant(UUID variantId) {
    return jdbc
        .query(
            """
            SELECT
              variant.price,
              COALESCE(balance.quantity, 0.000) physical_quantity,
              COALESCE((
                SELECT SUM(reservation.quantity)
                FROM inventory_reservations reservation
                WHERE reservation.variant_id = variant.id
                  AND reservation.status = 'ACTIVE'
                  AND reservation.expires_at > UTC_TIMESTAMP(6)
              ), 0.000) reserved_quantity,
              (
                product.status = 'PUBLISHED'
                AND category.status = 'ACTIVE'
                AND variant.status = 'ACTIVE'
              ) sellable
            FROM product_variants variant
            JOIN products product ON product.id = variant.product_id
            JOIN categories category ON category.id = product.category_id
            LEFT JOIN inventory_balances balance ON balance.variant_id = variant.id
            WHERE variant.public_id = UUID_TO_BIN(?)
            """,
            (rs, row) ->
                new VariantPrice(
                    rs.getBigDecimal("price"),
                    rs.getBigDecimal("physical_quantity"),
                    rs.getBigDecimal("reserved_quantity"),
                    rs.getBoolean("sellable")),
            variantId.toString())
        .stream()
        .findFirst()
        .orElseThrow(OrderUnavailableException::new);
  }

  private BigDecimal discount(BigDecimal listSubtotal, OrderPaymentMethod paymentMethod) {
    if (paymentMethod != OrderPaymentMethod.BANK_TRANSFER) {
      return BigDecimal.ZERO.setScale(2);
    }

    PaymentPricing pricing =
        jdbc.query(
                """
                SELECT bank_transfer_enabled, bank_transfer_discount_percentage
                FROM store_settings
                LIMIT 1
                """,
                (rs, row) ->
                    new PaymentPricing(
                        rs.getBoolean("bank_transfer_enabled"),
                        rs.getBigDecimal("bank_transfer_discount_percentage")))
            .stream()
            .findFirst()
            .orElse(new PaymentPricing(false, BigDecimal.ZERO));

    if (!pricing.bankTransferEnabled()) {
      throw new InvalidGuestOrderException(
          "La transferencia bancaria no está habilitada para esta tienda.");
    }

    BigDecimal percentage =
        pricing.discountPercentage() == null
            ? BigDecimal.ZERO.setScale(2)
            : pricing.discountPercentage().setScale(2, RoundingMode.HALF_UP);
    return listSubtotal
        .multiply(percentage)
        .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
  }

  private void validateItem(Item item, Set<UUID> variants) {
    if (item == null || item.variantId() == null || item.quantity() == null) {
      throw new InvalidGuestOrderException("Cada producto debe indicar una variante válida.");
    }
    if (!variants.add(item.variantId())) {
      throw new InvalidGuestOrderException("No se puede repetir una variante en el pedido.");
    }
    BigDecimal quantity;
    try {
      quantity = item.quantity().setScale(3, RoundingMode.UNNECESSARY);
    } catch (ArithmeticException exception) {
      throw new InvalidGuestOrderException("La cantidad debe ser un número entero.");
    }
    if (quantity.stripTrailingZeros().scale() > 0
        || quantity.compareTo(BigDecimal.ONE) < 0
        || quantity.compareTo(new BigDecimal("99")) > 0) {
      throw new InvalidGuestOrderException("Cada cantidad debe ser un entero entre 1 y 99.");
    }
  }
}
