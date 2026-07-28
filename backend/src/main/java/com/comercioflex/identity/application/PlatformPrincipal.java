package com.comercioflex.identity.application;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.comercioflex.identity.domain.UserStatus;

public final class PlatformPrincipal implements UserDetails, Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	private final Long id;
	private final UUID publicId;
	private final String email;
	private final String displayName;
	private final String passwordHash;
	private final UserStatus status;

	public PlatformPrincipal(UserCredentials user) {
		this.id = user.id();
		this.publicId = user.publicId();
		this.email = user.email();
		this.displayName = user.displayName();
		this.passwordHash = user.passwordHash();
		this.status = user.status();
	}

	public Long id() {
		return id;
	}

	public UUID publicId() {
		return publicId;
	}

	public String email() {
		return email;
	}

	public String displayName() {
		return displayName;
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return List.of(new SimpleGrantedAuthority("ROLE_AUTHENTICATED"));
	}

	@Override
	public String getPassword() {
		return passwordHash;
	}

	@Override
	public String getUsername() {
		return email;
	}

	@Override
	public boolean isAccountNonLocked() {
		return status != UserStatus.LOCKED;
	}

	@Override
	public boolean isEnabled() {
		return status == UserStatus.ACTIVE;
	}
}
