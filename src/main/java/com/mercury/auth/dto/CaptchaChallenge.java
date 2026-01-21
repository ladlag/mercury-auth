package com.mercury.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CaptchaChallenge {
    private String captchaId;
    private String question;
    private long expiresInSeconds;
}
