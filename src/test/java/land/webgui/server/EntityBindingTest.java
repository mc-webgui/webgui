package land.webgui.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Data-contract tests for the {@link EntityBinding} record used by the mod API. */
class EntityBindingTest {

    @Test
    void exposesUrlTemplateAndCancelFlag() {
        EntityBinding b = new EntityBinding("https://shop.example/{entity_uuid}", true);
        assertEquals("https://shop.example/{entity_uuid}", b.urlTemplate());
        assertTrue(b.cancelInteraction());
    }

    @Test
    void defaultsCanKeepInteraction() {
        EntityBinding b = new EntityBinding("https://info.example", false);
        assertFalse(b.cancelInteraction());
    }

    @Test
    void valueEquality() {
        assertEquals(
                new EntityBinding("u", true),
                new EntityBinding("u", true));
        assertNotEquals(
                new EntityBinding("u", true),
                new EntityBinding("u", false));
    }
}
