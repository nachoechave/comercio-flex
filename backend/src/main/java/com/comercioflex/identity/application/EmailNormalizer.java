package com.comercioflex.identity.application;

import java.util.Locale;

import org.springframework.stereotype.Component;

@Component
public class EmailNormalizer {

	public String normalize(String email) {
		return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
	}
}
