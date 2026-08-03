package finance.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class FinanceConfig {

    public static final ForgeConfigSpec COMMON_SPEC;

    private static final double DEFAULT_DIVIDEND_RATIO_VALUE = 0.40D;
    private static final int DEFAULT_DIVIDEND_CYCLE_DAYS_VALUE = 7;
    private static final int BANKRUPTCY_RISK_DAYS_VALUE = 3;
    private static final double BANKRUPTCY_CASH_RISK_MULTIPLIER_VALUE = 1.0D;
    private static final double MIN_PROPOSAL_PARTICIPATION_RATIO_VALUE = 0.25D;
    private static final int MAX_PRICE_ALERTS_PER_PLAYER_VALUE = 20;
    private static final int MAX_CONDITIONAL_STOCK_ORDERS_PER_PLAYER_VALUE = 20;
    private static final double STOCK_MARKET_MAKER_SPREAD_VALUE = 0.02D;
    private static final long IPO_FEE_VALUE = 5_000L;

    private static final ForgeConfigSpec.IntValue DEFAULT_DIVIDEND_CYCLE_DAYS;
    private static final ForgeConfigSpec.DoubleValue DEFAULT_DIVIDEND_RATIO;
    private static final ForgeConfigSpec.IntValue BANKRUPTCY_RISK_DAYS;
    private static final ForgeConfigSpec.DoubleValue BANKRUPTCY_CASH_RISK_MULTIPLIER;
    private static final ForgeConfigSpec.DoubleValue MIN_PROPOSAL_PARTICIPATION_RATIO;
    private static final ForgeConfigSpec.IntValue MAX_PRICE_ALERTS_PER_PLAYER;
    private static final ForgeConfigSpec.IntValue MAX_CONDITIONAL_STOCK_ORDERS_PER_PLAYER;
    private static final ForgeConfigSpec.DoubleValue STOCK_MARKET_MAKER_SPREAD;
    private static final ForgeConfigSpec.LongValue IPO_FEE;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.push("company");
        DEFAULT_DIVIDEND_RATIO = builder
                .comment("默认公司分红比例；没有公司级策略时使用。0.40 = 40%。")
                .defineInRange("defaultDividendRatio", DEFAULT_DIVIDEND_RATIO_VALUE, 0.0D, 1.0D);
        DEFAULT_DIVIDEND_CYCLE_DAYS = builder
                .comment("默认公司分红周期，单位为 MC 天。")
                .defineInRange("defaultDividendCycleDays", DEFAULT_DIVIDEND_CYCLE_DAYS_VALUE, 1, 365);
        BANKRUPTCY_RISK_DAYS = builder
                .comment("上市公司现金低于安全线后，持续多少个 MC 天触发破产。")
                .defineInRange("bankruptcyRiskDays", BANKRUPTCY_RISK_DAYS_VALUE, 1, 365);
        BANKRUPTCY_CASH_RISK_MULTIPLIER = builder
                .comment("破产现金安全线倍率。安全线 = 预计每日运营成本 × 此倍率。")
                .defineInRange("bankruptcyCashRiskMultiplier", BANKRUPTCY_CASH_RISK_MULTIPLIER_VALUE, 0.0D, 100.0D);
        IPO_FEE = builder
                .comment("公司发起 IPO 时向所有者收取的费用。")
                .defineInRange("ipoFee", IPO_FEE_VALUE, 0L, Long.MAX_VALUE);
        builder.pop();

        builder.push("proposal");
        MIN_PROPOSAL_PARTICIPATION_RATIO = builder
                .comment("股东提案最低参与率。0.25 = 至少 25% 投票权参与，提案才可能通过。")
                .defineInRange("minParticipationRatio", MIN_PROPOSAL_PARTICIPATION_RATIO_VALUE, 0.0D, 1.0D);
        builder.pop();

        builder.push("orders");
        MAX_PRICE_ALERTS_PER_PLAYER = builder
                .comment("每名玩家最多保留的未触发价格提醒数量。")
                .defineInRange("maxPriceAlertsPerPlayer", MAX_PRICE_ALERTS_PER_PLAYER_VALUE, 1, 10_000);
        MAX_CONDITIONAL_STOCK_ORDERS_PER_PLAYER = builder
                .comment("每名玩家最多保留的股票条件委托数量。")
                .defineInRange("maxConditionalStockOrdersPerPlayer", MAX_CONDITIONAL_STOCK_ORDERS_PER_PLAYER_VALUE, 1, 10_000);
        builder.pop();

        builder.push("stockMarketMaker");
        STOCK_MARKET_MAKER_SPREAD = builder
                .comment("股票做市商价差。0.02 = fairValue 上下 2% 形成 bid / ask。")
                .defineInRange("spread", STOCK_MARKET_MAKER_SPREAD_VALUE, 0.0D, 1.0D);
        builder.pop();

        COMMON_SPEC = builder.build();
    }

    private FinanceConfig() {
    }

    public static double defaultDividendRatio() {
        return getDouble(DEFAULT_DIVIDEND_RATIO, DEFAULT_DIVIDEND_RATIO_VALUE);
    }

    public static int defaultDividendCycleDays() {
        return getInt(DEFAULT_DIVIDEND_CYCLE_DAYS, DEFAULT_DIVIDEND_CYCLE_DAYS_VALUE);
    }

    public static int bankruptcyRiskDays() {
        return getInt(BANKRUPTCY_RISK_DAYS, BANKRUPTCY_RISK_DAYS_VALUE);
    }

    public static double bankruptcyCashRiskMultiplier() {
        return getDouble(BANKRUPTCY_CASH_RISK_MULTIPLIER, BANKRUPTCY_CASH_RISK_MULTIPLIER_VALUE);
    }

    public static double minProposalParticipationRatio() {
        return getDouble(MIN_PROPOSAL_PARTICIPATION_RATIO, MIN_PROPOSAL_PARTICIPATION_RATIO_VALUE);
    }

    public static int maxPriceAlertsPerPlayer() {
        return getInt(MAX_PRICE_ALERTS_PER_PLAYER, MAX_PRICE_ALERTS_PER_PLAYER_VALUE);
    }

    public static int maxConditionalStockOrdersPerPlayer() {
        return getInt(MAX_CONDITIONAL_STOCK_ORDERS_PER_PLAYER, MAX_CONDITIONAL_STOCK_ORDERS_PER_PLAYER_VALUE);
    }

    public static double stockMarketMakerSpread() {
        return getDouble(STOCK_MARKET_MAKER_SPREAD, STOCK_MARKET_MAKER_SPREAD_VALUE);
    }

    public static long ipoFee() {
        return getLong(IPO_FEE, IPO_FEE_VALUE);
    }

    private static int getInt(ForgeConfigSpec.IntValue value, int fallback) {
        try {
            return value.get();
        } catch (IllegalStateException ex) {
            return fallback;
        }
    }

    private static long getLong(ForgeConfigSpec.LongValue value, long fallback) {
        try {
            return value.get();
        } catch (IllegalStateException ex) {
            return fallback;
        }
    }

    private static double getDouble(ForgeConfigSpec.DoubleValue value, double fallback) {
        try {
            return value.get();
        } catch (IllegalStateException ex) {
            return fallback;
        }
    }
}
