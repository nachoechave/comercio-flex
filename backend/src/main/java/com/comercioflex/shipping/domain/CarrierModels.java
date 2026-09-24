package com.comercioflex.shipping.domain;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;

public final class CarrierModels {
  private CarrierModels() {}

  public enum Provider {
    ANDREANI
  }

  public enum Environment {
    SANDBOX,
    PRODUCTION
  }

  public enum DocumentType {
    DNI,
    CUIT,
    CUIL
  }

  public record Origin(
      @NotBlank @Size(max = 20) String postalCode,
      @NotBlank @Size(max = 160) String street,
      @NotBlank @Size(max = 30) String number,
      @NotBlank @Size(max = 160) String city,
      @NotBlank @Size(max = 160) String province,
      @NotBlank @Size(max = 80) String country,
      @NotBlank @Size(max = 160) String senderName,
      @NotBlank @Email @Size(max = 254) String senderEmail,
      @NotBlank @Size(max = 40) String senderPhone,
      @NotNull DocumentType senderDocumentType,
      @NotBlank @Pattern(regexp = "^[0-9]{7,20}$") String senderDocumentNumber) {}

  public record Parcel(
      @NotNull @DecimalMin("1") @Digits(integer = 7, fraction = 3) BigDecimal weightGrams,
      @NotNull @DecimalMin("0.1") @Digits(integer = 8, fraction = 2) BigDecimal lengthCm,
      @NotNull @DecimalMin("0.1") @Digits(integer = 8, fraction = 2) BigDecimal widthCm,
      @NotNull @DecimalMin("0.1") @Digits(integer = 8, fraction = 2) BigDecimal heightCm) {
    public BigDecimal kilos() {
      return weightGrams.divide(new BigDecimal("1000"), 3, java.math.RoundingMode.HALF_UP);
    }

    public BigDecimal volumeCm3() {
      return lengthCm.multiply(widthCm).multiply(heightCm).setScale(2, java.math.RoundingMode.HALF_UP);
    }
  }

  public record SettingsView(
      Provider provider,
      boolean enabled,
      Environment environment,
      String clientCode,
      String contractCode,
      boolean credentialsConfigured,
      @Valid Origin origin,
      @Valid Parcel defaultParcel,
      int trackingSyncMinutes,
      long version) {}

  public record SaveSettings(
      @NotNull Provider provider,
      boolean enabled,
      @NotNull Environment environment,
      @Size(max = 80) String clientCode,
      @Size(max = 80) String contractCode,
      @Size(max = 300) String username,
      @Size(max = 300) String password,
      boolean clearCredentials,
      @Valid Origin origin,
      @Valid Parcel defaultParcel,
      @Min(5) @Max(1440) int trackingSyncMinutes,
      @Min(0) long version) {}

  public record Account(
      Provider provider,
      Environment environment,
      String clientCode,
      String contractCode,
      String username,
      String password,
      Origin origin,
      Parcel defaultParcel,
      int trackingSyncMinutes) {}

  public record QuoteResult(
      String serviceCode,
      BigDecimal providerCost,
      Integer estimatedDays,
      String description) {}

  public record CreateShipmentRequest(
      String orderReference,
      String customerName,
      String customerEmail,
      String customerPhone,
      DocumentType customerDocumentType,
      String customerDocumentNumber,
      com.comercioflex.shipping.domain.ShippingModels.Address destination,
      Parcel parcel,
      BigDecimal declaredValue) {}

  public record CreatedShipment(
      String externalReference,
      String trackingNumber,
      String labelReference,
      String providerStatus) {}

  public record Tracking(
      String providerStatus,
      com.comercioflex.shipping.domain.ShippingModels.Status mappedStatus,
      Instant occurredAt) {}
}
