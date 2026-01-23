package com.mercury.auth.service;

import com.mercury.auth.exception.ApiException;
import com.mercury.auth.exception.ErrorCodes;
import com.mercury.auth.service.sms.SmsException;
import com.mercury.auth.service.sms.SmsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.List;

/**
 * Service for sending SMS verification codes
 * Supports multiple SMS providers (Alibaba Cloud, Tencent Cloud)
 * Automatically selects the first configured provider
 */
@Service
public class SmsService {
    
    private static final Logger logger = LoggerFactory.getLogger(SmsService.class);
    
    @Autowired(required = false)
    private List<SmsProvider> smsProviders;
    
    private SmsProvider activeProvider;
    private boolean smsConfigured = false;
    
    @PostConstruct
    public void init() {
        if (smsProviders == null || smsProviders.isEmpty()) {
            logger.warn("No SMS providers configured. SMS sending will be disabled.");
            smsConfigured = false;
            return;
        }
        
        // Find the first configured provider
        for (SmsProvider provider : smsProviders) {
            if (provider.isConfigured()) {
                activeProvider = provider;
                smsConfigured = true;
                logger.info("Using SMS provider: {}", provider.getProviderName());
                return;
            }
        }
        
        logger.warn("No SMS providers are properly configured. SMS sending will be disabled.");
        smsConfigured = false;
    }
    
    /**
     * Check if SMS service is configured and ready
     * 
     * @return true if at least one SMS provider is configured
     */
    public boolean isSmsConfigured() {
        return smsConfigured;
    }
    
    /**
     * Send SMS verification code to the specified phone number
     * 
     * @param phone The phone number to send SMS to
     * @param code The verification code to send
     * @throws ApiException if SMS service is not configured or sending fails
     */
    public void sendVerificationCode(String phone, String code) {
        if (!smsConfigured) {
            logger.warn("SMS service not configured. Cannot send SMS to: {}", phone);
            throw new ApiException(ErrorCodes.SMS_SERVICE_UNAVAILABLE, "SMS service not configured");
        }
        
        try {
            activeProvider.sendVerificationCode(phone, code);
            logger.info("SMS verification code sent successfully to {} via {}", 
                    phone, activeProvider.getProviderName());
        } catch (SmsException e) {
            logger.error("Failed to send SMS to {} via {}: {}", 
                    phone, activeProvider.getProviderName(), e.getMessage());
            throw new ApiException(ErrorCodes.SMS_SERVICE_UNAVAILABLE, "Failed to send SMS: " + e.getMessage());
        }
    }
    
    /**
     * Get the name of the active SMS provider
     * 
     * @return Provider name or "None" if no provider is configured
     */
    public String getActiveProviderName() {
        return activeProvider != null ? activeProvider.getProviderName() : "None";
    }
}
