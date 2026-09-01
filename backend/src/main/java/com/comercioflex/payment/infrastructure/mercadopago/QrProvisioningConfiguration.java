package com.comercioflex.payment.infrastructure.mercadopago;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.comercioflex.payment.application.MercadoPagoQrProvisioningGateway;
import com.comercioflex.payment.application.PaymentOAuthProperties;

@Configuration
public class QrProvisioningConfiguration {

	@Bean
	MercadoPagoQrProvisioningGateway mercadoPagoQrProvisioningGateway(
			PaymentOAuthProperties properties) {
		SimpleClientHttpRequestFactory requestFactory =
			new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(properties.connectTimeout());
		requestFactory.setReadTimeout(properties.readTimeout());
		RestClient client = RestClient.builder()
			.baseUrl(properties.apiBaseUri().toString())
			.requestFactory(requestFactory)
			.build();
		return new MercadoPagoQrProvisioningGatewayAdapter(client);
	}
}
