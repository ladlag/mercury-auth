package com.mercury.auth.service.sms;

/**
 * SMS provider interface for sending verification codes
 * Implementations support different cloud providers (Alibaba Cloud, Tencent Cloud)
 */
public interface SmsProvider {
    
    /**
     * Send SMS verification code to the specified phone number
     * 
     * @param phone The phone number to send SMS to (format: +86xxxxxxxxxxx or xxxxxxxxxxx)
     * @param code The verification code to send
     * @throws SmsException if SMS sending fails
     */
    void sendVerificationCode(String phone, String code) throws SmsException;
    
    /**
     * Get the provider name for logging
     * 
     * @return Provider name (e.g., "Aliyun", "Tencent")
     */
    String getProviderName();
    
    /**
     * Check if the provider is properly configured
     * 
     * @return true if provider is configured and ready to use
     */
    boolean isConfigured();
}
