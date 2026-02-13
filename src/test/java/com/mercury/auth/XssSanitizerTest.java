package com.mercury.auth;

import com.mercury.auth.util.XssSanitizer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class XssSanitizerTest {

    @Test
    void sanitize_nullInput_returnsNull() {
        assertNull(XssSanitizer.sanitize(null));
    }

    @Test
    void sanitize_emptyString_returnsEmpty() {
        assertEquals("", XssSanitizer.sanitize(""));
    }

    @Test
    void sanitize_safeInput_returnsUnchanged() {
        assertEquals("user123", XssSanitizer.sanitize("user123"));
        assertEquals("tenant-001", XssSanitizer.sanitize("tenant-001"));
        assertEquals("tenant_abc", XssSanitizer.sanitize("tenant_abc"));
    }

    @Test
    void sanitize_htmlTags_areEscaped() {
        assertEquals("&lt;script&gt;alert(1)&lt;/script&gt;",
                XssSanitizer.sanitize("<script>alert(1)</script>"));
    }

    @Test
    void sanitize_htmlAttributes_areEscaped() {
        assertEquals("&quot;onclick=&quot;alert(1)&quot;",
                XssSanitizer.sanitize("\"onclick=\"alert(1)\""));
    }

    @Test
    void sanitize_ampersand_isEscaped() {
        assertEquals("a&amp;b", XssSanitizer.sanitize("a&b"));
    }

    @Test
    void sanitize_singleQuote_isEscaped() {
        assertEquals("it&#39;s", XssSanitizer.sanitize("it's"));
    }

    @Test
    void sanitize_mixedContent_allSpecialCharsEscaped() {
        String input = "<div class=\"test\">'hello' & world</div>";
        String expected = "&lt;div class=&quot;test&quot;&gt;&#39;hello&#39; &amp; world&lt;/div&gt;";
        assertEquals(expected, XssSanitizer.sanitize(input));
    }

    @Test
    void sanitize_normalTenantId_unchanged() {
        assertEquals("my-tenant-123", XssSanitizer.sanitize("my-tenant-123"));
    }

    @Test
    void sanitize_normalUsername_unchanged() {
        assertEquals("johnsmith", XssSanitizer.sanitize("johnsmith"));
    }
}
