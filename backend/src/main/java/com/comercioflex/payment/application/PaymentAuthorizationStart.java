package com.comercioflex.payment.application;

import java.net.URI;
import java.time.Instant;

public record PaymentAuthorizationStart(URI authorizationUrl, Instant expiresAt) {
}
