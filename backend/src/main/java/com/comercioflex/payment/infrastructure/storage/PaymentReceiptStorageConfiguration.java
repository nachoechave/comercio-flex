package com.comercioflex.payment.infrastructure.storage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PaymentReceiptStorageProperties.class)
@Import({LocalPaymentReceiptStorage.class, S3PaymentReceiptStorage.class})
public class PaymentReceiptStorageConfiguration {
}
