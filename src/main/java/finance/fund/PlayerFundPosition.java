package finance.fund;

public final class PlayerFundPosition {
    private long shareUnits;
    private long frozenShareUnits;
    private long totalCost;
    private long realizedProfit;
    public long shareUnits() { return shareUnits; }
    public long frozenShareUnits() { return frozenShareUnits; }
    public long availableShareUnits() { return shareUnits - frozenShareUnits; }
    public long totalCost() { return totalCost; }
    public long realizedProfit() { return realizedProfit; }
    boolean canAdd(long shares, long cost) { return shares > 0 && cost >= 0 && shareUnits <= Long.MAX_VALUE - shares && totalCost <= Long.MAX_VALUE - cost; }
    void add(long shares, long cost) { shareUnits = Math.addExact(shareUnits, shares); totalCost = Math.addExact(totalCost, cost); }
    boolean remove(long shares, long proceeds) {
        if (shares <= 0 || shares > availableShareUnits() || proceeds < 0) return false;
        long costRemoved = shareUnits == shares ? totalCost : finance.fund.FundMath.ratioFloor(totalCost, shares, shareUnits);
        shareUnits -= shares; totalCost -= costRemoved;
        realizedProfit = finance.fund.FundMath.saturatedAddSigned(realizedProfit, proceeds - costRemoved);
        return true;
    }
    boolean freeze(long shares) { if (shares <= 0 || shares > availableShareUnits()) return false; frozenShareUnits += shares; return true; }
    boolean unfreeze(long shares) { if (shares <= 0 || shares > frozenShareUnits) return false; frozenShareUnits -= shares; return true; }
    boolean redeemFrozen(long shares, long proceeds) {
        if (shares <= 0 || shares > frozenShareUnits || proceeds < 0) return false;
        frozenShareUnits -= shares;
        long costRemoved = shareUnits == shares ? totalCost : FundMath.ratioFloor(totalCost, shares, shareUnits);
        shareUnits -= shares; totalCost -= costRemoved;
        realizedProfit = FundMath.saturatedAddSigned(realizedProfit, proceeds - costRemoved); return true;
    }
    public void restore(long shares, long frozen, long cost, long realized) { shareUnits = shares; frozenShareUnits = frozen; totalCost = cost; realizedProfit = realized; }
}
