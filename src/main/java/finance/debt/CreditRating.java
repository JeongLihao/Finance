package finance.debt;

public enum CreditRating {
    AAA(75, 80), AA(125, 70), A(200, 60), BBB(300, 50), BB(500, 40), B(800, 30), CCC(1200, 20), D(2500, 0);

    private final int spreadBasisPoints;
    private final int maxDebtPercent;

    CreditRating(int spreadBasisPoints, int maxDebtPercent) {
        this.spreadBasisPoints = spreadBasisPoints;
        this.maxDebtPercent = maxDebtPercent;
    }

    public int spreadBasisPoints() { return spreadBasisPoints; }
    public int maxDebtPercent() { return maxDebtPercent; }
}
