package finance.cash;

/**
 * 实体现金的面额。账户里的 1 个单位就是 1 便士，100 便士 = 1 磅。
 * 每种面额是一个独立物品（可以正常堆叠），顺序从大到小，找零和出钞都按这个顺序贪心取。
 */
public enum Denomination {
    POUND("pound_note", 100),
    TEN_PENCE("ten_pence", 10),
    PENNY("penny", 1);

    private final String itemId;
    private final long value;

    Denomination(String itemId, long value) {
        this.itemId = itemId;
        this.value = value;
    }

    /** 物品注册名（不含 finance: 前缀）。 */
    public String itemId() { return itemId; }

    /** 面值，单位便士。 */
    public long value() { return value; }
}
