package com.comercioflex.shipping.application;

import com.comercioflex.shipping.domain.ShippingModels.*;
import java.util.UUID;

public interface ShippingRepository {
  Settings settings(boolean lock);

  void save(Settings settings);

  void attach(long orderId, Snapshot snapshot);

  Shipment shipment(UUID orderId, boolean lock);

  void update(UUID orderId, UpdateShipment update);

  String lockOrderStatus(UUID orderId);

  void cancelPending(UUID orderId);
}
