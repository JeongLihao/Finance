package finance.gui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.FriendlyByteBuf;

/** 菜单数据编解码共用的小工具：长度上限、截断、long 列表和字符串→整数表。从 {@link FinanceMenu} 拆出。 */
final class MenuCodec {

    static final int MAX_MAP_ENTRIES = 1_024;
    static final int MAX_HISTORY_POINTS = 1_024;

    private MenuCodec() {}

    static void writeStringIntMap(FriendlyByteBuf buffer, Map<String, Integer> map) {
        List<Map.Entry<String, Integer>> entries = bounded(new ArrayList<>(map.entrySet()), MAX_MAP_ENTRIES);
        buffer.writeVarInt(entries.size());
        for (Map.Entry<String, Integer> entry : entries) {
            buffer.writeUtf(limitString(entry.getKey(), 64), 64);
            buffer.writeVarInt(entry.getValue());
        }
    }

    static Map<String, Integer> readStringIntMap(FriendlyByteBuf buffer) {
        int size = readBoundedSize(buffer, "string map", MAX_MAP_ENTRIES);
        Map<String, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i < size; i++) {
            map.put(buffer.readUtf(64), buffer.readVarInt());
        }
        return map;
    }

    static void writeLongList(FriendlyByteBuf buffer, List<Long> values) {
        List<Long> safe = tail(values, MAX_HISTORY_POINTS);
        buffer.writeVarInt(safe.size());
        for (Long value : safe) {
            buffer.writeLong(value != null ? value : 0L);
        }
    }

    static List<Long> readLongList(FriendlyByteBuf buffer) {
        int size = readBoundedSize(buffer, "price history", MAX_HISTORY_POINTS);
        List<Long> values = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            values.add(buffer.readLong());
        }
        return values;
    }

    static int readBoundedSize(FriendlyByteBuf buffer, String field, int maximum) {
        int size = buffer.readVarInt();
        if (size < 0 || size > maximum) {
            throw new IllegalArgumentException("Invalid " + field + " count: " + size);
        }
        return size;
    }

    static <T> List<T> bounded(List<T> values, int maximum) {
        if (values == null || values.isEmpty()) return List.of();
        return values.size() <= maximum ? values : values.subList(0, maximum);
    }

    static <T> List<T> tail(List<T> values, int maximum) {
        if (values == null || values.isEmpty()) return List.of();
        return values.size() <= maximum ? values : values.subList(values.size() - maximum, values.size());
    }

    static String limitString(String text, int maxLength) {
        String safe = text == null ? "" : text;
        return safe.length() > maxLength ? safe.substring(0, maxLength) : safe;
    }
}
