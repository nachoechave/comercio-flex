package com.comercioflex.payment.application;

import java.util.UUID;

public record OAuthTenantIdentity(long id, UUID publicId, String slug) {
}
