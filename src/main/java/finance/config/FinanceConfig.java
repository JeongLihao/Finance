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
    private static final int DEFAULT_BENCHMARK_RATE_BPS_VALUE = 500;
    private static final int MIN_BENCHMARK_RATE_BPS_VALUE = 0;
    private static final int MAX_BENCHMARK_RATE_BPS_VALUE = 5_000;
    private static final int MAX_BONDS_VALUE = 256;
    private static final int MAX_LOANS_VALUE = 256;
    private static final int LOAN_GRACE_DAYS_VALUE = 3;
    private static final int INDEX_BASE_POINTS_VALUE = 1_000;
    private static final int ANNUAL_MC_DAYS_VALUE = 365;
    private static final int MAX_BOND_TERM_DAYS_VALUE = 3_650;
    private static final double MAX_BOND_FINANCING_RATIO_VALUE = 0.80D;
    private static final int MAX_LOAN_TERM_DAYS_VALUE = 3_650;
    private static final int MAX_CONTRACT_RATE_BPS_VALUE = 10_000;
    private static final double MEDIUM_DEFAULT_RATE_VALUE = 5.0D;
    private static final double HIGH_DEFAULT_RATE_VALUE = 20.0D;
    private static final int CREDIT_AAA_SCORE_VALUE = 90, CREDIT_AA_SCORE_VALUE = 80,
            CREDIT_A_SCORE_VALUE = 70, CREDIT_BBB_SCORE_VALUE = 60,
            CREDIT_BB_SCORE_VALUE = 45, CREDIT_B_SCORE_VALUE = 30;
    private static final boolean FUTURES_ENABLED_VALUE = true;
    private static final long FUTURES_CONTRACT_SIZE_VALUE = 10L;
    private static final int FUTURES_INITIAL_MARGIN_BPS_VALUE = 2_000;
    private static final int FUTURES_MAINTENANCE_MARGIN_BPS_VALUE = 1_200;
    private static final int FUTURES_LIQUIDATION_MARGIN_BPS_VALUE = 800;
    private static final long FUTURES_MAX_POSITION_VALUE = 10_000L;
    private static final int FUTURES_MAX_ORDERS_VALUE = 4_096;
    private static final long FUTURES_MIN_TICK_VALUE = 1L;
    private static final int FUTURES_LIQUIDATION_SLIPPAGE_BPS_VALUE = 500;
    private static final long FUTURES_GUARANTEE_FUND_VALUE = 100_000L;
    private static final int FUTURES_SETTLEMENT_WINDOW_VALUE = 3;
    private static final long FUTURES_MIN_SETTLEMENT_VOLUME_VALUE = 10L;
    private static final int FUTURES_MAX_SPOT_DEVIATION_BPS_VALUE = 2_000;
    private static final boolean BANKING_ENABLED_VALUE=true;private static final int DEFAULT_BANK_COUNT_VALUE=3;
    private static final long BANK_INITIAL_CAPITAL_VALUE=5_000_000L,BANK_MIN_RESERVE_VALUE=10_000L,BANK_INSURANCE_LIMIT_VALUE=100_000L;
    private static final int BANK_DEMAND_SPREAD_VALUE=150,BANK_TIME_SPREAD_VALUE=75,BANK_LOAN_SPREAD_VALUE=250,
            BANK_DEMAND_RESERVE_BPS_VALUE=1_000,BANK_TIME_RESERVE_BPS_VALUE=500,BANK_MIN_CAPITAL_BPS_VALUE=800,
            BANK_SINGLE_BORROWER_BPS_VALUE=2_500,BANK_CB_PENALTY_BPS_VALUE=500,BANK_INSURANCE_FEE_BPS_VALUE=5,
            BANK_STRESS_ROUNDS_VALUE=8;

    private static final ForgeConfigSpec.IntValue DEFAULT_DIVIDEND_CYCLE_DAYS;
    private static final ForgeConfigSpec.DoubleValue DEFAULT_DIVIDEND_RATIO;
    private static final ForgeConfigSpec.IntValue BANKRUPTCY_RISK_DAYS;
    private static final ForgeConfigSpec.DoubleValue BANKRUPTCY_CASH_RISK_MULTIPLIER;
    private static final ForgeConfigSpec.DoubleValue MIN_PROPOSAL_PARTICIPATION_RATIO;
    private static final ForgeConfigSpec.IntValue MAX_PRICE_ALERTS_PER_PLAYER;
    private static final ForgeConfigSpec.IntValue MAX_CONDITIONAL_STOCK_ORDERS_PER_PLAYER;
    private static final ForgeConfigSpec.DoubleValue STOCK_MARKET_MAKER_SPREAD;
    private static final ForgeConfigSpec.LongValue IPO_FEE;
    private static final ForgeConfigSpec.IntValue DEFAULT_BENCHMARK_RATE_BPS;
    private static final ForgeConfigSpec.IntValue MAX_BONDS;
    private static final ForgeConfigSpec.IntValue MAX_LOANS;
    private static final ForgeConfigSpec.IntValue LOAN_GRACE_DAYS;
    private static final ForgeConfigSpec.IntValue INDEX_BASE_POINTS;
    private static final ForgeConfigSpec.IntValue ANNUAL_MC_DAYS;
    private static final ForgeConfigSpec.IntValue MAX_BOND_TERM_DAYS;
    private static final ForgeConfigSpec.DoubleValue MAX_BOND_FINANCING_RATIO;
    private static final ForgeConfigSpec.IntValue MAX_LOAN_TERM_DAYS;
    private static final ForgeConfigSpec.IntValue MAX_CONTRACT_RATE_BPS;
    private static final ForgeConfigSpec.DoubleValue MEDIUM_DEFAULT_RATE;
    private static final ForgeConfigSpec.DoubleValue HIGH_DEFAULT_RATE;
    private static final ForgeConfigSpec.IntValue CREDIT_AAA_SCORE, CREDIT_AA_SCORE, CREDIT_A_SCORE,
            CREDIT_BBB_SCORE, CREDIT_BB_SCORE, CREDIT_B_SCORE;
    private static final ForgeConfigSpec.BooleanValue FUTURES_ENABLED;
    private static final ForgeConfigSpec.LongValue FUTURES_CONTRACT_SIZE, FUTURES_MAX_POSITION,
            FUTURES_MIN_TICK, FUTURES_GUARANTEE_FUND, FUTURES_MIN_SETTLEMENT_VOLUME;
    private static final ForgeConfigSpec.IntValue FUTURES_INITIAL_MARGIN_BPS, FUTURES_MAINTENANCE_MARGIN_BPS,
            FUTURES_LIQUIDATION_MARGIN_BPS, FUTURES_MAX_ORDERS, FUTURES_LIQUIDATION_SLIPPAGE_BPS,
            FUTURES_SETTLEMENT_WINDOW, FUTURES_MAX_SPOT_DEVIATION_BPS;
    private static final ForgeConfigSpec.BooleanValue BANKING_ENABLED;private static final ForgeConfigSpec.LongValue BANK_INITIAL_CAPITAL,BANK_MIN_RESERVE,BANK_INSURANCE_LIMIT;
    private static final ForgeConfigSpec.IntValue DEFAULT_BANK_COUNT,BANK_DEMAND_SPREAD,BANK_TIME_SPREAD,BANK_LOAN_SPREAD,BANK_DEMAND_RESERVE_BPS,BANK_TIME_RESERVE_BPS,BANK_MIN_CAPITAL_BPS,BANK_SINGLE_BORROWER_BPS,BANK_CB_PENALTY_BPS,BANK_INSURANCE_FEE_BPS,BANK_STRESS_ROUNDS;

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

        builder.push("banking");
        BANKING_ENABLED=builder.comment("是否启用商业银行的新存款、贷款和同业业务；关闭不删除既有合同。").define("enabled",BANKING_ENABLED_VALUE);
        DEFAULT_BANK_COUNT=builder.comment("新世界创建的系统商业银行数量。").defineInRange("defaultBankCount",DEFAULT_BANK_COUNT_VALUE,2,4);
        BANK_INITIAL_CAPITAL=builder.comment("每家系统银行的明确初始资本与等额央行准备金。").defineInRange("initialCapital",BANK_INITIAL_CAPITAL_VALUE,1L,Long.MAX_VALUE);
        BANK_DEMAND_SPREAD=builder.comment("活期存款相对基准利率的扣减利差，基点。").defineInRange("demandDepositSpreadBasisPoints",BANK_DEMAND_SPREAD_VALUE,0,10_000);
        BANK_TIME_SPREAD=builder.comment("定期存款相对基准利率的扣减利差，基点。").defineInRange("timeDepositSpreadBasisPoints",BANK_TIME_SPREAD_VALUE,0,10_000);
        BANK_LOAN_SPREAD=builder.comment("商业银行贷款基础加点，基点。").defineInRange("loanSpreadBasisPoints",BANK_LOAN_SPREAD_VALUE,0,20_000);
        BANK_DEMAND_RESERVE_BPS=builder.comment("活期存款法定准备金率，基点。").defineInRange("demandReserveBasisPoints",BANK_DEMAND_RESERVE_BPS_VALUE,0,10_000);
        BANK_TIME_RESERVE_BPS=builder.comment("定期存款法定准备金率，基点，不高于活期率。").defineInRange("timeReserveBasisPoints",BANK_TIME_RESERVE_BPS_VALUE,0,10_000);
        BANK_MIN_RESERVE=builder.comment("每家银行最低绝对准备金。").defineInRange("minimumReserve",BANK_MIN_RESERVE_VALUE,0L,Long.MAX_VALUE);
        BANK_MIN_CAPITAL_BPS=builder.comment("正常经营最低资本充足率，基点。").defineInRange("minimumCapitalBasisPoints",BANK_MIN_CAPITAL_BPS_VALUE,1,10_000);
        BANK_SINGLE_BORROWER_BPS=builder.comment("单一公司贷款相对银行权益上限，基点。").defineInRange("singleBorrowerLimitBasisPoints",BANK_SINGLE_BORROWER_BPS_VALUE,1,10_000);
        BANK_CB_PENALTY_BPS=builder.comment("央行最后贷款人相对基准利率的惩罚加点。").defineInRange("centralBankPenaltyBasisPoints",BANK_CB_PENALTY_BPS_VALUE,1,20_000);
        BANK_INSURANCE_FEE_BPS=builder.comment("银行每日按存款余额缴纳的保险费率，基点。").defineInRange("insuranceFeeBasisPoints",BANK_INSURANCE_FEE_BPS_VALUE,0,1_000);
        BANK_INSURANCE_LIMIT=builder.comment("每名客户在每家银行合并存款保险上限。").defineInRange("insuranceLimitPerCustomer",BANK_INSURANCE_LIMIT_VALUE,0L,Long.MAX_VALUE);
        BANK_STRESS_ROUNDS=builder.comment("压力测试同业传染最大轮数。").defineInRange("stressTestMaxRounds",BANK_STRESS_ROUNDS_VALUE,1,32);
        builder.pop();

        builder.push("financialProducts");
        DEFAULT_BENCHMARK_RATE_BPS = builder
                .comment("基准年利率，单位为基点；500 表示 5%。修改后只影响新债券和新贷款。")
                .defineInRange("defaultBenchmarkRateBasisPoints", DEFAULT_BENCHMARK_RATE_BPS_VALUE,
                        MIN_BENCHMARK_RATE_BPS_VALUE, MAX_BENCHMARK_RATE_BPS_VALUE);
        MAX_BONDS = builder
                .comment("单个世界最多保留的公司债券合约数量，超过上限时拒绝新发行。")
                .defineInRange("maxCorporateBonds", MAX_BONDS_VALUE, 1, 10_000);
        MAX_LOANS = builder
                .comment("单个世界最多保留的公司贷款合约数量，超过上限时拒绝新贷款。")
                .defineInRange("maxCompanyLoans", MAX_LOANS_VALUE, 1, 10_000);
        LOAN_GRACE_DAYS = builder
                .comment("公司贷款逾期后进入违约前的宽限期，单位为 MC 天。")
                .defineInRange("loanGraceDays", LOAN_GRACE_DAYS_VALUE, 1, 365);
        INDEX_BASE_POINTS = builder
                .comment("股票、商品和行业指数首次建立时的基点。")
                .defineInRange("indexBasePoints", INDEX_BASE_POINTS_VALUE, 100, 1_000_000);
        ANNUAL_MC_DAYS = builder
                .comment("金融产品年化使用的 MC 天数；债券票息和贷款日息都使用该分母。")
                .defineInRange("annualMcDays", ANNUAL_MC_DAYS_VALUE, 1, 100_000);
        MAX_BOND_TERM_DAYS = builder
                .comment("公司债券允许的最大期限，单位为 MC 天。")
                .defineInRange("maxBondTermDays", MAX_BOND_TERM_DAYS_VALUE, 2, 100_000);
        MAX_BOND_FINANCING_RATIO = builder
                .comment("债券与既有债务合计相对公司资产的绝对上限；信用等级还会进一步收紧。")
                .defineInRange("maxBondFinancingRatio", MAX_BOND_FINANCING_RATIO_VALUE, 0.01D, 10.0D);
        MAX_LOAN_TERM_DAYS = builder
                .comment("公司贷款允许的最大期限，单位为 MC 天。")
                .defineInRange("maxLoanTermDays", MAX_LOAN_TERM_DAYS_VALUE, 2, 100_000);
        MAX_CONTRACT_RATE_BPS = builder
                .comment("新债券和新贷款的最高年化利率，单位为基点。")
                .defineInRange("maxContractRateBasisPoints", MAX_CONTRACT_RATE_BPS_VALUE, 1, 100_000);
        MEDIUM_DEFAULT_RATE = builder
                .comment("风险仪表盘进入中风险的合约违约率百分比。")
                .defineInRange("mediumDefaultRatePercent", MEDIUM_DEFAULT_RATE_VALUE, 0.0D, 100.0D);
        HIGH_DEFAULT_RATE = builder
                .comment("风险仪表盘进入高风险的合约违约率百分比。")
                .defineInRange("highDefaultRatePercent", HIGH_DEFAULT_RATE_VALUE, 0.0D, 100.0D);
        CREDIT_AAA_SCORE = builder.comment("AAA 信用等级最低分。")
                .defineInRange("creditAaaMinimumScore", CREDIT_AAA_SCORE_VALUE, 0, 100);
        CREDIT_AA_SCORE = builder.comment("AA 信用等级最低分。")
                .defineInRange("creditAaMinimumScore", CREDIT_AA_SCORE_VALUE, 0, 100);
        CREDIT_A_SCORE = builder.comment("A 信用等级最低分。")
                .defineInRange("creditAMinimumScore", CREDIT_A_SCORE_VALUE, 0, 100);
        CREDIT_BBB_SCORE = builder.comment("BBB 信用等级最低分。")
                .defineInRange("creditBbbMinimumScore", CREDIT_BBB_SCORE_VALUE, 0, 100);
        CREDIT_BB_SCORE = builder.comment("BB 信用等级最低分。")
                .defineInRange("creditBbMinimumScore", CREDIT_BB_SCORE_VALUE, 0, 100);
        CREDIT_B_SCORE = builder.comment("B 信用等级最低分；更低且未违约时为 CCC。")
                .defineInRange("creditBMinimumScore", CREDIT_B_SCORE_VALUE, 0, 100);
        builder.pop();

        builder.push("futures");
        FUTURES_ENABLED = builder.comment("是否启用商品期货。关闭后拒绝新合约和新订单，已有状态仍可加载。")
                .define("enabled", FUTURES_ENABLED_VALUE);
        FUTURES_CONTRACT_SIZE = builder.comment("每份标准期货合约代表的商品单位数。")
                .defineInRange("contractSize", FUTURES_CONTRACT_SIZE_VALUE, 1L, 1_000_000L);
        FUTURES_INITIAL_MARGIN_BPS = builder.comment("初始保证金率，单位为基点。")
                .defineInRange("initialMarginBasisPoints", FUTURES_INITIAL_MARGIN_BPS_VALUE, 1, 10_000);
        FUTURES_MAINTENANCE_MARGIN_BPS = builder.comment("维持保证金率，必须低于初始保证金率。")
                .defineInRange("maintenanceMarginBasisPoints", FUTURES_MAINTENANCE_MARGIN_BPS_VALUE, 1, 9_999);
        FUTURES_LIQUIDATION_MARGIN_BPS = builder.comment("强平触发保证金率，必须低于维持保证金率。")
                .defineInRange("liquidationMarginBasisPoints", FUTURES_LIQUIDATION_MARGIN_BPS_VALUE, 1, 9_998);
        FUTURES_MAX_POSITION = builder.comment("每名参与者在单一期货合约的最大绝对净持仓。")
                .defineInRange("maxPositionPerContract", FUTURES_MAX_POSITION_VALUE, 1L, 1_000_000_000L);
        FUTURES_MAX_ORDERS = builder.comment("单个世界最多保留的期货活跃订单。")
                .defineInRange("maxOrders", FUTURES_MAX_ORDERS_VALUE, 1, 100_000);
        FUTURES_MIN_TICK = builder.comment("期货报价最小价格变动单位。")
                .defineInRange("minimumPriceTick", FUTURES_MIN_TICK_VALUE, 1L, 1_000_000L);
        FUTURES_LIQUIDATION_SLIPPAGE_BPS = builder.comment("无流动性系统强平最大不利滑点，单位为基点。")
                .defineInRange("liquidationSlippageBasisPoints", FUTURES_LIQUIDATION_SLIPPAGE_BPS_VALUE, 0, 5_000);
        FUTURES_GUARANTEE_FUND = builder.comment("新世界清算保障基金初始余额。")
                .defineInRange("initialGuaranteeFund", FUTURES_GUARANTEE_FUND_VALUE, 0L, Long.MAX_VALUE);
        FUTURES_SETTLEMENT_WINDOW = builder.comment("到期最终结算使用的现货收盘价窗口。")
                .defineInRange("finalSettlementWindowDays", FUTURES_SETTLEMENT_WINDOW_VALUE, 1, 30);
        FUTURES_MIN_SETTLEMENT_VOLUME = builder.comment("采用当日期货收盘价所需的最低成交量。")
                .defineInRange("minimumSettlementVolume", FUTURES_MIN_SETTLEMENT_VOLUME_VALUE, 0L, Long.MAX_VALUE);
        FUTURES_MAX_SPOT_DEVIATION_BPS = builder.comment("每日结算价相对现货参考价允许的最大偏离，单位为基点。")
                .defineInRange("maxSettlementSpotDeviationBasisPoints", FUTURES_MAX_SPOT_DEVIATION_BPS_VALUE, 0, 10_000);
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

    public static int defaultBenchmarkRateBasisPoints() {
        return getInt(DEFAULT_BENCHMARK_RATE_BPS, DEFAULT_BENCHMARK_RATE_BPS_VALUE);
    }

    public static int minBenchmarkRateBasisPoints() { return MIN_BENCHMARK_RATE_BPS_VALUE; }
    public static int maxBenchmarkRateBasisPoints() { return MAX_BENCHMARK_RATE_BPS_VALUE; }
    public static int maxCorporateBonds() { return getInt(MAX_BONDS, MAX_BONDS_VALUE); }
    public static int maxCompanyLoans() { return getInt(MAX_LOANS, MAX_LOANS_VALUE); }
    public static int loanGraceDays() { return getInt(LOAN_GRACE_DAYS, LOAN_GRACE_DAYS_VALUE); }
    public static int indexBasePoints() { return getInt(INDEX_BASE_POINTS, INDEX_BASE_POINTS_VALUE); }
    public static int annualMcDays() { return getInt(ANNUAL_MC_DAYS, ANNUAL_MC_DAYS_VALUE); }
    public static int maxBondTermDays() { return getInt(MAX_BOND_TERM_DAYS, MAX_BOND_TERM_DAYS_VALUE); }
    public static double maxBondFinancingRatio() { return getDouble(MAX_BOND_FINANCING_RATIO, MAX_BOND_FINANCING_RATIO_VALUE); }
    public static int maxLoanTermDays() { return getInt(MAX_LOAN_TERM_DAYS, MAX_LOAN_TERM_DAYS_VALUE); }
    public static int maxContractRateBasisPoints() { return getInt(MAX_CONTRACT_RATE_BPS, MAX_CONTRACT_RATE_BPS_VALUE); }
    public static double mediumDefaultRatePercent() { return getDouble(MEDIUM_DEFAULT_RATE, MEDIUM_DEFAULT_RATE_VALUE); }
    public static double highDefaultRatePercent() { return getDouble(HIGH_DEFAULT_RATE, HIGH_DEFAULT_RATE_VALUE); }
    public static int creditAaaMinimumScore() { return getInt(CREDIT_AAA_SCORE, CREDIT_AAA_SCORE_VALUE); }
    public static int creditAaMinimumScore() { return Math.min(creditAaaMinimumScore(), getInt(CREDIT_AA_SCORE, CREDIT_AA_SCORE_VALUE)); }
    public static int creditAMinimumScore() { return Math.min(creditAaMinimumScore(), getInt(CREDIT_A_SCORE, CREDIT_A_SCORE_VALUE)); }
    public static int creditBbbMinimumScore() { return Math.min(creditAMinimumScore(), getInt(CREDIT_BBB_SCORE, CREDIT_BBB_SCORE_VALUE)); }
    public static int creditBbMinimumScore() { return Math.min(creditBbbMinimumScore(), getInt(CREDIT_BB_SCORE, CREDIT_BB_SCORE_VALUE)); }
    public static int creditBMinimumScore() { return Math.min(creditBbMinimumScore(), getInt(CREDIT_B_SCORE, CREDIT_B_SCORE_VALUE)); }
    public static boolean futuresEnabled() { try { return FUTURES_ENABLED.get(); } catch (IllegalStateException ex) { return FUTURES_ENABLED_VALUE; } }
    public static long futuresContractSize() { return getLong(FUTURES_CONTRACT_SIZE, FUTURES_CONTRACT_SIZE_VALUE); }
    public static int futuresInitialMarginBps() { return Math.max(3, getInt(FUTURES_INITIAL_MARGIN_BPS, FUTURES_INITIAL_MARGIN_BPS_VALUE)); }
    public static int futuresMaintenanceMarginBps() { return Math.min(futuresInitialMarginBps() - 1,
            Math.max(2, getInt(FUTURES_MAINTENANCE_MARGIN_BPS, FUTURES_MAINTENANCE_MARGIN_BPS_VALUE))); }
    public static int futuresLiquidationMarginBps() { return Math.min(futuresMaintenanceMarginBps() - 1,
            Math.max(1, getInt(FUTURES_LIQUIDATION_MARGIN_BPS, FUTURES_LIQUIDATION_MARGIN_BPS_VALUE))); }
    public static long futuresMaxPosition() { return getLong(FUTURES_MAX_POSITION, FUTURES_MAX_POSITION_VALUE); }
    public static int futuresMaxOrders() { return getInt(FUTURES_MAX_ORDERS, FUTURES_MAX_ORDERS_VALUE); }
    public static long futuresMinimumTick() { return getLong(FUTURES_MIN_TICK, FUTURES_MIN_TICK_VALUE); }
    public static int futuresLiquidationSlippageBps() { return getInt(FUTURES_LIQUIDATION_SLIPPAGE_BPS, FUTURES_LIQUIDATION_SLIPPAGE_BPS_VALUE); }
    public static long futuresInitialGuaranteeFund() { return getLong(FUTURES_GUARANTEE_FUND, FUTURES_GUARANTEE_FUND_VALUE); }
    public static int futuresFinalSettlementWindowDays() { return getInt(FUTURES_SETTLEMENT_WINDOW, FUTURES_SETTLEMENT_WINDOW_VALUE); }
    public static long futuresMinimumSettlementVolume() { return getLong(FUTURES_MIN_SETTLEMENT_VOLUME, FUTURES_MIN_SETTLEMENT_VOLUME_VALUE); }
    public static int futuresMaxSpotDeviationBps() { return getInt(FUTURES_MAX_SPOT_DEVIATION_BPS, FUTURES_MAX_SPOT_DEVIATION_BPS_VALUE); }
    public static boolean bankingEnabled(){try{return BANKING_ENABLED.get();}catch(IllegalStateException e){return BANKING_ENABLED_VALUE;}}public static int defaultBankCount(){return getInt(DEFAULT_BANK_COUNT,DEFAULT_BANK_COUNT_VALUE);}public static long bankInitialCapital(){return getLong(BANK_INITIAL_CAPITAL,BANK_INITIAL_CAPITAL_VALUE);}public static int bankDemandSpreadBps(){return getInt(BANK_DEMAND_SPREAD,BANK_DEMAND_SPREAD_VALUE);}public static int bankTimeSpreadBps(){return getInt(BANK_TIME_SPREAD,BANK_TIME_SPREAD_VALUE);}public static int bankLoanSpreadBps(){return getInt(BANK_LOAN_SPREAD,BANK_LOAN_SPREAD_VALUE);}public static int bankDemandReserveBps(){return getInt(BANK_DEMAND_RESERVE_BPS,BANK_DEMAND_RESERVE_BPS_VALUE);}public static int bankTimeReserveBps(){return Math.min(bankDemandReserveBps(),getInt(BANK_TIME_RESERVE_BPS,BANK_TIME_RESERVE_BPS_VALUE));}public static long bankMinimumReserve(){return getLong(BANK_MIN_RESERVE,BANK_MIN_RESERVE_VALUE);}public static int bankMinimumCapitalBps(){return getInt(BANK_MIN_CAPITAL_BPS,BANK_MIN_CAPITAL_BPS_VALUE);}public static int bankSingleBorrowerLimitBps(){return getInt(BANK_SINGLE_BORROWER_BPS,BANK_SINGLE_BORROWER_BPS_VALUE);}public static int bankCentralPenaltyBps(){return getInt(BANK_CB_PENALTY_BPS,BANK_CB_PENALTY_BPS_VALUE);}public static int bankInsuranceFeeBps(){return getInt(BANK_INSURANCE_FEE_BPS,BANK_INSURANCE_FEE_BPS_VALUE);}public static long bankInsuranceLimit(){return getLong(BANK_INSURANCE_LIMIT,BANK_INSURANCE_LIMIT_VALUE);}public static int bankStressMaxRounds(){return getInt(BANK_STRESS_ROUNDS,BANK_STRESS_ROUNDS_VALUE);}

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
