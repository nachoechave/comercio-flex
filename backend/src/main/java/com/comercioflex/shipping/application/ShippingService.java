package com.comercioflex.shipping.application;

import com.comercioflex.notification.application.CustomerNotificationPublisher;
import com.comercioflex.shipping.domain.ShippingModels.*;
import java.math.BigDecimal;
import java.net.URI;
import java.util.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@org.springframework.validation.annotation.Validated
public class ShippingService {
  private final ShippingRepository repository;
  private final ShippingProvider provider;
  private final TransactionTemplate tx;
  private final CustomerNotificationPublisher notifications;

  public ShippingService(
      ShippingRepository repository,
      ShippingProvider provider,
      @Qualifier("tenantTransactionTemplate") TransactionTemplate tx,
      CustomerNotificationPublisher notifications) {
    this.repository = repository;
    this.provider = provider;
    this.tx = tx;
    this.notifications = notifications;
  }

  public Settings settings() {
    return tx.execute(s -> repository.settings(false));
  }

  public Settings save(
      @jakarta.validation.Valid @jakarta.validation.constraints.NotNull Settings settings) {
    return tx.execute(
        s -> {
          Settings previous = repository.settings(true);
          if (previous.version() != settings.version())
            throw new ShippingException("La configuración cambió. Recargá antes de guardar.");
          Set<UUID> ids = new HashSet<>();
          List<Method> methods = new ArrayList<>();
          for (Method m : settings.methods()) {
            UUID id = m.id() == null ? UUID.randomUUID() : m.id();
            if (!ids.add(id)) throw new ShippingException("Método repetido.");
            if (m.active()
                && m.type() == MethodType.PICKUP
                && (m.pickupAddress() == null || m.pickupAddress().isBlank()))
              throw new ShippingException("Ingresá la dirección de retiro.");
            Set<String> destinations = new HashSet<>();
            List<Rule> rules = new ArrayList<>();
            for (Rule r : m.rules()) {
              String destination = InternalShippingProvider.normalize(r.destination());
              if (!destinations.add(destination)) throw new ShippingException("Destino repetido.");
              rules.add(new Rule(destination, r.price()));
            }
            methods.add(
                new Method(
                    id,
                    m.name().trim(),
                    m.description(),
                    m.type(),
                    m.price(),
                    m.active(),
                    m.pickupAddress(),
                    m.instructions(),
                    rules));
          }
          repository.save(
              new Settings(settings.freeShippingThreshold(), settings.version(), methods));
          return repository.settings(false);
        });
  }

  /** Public/advisory quote. It must not serialize checkout traffic with configuration locks. */
  public List<Quote> quotes(
      BigDecimal subtotal, BigDecimal discount, String city, String postalCode) {
    return provider.quote(repository.settings(false), subtotal, discount, city, postalCode);
  }

  public Snapshot select(
      @jakarta.validation.Valid Selection selection, BigDecimal subtotal, BigDecimal discount) {
    Address address = selection == null ? null : selection.address();
    // Order creation is authoritative: keep the settings row locked in the existing tenant
    // transaction so a concurrent admin edit cannot change the tariff between selection and attach.
    List<Quote> quotes =
        provider.quote(
            repository.settings(true),
            subtotal,
            discount,
            address == null ? null : address.city(),
            address == null ? null : address.postalCode());
    // Legacy callers may omit selection only when a free pickup remains enabled.
    Quote q =
        quotes.stream()
            .filter(
                v ->
                    selection == null
                        ? v.type() == MethodType.PICKUP && v.shippingAmount().signum() == 0
                        : v.methodId().equals(selection.methodId()))
            .findFirst()
            .orElseThrow(() -> new ShippingException("Elegí un método de entrega disponible."));
    if (q.type() != MethodType.PICKUP && address == null)
      throw new ShippingException("La dirección de entrega es obligatoria.");
    if (selection != null
        && (selection.expectedTotal() == null
            || selection.expectedTotal().compareTo(q.total()) != 0))
      throw new ShippingException(
          "El importe cambió. Volvé a consultar las opciones de entrega y confirmá el nuevo"
              + " total.");
    return new Snapshot(
        q.methodId(),
        q.name(),
        q.type(),
        q.shippingAmount(),
        q.pickupAddress(),
        q.instructions(),
        q.type() == MethodType.PICKUP ? null : address);
  }

  public void attach(long orderId, Snapshot snapshot) {
    repository.attach(orderId, snapshot);
  }

  public Shipment shipment(UUID orderId) {
    return tx.execute(
        s -> {
          if (repository.shipment(orderId, false) == null) return null;
          String status = repository.lockOrderStatus(orderId);
          if (Set.of("EXPIRED", "REJECTED", "CANCELLED").contains(status))
            repository.cancelPending(orderId);
          return repository.shipment(orderId, false);
        });
  }

  public Shipment update(
      UUID orderId,
      @jakarta.validation.Valid @jakarta.validation.constraints.NotNull UpdateShipment update) {
    if (update.trackingUrl() != null && !update.trackingUrl().isBlank()) {
      try {
        URI url = URI.create(update.trackingUrl());
        if (!("https".equalsIgnoreCase(url.getScheme()) || "http".equalsIgnoreCase(url.getScheme()))
            || url.getHost() == null
            || url.getUserInfo() != null) throw new IllegalArgumentException();
      } catch (IllegalArgumentException e) {
        throw new ShippingException("Usá una URL de seguimiento HTTP o HTTPS válida.");
      }
    }
    return tx.execute(
        s -> {
          String orderStatus = repository.lockOrderStatus(orderId);
          Shipment current = repository.shipment(orderId, true);
          if (current == null) throw new ShippingException("El pedido no tiene un envío.");
          if (!current.status().allows(update.status()))
            throw new ShippingException("Transición de envío inválida.");
          if (current.version() != update.version())
            throw new ShippingException("El envío cambió. Recargá el pedido.");
          if (update.status() != Status.CANCELLED
              && update.status() != Status.PENDING
              && !Set.of("CONFIRMED", "READY_FOR_PICKUP", "COMPLETED").contains(orderStatus))
            throw new ShippingException(
                "El pedido debe estar confirmado antes de preparar o despachar.");
          repository.update(orderId, update);
          Shipment result = repository.shipment(orderId, false);
          if (current.shippedAt() == null && result.status() == Status.SHIPPED)
            notifications.orderShipped(result);
          return result;
        });
  }
}
