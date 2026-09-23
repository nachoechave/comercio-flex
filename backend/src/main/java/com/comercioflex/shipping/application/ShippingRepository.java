package com.comercioflex.shipping.application;
import java.util.UUID;
import com.comercioflex.shipping.domain.ShippingModels.*;
public interface ShippingRepository {
 Settings settings(boolean lock);
 void save(Settings settings);
 void attach(long orderId, Snapshot snapshot);
 Shipment shipment(UUID orderId, boolean lock);
 void update(UUID orderId, UpdateShipment update);
 String lockOrderStatus(UUID orderId);
}

