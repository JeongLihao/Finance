package finance.util;

import java.util.UUID;

/** UUID 小工具。 */
public final class Uuids {

    private Uuids() {}

    /** 把玩家输入或网络包里的文字解析成 UUID；空、空白或格式不对返回 null，前后空格忽略。 */
    public static UUID parseOrNull(String text) {
        if (text == null || text.isBlank()) return null;
        try {
            return UUID.fromString(text.trim());
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }
}
