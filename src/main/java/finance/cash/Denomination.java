package finance.cash;

/**
 * 实体现金的面额。每种面额是一个独立物品（可以正常堆叠），面值与账户里的金币 1:1。
 * 顺序从大到小，找零和出钞都按这个顺序贪心取。
 */
public enum Denomination {
    NOTE_100("banknote_100", 100),
    NOTE_10("banknote_10", 10),
    COIN_1("coin", 1);

    private final String itemId;
    private final long value;

    Denomination(String itemId, long value) {
        this.itemId = itemId;
        this.value = value;
    }

    /** 物品注册名（不含 finance: 前缀）。 */
    public String itemId() { return itemId; }

    public long value() { return value; }
}
