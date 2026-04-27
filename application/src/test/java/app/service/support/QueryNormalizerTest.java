package app.service.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QueryNormalizerTest {

    @Test
    void normalizeForHistory_removesSortQualifier() {
        assertEquals("docs api", QueryNormalizer.normalizeForHistory("  docs sort:behavior api  "));
    }

    @Test
    void normalizeForHistory_keepsOtherQualifiers() {
        assertEquals("config ext:json", QueryNormalizer.normalizeForHistory("Config ext:json sort:date"));
    }
}
