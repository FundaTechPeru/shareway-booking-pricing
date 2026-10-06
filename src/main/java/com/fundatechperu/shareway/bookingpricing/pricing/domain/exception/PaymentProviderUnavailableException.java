package com.fundatechperu.shareway.bookingpricing.pricing.domain.exception;
public class PaymentProviderUnavailableException extends RuntimeException {
    public PaymentProviderUnavailableException() { super("Payment provider is unavailable"); }
}
