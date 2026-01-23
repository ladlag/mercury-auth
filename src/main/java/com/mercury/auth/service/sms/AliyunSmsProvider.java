package com.mercury.auth.service.sms;

import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.dysmsapi.model.v20170525.SendSmsRequest;
import com.aliyuncs.dysmsapi.model.v20170525.SendSmsResponse;
import com.aliyuncs.profile.DefaultProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;

/**
 * Alibaba Cloud (Aliyun) SMS provider implementation
 */
@Component
@ConditionalOnProperty(prefix = "sms.aliyun", name = "enabled", havingValue = "true")
public class AliyunSmsProvider implements SmsProvider {
    
    private static final Logger logger = LoggerFactory.getLogger(AliyunSmsProvider.class);
    
    @Value("${sms.aliyun.access-key-id:}")
    private String accessKeyId;
    
    @Value("${sms.aliyun.access-key-secret:}")
    private String accessKeySecret;
    
    @Value("${sms.aliyun.sign-name:}")
    private String signName;
    
    @Value("${sms.aliyun.template-code:}")
    private String templateCode;
    
    @Value("${sms.aliyun.region-id:cn-hangzhou}")
    private String regionId;
    
    private IAcsClient client;
    private boolean configured = false;
    
    @PostConstruct
    public void init() {
        if (!StringUtils.hasText(accessKeyId) || !StringUtils.hasText(accessKeySecret) 
                || !StringUtils.hasText(signName) || !StringUtils.hasText(templateCode)) {
            logger.warn("Aliyun SMS not fully configured. Missing required properties.");
            configured = false;
            return;
        }
        
        try {
            DefaultProfile profile = DefaultProfile.getProfile(regionId, accessKeyId, accessKeySecret);
            client = new DefaultAcsClient(profile);
            configured = true;
            logger.info("Aliyun SMS provider initialized successfully. Region: {}, SignName: {}", 
                    regionId, signName);
        } catch (Exception e) {
            logger.error("Failed to initialize Aliyun SMS client", e);
            configured = false;
        }
    }
    
    @Override
    public void sendVerificationCode(String phone, String code) throws SmsException {
        if (!configured) {
            throw new SmsException("Aliyun SMS provider not configured");
        }
        
        try {
            // Ensure phone number does not have country code prefix
            // Aliyun API expects phone numbers without country code
            String normalizedPhone = normalizePhoneNumber(phone);
            
            SendSmsRequest request = new SendSmsRequest();
            request.setPhoneNumbers(normalizedPhone);
            request.setSignName(signName);
            request.setTemplateCode(templateCode);
            // Template param format: {"code":"123456"}
            request.setTemplateParam("{\"code\":\"" + code + "\"}");
            
            SendSmsResponse response = client.getAcsResponse(request);
            
            if (response != null && "OK".equalsIgnoreCase(response.getCode())) {
                logger.info("Aliyun SMS sent successfully to: {}, BizId: {}", 
                        normalizedPhone, response.getBizId());
            } else {
                String errorMsg = response != null ? 
                        String.format("Code: %s, Message: %s", response.getCode(), response.getMessage()) 
                        : "Unknown error";
                logger.error("Failed to send Aliyun SMS to {}: {}", normalizedPhone, errorMsg);
                throw new SmsException("Failed to send SMS: " + errorMsg);
            }
        } catch (SmsException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error sending Aliyun SMS to {}", phone, e);
            throw new SmsException("Error sending SMS", e);
        }
    }
    
    @Override
    public String getProviderName() {
        return "Aliyun";
    }
    
    @Override
    public boolean isConfigured() {
        return configured;
    }
    
    /**
     * Normalize phone number to ensure it doesn't have country code prefix
     * Aliyun API expects phone numbers without country code
     */
    private String normalizePhoneNumber(String phone) {
        if (phone.startsWith("+86")) {
            return phone.substring(3);
        } else if (phone.startsWith("86") && phone.length() == 13) {
            return phone.substring(2);
        }
        return phone;
    }
}
