package com.comercioflex.shipping.application;

import com.comercioflex.payment.application.EncryptedSecret;
import com.comercioflex.shipping.domain.CarrierModels.*;
import com.comercioflex.shipping.domain.ShippingModels.Snapshot;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface CarrierRepository {
  record StoredSettings(
      Provider provider,
      boolean enabled,
      Environment environment,
      String clientCode,
      String contractCode,
      EncryptedSecret username,
      EncryptedSecret password,
      String credentialContext,
      Origin origin,
      Parcel defaultParcel,
      int trackingSyncMinutes,
      long version) {}

  record StoredQuote(
      UUID id,
      UUID methodId,
      Provider provider,
      String serviceCode,
      BigDecimal providerCost,
      BigDecimal shippingAmount,
      BigDecimal listSubtotal,
      BigDecimal discountAmount,
      BigDecimal total,
      String postalCode,
      Parcel parcel,
      Instant expiresAt,
      Instant consumedAt) {}

  record ProvisionOrder(
      UUID orderId,
      String orderStatus,
      String customerName,
      String customerEmail,
      String customerPhone,
      BigDecimal listSubtotal,
      Snapshot shipping,
      String externalReference,
      String labelReference) {}

  StoredSettings settings(boolean lock);

  void save(StoredSettings settings);

  void saveQuote(StoredQuote quote);

  Optional<StoredQuote> quote(UUID id, boolean lock);

  void consumeQuote(UUID id);

  Optional<ProvisionOrder> provisionOrder(UUID orderId);

  boolean claimProvision(UUID orderId, Provider provider);

  void saveProvisioned(
      UUID orderId,
      Provider provider,
      BigDecimal providerCost,
      String externalReference,
      String labelReference,
      String trackingNumber,
      String trackingUrl,
      String providerStatus);

  void saveProviderError(UUID orderId, String message);

  void saveTracking(
      UUID orderId,
      String providerStatus,
      com.comercioflex.shipping.domain.ShippingModels.Status mappedStatus,
      Instant occurredAt,
      Instant syncedAt);
}
