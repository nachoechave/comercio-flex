package com.comercioflex.order.application;

import com.comercioflex.order.domain.GuestOrder;

public record GuestOrderCreation(GuestOrder order, String lookupToken, boolean replayed) {
}

