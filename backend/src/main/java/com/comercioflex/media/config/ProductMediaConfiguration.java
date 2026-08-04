package com.comercioflex.media.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ProductMediaProperties.class)
public class ProductMediaConfiguration {
}
