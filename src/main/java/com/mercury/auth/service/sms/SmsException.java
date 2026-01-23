package com.mercury.auth.service.sms;

/**
 * Exception thrown when SMS sending fails
 */
public class SmsException extends Exception {
    
    public SmsException(String message) {
        super(message);
    }
    
    public SmsException(String message, Throwable cause) {
        super(message, cause);
    }
}
