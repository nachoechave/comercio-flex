package com.comercioflex.identity.application;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.comercioflex.identity.infrastructure.control.PlatformUserRepository;

@Service
public class PlatformUserDetailsService implements UserDetailsService {

	private final PlatformUserRepository userRepository;
	private final EmailNormalizer emailNormalizer;

	public PlatformUserDetailsService(
			PlatformUserRepository userRepository,
			EmailNormalizer emailNormalizer) {
		this.userRepository = userRepository;
		this.emailNormalizer = emailNormalizer;
	}

	@Override
	@Transactional(readOnly = true)
	public UserDetails loadUserByUsername(String username) {
		return userRepository.findCredentialsByEmail(emailNormalizer.normalize(username))
			.map(PlatformPrincipal::new)
			.orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
	}
}
