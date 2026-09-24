package com.comercioflex.shipping.application;

import com.comercioflex.notification.application.CustomerNotificationPublisher;
import com.comercioflex.shipping.domain.ShippingModels.*;
import java.math.BigDecimal;
import java.net.URI;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@org.springframework.validation.annotation.Validated
public class ShippingService {
  private final ShippingRepository repository;
  private final ShippingProvider provider;
  private final CarrierShippingService carrier;
  private final TransactionTemplate tx;
  private final CustomerNotificationPublisher notifications;

  public record Availability(boolean pickupAvailable, boolean shippingAvailable) {}

  @Autowired
  public ShippingService(
      ShippingRepository repository,
      ShippingProvider provider,
      CarrierShippingService carrier,
      @Qualifier("tenantTransactionTemplate") TransactionTemplate tx,
      CustomerNotificationPublisher notifications) {
    this.repository = repository;
    this.provider = provider;
    this.carrier = carrier;
    this.tx = tx;
    this.notifications = notifications;
  }

  /** Compatibility constructor retained for focused unit tests and phase-one callers. */
  public ShippingService(
      ShippingRepository repository,
      ShippingProvider provider,
      TransactionTemplate tx,
      CustomerNotificationPublisher notifications) {
    this(repository, provider, null, tx, notifications);
  }

  public Settings settings() {
    return tx == null ? repository.settings(false) : tx.execute(s -> repository.settings(false));
  }

  public Availability availability() {
    Settings settings = settings();
    boolean pickupAvailable =
        settings.methods().stream().anyMatch(m -> m.active() && m.type() == MethodType.PICKUP);
    boolean shippingAvailable =
        settings.methods().stream().anyMatch(m -> m.active() && m.type() != MethodType.PICKUP);
    if (!shippingAvailable && carrier != null) {
      shippingAvailable = carrier.settings().enabled();
    }
    return new Availability(pickupAvailable, shippingAvailable);
  }

  public Settings save(
      @jakarta.validation.Valid @jakarta.validation.constraints.NotNull Settings settings) {
    return requireTx()
        .execute(
            s -> {
              Settings previous = repository.settings(true);
              if (previous.version() != settings.version())
                throw new ShippingException("La configuración cambió. Recargá antes de guardar.");
              Set<UUID> ids = new HashSet<>();
              List<Method> methods = new ArrayList<>();
              for (Method m : settings.methods()) {
                if (m.type() == MethodType.CARRIER) {
                  throw new ShippingException(
                      "Los transportistas externos se configuran en Integraciones de envío.");
                }
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

  /** Public/advisory internal quote. External carrier quotes are added by ShippingQuoteService. */
  public List<Quote> quotes(
      BigDecimal subtotal, BigDecimal discount, String city, String postalCode) {
    return provider.quote(repository.settings(false), subtotal, discount, city, postalCode);
  }

  public Snapshot select(
      @jakarta.validation.Valid Selection selection, BigDecimal subtotal, BigDecimal discount) {
    Address address = selection == null ? null : selection.address();
    if (selection != null && selection.quoteToken() != null) {
      if (carrier == null) throw new ShippingException("Transportista externo no disponible.");
      return carrier.select(selection, subtotal, discount, address);
    }

    List<Quote> quotes =
        provider.quote(
            repository.settings(true),
            subtotal,
            discount,
            address == null ? null : address.city(),
            address == null ? null : address.postalCode());
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
          "El importe cambió. Volvé a consultar las opciones de entrega y confirmá el nuevo total.");
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
    if (carrier != null) carrier.consume(snapshot);
  }

  public Shipment shipment(UUID orderId) {
    Shipment local =
        requireTx()
            .execute(
                s -> {
                  if (repository.shipment(orderId, false) == null) return null;
                  String status = repository.lockOrderStatus(orderId);
                  if (Set.of("EXPIRED", "REJECTED", "CANCELLED").contains(status))
                    repository.cancelPending(orderId);
                  return repository.shipment(orderId, false);
                });
    return carrier == null ? local : carrier.refreshIfStale(orderId, local);
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
    return requireTx()
        .execute(
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

  private TransactionTemplate requireTx() {
    if (tx == null) throw new IllegalStateException("La transacción tenant no está disponible.");
    return tx;
  }
}
