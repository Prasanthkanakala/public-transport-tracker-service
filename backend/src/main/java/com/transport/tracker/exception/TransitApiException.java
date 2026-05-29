package com.transport.tracker.exception;

/**
* Thrown when an upstream transit API fails or returns an unexpected response.
* The service layer catches this and triggers the fallback degradation chain.
*/
public class TransitApiException extends RuntimeException {

    public TransitApiException(String message) {
        super(message);
    }

    public TransitApiException(String message, Throwable cause) {
        super(message, cause);
    }
}