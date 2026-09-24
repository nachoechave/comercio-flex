package com.comercioflex.shipping.domain;

import com.comercioflex.shipping.domain.CarrierModels.DocumentType;
import com.comercioflex.shipping.domain.CarrierModels.Parcel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ShippingModels {
  private ShippingModels() {}

  public enum MethodType {
    PICKUP,
    FIXED_RATE,
    LOCATION_RATE,
    POSTAL_CODE_RATE,
    CARRIER
  }

  public enum Status {
    PENDING,
    PREPARING,
    SHIPPED,
    DELIVERED,
    CANCELLED;

    public boolean allows(Status next) {
      return this == next
          || switch (this) {
            case PENDING -> next == PREPARING || next == CANCELLED;
            case PREPARING -> next == SHIPPED || next == CANCELLED;
            case SHIPPED -> next == DELIVERED;
            default -> false;
          };
    }
  }

  public record Rule(
      @NotBlank @Size(max = 160) String destination,
      @NotNull @DecimalMin("0") @Digits(integer = 13, fraction = 2) BigDecimal price) {}

  public record Method(
      UUID id,
      @NotBlank @Size(max = 160) String name,
      @Size(max = 1000) String description,
      @NotNull MethodType type,
      @NotNull @DecimalMin("0") @Digits(integer = 13, fraction = 2) BigDecimal price,
      boolean active,
      @Size(max = 500) String pickupAddress,
      @Size(max = 1000) String instructions,
      @NotNull @Size(max = 500) List<@NotNull @Valid Rule> rules) {}

  public record Settings(
      @DecimalMin("0") @Digits(integer = 13, fraction = 2) BigDecimal freeShippingThreshold,
      @Min(0) long version,
      @NotNull @Size(max = 100) List<@NotNull @Valid Method> methods) {}

  public record Address(
      @NotBlank @Size(max = 160) String street,
      @NotBlank @Size(max = 30) String number,
      @Size(max = 80) String apartment,
      @NotBlank @Size(max = 160) String city,
      @NotBlank @Size(max = 160) String province,
      @NotBlank @Size(max = 20) String postalCode) {
    public String display() {
      return street
          + " "
          + number
          + (apartment == null || apartment.isBlank() ? "" : " " + apartment)
          + ", "
          + city
          + ", "
          + province
          + " (CP "
          + postalCode
          + ")";
    }
  }

  public record Selection(
      @NotNull UUID methodId,
      @Valid Address address,
      @NotNull @DecimalMin("0") BigDecimal expectedTotal,
      UUID quoteToken,
      DocumentType documentType,
      @Size(max = 20) String documentNumber) {
    public Selection(UUID methodId, Address address, BigDecimal expectedTotal) {
      this(methodId, address, expectedTotal, null, null, null);
    }
  }

  public record Snapshot(
      UUID methodId,
      String name,
      MethodType type,
      BigDecimal cost,
      String pickupAddress,
      String instructions,
      Address address,
      String provider,
      String serviceCode,
      UUID quoteToken,
      DocumentType documentType,
      String documentNumber,
      Parcel parcel,
      BigDecimal providerCost) {
    public Snapshot(
        UUID methodId,
        String name,
        MethodType type,
        BigDecimal cost,
        String pickupAddress,
        String instructions,
        Address address) {
      this(
          methodId,
          name,
          type,
          cost,
          pickupAddress,
          instructions,
          address,
          null,
          null,
          null,
          null,
          null,
          null,
          null);
    }
  }

  public record Quote(
      UUID methodId,
      String name,
      String description,
      MethodType type,
      @com.fasterxml.jackson.annotation.JsonFormat(
              shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
          BigDecimal shippingAmount,
      boolean freeShipping,
      @com.fasterxml.jackson.annotation.JsonFormat(
              shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
          BigDecimal listSubtotal,
      @com.fasterxml.jackson.annotation.JsonFormat(
              shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
          BigDecimal discountAmount,
      @com.fasterxml.jackson.annotation.JsonFormat(
              shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
          BigDecimal subtotal,
      @com.fasterxml.jackson.annotation.JsonFormat(
              shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
          BigDecimal total,
      String pickupAddress,
      String instructions,
      String provider,
      String serviceCode,
      Integer estimatedDays,
      UUID quoteToken) {
    public Quote(
        UUID methodId,
        String name,
        String description,
        MethodType type,
        BigDecimal shippingAmount,
        boolean freeShipping,
        BigDecimal listSubtotal,
        BigDecimal discountAmount,
        BigDecimal subtotal,
        BigDecimal total,
        String pickupAddress,
        String instructions) {
      this(
          methodId,
          name,
          description,
          type,
          shippingAmount,
          freeShipping,
          listSubtotal,
          discountAmount,
          subtotal,
          total,
          pickupAddress,
          instructions,
          null,
          null,
          null,
          null);
    }
  }

  public record Shipment(
      UUID id,
      UUID orderId,
      String provider,
      Status status,
      String carrierName,
      String trackingNumber,
      String trackingUrl,
      BigDecimal shippingCost,
      Instant createdAt,
      Instant shippedAt,
      Instant deliveredAt,
      String notes,
      long version,
      BigDecimal providerCost,
      String externalReference,
      String labelReference,
      String providerStatus,
      Instant lastSyncedAt,
      String providerError) {
    public Shipment(
        UUID id,
        UUID orderId,
        String provider,
        Status status,
        String carrierName,
        String trackingNumber,
        String trackingUrl,
        BigDecimal shippingCost,
        Instant createdAt,
        Instant shippedAt,
        Instant deliveredAt,
        String notes,
        long version) {
      this(
          id,
          orderId,
          provider,
          status,
          carrierName,
          trackingNumber,
          trackingUrl,
          shippingCost,
          createdAt,
          shippedAt,
          deliveredAt,
          notes,
          version,
          null,
          null,
          null,
          null,
          null,
          null);
    }

    public boolean labelAvailable() {
      return labelReference != null && !labelReference.isBlank();
    }
  }

  public record UpdateShipment(
      @NotNull Status status,
      @Size(max = 160) String carrierName,
      @Size(max = 160) String trackingNumber,
      @Size(max = 1000) String trackingUrl,
      @Size(max = 1000) String notes,
      @Min(0) long version) {}
}
