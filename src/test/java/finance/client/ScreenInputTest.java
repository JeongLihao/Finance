package finance.client;

import finance.network.CompanyGameplayActionPacket;
import finance.network.WarehouseActionPacket;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScreenInputTest {
    @Test
    void focusedTextBoxKeepsInventoryAndHotbarKeysButNotEscapeOrTab() {
        assertTrue(ScreenInput.keepKeyInTextBox(GLFW.GLFW_KEY_E));
        assertTrue(ScreenInput.keepKeyInTextBox(GLFW.GLFW_KEY_1));
        assertTrue(ScreenInput.keepKeyInTextBox(GLFW.GLFW_KEY_Q));
        assertFalse(ScreenInput.keepKeyInTextBox(GLFW.GLFW_KEY_ESCAPE));
        assertFalse(ScreenInput.keepKeyInTextBox(GLFW.GLFW_KEY_TAB));
    }

    @Test
    void positiveAmountsOutsideServerDecoderLimitsAreRejected() {
        int max = WarehouseActionPacket.MAX_TRANSFER_AMOUNT;
        assertEquals(1, ScreenInput.parsePositive("1", max));
        assertEquals(64, ScreenInput.parsePositive(" 64 ", max));
        assertEquals(max, ScreenInput.parsePositive(Integer.toString(max), max));
        assertEquals(-1, ScreenInput.parsePositive(Integer.toString(max + 1), max));
        assertEquals(-1, ScreenInput.parsePositive("0", max));
        assertEquals(-1, ScreenInput.parsePositive("-5", max));
        assertEquals(-1, ScreenInput.parsePositive("", max));
        assertEquals(-1, ScreenInput.parsePositive("abc", max));
        assertEquals(-1, ScreenInput.parsePositive("99999999999999999999", max));
        assertEquals(-1, ScreenInput.parsePositive(null, max));

        long reward = CompanyGameplayActionPacket.MAX_CONTRACT_REWARD;
        assertEquals(reward, ScreenInput.parsePositive(Long.toString(reward), reward));
        assertEquals(-1L, ScreenInput.parsePositive(Long.toString(reward + 1), reward));
    }

    @Test
    void invalidUuidsParseToNull() {
        UUID id = UUID.randomUUID();
        assertEquals(id, ScreenInput.parseUuid(" " + id + " "));
        assertNull(ScreenInput.parseUuid("Steve"));
        assertNull(ScreenInput.parseUuid(""));
        assertNull(ScreenInput.parseUuid(null));
    }
}
