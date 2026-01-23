package com.mercury.auth.service.sms;

import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.common.profile.HttpProfile;
import com.tencentcloudapi.sms.v20210111.SmsClient;
import com.tencentcloudapi.sms.v20210111.models.SendSmsRequest;
import com.tencentcloudapi.sms.v20210111.models.SendSmsResponse;
import com.tencentcloudapi.sms.v20210111.models.SendStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;

/**
 * Tencent Cloud SMS provider implementation
 */
@Component
@ConditionalOnProperty(prefix = "sms.tencent", name = "enabled", havingValue = "true")
public class TencentSmsProvider implements SmsProvider {
    
    private static final Logger logger = LoggerFactory.getLogger(TencentSmsProvider.class);
    
    @Value("${sms.tencent.secret-id:}")
    private String secretId;
    
    @Value("${sms.tencent.secret-key:}")
    private String secretKey;
    
    @Value("${sms.tencent.sdk-app-id:}")
    private String sdkAppId;
    
    @Value("${sms.tencent.sign-name:}")
    private String signName;
    
    @Value("${sms.tencent.template-id:}")
    private String templateId;
    
    @Value("${sms.tencent.region:ap-guangzhou}")
    private String region;
    
    private SmsClient client;
    private boolean configured = false;
    
    @PostConstruct
    public void init() {
        if (!StringUtils.hasText(secretId) || !StringUtils.hasText(secretKey) 
                || !StringUtils.hasText(sdkAppId) || !StringUtils.hasText(signName) 
                || !StringUtils.hasText(templateId)) {
            logger.warn("Tencent SMS not fully configured. Missing required properties.");
            configured = false;
            return;
        }
        
        try {
            Credential cred = new Credential(secretId, secretKey);
            HttpProfile httpProfile = new HttpProfile();
            httpProfile.setEndpoint("sms.tencentcloudapi.com");
            
            ClientProfile clientProfile = new ClientProfile();
            clientProfile.setHttpProfile(httpProfile);
            
            client = new SmsClient(cred, region, clientProfile);
            configured = true;
            logger.info("Tencent SMS provider initialized successfully. Region: {}, SignName: {}", 
                    region, signName);
        } catch (Exception e) {
            logger.error("Failed to initialize Tencent SMS client", e);
            configured = false;
        }
    }
    
    @Override
    public void sendVerificationCode(String phone, String code) throws SmsException {
        if (!configured) {
            throw new SmsException("Tencent SMS provider not configured");
        }
        
        try {
            // Ensure phone number has +86 country code prefix for Tencent API
            // Tencent API expects phone numbers with country code (e.g., +8613800138000)
            String normalizedPhone = normalizePhoneNumber(phone);
            
            SendSmsRequest request = new SendSmsRequest();
            request.setSmsSdkAppId(sdkAppId);
            request.setSignName(signName);
            request.setTemplateId(templateId);
            // Template params: verification code
            request.setTemplateParamSet(new String[]{code});
            // Phone numbers array with country code
            request.setPhoneNumberSet(new String[]{normalizedPhone});
            
            SendSmsResponse response = client.SendSms(request);
            
            if (response != null && response.getSendStatusSet() != null 
                    && response.getSendStatusSet().length > 0) {
                SendStatus status = response.getSendStatusSet()[0];
                if ("Ok".equalsIgnoreCase(status.getCode())) {
                    logger.info("Tencent SMS sent successfully to: {}, SerialNo: {}", 
                            normalizedPhone, status.getSerialNo());
                } else {
                    String errorMsg = String.format("Code: %s, Message: %s", 
                            status.getCode(), status.getMessage());
                    logger.error("Failed to send Tencent SMS to {}: {}", normalizedPhone, errorMsg);
                    throw new SmsException("Failed to send SMS: " + errorMsg);
                }
            } else {
                logger.error("Failed to send Tencent SMS to {}: Empty response", normalizedPhone);
                throw new SmsException("Failed to send SMS: Empty response");
            }
        } catch (TencentCloudSDKException e) {
            logger.error("Tencent Cloud SDK error sending SMS to {}: {}", phone, e.getMessage(), e);
            throw new SmsException("Tencent Cloud SDK error: " + e.getMessage(), e);
        } catch (SmsException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error sending Tencent SMS to {}", phone, e);
            throw new SmsException("Error sending SMS", e);
        }
    }
    
    @Override
    public String getProviderName() {
        return "Tencent";
    }
    
    @Override
    public boolean isConfigured() {
        return configured;
    }
    
    /**
     * Normalize phone number to ensure it has country code prefix
     * Tencent API expects phone numbers with country code (e.g., +8613800138000)
     */
    private String normalizePhoneNumber(String phone) {
        if (phone.startsWith("+86")) {
            return phone;
        } else if (phone.startsWith("86") && phone.length() == 13) {
            return "+" + phone;
        } else if (phone.length() == 11) {
            // Assume Chinese mobile number
            return "+86" + phone;
        }
        return phone;
    }
}
