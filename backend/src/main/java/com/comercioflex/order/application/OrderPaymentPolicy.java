package com.comercioflex.order.application;

public interface OrderPaymentPolicy {

	boolean blocksManualConfirmation(long orderInternalId);

	boolean hasAppliedPayment(long orderInternalId);

	static OrderPaymentPolicy allowAll() {
		return new OrderPaymentPolicy() {
			@Override
			public boolean blocksManualConfirmation(long orderInternalId) {
				return false;
			}

			@Override
			public boolean hasAppliedPayment(long orderInternalId) {
				return false;
			}
		};
	}
}
