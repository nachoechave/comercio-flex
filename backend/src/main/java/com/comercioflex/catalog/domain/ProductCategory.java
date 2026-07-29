package com.comercioflex.catalog.domain;

import java.util.UUID;

public record ProductCategory(UUID id, String name, boolean active) {
}
