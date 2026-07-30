package com.comercioflex.order.application;

import com.comercioflex.order.domain.GuestOrder;

public record StoredGuestOrder(byte[] requestFingerprint, GuestOrder order) {
}

