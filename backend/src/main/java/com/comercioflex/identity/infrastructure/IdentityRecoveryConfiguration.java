package com.comercioflex.identity.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class IdentityRecoveryConfiguration {

	@Bean("identityRecoveryExecutor")
	ThreadPoolTaskExecutor identityRecoveryExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(1);
		executor.setMaxPoolSize(2);
		executor.setQueueCapacity(100);
		executor.setThreadNamePrefix("identity-recovery-");
		executor.setWaitForTasksToCompleteOnShutdown(true);
		executor.setAwaitTerminationSeconds(20);
		return executor;
	}
}
