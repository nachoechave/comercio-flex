package com.comercioflex.membership.infrastructure;
import java.time.Clock;
import org.springframework.context.annotation.*;
@Configuration
public class MembershipConfiguration {
 @Bean("membershipClock") Clock membershipClock() { return Clock.systemUTC(); }
}
