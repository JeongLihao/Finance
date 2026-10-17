package finance.client;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import org.lwjgl.glfw.GLFW;

import java.util.OptionalLong;
import java.util.UUID;

/** 各界面共用的输入框按键处理与发包前的客户端预校验。
 * 服务端解码器会把越界参数当作非法包直接断开连接，所以界面上能输入的值必须先在这里挡住。 */
final class ScreenInput {
    /** 发出操作后等待服务端重开界面的最长时间（tick）；超时后解除锁定，避免丢包时界面永久卡在“处理中”。 */
    static final int PENDING_TIMEOUT_TICKS = 100;

    private ScreenInput() {}

    /** 输入框获得焦点时，除 Esc 和 Tab 外的按键都留给输入框：否则按 E 会关闭界面、数字键会切换快捷栏。 */
    static boolean keepKeyInTextBox(int keyCode) {
        return keyCode != GLFW.GLFW_KEY_ESCAPE && keyCode != GLFW.GLFW_KEY_TAB;
    }

    /** 当前焦点是可输入的输入框时处理按键并返回 true；否则返回 false，交给界面默认逻辑。 */
    static boolean handleTextBoxKey(GuiEventListener focused, int keyCode, int scanCode, int modifiers) {
        if (!(focused instanceof EditBox box) || !box.canConsumeInput()) return false;
        return box.keyPressed(keyCode, scanCode, modifiers) || keepKeyInTextBox(keyCode);
    }

    /** 解析 1..max 的整数；空、非数字、越界都返回 -1。 */
    static int parsePositive(String text, int max) {
        long value = parsePositive(text, (long) max);
        return value < 0 ? -1 : (int) value;
    }

    /** 解析 1..max 的长整数；空、非数字、越界都返回 -1。 */
    static long parsePositive(String text, long max) {
        return parseInRange(text, 1, max).orElse(-1);
    }

    /** 解析 min..max 的整数；空、非数字、越界都返回空。 */
    static OptionalLong parseInRange(String text, long min, long max) {
        if (text == null) return OptionalLong.empty();
        try {
            long value = Long.parseLong(text.trim());
            return value >= min && value <= max ? OptionalLong.of(value) : OptionalLong.empty();
        } catch (NumberFormatException exception) {
            return OptionalLong.empty();
        }
    }

    /** 输入不合法时的状态栏提示。 */
    static String rangeMessage(String label, long min, long max) {
        return max >= FinanceScreen.MAX_INPUT_AMOUNT
                ? label + "须为不小于 " + min + " 的整数"
                : label + "须为 " + min + "～" + max + " 的整数";
    }

    /** 解析 UUID；不合法返回 null。 */
    static UUID parseUuid(String text) {
        if (text == null || text.isBlank()) return null;
        try {
            return UUID.fromString(text.trim());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
