package finance.settlement;

import java.util.UUID;

/** 新聚落的名字：按 ID 从村名表里挑一个，和已有聚落重名时加编号。已有存档里的名字不改。 */
public final class SettlementNames {

    private static final String[] NAMES = {
        "柳溪村", "石桥村", "麦田村", "松林村", "河湾村", "青山村", "枫叶村", "白沙村",
        "杏花村", "云岭村", "鹿鸣村", "桃源村", "雁回村", "清泉村", "红叶村", "落霞村"
    };

    private SettlementNames() {}

    public static String forId(UUID id) {
        String base = NAMES[Math.floorMod(id.hashCode(), NAMES.length)];
        String name = base;
        for (int n = 2; taken(name); n++) name = base + n;
        return name;
    }

    private static boolean taken(String name) {
        return SettlementManager.settlements().values().stream()
                .anyMatch(s -> s.displayName().equals(name));
    }
}
