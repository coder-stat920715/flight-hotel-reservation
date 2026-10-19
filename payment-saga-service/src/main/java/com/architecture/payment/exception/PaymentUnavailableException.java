package com.architecture.payment.exception;

/** Transient failure: triggers the Kafka retry topics (3 exponential retries) and finally the DLT. */
public class PaymentUnavailableException extends RuntimeException {
    public PaymentUnavailableException(String message) {
        super(message);
    }
}
