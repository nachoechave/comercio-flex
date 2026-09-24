package com.comercioflex.shipping.application;

import com.comercioflex.shipping.domain.CarrierModels.*;

public interface CarrierGateway {
  Provider provider();

  QuoteResult quote(Account account, String postalCode, Parcel parcel);

  CreatedShipment create(Account account, CreateShipmentRequest request);

  Tracking track(Account account, String externalReference);

  byte[] label(Account account, String labelReference);
}
