package com.mercury.auth.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SanitizationUtilsTest {

    @Test
    void sanitize_escapes_html_content() {
        String sanitized = SanitizationUtils.sanitize("<script>alert('x')</script>");

        assertThat(sanitized)
                .contains("&lt;script&gt;")
                .doesNotContain("<script>")
                .doesNotContain("</script>");
    }

    @Test
    void sanitize_handles_null() {
        assertThat(SanitizationUtils.sanitize(null)).isNull();
    }
}
