package com.comercioflex.payment.api;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;

import com.comercioflex.identity.application.TenantAccessDeniedException;
import com.comercioflex.identity.application.TenantPermissionGuard;
import com.comercioflex.identity.domain.TenantPermission;
import com.comercioflex.payment.application.MerchantPaymentConnectionService;
import com.comercioflex.payment.application.QrSetupService;

import jakarta.servlet.http.HttpServletRequest;

class PaymentConnectionControllerTests {

	@Test
	void requiresOwnerPaymentPermissionBeforeQrDiscovery() {
		MerchantPaymentConnectionService connectionService =
			mock(MerchantPaymentConnectionService.class);
		QrSetupService qrSetupService = mock(QrSetupService.class);
		TenantPermissionGuard permissionGuard = mock(TenantPermissionGuard.class);
		HttpServletRequest request = mock(HttpServletRequest.class);
		PaymentConnectionController controller = new PaymentConnectionController(
			connectionService, qrSetupService, permissionGuard);
		doThrow(new TenantAccessDeniedException()).when(permissionGuard)
			.require(request, TenantPermission.MANAGE_PAYMENTS);

		assertThatThrownBy(() -> controller.discoverQr("tienda-a", request))
			.isInstanceOf(TenantAccessDeniedException.class);

		verify(permissionGuard).require(request, TenantPermission.MANAGE_PAYMENTS);
		verifyNoInteractions(qrSetupService);
	}
}
