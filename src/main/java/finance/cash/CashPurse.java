package finance.cash;

/**
 * 玩家随身的现金和绿宝石（通常是背包）。{@link CashService} 只通过这个接口读写物品，
 * 游戏里由 {@link PlayerCashPurse} 实现，测试里用内存实现。
 * <p>
 * remove* 只在数量足够时才修改并返回 true；give* 总能成功（背包满了就掉在脚下）。
 */
public interface CashPurse {
    long count(Denomination denomination);

    boolean remove(Denomination denomination, long count);

    void give(Denomination denomination, long count);

    long emeralds();

    boolean removeEmeralds(long count);

    void giveEmeralds(long count);

    /** 随身现金总面值。 */
    default long cashTotal() {
        long total = 0;
        for (Denomination d : Denomination.values()) {
            long n = count(d);
            if (n > 0) total = Math.addExact(total, Math.multiplyExact(n, d.value()));
        }
        return total;
    }
}
