package com.mercury.auth.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CaptchaChallenge {
    private String captchaId;
    private String question;
    private String captchaImage;
    private long ttlSeconds;

    public CaptchaChallenge(String captchaId, String question, long ttlSeconds) {
        this.captchaId = captchaId;
        this.question = question;
        this.ttlSeconds = ttlSeconds;
    }

    public CaptchaChallenge(String captchaId, String question, String captchaImage, long ttlSeconds) {
        this.captchaId = captchaId;
        this.question = question;
        this.captchaImage = captchaImage;
        this.ttlSeconds = ttlSeconds;
    }

    public long getExpiresInSeconds() {
        return ttlSeconds;
    }
}
