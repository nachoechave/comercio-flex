package com.comercioflex.shipping.application;

import com.comercioflex.notification.application.CustomerNotificationPublisher;
import com.comercioflex.payment.application.*;
import com.comercioflex.shipping.application.CarrierRepository.StoredQuote;
import com.comercioflex.shipping.application.CarrierRepository.StoredSettings;
import com.comercioflex.shipping.domain.CarrierModels.*;
import com.comercioflex.shipping.domain.ShippingModels.*;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class CarrierShippingService {
  public static final UUID ANDREANI_METHOD_ID =
      UUID.fromString("7f3c7217-4e8e-4a8c-9fd6-bbe52e8a8d21");
  private static final int QUOTE_TTL_MINUTES = 15;
  private static final BigDecimal MAX_TOTAL = new BigDecimal("9999999999999.99");

  private final CarrierRepository repository;
  private final ShippingRepository shippingRepository;
  private final Map<Provider, CarrierGateway> gateways;
  private final CredentialCipher cipher;
  private final PaymentOAuthProperties encryptionProperties;
  private final TransactionTemplate tx;
  private final CustomerNotificationPublisher notifications;

  public CarrierShippingService(
      CarrierRepository repository,
      ShippingRepository shippingRepository,
      List<CarrierGateway> gateways,
      CredentialCipher cipher,
      PaymentOAuthProperties encryptionProperties,
      @Qualifier("tenantTransactionTemplate") TransactionTemplate tx,
      CustomerNotificationPublisher notifications) {
    this.repository = repository;
    this.shippingRepository = shippingRepository;
    EnumMap<Provider, CarrierGateway> map = new EnumMap<>(Provider.class);
    gateways.forEach(g -> map.put(g.provider(), g));
    this.gateways = Map.copyOf(map);
    this.cipher = cipher;
    this.encryptionProperties = encryptionProperties;
    this.tx = tx;
    this.notifications = notifications;
  }

  public SettingsView settings() {
    return view(repository.settings(false));
  }

  public SettingsView save(@Valid SaveSettings command) {
    return requireTx()
        .execute(
            ignored -> {
              StoredSettings previous = repository.settings(true);
              if (previous.version() != command.version()) {
                throw new ShippingException(
                    "La configuración de Andreani cambió. Recargá antes de guardar.");
              }

              boolean suppliedUser = !blank(command.username());
              boolean suppliedPassword = !blank(command.password());
              if (suppliedUser != suppliedPassword) {
                throw new ShippingException("Ingresá usuario y contraseña de Andreani juntos.");
              }
              if (previous.environment() != command.environment()
                  && !command.clearCredentials()
                  && !suppliedUser
                  && credentials(previous)) {
                throw new ShippingException(
                    "Al cambiar de ambiente, volvé a ingresar las credenciales de Andreani.");
              }

              String context =
                  previous.credentialContext() == null || previous.credentialContext().isBlank()
                      ? UUID.randomUUID().toString()
                      : previous.credentialContext();
              EncryptedSecret username = command.clearCredentials() ? null : previous.username();
              EncryptedSecret password = command.clearCredentials() ? null : previous.password();
              if (suppliedUser) {
                requirePersistentEncryptionKey();
                username =
                    cipher.encrypt(
                        command.username().trim(),
                        context(context, command.environment(), "username"));
                password =
                    cipher.encrypt(
                        command.password(), context(context, command.environment(), "password"));
              }

              StoredSettings next =
                  new StoredSettings(
                      command.provider(),
                      command.enabled(),
                      command.environment(),
                      trim(command.clientCode()),
                      trim(command.contractCode()),
                      username,
                      password,
                      context,
                      command.origin(),
                      command.defaultParcel(),
                      command.trackingSyncMinutes(),
                      previous.version());
              validateEnabled(next);
              repository.save(next);
              return view(repository.settings(false));
            });
  }

  public List<Quote> quotes(
      BigDecimal listSubtotal,
      BigDecimal discountAmount,
      BigDecimal freeShippingThreshold,
      String postalCode,
      BigDecimal units) {
    StoredSettings stored = repository.settings(false);
    if (!stored.enabled()) return List.of();
    validateEnabled(stored);
    Account account = account(stored);
    Parcel base = account.defaultParcel();
    BigDecimal safeUnits = units == null || units.signum() <= 0 ? BigDecimal.ONE : units;
    Parcel parcel =
        new Parcel(
            base.weightGrams().multiply(safeUnits).setScale(3, RoundingMode.HALF_UP),
            base.lengthCm(),
            base.widthCm(),
            base.heightCm());
    if (parcel.weightGrams().compareTo(new BigDecimal("50000")) > 0) return List.of();

    QuoteResult remote = gateway(account.provider()).quote(account, postalCode, parcel);
    BigDecimal providerCost = remote.providerCost().setScale(2, RoundingMode.HALF_UP);
    boolean free =
        freeShippingThreshold != null && listSubtotal.compareTo(freeShippingThreshold) >= 0;
    BigDecimal shippingAmount = free ? BigDecimal.ZERO.setScale(2) : providerCost;
    BigDecimal subtotal = listSubtotal.subtract(discountAmount).setScale(2, RoundingMode.HALF_UP);
    BigDecimal total = subtotal.add(shippingAmount).setScale(2, RoundingMode.HALF_UP);
    if (total.compareTo(MAX_TOTAL) > 0)
      throw new ShippingException("El total supera el máximo permitido.");

    UUID token = UUID.randomUUID();
    StoredQuote quote =
        new StoredQuote(
            token,
            ANDREANI_METHOD_ID,
            account.provider(),
            remote.serviceCode(),
            providerCost,
            shippingAmount,
            listSubtotal,
            discountAmount,
            total,
            postalCode.trim(),
            parcel,
            Instant.now().plus(QUOTE_TTL_MINUTES, ChronoUnit.MINUTES),
            null);
    repository.saveQuote(quote);

    return List.of(
        new Quote(
            ANDREANI_METHOD_ID,
            "Andreani a domicilio",
            remote.description(),
            MethodType.CARRIER,
            shippingAmount,
            free,
            listSubtotal,
            discountAmount,
            subtotal,
            total,
            null,
            remote.estimatedDays() == null
                ? "La tarifa se consulta en tiempo real con Andreani."
                : "Entrega estimada: " + remote.estimatedDays() + " días.",
            account.provider().name(),
            remote.serviceCode(),
            remote.estimatedDays(),
            token));
  }

  public Snapshot select(
      Selection selection, BigDecimal listSubtotal, BigDecimal discountAmount, Address address) {
    if (selection == null || selection.quoteToken() == null) {
      throw new ShippingException("La cotización del transportista es obligatoria.");
    }
    if (address == null) throw new ShippingException("La dirección de entrega es obligatoria.");
    if (selection.documentType() == null || blank(selection.documentNumber())) {
      throw new ShippingException("Ingresá el documento del destinatario para Andreani.");
    }
    String document = selection.documentNumber().replaceAll("\\D", "");
    if (document.length() < 7 || document.length() > 20) {
      throw new ShippingException("Revisá el número de documento del destinatario.");
    }

    StoredQuote quote =
        repository
            .quote(selection.quoteToken(), true)
            .orElseThrow(
                () -> new ShippingException("La cotización de Andreani ya no está disponible."));
    if (quote.consumedAt() != null || !quote.expiresAt().isAfter(Instant.now())) {
      throw new ShippingException("La cotización de Andreani venció. Volvé a cotizar.");
    }
    if (!quote.methodId().equals(selection.methodId())
        || !quote.postalCode().equalsIgnoreCase(address.postalCode().trim())
        || quote.listSubtotal().compareTo(listSubtotal) != 0
        || quote.discountAmount().compareTo(discountAmount) != 0
        || quote.total().compareTo(selection.expectedTotal()) != 0) {
      throw new ShippingException("La cotización cambió. Volvé a consultar Andreani.");
    }

    return new Snapshot(
        quote.methodId(),
        "Andreani a domicilio",
        MethodType.CARRIER,
        quote.shippingAmount(),
        null,
        "Envío gestionado con Andreani.",
        address,
        quote.provider().name(),
        quote.serviceCode(),
        quote.id(),
        selection.documentType(),
        document,
        quote.parcel(),
        quote.providerCost());
  }

  public void consume(Snapshot snapshot) {
    if (snapshot != null && snapshot.quoteToken() != null)
      repository.consumeQuote(snapshot.quoteToken());
  }

  public Shipment provision(UUID orderId) {
    CarrierRepository.ProvisionOrder order =
        repository
            .provisionOrder(orderId)
            .orElseThrow(
                () -> new ShippingException("El pedido no tiene un envío para provisionar."));
    if (!blank(order.externalReference())) {
      return shippingRepository.shipment(orderId, false);
    }
    if (!Set.of("CONFIRMED", "READY_FOR_PICKUP", "COMPLETED").contains(order.orderStatus())) {
      throw new ShippingException("Confirmá el pago antes de generar el envío en Andreani.");
    }
    Snapshot snapshot = order.shipping();
    if (snapshot == null
        || snapshot.type() != MethodType.CARRIER
        || !Provider.ANDREANI.name().equals(snapshot.provider())) {
      throw new ShippingException("Este pedido no utiliza Andreani.");
    }
    StoredSettings stored = repository.settings(false);
    validateEnabled(stored);
    Account account = account(stored);

    Boolean claimed =
        requireTx().execute(ignored -> repository.claimProvision(orderId, account.provider()));
    if (!Boolean.TRUE.equals(claimed)) {
      CarrierRepository.ProvisionOrder latest =
          repository
              .provisionOrder(orderId)
              .orElseThrow(() -> new ShippingException("El envío ya no está disponible."));
      if (!blank(latest.externalReference())) return shippingRepository.shipment(orderId, false);
      throw new ShippingException("El envío ya se está generando en Andreani. Reintentá en unos segundos.");
    }

    try {
      CreatedShipment created =
          gateway(account.provider())
              .create(
                  account,
                  new CreateShipmentRequest(
                      orderId.toString(),
                      order.customerName(),
                      order.customerEmail(),
                      order.customerPhone(),
                      snapshot.documentType(),
                      snapshot.documentNumber(),
                      snapshot.address(),
                      snapshot.parcel(),
                      order.listSubtotal()));
      requireTx()
          .executeWithoutResult(
              ignored ->
                  repository.saveProvisioned(
                      orderId,
                      account.provider(),
                      snapshot.providerCost(),
                      created.externalReference(),
                      created.labelReference(),
                      created.trackingNumber(),
                      "https://www.andreani.com/",
                      created.providerStatus()));
      return shippingRepository.shipment(orderId, false);
    } catch (CarrierUnavailableException e) {
      requireTx()
          .executeWithoutResult(ignored -> repository.saveProviderError(orderId, e.getMessage()));
      throw e;
    }
  }

  public byte[] label(UUID orderId) {
    CarrierRepository.ProvisionOrder order =
        repository
            .provisionOrder(orderId)
            .orElseThrow(() -> new ShippingException("El pedido no tiene envío."));
    if (blank(order.externalReference()) || blank(order.labelReference())) {
      throw new ShippingException("Primero generá el envío en Andreani.");
    }
    StoredSettings stored = repository.settings(false);
    Account account = account(stored);
    return gateway(account.provider()).label(account, order.labelReference());
  }

  public Shipment refreshIfStale(UUID orderId, Shipment current) {
    if (current == null
        || current.status() == Status.CANCELLED
        || current.status() == Status.DELIVERED
        || blank(current.provider())
        || blank(current.externalReference())) return current;
    Provider provider;
    try {
      provider = Provider.valueOf(current.provider());
    } catch (IllegalArgumentException e) {
      return current;
    }
    StoredSettings stored = repository.settings(false);
    if (stored.provider() != provider || !credentials(stored)) return current;
    int minutes = Math.max(5, stored.trackingSyncMinutes());
    if (current.lastSyncedAt() != null
        && current.lastSyncedAt().plus(minutes, ChronoUnit.MINUTES).isAfter(Instant.now())) return current;

    try {
      Account account = account(stored);
      Tracking tracking = gateway(provider).track(account, current.externalReference());
      Instant now = Instant.now();
      requireTx()
          .executeWithoutResult(
              ignored ->
                  repository.saveTracking(
                      orderId,
                      tracking.providerStatus(),
                      tracking.mappedStatus(),
                      tracking.occurredAt(),
                      now));
      Shipment updated = shippingRepository.shipment(orderId, false);
      if (current.shippedAt() == null
          && updated != null
          && updated.status() == Status.SHIPPED
          && notifications != null) {
        notifications.orderShipped(updated);
      }
      return updated == null ? current : updated;
    } catch (CarrierUnavailableException e) {
      requireTx()
          .executeWithoutResult(ignored -> repository.saveProviderError(orderId, e.getMessage()));
      return shippingRepository.shipment(orderId, false);
    }
  }

  private SettingsView view(StoredSettings s) {
    return new SettingsView(
        s.provider(),
        s.enabled(),
        s.environment(),
        s.clientCode(),
        s.contractCode(),
        credentials(s),
        s.origin(),
        s.defaultParcel(),
        s.trackingSyncMinutes(),
        s.version());
  }

  private Account account(StoredSettings s) {
    if (!credentials(s)) throw new CarrierUnavailableException("Faltan las credenciales de Andreani.");
    return new Account(
        s.provider(),
        s.environment(),
        s.clientCode(),
        s.contractCode(),
        cipher.decrypt(s.username(), context(s.credentialContext(), s.environment(), "username")),
        cipher.decrypt(s.password(), context(s.credentialContext(), s.environment(), "password")),
        s.origin(),
        s.defaultParcel(),
        s.trackingSyncMinutes());
  }

  private void validateEnabled(StoredSettings s) {
    if (!s.enabled()) return;
    if (blank(s.clientCode())
        || blank(s.contractCode())
        || s.origin() == null
        || s.defaultParcel() == null
        || !credentials(s)) {
      throw new ShippingException(
          "Completá cliente, contrato, credenciales, origen y paquete antes de activar Andreani.");
    }
  }

  private boolean credentials(StoredSettings s) {
    return s.username() != null && s.password() != null;
  }

  private CarrierGateway gateway(Provider provider) {
    CarrierGateway gateway = gateways.get(provider);
    if (gateway == null) throw new CarrierUnavailableException("Transportista no disponible.");
    return gateway;
  }

  private EncryptionContext context(String ref, Environment environment, String field) {
    return new EncryptionContext(ref, "ANDREANI", environment.name(), "shipping-carrier", field);
  }

  private void requirePersistentEncryptionKey() {
    if (blank(encryptionProperties.encryptionKey())) {
      throw new ShippingException(
          "Configurá PAYMENT_TOKEN_ENCRYPTION_KEY_V1 antes de guardar credenciales de Andreani.");
    }
  }

  private TransactionTemplate requireTx() {
    if (tx == null) throw new IllegalStateException("La transacción tenant no está disponible.");
    return tx;
  }

  private String trim(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
