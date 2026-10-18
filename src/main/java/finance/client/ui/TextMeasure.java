package finance.client.ui;

/** 量文字宽度。游戏里用真实字体；测试里用 {@link #ESTIMATE}，按 Minecraft 默认字体保守估算。 */
@FunctionalInterface
public interface TextMeasure {
    int width(String text);

    /** 测试用：ASCII 每字 6 像素（空格 4），其余（中文、全角符号）每字 9 像素，都不比真实字体窄。 */
    TextMeasure ESTIMATE = text -> {
        if (text == null) return 0;
        int w = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            w += c == ' ' ? 4 : c < 128 ? 6 : 9;
        }
        return w;
    };
}
