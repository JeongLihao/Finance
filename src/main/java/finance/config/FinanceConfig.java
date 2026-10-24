package finance.config;

import finance.config.ConfigOption.BoolOption;
import finance.config.ConfigOption.DoubleOption;
import finance.config.ConfigOption.IntOption;
import finance.config.ConfigOption.LongOption;
import finance.config.ConfigOption.StringOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraftforge.common.ForgeConfigSpec;

public final class FinanceConfig {

    public static final ForgeConfigSpec COMMON_SPEC;

    private static final DoubleOption DEFAULT_DIVIDEND_RATIO = new DoubleOption(0.40D);
    private static final IntOption DEFAULT_DIVIDEND_CYCLE_DAYS = new IntOption(7);
    private static final IntOption BANKRUPTCY_RISK_DAYS = new IntOption(3);
    private static final DoubleOption BANKRUPTCY_CASH_RISK_MULTIPLIER = new DoubleOption(1.0D);
    // ---- 公司日常开销（每天真实从公司现金付给 NPC 资金池） ----
    private static final LongOption MACHINE_MAINTENANCE_TIER1 = new LongOption(10L);
    private static final LongOption MACHINE_MAINTENANCE_TIER2 = new LongOption(22L);
    private static final LongOption MACHINE_MAINTENANCE_TIER3 = new LongOption(36L);
    private static final IntOption INVENTORY_STORAGE_FEE_BPS = new IntOption(20);
    private static final IntOption STORAGE_LEVEL_DISCOUNT_BPS = new IntOption(1_200);
    private static final IntOption MANAGEMENT_LEVEL_DISCOUNT_BPS = new IntOption(600);
    private static final DoubleOption MIN_PROPOSAL_PARTICIPATION_RATIO = new DoubleOption(0.25D);
    private static final IntOption MAX_PRICE_ALERTS_PER_PLAYER = new IntOption(20);
    private static final IntOption MAX_CONDITIONAL_STOCK_ORDERS_PER_PLAYER = new IntOption(20);
    private static final DoubleOption STOCK_MARKET_MAKER_SPREAD = new DoubleOption(0.02D);
    // ---- 要约收购（takeover） ----
    private static final IntOption TAKEOVER_MIN_HOLDING_BPS = new IntOption(3_000); // 发起要约需事前持股 30%
    private static final IntOption TAKEOVER_SQUEEZE_OUT_BPS = new IntOption(9_000); // 强制私有化线 90%
    private static final DoubleOption TAKEOVER_PRICE_FLOOR_RATIO = new DoubleOption(0.8D); // 要约价下限 = 0.8 × fairValue
    private static final DoubleOption TAKEOVER_PRICE_CAP_RATIO = new DoubleOption(1.5D); // 要约价上限 = 1.5 × fairValue
    private static final IntOption TAKEOVER_MIN_DURATION_DAYS = new IntOption(3); // 要约期下限（MC 天）
    private static final IntOption TAKEOVER_MAX_DURATION_DAYS = new IntOption(14); // 要约期上限（MC 天）
    // ---- 融资融券（margin） ----
    private static final IntOption MARGIN_MAX_LEVERAGE = new IntOption(2); // 最大杠杆倍数 2x（初始保证金率 50%）
    private static final IntOption MARGIN_MAINTENANCE_BPS = new IntOption(13_000); // 维持保证金率 130%
    private static final IntOption MARGIN_WARNING_BPS = new IntOption(15_000); // 预警线 150%
    private static final DoubleOption MARGIN_FINANCE_ANNUAL_RATE = new DoubleOption(0.086D); // 融资年利率 8.6%
    private static final DoubleOption MARGIN_SHORT_ANNUAL_RATE = new DoubleOption(0.106D); // 融券年利率 10.6%
    private static final DoubleOption MARGIN_POOL_RATIO = new DoubleOption(0.05D); // 券池 = 流通股 5%
    private static final DoubleOption MARGIN_PER_PLAYER_BORROW_RATIO = new DoubleOption(0.20D); // 单人借券上限 = 券池 20%
    private static final DoubleOption STOCK_TARGET_INVENTORY_RATIO = new DoubleOption(0.05D);
    private static final DoubleOption STOCK_MAX_INVENTORY_MULTIPLIER = new DoubleOption(2.0D);
    private static final DoubleOption STOCK_MAX_TRADE_FLOAT_RATIO = new DoubleOption(0.01D);
    private static final DoubleOption IPO_MARKET_MAKER_RESERVE_RATIO = new DoubleOption(0.05D);
    // Phase 2 做市商报价
    private static final BoolOption MM_TARGET_PRICE_STRATEGY_ENABLED = new BoolOption(true);
    private static final BoolOption MM_INVENTORY_SKEW_STRATEGY_ENABLED = new BoolOption(true);
    private static final BoolOption MM_VOLATILITY_SPREAD_STRATEGY_ENABLED = new BoolOption(true);
    private static final DoubleOption MM_INVENTORY_SKEW_FACTOR = new DoubleOption(0.5D);
    private static final DoubleOption MM_VOLATILITY_SPREAD_FACTOR = new DoubleOption(0.5D);
    private static final DoubleOption MM_MAX_INVENTORY_SKEW = new DoubleOption(0.10D);
    private static final DoubleOption MM_MAX_VOLATILITY_ADJUST = new DoubleOption(0.10D);
    private static final DoubleOption MM_CIRCUIT_BREAKER_THRESHOLD = new DoubleOption(0.30D);
    private static final DoubleOption MM_TARGET_PRICE_DEVIATION_THRESHOLD = new DoubleOption(0.05D);
    // Phase 4 央行联动
    private static final BoolOption CB_MM_HEALTH_CHECK_ENABLED = new BoolOption(true);
    private static final LongOption CB_MM_EMERGENCY_LOAN_CAP = new LongOption(5_000_000L);
    private static final IntOption CB_MM_LOAN_REPAYMENT_BPS = new IntOption(2_000);
    private static final IntOption FACTORY_SPEED_PERCENT = new IntOption(100);
    private static final LongOption IPO_FEE = new LongOption(5_000L);
    private static final LongOption CAPITAL_PROJECT_GOVERNANCE_THRESHOLD = new LongOption(50_000L);
    private static final IntOption CAPITAL_PROJECT_MAX_DURATION_DAYS = new IntOption(60);
    private static final IntOption DEFAULT_BENCHMARK_RATE_BPS = new IntOption(500);
    private static final int MIN_BENCHMARK_RATE_BPS_VALUE = 0;
    private static final int MAX_BENCHMARK_RATE_BPS_VALUE = 5_000;
    private static final IntOption MAX_BONDS = new IntOption(256);
    private static final LongOption TREASURY_BOND_MIN_AMOUNT = new LongOption(1_000L);
    private static final LongOption TREASURY_BOND_MAX_PER_PLAYER = new LongOption(2_000_000L);
    private static final LongOption TREASURY_BOND_MAX_ISSUE_PER_TERM = new LongOption(10_000_000L);
    private static final LongOption TREASURY_BOND_EARLY_FEE = new LongOption(0L);
    private static final IntOption MAX_LOANS = new IntOption(256);
    private static final IntOption LOAN_GRACE_DAYS = new IntOption(3);
    private static final IntOption INDEX_BASE_POINTS = new IntOption(1_000);
    private static final IntOption ANNUAL_MC_DAYS = new IntOption(365);
    private static final IntOption MAX_BOND_TERM_DAYS = new IntOption(3_650);
    private static final DoubleOption MAX_BOND_FINANCING_RATIO = new DoubleOption(0.80D);
    private static final IntOption MAX_LOAN_TERM_DAYS = new IntOption(3_650);
    private static final IntOption MAX_CONTRACT_RATE_BPS = new IntOption(10_000);
    private static final DoubleOption MEDIUM_DEFAULT_RATE = new DoubleOption(5.0D);
    private static final DoubleOption HIGH_DEFAULT_RATE = new DoubleOption(20.0D);
    private static final IntOption CREDIT_AAA_SCORE = new IntOption(90);
    private static final IntOption CREDIT_AA_SCORE = new IntOption(80);
    private static final IntOption CREDIT_A_SCORE = new IntOption(70);
    private static final IntOption CREDIT_BBB_SCORE = new IntOption(60);
    private static final IntOption CREDIT_BB_SCORE = new IntOption(45);
    private static final IntOption CREDIT_B_SCORE = new IntOption(30);
    private static final BoolOption FUTURES_ENABLED = new BoolOption(true);
    private static final LongOption FUTURES_CONTRACT_SIZE = new LongOption(10L);
    private static final IntOption FUTURES_INITIAL_MARGIN_BPS = new IntOption(2_000);
    private static final IntOption FUTURES_MAINTENANCE_MARGIN_BPS = new IntOption(1_200);
    private static final IntOption FUTURES_LIQUIDATION_MARGIN_BPS = new IntOption(800);
    private static final LongOption FUTURES_MAX_POSITION = new LongOption(10_000L);
    private static final IntOption FUTURES_MAX_ORDERS = new IntOption(4_096);
    private static final LongOption FUTURES_MIN_TICK = new LongOption(1L);
    private static final IntOption FUTURES_LIQUIDATION_SLIPPAGE_BPS = new IntOption(500);
    private static final LongOption FUTURES_GUARANTEE_FUND = new LongOption(100_000L);
    private static final IntOption FUTURES_SETTLEMENT_WINDOW = new IntOption(3);
    private static final LongOption FUTURES_MIN_SETTLEMENT_VOLUME = new LongOption(10L);
    private static final IntOption FUTURES_MAX_SPOT_DEVIATION_BPS = new IntOption(2_000);
    // Phase 3 期货做市商
    private static final DoubleOption FUTURES_MM_SPREAD = new DoubleOption(0.015D);
    private static final DoubleOption FUTURES_MM_NET_POSITION_RATIO = new DoubleOption(0.10D);
    private static final IntOption FUTURES_MM_WASH_TRADE_WINDOW_MINUTES = new IntOption(5);
    private static final IntOption FUTURES_MM_WASH_TRADE_MAX_TRADES = new IntOption(6);
    private static final LongOption FUTURES_MM_WASH_TRADE_MAX_NOTIONAL = new LongOption(50_000L);
    private static final IntOption FUTURES_MM_WASH_TRADE_COOLDOWN_MINUTES = new IntOption(30);
    private static final BoolOption FUTURES_MM_CIRCUIT_BREAKER_ENABLED = new BoolOption(true);
    private static final IntOption FUTURES_MM_CIRCUIT_BREAKER_DEVIATION_BPS = new IntOption(3_000);
    private static final LongOption FUTURES_MM_INITIAL_CAPITAL = new LongOption(500_000L);
    // ---- 单人经济：NPC 投资者 ----
    private static final BoolOption NPC_INVESTORS_ENABLED = new BoolOption(true);
    private static final LongOption NPC_INITIAL_CAPITAL_EACH = new LongOption(50_000L);
    private static final LongOption NPC_DAILY_BUDGET = new LongOption(5_000L);
    private static final LongOption NPC_POOL_RESERVE_FLOOR = new LongOption(1_000_000L);
    private static final IntOption NPC_MAX_HOLDING_PERCENT_EACH = new IntOption(20);
    private static final IntOption NPC_MAX_COMBINED_HOLDING_PERCENT = new IntOption(49);
    private static final IntOption NPC_MAX_FLOAT_PERCENT_PER_DAY = new IntOption(2);
    private static final BoolOption NPC_VOTING_ENABLED = new BoolOption(true);
    private static final IntOption NPC_TENDER_PREMIUM_DIVIDEND = new IntOption(35);
    private static final IntOption NPC_TENDER_PREMIUM_GROWTH = new IntOption(30);
    private static final IntOption NPC_TENDER_PREMIUM_SPECULATOR = new IntOption(10);
    private static final IntOption NPC_TENDER_PREMIUM_VALUE = new IntOption(25);
    // ---- 单人经济：NPC 竞争公司 ----
    private static final BoolOption RIVALS_ENABLED = new BoolOption(true);
    private static final IntOption RIVALS_MAX_COMPANIES = new IntOption(6);
    private static final IntOption RIVALS_SPAWN_COOLDOWN_DAYS = new IntOption(14);
    private static final LongOption RIVALS_SEED_CAPITAL = new LongOption(50_000L);
    private static final BoolOption RIVALS_IPO_ENABLED = new BoolOption(true);
    private static final BoolOption RIVALS_CONTRACTS_ENABLED = new BoolOption(true);
    private static final BoolOption RIVALS_HOSTILE_TAKEOVERS = new BoolOption(false);
    private static final BoolOption RIVALS_ACTIVISM_ENABLED = new BoolOption(false);
    private static final BoolOption CASH_ENABLED = new BoolOption(true);
    // 简单模式金融中心（四页：首页、买卖、我的公司、投资）
    private static final LongOption SIMPLE_STARTER_GRANT = new LongOption(500L);
    private static final LongOption SIMPLE_STOCK_UNLOCK = new LongOption(3_000L);
    private static final LongOption SIMPLE_IPO_MIN_VALUATION = new LongOption(20_000L);
    private static final StringOption SIMPLE_ADVANCED_MODE = new StringOption("player");
    private static final StringOption SIMPLE_EXTRAS = new StringOption("");
    private static final StringOption SIMPLE_DISABLED = new StringOption("");
    private static final BoolOption SIMPLE_WEEKLY_LEADERBOARD = new BoolOption(true);
    private static final LongOption CASH_EMERALD_TO_CASH = new LongOption(50L);
    private static final LongOption CASH_CASH_TO_EMERALD = new LongOption(60L);
    private static final IntOption CASH_EMERALD_IMPACT_BPS = new IntOption(100);
    private static final IntOption CASH_EMERALD_RECOVERY_PERCENT = new IntOption(25);
    private static final DoubleOption INSURANCE_ACCIDENT_CHANCE = new DoubleOption(1.0D);
    private static final BoolOption SETTLEMENT_AUTO_DISCOVER = new BoolOption(true);
    private static final IntOption INSURANCE_ACCIDENT_MAX_LOSS = new IntOption(20);
    // ---- 宏观实体经济 ----
    private static final BoolOption MACRO_ENABLED = new BoolOption(true);
    private static final BoolOption MACRO_LABOR_ENABLED = new BoolOption(true);
    private static final IntOption MACRO_MIN_POPULATION = new IntOption(5);
    private static final IntOption MACRO_MAX_POPULATION = new IntOption(400);
    private static final IntOption MACRO_GROWTH_MAX_BPS = new IntOption(150);
    private static final IntOption MACRO_MIGRATION_MAX_BPS = new IntOption(50);
    private static final IntOption MACRO_MIGRATION_GAP_BPS = new IntOption(500);
    private static final IntOption MACRO_PRICE_STEP_BPS = new IntOption(1000);
    // ---- 利率传导（A2） ----
    private static final DoubleOption MACRO_RATE_DEMAND_SENSITIVITY = new DoubleOption(2.0D);
    private static final DoubleOption MACRO_RATE_STOCK_SENSITIVITY = new DoubleOption(3.0D);
    private static final IntOption MACRO_LABOR_RADIUS = new IntOption(256);
    private static final LongOption MACRO_DEFAULT_WAGE = new LongOption(12L);
    private static final LongOption MACRO_BASE_RESERVATION_WAGE = new LongOption(4L);
    private static final BoolOption MACRO_WORLD_FEEDBACK = new BoolOption(true);
    private static final BoolOption MACRO_NEWS_ENABLED = new BoolOption(true);
    private static final IntOption MACRO_NEWS_INTERVAL_DAYS = new IntOption(7);
    private static final IntOption MACRO_NEWS_EXTRA_GAP_DAYS = new IntOption(3);
    private static final LongOption MACRO_RADAR_COST = new LongOption(200L);
    private static final IntOption MACRO_RADAR_COOLDOWN_DAYS = new IntOption(1);
    // ---- 商队商路 ----
    private static final IntOption CARAVAN_MAX_ROUTES_PER_PLAYER = new IntOption(8);
    private static final IntOption CARAVAN_MAX_QTY_PER_TRIP = new IntOption(1_024);
    private static final LongOption CARAVAN_BLOCKS_PER_DAY = new LongOption(5_000L);
    private static final IntOption CARAVAN_RAID_BASE_BPS = new IntOption(800);
    private static final IntOption CARAVAN_RAID_MIN_BPS = new IntOption(100);
    private static final IntOption CARAVAN_RAID_MAX_BPS = new IntOption(3_000);
    private static final IntOption CARAVAN_RAID_PER_KM_BPS = new IntOption(20);
    private static final IntOption CARAVAN_RAID_PER_KM_CAP_BPS = new IntOption(2_000);
    private static final IntOption CARAVAN_RAID_ALERT_BONUS_BPS = new IntOption(3_000);
    private static final IntOption CARAVAN_GUARD_REDUCE_BPS = new IntOption(2_000);
    private static final LongOption CARAVAN_GUARD_WAGE_1 = new LongOption(500L);
    private static final LongOption CARAVAN_GUARD_WAGE_2 = new LongOption(1_500L);
    private static final LongOption CARAVAN_GUARD_WAGE_3 = new LongOption(4_000L);
    private static final IntOption CARAVAN_RAID_LOSS_RATIO_BPS = new IntOption(3_000);
    private static final IntOption CARAVAN_INSURE_MARGIN_BPS = new IntOption(12_000);
    private static final IntOption CARAVAN_SATURATION_PER_UNIT_BPS = new IntOption(2);
    private static final IntOption CARAVAN_SATURATION_DECAY_BPS = new IntOption(1_500);
    private static final IntOption CARAVAN_SELL_PRICE_CAP_MULTIPLIER = new IntOption(3);
    private static final BoolOption BANKING_ENABLED = new BoolOption(true);
    private static final IntOption DEFAULT_BANK_COUNT = new IntOption(3);
    private static final LongOption BANK_INITIAL_CAPITAL = new LongOption(5_000_000L);
    private static final LongOption BANK_MIN_RESERVE = new LongOption(10_000L);
    private static final LongOption BANK_INSURANCE_LIMIT = new LongOption(100_000L);
    private static final IntOption BANK_DEMAND_SPREAD = new IntOption(150);
    private static final IntOption BANK_TIME_SPREAD = new IntOption(75);
    private static final IntOption BANK_LOAN_SPREAD = new IntOption(250);
    private static final IntOption BANK_DEMAND_RESERVE_BPS = new IntOption(1_000);
    private static final IntOption BANK_TIME_RESERVE_BPS = new IntOption(500);
    private static final IntOption BANK_MIN_CAPITAL_BPS = new IntOption(800);
    private static final IntOption BANK_SINGLE_BORROWER_BPS = new IntOption(2_500);
    private static final IntOption BANK_CB_PENALTY_BPS = new IntOption(500);
    private static final IntOption BANK_INSURANCE_FEE_BPS = new IntOption(5);
    private static final IntOption BANK_STRESS_ROUNDS = new IntOption(8);
    private static final IntOption BANK_REFERENCE_TTL_DAYS = new IntOption(30);
    private static final StringOption BANK_TIME_DEPOSIT_TERMS = new StringOption("7,30,90");
    private static final LongOption BANK_TIME_DEPOSIT_MIN_AMOUNT = new LongOption(1_000L);
    private static final LongOption BANK_TIME_DEPOSIT_MAX_PER_PLAYER = new LongOption(1_000_000L);
    private static final LongOption BANK_TIME_DEPOSIT_EARLY_FEE = new LongOption(0L); // 定期存款
    private static final BoolOption BANK_RUN_ENABLED = new BoolOption(true);
    private static final BoolOption BANK_RUN_AUTO_RESOLVE = new BoolOption(true);
    private static final IntOption BANK_RUN_PANIC_DECAY = new IntOption(6);
    private static final IntOption BANK_RUN_MAX_SHARE_BPS = new IntOption(2_000);
    private static final IntOption BANK_RUN_DAILY_CAP_BPS = new IntOption(1_000);
    private static final IntOption BANK_RUN_CAP_PANIC = new IntOption(60);
    private static final IntOption BANK_RUN_FREEZE_PANIC = new IntOption(70);
    private static final IntOption BANK_RUN_FAIL_PANIC = new IntOption(85);
    private static final IntOption BANK_RUN_NPC_SEED_BPS = new IntOption(4_000);
    private static final IntOption BANK_RUN_NPC_TIME_BPS = new IntOption(3_000);
    private static final IntOption BANK_RUN_NPC_DEFAULT_BPS = new IntOption(800);
    private static final IntOption BANK_RUN_RUMOR_COOLDOWN = new IntOption(3);
    private static final IntOption BANK_RUN_ILLIQUID_DAYS = new IntOption(3);
    private static final DoubleOption BANK_RUN_RUMOR_CHANCE = new DoubleOption(0.25);
    private static final LongOption PLAYER_BANK_CHARTER_FEE = new LongOption(1_000_000L);
    private static final LongOption PLAYER_BANK_MIN_CAPITAL = new LongOption(10_000_000L);
    private static final LongOption PLAYER_BANK_DAILY_FINE = new LongOption(50_000L);
    private static final IntOption PLAYER_BANK_MAX_PER_PLAYER = new IntOption(1);
    private static final IntOption PLAYER_BANK_RELATED_PARTY_LIMIT_BPS = new IntOption(0);
    private static final IntOption PLAYER_BANK_DIVIDEND_LEVERAGE_BPS = new IntOption(800);
    private static final BoolOption MINECRAFT_FIRST_MODE = new BoolOption(true);
    private static final BoolOption REQUIRE_PHYSICAL_TERMINAL = new BoolOption(true);
    private static final BoolOption ENABLE_PORTABLE_LEDGER = new BoolOption(true);
    private static final BoolOption LEGACY_FULL_SCREEN_KEYBIND = new BoolOption(false);
    private static final BoolOption WAREHOUSE_CAPACITY_ENABLED = new BoolOption(true);
    private static final BoolOption CONTRACTS_ENABLED = new BoolOption(true);
    private static final BoolOption ADVANCED_FINANCE_REQUIRES_TERMINAL = new BoolOption(true);
    private static final BoolOption ADMIN_CONSOLE_REQUIRES_PERMISSION = new BoolOption(true);
    private static final DoubleOption TERMINAL_INTERACTION_DISTANCE = new DoubleOption(8.0D);
    private static final BoolOption WORLD_ECONOMY_GLOBAL_BROADCASTS = new BoolOption(true);
    private static final IntOption EARNINGS_QUARTER_DAYS = new IntOption(30);
    private static final IntOption EARNINGS_EXPECTATION_QUARTERS = new IntOption(3);
    private static final IntOption EARNINGS_SURPRISE_CAP_BPS = new IntOption(5_000);
    private static final IntOption EARNINGS_REACTION_BPS = new IntOption(30);
    private static final IntOption EARNINGS_MAX_SHOCK_BPS = new IntOption(2_000);
    private static final IntOption EARNINGS_FV_PASS_THROUGH_BPS = new IntOption(5_000);
    private static final IntOption EARNINGS_HISTORY_KEPT = new IntOption(8);
    private static final BoolOption EARNINGS_EX_DIVIDEND = new BoolOption(true);
    /** 仓库容量、单次存取上限、机器速度倍率按 1～3 档各一项。 */
    private static final IntOption[] WAREHOUSE_CAPACITIES = {
        new IntOption(1_024), new IntOption(4_096), new IntOption(16_384)
    };

    private static final IntOption[] WAREHOUSE_TRANSFER_LIMITS = {
        new IntOption(64), new IntOption(256), new IntOption(1_024)
    };
    private static final IntOption[] FACTORY_THROUGHPUT = {new IntOption(1), new IntOption(2), new IntOption(4)};
    private static final IntOption LOGISTICS_MAX_ACTIVE_PLAYER = new IntOption(8);
    private static final IntOption LOGISTICS_MAX_ACTIVE_COMPANY = new IntOption(32);
    private static final IntOption LOGISTICS_MAX_CARGO_UNITS = new IntOption(1_024);
    private static final IntOption LOGISTICS_DEADLINE_DAYS = new IntOption(14);
    private static final IntOption SETTLEMENT_MAX_ACTIVE_PLAYER = new IntOption(4);
    private static final IntOption SETTLEMENT_MAX_OPEN = new IntOption(8);
    private static final IntOption SETTLEMENT_DEADLINE_DAYS = new IntOption(5);
    private static final IntOption SETTLEMENT_REWARD_BPS = new IntOption(12_000);
    private static final BoolOption EXPLORATION_ENABLED = new BoolOption(true);
    private static final BoolOption EXPLORATION_WORLDGEN_ENABLED = new BoolOption(false);
    private static final IntOption EXPLORATION_COOLDOWN_DAYS = new IntOption(2);
    private static final IntOption EXPLORATION_MAX_DISTANCE = new IntOption(4_096);
    private static final IntOption EXPLORATION_DEADLINE_DAYS = new IntOption(7);
    private static final LongOption EXPLORATION_REWARD = new LongOption(250L);
    private static final IntOption REGIONAL_HISTORY_DAYS = new IntOption(120);
    private static final IntOption COLLATERAL_MAX_ACTIVE = new IntOption(4096);
    private static final IntOption HEDGE_MAX_OBJECTIVES = new IntOption(4096);
    private static final IntOption RISK_DAILY_BATCH = new IntOption(128);
    private static final IntOption COLLATERAL_INITIAL_LTV_BPS = new IntOption(6000);
    private static final IntOption COLLATERAL_MAINTENANCE_LTV_BPS = new IntOption(7500);
    private static final IntOption COLLATERAL_LIQUIDATION_LTV_BPS = new IntOption(9000);
    private static final IntOption VILLAGE_POPULATION = new IntOption(30);
    private static final LongOption VILLAGE_PER_CAPITA_BUDGET = new LongOption(100L);
    private static final IntOption VILLAGE_BUDGET_RESERVE_DAYS = new IntOption(3);
    private static final IntOption VILLAGE_PREMIUM_MIN_BPS = new IntOption(7500);
    private static final IntOption VILLAGE_PREMIUM_MAX_BPS = new IntOption(14000);
    private static final IntOption VILLAGE_FAIR_TOLERANCE_BPS = new IntOption(12000);
    private static final IntOption VILLAGE_GOUGE_TOLERANCE_BPS = new IntOption(15000);
    private static final IntOption VILLAGE_BOYCOTT_FAVOR = new IntOption(-50);
    private static final IntOption VILLAGE_TRUST_FAVOR = new IntOption(50);

    // Phase 2 做市商报价
    // Phase 4 央行联动
    private static final StringOption RESERVE_TARGETS = new StringOption("1000,500,200");
    private static final IntOption RESERVE_RESTOCK_BPS = new IntOption(200);
    private static final IntOption RESERVE_FLOOR_BPS = new IntOption(5_000);
    private static final IntOption RESERVE_FLOOR_DAILY_CAP = new IntOption(256);
    private static final IntOption RESERVE_SURPLUS_DEMAND_BPS = new IntOption(2_500);
    private static final LongOption RESERVE_TIER2_VALUE = new LongOption(50_000L);
    private static final LongOption RESERVE_TIER3_VALUE = new LongOption(200_000L);
    private static final LongOption RESERVE_PLAYER_GOODS_REFERENCE = new LongOption(512L);
    private static final DoubleOption RESERVE_ELASTICITY = new DoubleOption(0.6D);
    private static final StringOption POLICY_RATE_MODE = new StringOption("AUTO");
    private static final IntOption POLICY_NEUTRAL_RATE_BPS = new IntOption(500);
    private static final IntOption POLICY_MEETING_INTERVAL_DAYS = new IntOption(30);
    private static final IntOption POLICY_GUIDANCE_LEAD_DAYS = new IntOption(3);
    private static final IntOption POLICY_MAX_STEP_BPS = new IntOption(100);
    private static final IntOption POLICY_MIN_STEP_BPS = new IntOption(25);
    private static final DoubleOption POLICY_INFLATION_RESPONSE = new DoubleOption(1.5D);
    private static final DoubleOption POLICY_SLACK_RESPONSE = new DoubleOption(0.5D);
    // ---- 中央银行（物价目标、准备金率、公开市场操作、商品平准） ----
    private static final IntOption CB_INFLATION_TARGET_BPS = new IntOption(200);
    private static final IntOption CB_INFLATION_BAND_BPS = new IntOption(100);
    private static final IntOption CB_RESERVE_MIN_BPS = new IntOption(500);
    private static final IntOption CB_RESERVE_MAX_BPS = new IntOption(2_000);
    private static final IntOption CB_RESERVE_STEP_BPS = new IntOption(50);
    private static final IntOption CB_OMO_MAX_DAILY_BPS = new IntOption(200);
    private static final IntOption CB_OMO_EXCESS_TARGET_BPS = new IntOption(500);
    private static final IntOption CB_STABILIZATION_BAND_BPS = new IntOption(5_000);
    private static final IntOption CB_STABILIZATION_MAX_DAILY_BPS = new IntOption(200);
    private static final BoolOption CB_RESERVE_AUTO = new BoolOption(true);
    private static final BoolOption CB_OMO_ENABLED = new BoolOption(true);
    private static final BoolOption CB_STABILIZATION_ENABLED = new BoolOption(true);
    private static final DoubleOption CB_OMO_GAP_RESPONSE = new DoubleOption(1.0D);
    // Phase 3 期货做市商

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.push("gameplay");
        MINECRAFT_FIRST_MODE.bind(builder.comment("默认使用 Minecraft-first 入口；金融合同与日结不会因此停止。")
                .define("minecraftFirstMode", MINECRAFT_FIRST_MODE.def));
        REQUIRE_PHYSICAL_TERMINAL.bind(builder.comment("市场、仓库、银行、公司和监管界面是否要求服务端验证实体终端。")
                .define("requirePhysicalTerminal", REQUIRE_PHYSICAL_TERMINAL.def));
        ENABLE_PORTABLE_LEDGER.bind(
                builder.comment("是否允许随身金融账本入口。").define("enablePortableLedger", ENABLE_PORTABLE_LEDGER.def));
        LEGACY_FULL_SCREEN_KEYBIND.bind(builder.comment("是否允许旧快捷键直接打开完整金融界面；默认关闭以引导使用世界终端。")
                .define("legacyFullScreenKeybind", LEGACY_FULL_SCREEN_KEYBIND.def));
        WAREHOUSE_CAPACITY_ENABLED.bind(builder.comment("是否对新的世界仓库启用容量限制；历史托管资产不会被删除。")
                .define("warehouseCapacityEnabled", WAREHOUSE_CAPACITY_ENABLED.def));
        CONTRACTS_ENABLED.bind(
                builder.comment("是否允许创建新的世界合同；关闭不会吞掉既有托管或到期结算。").define("contractsEnabled", CONTRACTS_ENABLED.def));
        ADVANCED_FINANCE_REQUIRES_TERMINAL.bind(builder.comment("股票、债券、基金、期货等高级金融界面是否要求证券终端。")
                .define("advancedFinanceRequiresTerminal", ADVANCED_FINANCE_REQUIRES_TERMINAL.def));
        ADMIN_CONSOLE_REQUIRES_PERMISSION.bind(builder.comment("央行控制台是否要求服务端 2 级管理员权限。")
                .define("adminConsoleRequiresPermission", ADMIN_CONSOLE_REQUIRES_PERMISSION.def));
        TERMINAL_INTERACTION_DISTANCE.bind(builder.comment("玩家可持续使用金融终端的最大距离（方块）。超出距离会关闭菜单。")
                .defineInRange("terminalInteractionDistance", TERMINAL_INTERACTION_DISTANCE.def, 1.0D, 32.0D));
        WORLD_ECONOMY_GLOBAL_BROADCASTS.bind(builder.comment("是否允许真正重大的经济事件向全服广播；局部与参与者通知不受此项影响。")
                .define("worldEconomyGlobalBroadcasts", WORLD_ECONOMY_GLOBAL_BROADCASTS.def));
        builder.push("facilityTiers");
        for (int tier = 1; tier <= 3; tier++) {
            int index = tier - 1;
            IntOption capacity = WAREHOUSE_CAPACITIES[index], transfer = WAREHOUSE_TRANSFER_LIMITS[index];
            IntOption throughput = FACTORY_THROUGHPUT[index];
            capacity.bind(builder.comment("仓库等级 " + tier + " 的总容量。重载不会删除超额资产。")
                    .defineInRange("warehouseTier" + tier + "Capacity", capacity.def, 64, 1_000_000));
            transfer.bind(builder.comment("仓库等级 " + tier + " 的单次存取上限。")
                    .defineInRange("warehouseTier" + tier + "TransferLimit", transfer.def, 1, 1_000_000));
            throughput.bind(builder.comment("机器 " + tier + " 档满员时的速度倍率（1 = 每 tick 推进一步）。")
                    .defineInRange("factoryTier" + tier + "Throughput", throughput.def, 1, 16));
        }
        builder.pop();
        builder.push("logistics");
        LOGISTICS_MAX_ACTIVE_PLAYER.bind(builder.comment("每名玩家最多关联的未结束运单。")
                .defineInRange("maxActivePerPlayer", LOGISTICS_MAX_ACTIVE_PLAYER.def, 1, 64));
        LOGISTICS_MAX_ACTIVE_COMPANY.bind(builder.comment("每家公司最多保留的未结束运单。")
                .defineInRange("maxActivePerCompany", LOGISTICS_MAX_ACTIVE_COMPANY.def, 1, 256));
        LOGISTICS_MAX_CARGO_UNITS.bind(builder.comment("单个密封货箱可承载的最大商品单位；仍受源仓库单次吞吐限制。")
                .defineInRange("maxCargoUnits", LOGISTICS_MAX_CARGO_UNITS.def, 1, 16_384));
        LOGISTICS_DEADLINE_DAYS.bind(builder.comment("普通运单默认提示期限，单位为 MC 天；超期不会删除运输托管。")
                .defineInRange("defaultDeadlineDays", LOGISTICS_DEADLINE_DAYS.def, 1, 365));
        builder.pop();
        builder.push("settlements");
        SETTLEMENT_MAX_ACTIVE_PLAYER.bind(builder.comment("每名玩家最多同时接受的聚落需求。")
                .defineInRange("maxActivePerPlayer", SETTLEMENT_MAX_ACTIVE_PLAYER.def, 1, 32));
        SETTLEMENT_MAX_OPEN.bind(builder.comment("每个聚落最多保留的未结束公共需求。")
                .defineInRange("maxOpenPerSettlement", SETTLEMENT_MAX_OPEN.def, 1, 16));
        SETTLEMENT_DEADLINE_DAYS.bind(builder.comment("本地需求默认期限，单位为 MC 天。")
                .defineInRange("demandDeadlineDays", SETTLEMENT_DEADLINE_DAYS.def, 1, 30));
        SETTLEMENT_REWARD_BPS.bind(builder.comment("本地需求相对可信市场采购价的奖励比例，10000=100%。")
                .defineInRange("rewardBasisPoints", SETTLEMENT_REWARD_BPS.def, 5_000, 20_000));
        SETTLEMENT_AUTO_DISCOVER.bind(
                builder.comment("玩家走进原版村庄时自动登记为聚落，村民集市、人口、工资和本地物价随即运转；之后在村里放贸易站会接管这个聚落。关闭后只有放了贸易站的村庄才算聚落。")
                        .define("autoDiscoverVillages", SETTLEMENT_AUTO_DISCOVER.def));
        builder.pop();
        builder.push("exploration");
        EXPLORATION_ENABLED.bind(
                builder.comment("是否允许玩家通过调查板创建有真实预算托管的探索任务。").define("enabled", EXPLORATION_ENABLED.def));
        EXPLORATION_WORLDGEN_ENABLED.bind(
                builder.comment("实验性遗迹世界生成总开关。首版默认关闭；模板资源仍可由数据包或管理员使用。修改后需完整重启服务器，/reload 不会重生成旧区块。")
                        .define("worldgenEnabled", EXPLORATION_WORLDGEN_ENABLED.def));
        EXPLORATION_COOLDOWN_DAYS.bind(builder.comment("同一玩家申请新调查任务的冷却，单位为 MC 天。")
                .defineInRange("requestCooldownDays", EXPLORATION_COOLDOWN_DAYS.def, 0, 30));
        EXPLORATION_MAX_DISTANCE.bind(builder.comment("调查板只会在此半径内选择已登记目标；不会加载或扫描新区块。")
                .defineInRange("maximumTargetDistance", EXPLORATION_MAX_DISTANCE.def, 128, 16_384));
        EXPLORATION_DEADLINE_DAYS.bind(builder.comment("调查任务有效期，单位为 MC 天。")
                .defineInRange("deadlineDays", EXPLORATION_DEADLINE_DAYS.def, 1, 30));
        EXPLORATION_REWARD.bind(builder.comment("每次调查完成奖励；申请时从 NPC 市场账户转入独立托管。")
                .defineInRange("reward", EXPLORATION_REWARD.def, 1L, 100_000L));
        builder.pop();
        builder.push("regionalRiskFinance");
        REGIONAL_HISTORY_DAYS.bind(builder.comment("区域商品指标保留天数。缩短后会在下一次日结逐步裁剪；修改配置后建议重启服务器。")
                .defineInRange("regionalHistoryDays", REGIONAL_HISTORY_DAYS.def, 7, 120));
        COLLATERAL_MAX_ACTIVE.bind(builder.comment("世界中库存质押记录的软上限；硬上限仍为 4096，降低上限不会删除既有质押。")
                .defineInRange("maxCollateralAgreements", COLLATERAL_MAX_ACTIVE.def, 1, 4096));
        HEDGE_MAX_OBJECTIVES.bind(builder.comment("世界中公司经营对冲目标的软上限；硬上限仍为 4096。")
                .defineInRange("maxHedgeObjectives", HEDGE_MAX_OBJECTIVES.def, 1, 4096));
        RISK_DAILY_BATCH.bind(builder.comment("每个 MC 日最多重估的质押协议数量；采用轮转游标，不会永久遗漏后排协议。")
                .defineInRange("dailyCollateralBatch", RISK_DAILY_BATCH.def, 1, 512));
        COLLATERAL_INITIAL_LTV_BPS.bind(builder.comment("新库存质押贷款的初始 LTV，基点。")
                .defineInRange("collateralInitialLtvBasisPoints", COLLATERAL_INITIAL_LTV_BPS.def, 1000, 6000));
        COLLATERAL_MAINTENANCE_LTV_BPS.bind(builder.comment("库存质押触发追保的 LTV，基点；运行时不低于初始 LTV。")
                .defineInRange("collateralMaintenanceLtvBasisPoints", COLLATERAL_MAINTENANCE_LTV_BPS.def, 2000, 9500));
        COLLATERAL_LIQUIDATION_LTV_BPS.bind(builder.comment("追保至少一个完整 MC 日后允许清算的 LTV，基点；运行时不低于维持线。")
                .defineInRange("collateralLiquidationLtvBasisPoints", COLLATERAL_LIQUIDATION_LTV_BPS.def, 3000, 10000));
        builder.pop();
        builder.pop();

        builder.push("company");
        DEFAULT_DIVIDEND_RATIO.bind(builder.comment("默认公司分红比例；没有公司级策略时使用。0.40 = 40%。")
                .defineInRange("defaultDividendRatio", DEFAULT_DIVIDEND_RATIO.def, 0.0D, 1.0D));
        DEFAULT_DIVIDEND_CYCLE_DAYS.bind(builder.comment("默认公司分红周期，单位为 MC 天。")
                .defineInRange("defaultDividendCycleDays", DEFAULT_DIVIDEND_CYCLE_DAYS.def, 1, 365));
        BANKRUPTCY_RISK_DAYS.bind(builder.comment("上市公司现金低于安全线后，持续多少个 MC 天触发破产。")
                .defineInRange("bankruptcyRiskDays", BANKRUPTCY_RISK_DAYS.def, 1, 365));
        BANKRUPTCY_CASH_RISK_MULTIPLIER.bind(builder.comment("破产现金安全线倍率。安全线 = 最近 7 天日常开销（工资 + 维护 + 库存持有）的日均 × 此倍率。")
                .defineInRange("bankruptcyCashRiskMultiplier", BANKRUPTCY_CASH_RISK_MULTIPLIER.def, 0.0D, 100.0D));
        MACHINE_MAINTENANCE_TIER1.bind(builder.comment("每台 1 档机器每天的维护费，从公司现金付给 NPC 资金池。NPC 公司按行业产量折算成 1 档机器台数收取。")
                .defineInRange("machineMaintenanceTier1", MACHINE_MAINTENANCE_TIER1.def, 0L, 1_000_000L));
        MACHINE_MAINTENANCE_TIER2.bind(builder.comment("每台 2 档机器每天的维护费。")
                .defineInRange("machineMaintenanceTier2", MACHINE_MAINTENANCE_TIER2.def, 0L, 1_000_000L));
        MACHINE_MAINTENANCE_TIER3.bind(builder.comment("每台 3 档机器每天的维护费。")
                .defineInRange("machineMaintenanceTier3", MACHINE_MAINTENANCE_TIER3.def, 0L, 1_000_000L));
        INVENTORY_STORAGE_FEE_BPS.bind(builder.comment("每天的仓储费占库存市值的比例，单位为基点；20 表示 0.2%。库存另按央行基准利率 ÷ 365 付持有利息。")
                .defineInRange("inventoryStorageFeeBasisPoints", INVENTORY_STORAGE_FEE_BPS.def, 0, 10_000));
        STORAGE_LEVEL_DISCOUNT_BPS.bind(builder.comment("仓储每升一级，仓储费打的折扣，单位为基点；1200 表示每级便宜 12%。")
                .defineInRange("storageLevelDiscountBasisPoints", STORAGE_LEVEL_DISCOUNT_BPS.def, 0, 2_000));
        MANAGEMENT_LEVEL_DISCOUNT_BPS.bind(builder.comment("管理每升一级，机器维护费打的折扣，单位为基点；600 表示每级便宜 6%。")
                .defineInRange("managementLevelDiscountBasisPoints", MANAGEMENT_LEVEL_DISCOUNT_BPS.def, 0, 2_000));
        IPO_FEE.bind(builder.comment("公司发起 IPO 时向所有者收取的费用；下限钳制 5000，服主配 0 会让小号每号净赚 5000（P0-1）。")
                .defineInRange("ipoFee", IPO_FEE.def, 5_000L, Long.MAX_VALUE));
        CAPITAL_PROJECT_GOVERNANCE_THRESHOLD.bind(builder.comment("资本项目预算达到或超过该金额时必须显式授权；上市公司走股东提案，非上市公司由所有者确认。")
                .defineInRange(
                        "capitalProjectGovernanceThreshold",
                        CAPITAL_PROJECT_GOVERNANCE_THRESHOLD.def,
                        0L,
                        Long.MAX_VALUE));
        CAPITAL_PROJECT_MAX_DURATION_DAYS.bind(builder.comment("资本项目自创建到截止的最长 MC 天数。")
                .defineInRange("capitalProjectMaxDurationDays", CAPITAL_PROJECT_MAX_DURATION_DAYS.def, 7, 365));
        builder.pop();

        builder.push("banking");
        BANKING_ENABLED.bind(builder.comment("是否启用商业银行的新存款、贷款和同业业务；关闭不删除既有合同。").define("enabled", BANKING_ENABLED.def));
        DEFAULT_BANK_COUNT.bind(
                builder.comment("新世界创建的系统商业银行数量。").defineInRange("defaultBankCount", DEFAULT_BANK_COUNT.def, 2, 4));
        BANK_INITIAL_CAPITAL.bind(builder.comment("每家系统银行的明确初始资本与等额央行准备金。")
                .defineInRange("initialCapital", BANK_INITIAL_CAPITAL.def, 1L, Long.MAX_VALUE));
        BANK_DEMAND_SPREAD.bind(builder.comment("活期存款相对基准利率的扣减利差，基点。")
                .defineInRange("demandDepositSpreadBasisPoints", BANK_DEMAND_SPREAD.def, 0, 10_000));
        BANK_TIME_SPREAD.bind(builder.comment("定期存款相对基准利率的扣减利差，基点。")
                .defineInRange("timeDepositSpreadBasisPoints", BANK_TIME_SPREAD.def, 0, 10_000));
        BANK_LOAN_SPREAD.bind(builder.comment("商业银行贷款基础加点，基点。")
                .defineInRange("loanSpreadBasisPoints", BANK_LOAN_SPREAD.def, 0, 20_000));
        BANK_DEMAND_RESERVE_BPS.bind(builder.comment("活期存款法定准备金率，基点。")
                .defineInRange("demandReserveBasisPoints", BANK_DEMAND_RESERVE_BPS.def, 0, 10_000));
        BANK_TIME_RESERVE_BPS.bind(builder.comment("定期存款法定准备金率，基点，不高于活期率。")
                .defineInRange("timeReserveBasisPoints", BANK_TIME_RESERVE_BPS.def, 0, 10_000));
        BANK_MIN_RESERVE.bind(builder.comment("每家银行最低绝对准备金。")
                .defineInRange("minimumReserve", BANK_MIN_RESERVE.def, 0L, Long.MAX_VALUE));
        BANK_MIN_CAPITAL_BPS.bind(builder.comment("正常经营最低资本充足率，基点。")
                .defineInRange("minimumCapitalBasisPoints", BANK_MIN_CAPITAL_BPS.def, 1, 10_000));
        BANK_SINGLE_BORROWER_BPS.bind(builder.comment("单一公司贷款相对银行权益上限，基点。")
                .defineInRange("singleBorrowerLimitBasisPoints", BANK_SINGLE_BORROWER_BPS.def, 1, 10_000));
        BANK_CB_PENALTY_BPS.bind(builder.comment("央行最后贷款人相对基准利率的惩罚加点。")
                .defineInRange("centralBankPenaltyBasisPoints", BANK_CB_PENALTY_BPS.def, 1, 20_000));
        BANK_INSURANCE_FEE_BPS.bind(builder.comment("银行每日按存款余额缴纳的保险费率，基点。")
                .defineInRange("insuranceFeeBasisPoints", BANK_INSURANCE_FEE_BPS.def, 0, 1_000));
        BANK_INSURANCE_LIMIT.bind(builder.comment("每名客户在每家银行合并存款保险上限。")
                .defineInRange("insuranceLimitPerCustomer", BANK_INSURANCE_LIMIT.def, 0L, Long.MAX_VALUE));
        BANK_STRESS_ROUNDS.bind(
                builder.comment("压力测试同业传染最大轮数。").defineInRange("stressTestMaxRounds", BANK_STRESS_ROUNDS.def, 1, 32));
        BANK_REFERENCE_TTL_DAYS.bind(builder.comment("账本幂等引用保留天数（TTL），过期自动清理；须大于任何合法重试窗口。")
                .defineInRange("referenceTtlDays", BANK_REFERENCE_TTL_DAYS.def, 7, 365));
        BANK_TIME_DEPOSIT_TERMS.bind(builder.comment("定期存款 定期存款允许的期限档，MC天，逗号分隔。")
                .define("timeDepositTerms", BANK_TIME_DEPOSIT_TERMS.def)); // 定期存款
        BANK_TIME_DEPOSIT_MIN_AMOUNT.bind(builder.comment("定期存款 定期存款起存金额。")
                .defineInRange("timeDepositMinAmount", BANK_TIME_DEPOSIT_MIN_AMOUNT.def, 0L, Long.MAX_VALUE)); // 定期存款
        BANK_TIME_DEPOSIT_MAX_PER_PLAYER.bind(builder.comment("定期存款 单人跨所有银行ACTIVE定期存单本金合计上限。")
                .defineInRange(
                        "timeDepositMaxPerPlayer", BANK_TIME_DEPOSIT_MAX_PER_PLAYER.def, 1L, Long.MAX_VALUE)); // 定期存款
        BANK_TIME_DEPOSIT_EARLY_FEE.bind(builder.comment("定期存款 提前支取手续费，从返还本金中扣除，记手续费收入。")
                .defineInRange("timeDepositEarlyFee", BANK_TIME_DEPOSIT_EARLY_FEE.def, 0L, Long.MAX_VALUE)); // 定期存款
        BANK_RUN_ENABLED.bind(
                builder.comment("是否启用银行挤兑玩法（恐慌度→挤兑动态→处置）；关闭则恐慌冻结、不再产生挤兑。").define("runEnabled", BANK_RUN_ENABLED.def));
        BANK_RUN_AUTO_RESOLVE.bind(builder.comment("银行进入处置状态后是否在日终自动触发桥接银行处置；关闭则只走管理手动处置。")
                .define("runAutoResolve", BANK_RUN_AUTO_RESOLVE.def));
        BANK_RUN_PANIC_DECAY.bind(builder.comment("无压力信号时恐慌度每日衰减。")
                .defineInRange("runPanicDecayPerDay", BANK_RUN_PANIC_DECAY.def, 0, 100));
        BANK_RUN_MAX_SHARE_BPS.bind(builder.comment("恐慌度100时NPC零售池单日最大流出比例，基点。")
                .defineInRange("runMaxDailyShareBasisPoints", BANK_RUN_MAX_SHARE_BPS.def, 0, 10_000));
        BANK_RUN_DAILY_CAP_BPS.bind(builder.comment("挤兑档位下全行单日提现预算占存款比例，基点。")
                .defineInRange("runDailyCapBasisPoints", BANK_RUN_DAILY_CAP_BPS.def, 1, 10_000));
        BANK_RUN_CAP_PANIC.bind(
                builder.comment("启动排队限提的恐慌度阈值。").defineInRange("runCapPanicThreshold", BANK_RUN_CAP_PANIC.def, 0, 100));
        BANK_RUN_FREEZE_PANIC.bind(builder.comment("同业市场冻结对该银行放贷的恐慌度阈值。")
                .defineInRange("runFreezePanicThreshold", BANK_RUN_FREEZE_PANIC.def, 0, 100));
        BANK_RUN_FAIL_PANIC.bind(builder.comment("非流动性死亡判定的恐慌度阈值。")
                .defineInRange("runFailPanicThreshold", BANK_RUN_FAIL_PANIC.def, 0, 100));
        BANK_RUN_NPC_SEED_BPS.bind(builder.comment("NPC零售池播种规模占银行初始资本比例，基点。")
                .defineInRange("runNpcSeedBasisPoints", BANK_RUN_NPC_SEED_BPS.def, 0, 10_000));
        BANK_RUN_NPC_TIME_BPS.bind(builder.comment("NPC零售池中定期占比，基点。")
                .defineInRange("runNpcTimeShareBasisPoints", BANK_RUN_NPC_TIME_BPS.def, 0, 10_000));
        BANK_RUN_NPC_DEFAULT_BPS.bind(builder.comment("恐慌度100时NPC贷款单日违约率上限，基点。")
                .defineInRange("runNpcDefaultBasisPoints", BANK_RUN_NPC_DEFAULT_BPS.def, 0, 10_000));
        BANK_RUN_RUMOR_COOLDOWN.bind(builder.comment("同一银行两次谣言事件最小间隔天数。")
                .defineInRange("runRumorCooldownDays", BANK_RUN_RUMOR_COOLDOWN.def, 0, 30));
        BANK_RUN_ILLIQUID_DAYS.bind(
                builder.comment("非流动性死亡所需连续天数。").defineInRange("runIlliquidDays", BANK_RUN_ILLIQUID_DAYS.def, 1, 30));
        BANK_RUN_RUMOR_CHANCE.bind(builder.comment("谣言事件触发概率（0~1）；仅在真实风险存在时掷骰。")
                .defineInRange("runRumorChance", BANK_RUN_RUMOR_CHANCE.def, 0.0, 1.0));
        builder.pop();

        builder.push("playerBank");
        PLAYER_BANK_CHARTER_FEE.bind(builder.comment("玩家申请银行牌照费用（一次性，从钱包扣除并销毁）。")
                .defineInRange("charterFee", PLAYER_BANK_CHARTER_FEE.def, 0L, Long.MAX_VALUE));
        PLAYER_BANK_MIN_CAPITAL.bind(builder.comment("玩家银行最低实缴资本金。")
                .defineInRange("minCapital", PLAYER_BANK_MIN_CAPITAL.def, 1L, Long.MAX_VALUE));
        PLAYER_BANK_MAX_PER_PLAYER.bind(
                builder.comment("每名玩家最多可开设的银行数量。").defineInRange("maxPerPlayer", PLAYER_BANK_MAX_PER_PLAYER.def, 1, 8));
        PLAYER_BANK_RELATED_PARTY_LIMIT_BPS.bind(builder.comment("玩家银行向所有者关联公司贷款上限（基点 / 银行权益）；0 为硬拒绝。")
                .defineInRange("relatedPartyLimitBasisPoints", PLAYER_BANK_RELATED_PARTY_LIMIT_BPS.def, 0, 10_000));
        PLAYER_BANK_DAILY_FINE.bind(builder.comment("玩家银行处于 RESTRICTED 状态时的每日监管罚金。")
                .defineInRange("restrictedDailyFine", PLAYER_BANK_DAILY_FINE.def, 0L, Long.MAX_VALUE));
        PLAYER_BANK_DIVIDEND_LEVERAGE_BPS.bind(builder.comment("玩家银行分红后权益不得低于存款总额的比例（基点，默认 800=8%）；防范分红掏空后破产转嫁保险基金。")
                .defineInRange("dividendLeverageBasisPoints", PLAYER_BANK_DIVIDEND_LEVERAGE_BPS.def, 0, 10_000));
        builder.pop();

        builder.push("centralBank");
        CB_INFLATION_TARGET_BPS.bind(builder.comment("央行物价目标：年化通胀率（基点），200 = 2%。议息按实际通胀与目标的差距调利率。")
                .defineInRange("inflationTargetBps", CB_INFLATION_TARGET_BPS.def, -1000, 2000));
        CB_INFLATION_BAND_BPS.bind(builder.comment("物价目标的容忍区间（基点）：通胀在目标 ± 该值以内视为物价稳定。")
                .defineInRange("inflationBandBps", CB_INFLATION_BAND_BPS.def, 0, 2000));
        CB_RESERVE_AUTO.bind(
                builder.comment("央行是否在议息日自动调整存款准备金率（MANUAL 利率模式下不调）。").define("reserveRatioAuto", CB_RESERVE_AUTO.def));
        CB_RESERVE_MIN_BPS.bind(builder.comment("活期存款准备金率下限（基点）；定期为活期的一半。")
                .defineInRange("reserveRatioMinBps", CB_RESERVE_MIN_BPS.def, 0, 10_000));
        CB_RESERVE_MAX_BPS.bind(builder.comment("活期存款准备金率上限（基点）。")
                .defineInRange("reserveRatioMaxBps", CB_RESERVE_MAX_BPS.def, 0, 10_000));
        CB_RESERVE_STEP_BPS.bind(builder.comment("每次议息调整准备金率的幅度（基点）。")
                .defineInRange("reserveRatioStepBps", CB_RESERVE_STEP_BPS.def, 1, 1_000));
        CB_OMO_ENABLED.bind(builder.comment("公开市场操作：央行每天向商业银行卖出或买回央行票据，把超额准备金调向目标。")
                .define("openMarketEnabled", CB_OMO_ENABLED.def));
        CB_OMO_MAX_DAILY_BPS.bind(builder.comment("单家银行每天公开市场操作上限，占其存款的基点。")
                .defineInRange("openMarketMaxDailyBps", CB_OMO_MAX_DAILY_BPS.def, 0, 10_000));
        CB_OMO_EXCESS_TARGET_BPS.bind(builder.comment("物价在目标时，商业银行超额准备金的目标（占存款的基点）。")
                .defineInRange("openMarketExcessTargetBps", CB_OMO_EXCESS_TARGET_BPS.def, 0, 10_000));
        CB_OMO_GAP_RESPONSE.bind(builder.comment("通胀每高出目标 1 个基点，超额准备金目标下调多少基点（负缺口则上调）。")
                .defineInRange("openMarketGapResponse", CB_OMO_GAP_RESPONSE.def, 0.0D, 20.0D));
        CB_STABILIZATION_ENABLED.bind(builder.comment("商品平准：消费品价格偏离基准价超过区间时，央行用战略储备吞吐商品稳定物价。")
                .define("stabilizationEnabled", CB_STABILIZATION_ENABLED.def));
        CB_STABILIZATION_BAND_BPS.bind(builder.comment("商品平准的价格区间（基点）：中间价偏离基准价超过该比例才出手。")
                .defineInRange("stabilizationBandBps", CB_STABILIZATION_BAND_BPS.def, 100, 10_000));
        CB_STABILIZATION_MAX_DAILY_BPS.bind(builder.comment("每种商品每天平准数量上限，占市场参考库存的基点。")
                .defineInRange("stabilizationMaxDailyBps", CB_STABILIZATION_MAX_DAILY_BPS.def, 0, 10_000));
        builder.pop();

        builder.push("financialProducts");
        DEFAULT_BENCHMARK_RATE_BPS.bind(builder.comment("基准年利率，单位为基点；500 表示 5%。修改后只影响新债券和新贷款。")
                .defineInRange(
                        "defaultBenchmarkRateBasisPoints",
                        DEFAULT_BENCHMARK_RATE_BPS.def,
                        MIN_BENCHMARK_RATE_BPS_VALUE,
                        MAX_BENCHMARK_RATE_BPS_VALUE));
        POLICY_RATE_MODE.bind(builder.comment("基准利率政策模式：AUTO=议息日按规则自动调息；ADVISORY=规则只给建议，需管理员确认；MANUAL=纯手动。")
                .define("policyRateMode", POLICY_RATE_MODE.def));
        POLICY_NEUTRAL_RATE_BPS.bind(builder.comment("政策规则中的中性利率（基点），经济处于均衡时利率向其回归。")
                .defineInRange(
                        "policyNeutralRateBps",
                        POLICY_NEUTRAL_RATE_BPS.def,
                        MIN_BENCHMARK_RATE_BPS_VALUE,
                        MAX_BENCHMARK_RATE_BPS_VALUE));
        POLICY_MEETING_INTERVAL_DAYS.bind(builder.comment("议息周期，单位为 MC 天。")
                .defineInRange("policyMeetingIntervalDays", POLICY_MEETING_INTERVAL_DAYS.def, 1, 3650));
        POLICY_GUIDANCE_LEAD_DAYS.bind(builder.comment("议息日前瞻指引提前天数（只给方向，不承诺幅度）。")
                .defineInRange("policyGuidanceLeadDays", POLICY_GUIDANCE_LEAD_DAYS.def, 0, 30));
        POLICY_MAX_STEP_BPS.bind(builder.comment("单次议息最大调整幅度（基点），防规则抽风。")
                .defineInRange("policyMaxStepBps", POLICY_MAX_STEP_BPS.def, 1, 2000));
        POLICY_MIN_STEP_BPS.bind(builder.comment("小于此幅度的建议视为噪音，不调息。")
                .defineInRange("policyMinStepBps", POLICY_MIN_STEP_BPS.def, 1, 500));
        POLICY_INFLATION_RESPONSE.bind(builder.comment("通胀缺口反应系数（类 Taylor 规则，经典值 1.5）。")
                .defineInRange("policyInflationResponse", POLICY_INFLATION_RESPONSE.def, 0.0D, 5.0D));
        POLICY_SLACK_RESPONSE.bind(builder.comment("衰退指标反应系数。")
                .defineInRange("policySlackResponse", POLICY_SLACK_RESPONSE.def, 0.0D, 5.0D));
        MAX_BONDS.bind(builder.comment("单个世界最多保留的公司债券合约数量，超过上限时拒绝新发行。")
                .defineInRange("maxCorporateBonds", MAX_BONDS.def, 1, 10_000));
        TREASURY_BOND_MIN_AMOUNT.bind(builder.comment("央行债券起认金额。")
                .defineInRange("treasuryBondMinAmount", TREASURY_BOND_MIN_AMOUNT.def, 1L, Long.MAX_VALUE));
        TREASURY_BOND_MAX_PER_PLAYER.bind(builder.comment("单名玩家央行债券 ACTIVE 本金合计上限。")
                .defineInRange("treasuryBondMaxPerPlayer", TREASURY_BOND_MAX_PER_PLAYER.def, 0L, Long.MAX_VALUE));
        TREASURY_BOND_MAX_ISSUE_PER_TERM.bind(builder.comment("每期限档央行债券未到期发行余额上限。")
                .defineInRange(
                        "treasuryBondMaxIssuePerTerm", TREASURY_BOND_MAX_ISSUE_PER_TERM.def, 0L, Long.MAX_VALUE));
        TREASURY_BOND_EARLY_FEE.bind(builder.comment("央行债券提前赎回手续费，从本金扣除且不超过本金。")
                .defineInRange("treasuryBondEarlyFee", TREASURY_BOND_EARLY_FEE.def, 0L, Long.MAX_VALUE));
        MAX_LOANS.bind(builder.comment("单个世界最多保留的公司贷款合约数量，超过上限时拒绝新贷款。")
                .defineInRange("maxCompanyLoans", MAX_LOANS.def, 1, 10_000));
        LOAN_GRACE_DAYS.bind(builder.comment("公司贷款逾期后进入违约前的宽限期，单位为 MC 天。")
                .defineInRange("loanGraceDays", LOAN_GRACE_DAYS.def, 1, 365));
        INDEX_BASE_POINTS.bind(builder.comment("股票、商品和行业指数首次建立时的基点。")
                .defineInRange("indexBasePoints", INDEX_BASE_POINTS.def, 100, 1_000_000));
        ANNUAL_MC_DAYS.bind(builder.comment("金融产品年化使用的 MC 天数；债券票息和贷款日息都使用该分母。")
                .defineInRange("annualMcDays", ANNUAL_MC_DAYS.def, 1, 100_000));
        MAX_BOND_TERM_DAYS.bind(builder.comment("公司债券允许的最大期限，单位为 MC 天。")
                .defineInRange("maxBondTermDays", MAX_BOND_TERM_DAYS.def, 2, 100_000));
        MAX_BOND_FINANCING_RATIO.bind(builder.comment("债券与既有债务合计相对公司资产的绝对上限；信用等级还会进一步收紧。")
                .defineInRange("maxBondFinancingRatio", MAX_BOND_FINANCING_RATIO.def, 0.01D, 10.0D));
        MAX_LOAN_TERM_DAYS.bind(builder.comment("公司贷款允许的最大期限，单位为 MC 天。")
                .defineInRange("maxLoanTermDays", MAX_LOAN_TERM_DAYS.def, 2, 100_000));
        MAX_CONTRACT_RATE_BPS.bind(builder.comment("新债券和新贷款的最高年化利率，单位为基点。")
                .defineInRange("maxContractRateBasisPoints", MAX_CONTRACT_RATE_BPS.def, 1, 100_000));
        MEDIUM_DEFAULT_RATE.bind(builder.comment("风险仪表盘进入中风险的合约违约率百分比。")
                .defineInRange("mediumDefaultRatePercent", MEDIUM_DEFAULT_RATE.def, 0.0D, 100.0D));
        HIGH_DEFAULT_RATE.bind(builder.comment("风险仪表盘进入高风险的合约违约率百分比。")
                .defineInRange("highDefaultRatePercent", HIGH_DEFAULT_RATE.def, 0.0D, 100.0D));
        CREDIT_AAA_SCORE.bind(
                builder.comment("AAA 信用等级最低分。").defineInRange("creditAaaMinimumScore", CREDIT_AAA_SCORE.def, 0, 100));
        CREDIT_AA_SCORE.bind(
                builder.comment("AA 信用等级最低分。").defineInRange("creditAaMinimumScore", CREDIT_AA_SCORE.def, 0, 100));
        CREDIT_A_SCORE.bind(
                builder.comment("A 信用等级最低分。").defineInRange("creditAMinimumScore", CREDIT_A_SCORE.def, 0, 100));
        CREDIT_BBB_SCORE.bind(
                builder.comment("BBB 信用等级最低分。").defineInRange("creditBbbMinimumScore", CREDIT_BBB_SCORE.def, 0, 100));
        CREDIT_BB_SCORE.bind(
                builder.comment("BB 信用等级最低分。").defineInRange("creditBbMinimumScore", CREDIT_BB_SCORE.def, 0, 100));
        CREDIT_B_SCORE.bind(builder.comment("B 信用等级最低分；更低且未违约时为 CCC。")
                .defineInRange("creditBMinimumScore", CREDIT_B_SCORE.def, 0, 100));
        builder.pop();

        builder.push("futures");
        FUTURES_ENABLED.bind(builder.comment("是否启用商品期货。关闭后拒绝新合约和新订单，已有状态仍可加载。").define("enabled", FUTURES_ENABLED.def));
        FUTURES_CONTRACT_SIZE.bind(builder.comment("每份标准期货合约代表的商品单位数。")
                .defineInRange("contractSize", FUTURES_CONTRACT_SIZE.def, 1L, 1_000_000L));
        FUTURES_INITIAL_MARGIN_BPS.bind(builder.comment("初始保证金率，单位为基点。")
                .defineInRange("initialMarginBasisPoints", FUTURES_INITIAL_MARGIN_BPS.def, 1, 10_000));
        FUTURES_MAINTENANCE_MARGIN_BPS.bind(builder.comment("维持保证金率，必须低于初始保证金率。")
                .defineInRange("maintenanceMarginBasisPoints", FUTURES_MAINTENANCE_MARGIN_BPS.def, 1, 9_999));
        FUTURES_LIQUIDATION_MARGIN_BPS.bind(builder.comment("强平触发保证金率，必须低于维持保证金率。")
                .defineInRange("liquidationMarginBasisPoints", FUTURES_LIQUIDATION_MARGIN_BPS.def, 1, 9_998));
        FUTURES_MAX_POSITION.bind(builder.comment("每名参与者在单一期货合约的最大绝对净持仓。")
                .defineInRange("maxPositionPerContract", FUTURES_MAX_POSITION.def, 1L, 1_000_000_000L));
        FUTURES_MAX_ORDERS.bind(
                builder.comment("单个世界最多保留的期货活跃订单。").defineInRange("maxOrders", FUTURES_MAX_ORDERS.def, 1, 100_000));
        FUTURES_MIN_TICK.bind(builder.comment("期货报价最小价格变动单位。")
                .defineInRange("minimumPriceTick", FUTURES_MIN_TICK.def, 1L, 1_000_000L));
        FUTURES_LIQUIDATION_SLIPPAGE_BPS.bind(builder.comment("无流动性系统强平最大不利滑点，单位为基点。")
                .defineInRange("liquidationSlippageBasisPoints", FUTURES_LIQUIDATION_SLIPPAGE_BPS.def, 0, 5_000));
        FUTURES_GUARANTEE_FUND.bind(builder.comment("新世界清算保障基金初始余额。")
                .defineInRange("initialGuaranteeFund", FUTURES_GUARANTEE_FUND.def, 0L, Long.MAX_VALUE));
        FUTURES_SETTLEMENT_WINDOW.bind(builder.comment("到期最终结算使用的现货收盘价窗口。")
                .defineInRange("finalSettlementWindowDays", FUTURES_SETTLEMENT_WINDOW.def, 1, 30));
        FUTURES_MIN_SETTLEMENT_VOLUME.bind(builder.comment("采用当日期货收盘价所需的最低成交量。")
                .defineInRange("minimumSettlementVolume", FUTURES_MIN_SETTLEMENT_VOLUME.def, 0L, Long.MAX_VALUE));
        FUTURES_MAX_SPOT_DEVIATION_BPS.bind(builder.comment("每日结算价相对现货参考价允许的最大偏离，单位为基点。")
                .defineInRange("maxSettlementSpotDeviationBasisPoints", FUTURES_MAX_SPOT_DEVIATION_BPS.def, 0, 10_000));
        builder.pop();

        // Phase 3 期货做市商
        builder.push("futuresMarketMaker");
        FUTURES_MM_SPREAD.bind(builder.comment("期货做市商基础价差。0.015 = 理论期货价上下 1.5% 形成 bid / ask。")
                .defineInRange("spread", FUTURES_MM_SPREAD.def, 0.0D, 1.0D));
        FUTURES_MM_NET_POSITION_RATIO.bind(builder.comment("期货做市商净持仓上限比例：|净持仓| ≤ 合约持仓总量 × 该比例。超限后只提供减仓方向报价。")
                .defineInRange("netPositionRatio", FUTURES_MM_NET_POSITION_RATIO.def, 0.0D, 1.0D));
        FUTURES_MM_WASH_TRADE_WINDOW_MINUTES.bind(builder.comment("对敲检测窗口（分钟）：同一玩家在窗口内与做市商双向成交超阈值则告警并临时只报单边。")
                .defineInRange("washTradeWindowMinutes", FUTURES_MM_WASH_TRADE_WINDOW_MINUTES.def, 1, 1440));
        FUTURES_MM_WASH_TRADE_MAX_TRADES.bind(builder.comment("对敲检测触发笔数阈值：窗口内与做市商双向成交笔数超过此值触发告警。")
                .defineInRange("washTradeMaxTrades", FUTURES_MM_WASH_TRADE_MAX_TRADES.def, 1, 100_000));
        FUTURES_MM_WASH_TRADE_MAX_NOTIONAL.bind(builder.comment("对敲检测触发名义金额阈值：窗口内与做市商双向成交名义金额超过此值触发告警。")
                .defineInRange("washTradeMaxNotional", FUTURES_MM_WASH_TRADE_MAX_NOTIONAL.def, 1L, Long.MAX_VALUE));
        FUTURES_MM_WASH_TRADE_COOLDOWN_MINUTES.bind(builder.comment("对敲告警后单边报价冷却时长（分钟）。")
                .defineInRange("washTradeCooldownMinutes", FUTURES_MM_WASH_TRADE_COOLDOWN_MINUTES.def, 1, 10080));
        FUTURES_MM_CIRCUIT_BREAKER_ENABLED.bind(builder.comment("期货做市商熔断开关：结算参考价偏离理论期货价超阈值时暂停报价。")
                .define("circuitBreakerEnabled", FUTURES_MM_CIRCUIT_BREAKER_ENABLED.def));
        FUTURES_MM_CIRCUIT_BREAKER_DEVIATION_BPS.bind(builder.comment("期货做市商熔断偏离阈值，单位为基点。3000 = 偏离理论价 ±30% 熔断。")
                .defineInRange(
                        "circuitBreakerDeviationBasisPoints",
                        FUTURES_MM_CIRCUIT_BREAKER_DEVIATION_BPS.def,
                        0,
                        100_000));
        FUTURES_MM_INITIAL_CAPITAL.bind(builder.comment("期货做市商保证金账户目标注资额：从做市商总账户划转，绝不凭空印钱；总账户余额不足时按实际可划转额注资。")
                .defineInRange("initialCapital", FUTURES_MM_INITIAL_CAPITAL.def, 0L, Long.MAX_VALUE));
        builder.pop();

        builder.push("proposal");
        MIN_PROPOSAL_PARTICIPATION_RATIO.bind(builder.comment("股东提案最低参与率。0.25 = 至少 25% 投票权参与，提案才可能通过。")
                .defineInRange("minParticipationRatio", MIN_PROPOSAL_PARTICIPATION_RATIO.def, 0.0D, 1.0D));
        builder.pop();

        builder.push("orders");
        MAX_PRICE_ALERTS_PER_PLAYER.bind(builder.comment("每名玩家最多保留的未触发价格提醒数量。")
                .defineInRange("maxPriceAlertsPerPlayer", MAX_PRICE_ALERTS_PER_PLAYER.def, 1, 10_000));
        MAX_CONDITIONAL_STOCK_ORDERS_PER_PLAYER.bind(builder.comment("每名玩家最多保留的股票条件委托数量。")
                .defineInRange(
                        "maxConditionalStockOrdersPerPlayer", MAX_CONDITIONAL_STOCK_ORDERS_PER_PLAYER.def, 1, 10_000));
        builder.pop();

        builder.push("stockMarketMaker");
        STOCK_MARKET_MAKER_SPREAD.bind(builder.comment("股票做市商价差。0.02 = fairValue 上下 2% 形成 bid / ask。")
                .defineInRange("spread", STOCK_MARKET_MAKER_SPREAD.def, 0.0D, 1.0D));
        STOCK_TARGET_INVENTORY_RATIO.bind(builder.comment("股票做市商目标库存比例：流通股 × 该比例。库存偏离目标时触发报价偏斜（Phase 2）。")
                .defineInRange("targetInventoryRatio", STOCK_TARGET_INVENTORY_RATIO.def, 0.0D, 1.0D));
        STOCK_MAX_INVENTORY_MULTIPLIER.bind(builder.comment("股票做市商库存上限倍数：目标库存 × 该倍数。库存达上限后只卖不买。")
                .defineInRange("maxInventoryMultiplier", STOCK_MAX_INVENTORY_MULTIPLIER.def, 1.0D, 10.0D));
        STOCK_MAX_TRADE_FLOAT_RATIO.bind(builder.comment("股票做市商单笔成交上限比例：流通股 × 该比例（另受目标库存 20% 与现金可支撑量约束）。")
                .defineInRange("maxTradeFloatRatio", STOCK_MAX_TRADE_FLOAT_RATIO.def, 0.0D, 1.0D));
        IPO_MARKET_MAKER_RESERVE_RATIO.bind(builder.comment("IPO 做市商预留比例：发行量 × 该比例以发行价划入做市商库存，资金从做市商总账户支付给公司。")
                .defineInRange("ipoMarketMakerReserveRatio", IPO_MARKET_MAKER_RESERVE_RATIO.def, 0.0D, 1.0D));
        builder.pop();

        // Phase 2 做市商报价
        builder.push("marketMakerQuotes");
        MM_TARGET_PRICE_STRATEGY_ENABLED.bind(builder.comment("做市商均值回归策略（TargetPriceStrategy）开关：现价偏离中间价超阈值时把报价往回拉。")
                .define("targetPriceStrategyEnabled", MM_TARGET_PRICE_STRATEGY_ENABLED.def));
        MM_INVENTORY_SKEW_STRATEGY_ENABLED.bind(builder.comment("做市商库存偏斜策略（InventorySkewStrategy）开关：库存高于目标下移报价，低于目标上移。")
                .define("inventorySkewStrategyEnabled", MM_INVENTORY_SKEW_STRATEGY_ENABLED.def));
        MM_VOLATILITY_SPREAD_STRATEGY_ENABLED.bind(
                builder.comment("做市商波动率价差策略（VolatilitySpreadStrategy）开关：近期波动大时加宽价差自保。")
                        .define("volatilitySpreadStrategyEnabled", MM_VOLATILITY_SPREAD_STRATEGY_ENABLED.def));
        MM_INVENTORY_SKEW_FACTOR.bind(builder.comment("库存偏斜系数：skew = 该系数 × (库存/目标 - 1)，双边报价整体平移 mid × skew。")
                .defineInRange("inventorySkewFactor", MM_INVENTORY_SKEW_FACTOR.def, 0.0D, 5.0D));
        MM_VOLATILITY_SPREAD_FACTOR.bind(builder.comment("波动率价差系数：volAdj = 该系数 × 近期波动率，双边各加宽 mid × volAdj。")
                .defineInRange("volatilitySpreadFactor", MM_VOLATILITY_SPREAD_FACTOR.def, 0.0D, 5.0D));
        MM_MAX_INVENTORY_SKEW.bind(builder.comment("库存偏斜上限：|skew| 不超过该值，防止极端库存把报价推离中间价太远。")
                .defineInRange("maxInventorySkew", MM_MAX_INVENTORY_SKEW.def, 0.0D, 0.5D));
        MM_MAX_VOLATILITY_ADJUST.bind(builder.comment("波动率价差上限：volAdj 不超过该值。")
                .defineInRange("maxVolatilityAdjust", MM_MAX_VOLATILITY_ADJUST.def, 0.0D, 0.5D));
        MM_CIRCUIT_BREAKER_THRESHOLD.bind(
                builder.comment("熔断阈值：|现价 - 参考价| / 参考价超过该值时做市商暂停报价（商品参考价=基准价，股票参考价=fairValue），回归后自动恢复。")
                        .defineInRange("circuitBreakerThreshold", MM_CIRCUIT_BREAKER_THRESHOLD.def, 0.0D, 2.0D));
        MM_TARGET_PRICE_DEVIATION_THRESHOLD.bind(builder.comment("目标价偏离阈值：现价偏离中间价超过该比例时均值回归策略介入。")
                .defineInRange("targetPriceDeviationThreshold", MM_TARGET_PRICE_DEVIATION_THRESHOLD.def, 0.0D, 1.0D));
        // Phase 4 央行联动
        builder.push("centralBankMarketMaker");
        CB_MM_HEALTH_CHECK_ENABLED.bind(builder.comment("央行跨市场做市商健康度检查总开关（随每日央行干预执行）。关闭后不再发放紧急贷款、扣回还款，也不再做库存触顶告警。")
                .define("healthCheckEnabled", CB_MM_HEALTH_CHECK_ENABLED.def));
        CB_MM_EMERGENCY_LOAN_CAP.bind(builder.comment("央行对期货做市商单次紧急贷款上限。贷款记入央行紧急贷款账本；做市商总账户余额健康时按还款比例逐日扣回。")
                .defineInRange("emergencyLoanCap", CB_MM_EMERGENCY_LOAN_CAP.def, 0L, Long.MAX_VALUE));
        CB_MM_LOAN_REPAYMENT_BPS.bind(builder.comment("紧急贷款每日还款比例（基点）：做市商总账户余额高于健康线时，按超出部分的该比例划扣还款。2000 = 20%。")
                .defineInRange("loanRepaymentBasisPoints", CB_MM_LOAN_REPAYMENT_BPS.def, 0, 10_000));
        builder.pop();

        builder.push("takeover");
        TAKEOVER_MIN_HOLDING_BPS.bind(builder.comment("发起要约收购需事前持有的有表决权股份比例（基点）。3000 = 30%。")
                .defineInRange("minHoldingBps", TAKEOVER_MIN_HOLDING_BPS.def, 0, 10_000));
        TAKEOVER_SQUEEZE_OUT_BPS.bind(builder.comment("强制私有化（挤出小股东）所需的持股比例（基点）。9000 = 90%。")
                .defineInRange("squeezeOutBps", TAKEOVER_SQUEEZE_OUT_BPS.def, 5_001, 10_000));
        TAKEOVER_PRICE_FLOOR_RATIO.bind(builder.comment("要约价下限倍数（相对发起时 fairValue）。防恶意压价掠夺小股东。")
                .defineInRange("priceFloorRatio", TAKEOVER_PRICE_FLOOR_RATIO.def, 0.01D, 10.0D));
        TAKEOVER_PRICE_CAP_RATIO.bind(builder.comment("要约价上限倍数（相对发起时 fairValue）。防拉高套现。")
                .defineInRange("priceCapRatio", TAKEOVER_PRICE_CAP_RATIO.def, 0.01D, 10.0D));
        TAKEOVER_MIN_DURATION_DAYS.bind(builder.comment("要约期下限（MC 天）。")
                .defineInRange("minDurationDays", TAKEOVER_MIN_DURATION_DAYS.def, 1, 90));
        TAKEOVER_MAX_DURATION_DAYS.bind(builder.comment("要约期上限（MC 天）。停牌是有代价的武器，不宜过长。")
                .defineInRange("maxDurationDays", TAKEOVER_MAX_DURATION_DAYS.def, 1, 90));
        builder.pop();

        builder.push("margin");
        MARGIN_MAX_LEVERAGE.bind(builder.comment("融资融券最大杠杆倍数。2 = 2x（初始保证金率 50%）。")
                .defineInRange("maxLeverage", MARGIN_MAX_LEVERAGE.def, 1, 10));
        MARGIN_MAINTENANCE_BPS.bind(builder.comment("维持保证金率（基点）。13000 = 130%，跌破强制平仓。")
                .defineInRange("maintenanceBps", MARGIN_MAINTENANCE_BPS.def, 10_001, 100_000));
        MARGIN_WARNING_BPS.bind(builder.comment("预警线（基点）。15000 = 150%，跌破仅预警不强平。")
                .defineInRange("warningBps", MARGIN_WARNING_BPS.def, 10_001, 100_000));
        MARGIN_FINANCE_ANNUAL_RATE.bind(builder.comment("融资年利率。0.086 = 8.6%，按日单利计提。不可为负。")
                .defineInRange("financeAnnualRate", MARGIN_FINANCE_ANNUAL_RATE.def, 0.0D, 1.0D));
        MARGIN_SHORT_ANNUAL_RATE.bind(builder.comment("融券年利率。0.106 = 10.6%，按借出时市值计提。不可为负。")
                .defineInRange("shortAnnualRate", MARGIN_SHORT_ANNUAL_RATE.def, 0.0D, 1.0D));
        MARGIN_POOL_RATIO.bind(builder.comment("券池总额占流通股比例。0.05 = 5%，系统出借硬上限（防无限印钞）。")
                .defineInRange("poolRatio", MARGIN_POOL_RATIO.def, 0.0D, 1.0D));
        MARGIN_PER_PLAYER_BORROW_RATIO.bind(builder.comment("单人借券上限占券池比例。0.20 = 20%。")
                .defineInRange("perPlayerBorrowRatio", MARGIN_PER_PLAYER_BORROW_RATIO.def, 0.0D, 1.0D));
        builder.pop();

        builder.push("villageEconomy");
        VILLAGE_POPULATION.bind(
                builder.comment("每聚落默认村庄人口。").defineInRange("villagePopulation", VILLAGE_POPULATION.def, 1, 10_000));
        VILLAGE_PER_CAPITA_BUDGET.bind(builder.comment("村民人均日预算。")
                .defineInRange("villagePerCapitaBudget", VILLAGE_PER_CAPITA_BUDGET.def, 1L, 100_000L));
        VILLAGE_BUDGET_RESERVE_DAYS.bind(builder.comment("村庄预算上限 = 人均日预算 × 人口 × 天数。")
                .defineInRange("villageBudgetReserveDays", VILLAGE_BUDGET_RESERVE_DAYS.def, 1, 30));
        VILLAGE_PREMIUM_MIN_BPS.bind(builder.comment("村庄溢价下限，基点；7500 = 0.75x。")
                .defineInRange("villagePremiumMinBps", VILLAGE_PREMIUM_MIN_BPS.def, 1_000, 10_000));
        VILLAGE_PREMIUM_MAX_BPS.bind(builder.comment("村庄溢价上限，基点；14000 = 1.40x。")
                .defineInRange("villagePremiumMaxBps", VILLAGE_PREMIUM_MAX_BPS.def, 10_000, 30_000));
        VILLAGE_FAIR_TOLERANCE_BPS.bind(builder.comment("公允容忍，基点；12000 = 1.2x WTP。")
                .defineInRange("villageFairToleranceBps", VILLAGE_FAIR_TOLERANCE_BPS.def, 10_000, 20_000));
        VILLAGE_GOUGE_TOLERANCE_BPS.bind(builder.comment("宰客线，基点；15000 = 1.5x WTP。")
                .defineInRange("villageGougeToleranceBps", VILLAGE_GOUGE_TOLERANCE_BPS.def, 10_000, 30_000));
        VILLAGE_BOYCOTT_FAVOR.bind(
                builder.comment("抵制好感阈值：村民好感 ≤ 该值时拒绝该玩家全部摊位。" + " 注意：设为 0 会抵制所有新玩家（新玩家好感为 0，0 ≤ 0 恒成立）。")
                        .defineInRange("villageBoycottFavor", VILLAGE_BOYCOTT_FAVOR.def, -100, 0));
        VILLAGE_TRUST_FAVOR.bind(
                builder.comment("信赖好感阈值。").defineInRange("villageTrustFavor", VILLAGE_TRUST_FAVOR.def, 0, 100));
        builder.pop();

        builder.push("factory");
        FACTORY_SPEED_PERCENT.bind(builder.comment("加工机速度，百分比。200 = 每件耗时减半。")
                .defineInRange("speedPercent", FACTORY_SPEED_PERCENT.def, 10, 1000));
        builder.pop();

        builder.push("caravan");
        CARAVAN_MAX_ROUTES_PER_PLAYER.bind(builder.comment("每名玩家商队路线上限。")
                .defineInRange("maxRoutesPerPlayer", CARAVAN_MAX_ROUTES_PER_PLAYER.def, 1, 64));
        CARAVAN_MAX_QTY_PER_TRIP.bind(builder.comment("商队每趟货物数量上限。")
                .defineInRange("maxQtyPerTrip", CARAVAN_MAX_QTY_PER_TRIP.def, 1, 1_000_000));
        CARAVAN_BLOCKS_PER_DAY.bind(builder.comment("商队每日行进格数（运输天数 = 1 + 距离/该值）。")
                .defineInRange("blocksPerDay", CARAVAN_BLOCKS_PER_DAY.def, 100L, 1_000_000L));
        CARAVAN_RAID_BASE_BPS.bind(
                builder.comment("被劫基础概率（基点）。").defineInRange("raidBaseBps", CARAVAN_RAID_BASE_BPS.def, 0, 10_000));
        CARAVAN_RAID_MIN_BPS.bind(
                builder.comment("被劫概率下限（基点）。").defineInRange("raidMinBps", CARAVAN_RAID_MIN_BPS.def, 0, 10_000));
        CARAVAN_RAID_MAX_BPS.bind(
                builder.comment("被劫概率上限（基点）。").defineInRange("raidMaxBps", CARAVAN_RAID_MAX_BPS.def, 0, 10_000));
        CARAVAN_RAID_PER_KM_BPS.bind(builder.comment("每公里增加的被劫概率（基点）。")
                .defineInRange("raidPerKmBps", CARAVAN_RAID_PER_KM_BPS.def, 0, 10_000));
        CARAVAN_RAID_PER_KM_CAP_BPS.bind(builder.comment("距离带来的被劫概率上限（基点）。")
                .defineInRange("raidPerKmCapBps", CARAVAN_RAID_PER_KM_CAP_BPS.def, 0, 10_000));
        CARAVAN_RAID_ALERT_BONUS_BPS.bind(builder.comment("任一端点处于 RAID_ALERT 时的被劫加成（基点）。")
                .defineInRange("raidAlertBonusBps", CARAVAN_RAID_ALERT_BONUS_BPS.def, 0, 10_000));
        CARAVAN_GUARD_REDUCE_BPS.bind(builder.comment("每级保镖降低的被劫概率（基点）。")
                .defineInRange("guardReduceBps", CARAVAN_GUARD_REDUCE_BPS.def, 0, 10_000));
        CARAVAN_GUARD_WAGE_1.bind(
                builder.comment("1 级保镖每趟工资。").defineInRange("guardWage1", CARAVAN_GUARD_WAGE_1.def, 0L, 1_000_000L));
        CARAVAN_GUARD_WAGE_2.bind(
                builder.comment("2 级保镖每趟工资。").defineInRange("guardWage2", CARAVAN_GUARD_WAGE_2.def, 0L, 1_000_000L));
        CARAVAN_GUARD_WAGE_3.bind(
                builder.comment("3 级保镖每趟工资。").defineInRange("guardWage3", CARAVAN_GUARD_WAGE_3.def, 0L, 1_000_000L));
        CARAVAN_RAID_LOSS_RATIO_BPS.bind(builder.comment("被劫时丢失货物的比例（基点），默认 3000 = 30%。")
                .defineInRange("raidLossRatioBps", CARAVAN_RAID_LOSS_RATIO_BPS.def, 0, 10_000));
        CARAVAN_INSURE_MARGIN_BPS.bind(builder.comment("保费边际（基点）：保费 = 期望赔付 × 该值/10000，须 > 10000 保证系统不亏。")
                .defineInRange("insureMarginBps", CARAVAN_INSURE_MARGIN_BPS.def, 10_000, 100_000));
        CARAVAN_SATURATION_PER_UNIT_BPS.bind(builder.comment("每单位到货增加的路线饱和度（基点）。")
                .defineInRange("saturationPerUnitBps", CARAVAN_SATURATION_PER_UNIT_BPS.def, 0, 10_000));
        CARAVAN_SATURATION_DECAY_BPS.bind(builder.comment("路线饱和度每日衰减（基点）。")
                .defineInRange("saturationDecayBps", CARAVAN_SATURATION_DECAY_BPS.def, 0, 10_000));
        CARAVAN_SELL_PRICE_CAP_MULTIPLIER.bind(builder.comment("商队买卖价相对 basePrice 的上限倍数。")
                .defineInRange("sellPriceCapMultiplier", CARAVAN_SELL_PRICE_CAP_MULTIPLIER.def, 1, 100));
        builder.pop();

        builder.push("earnings");
        EARNINGS_QUARTER_DAYS.bind(builder.comment("财报季：季度天数；每家上市公司每季度发布一次季报。")
                .defineInRange("quarterDays", EARNINGS_QUARTER_DAYS.def, 1, 365));
        EARNINGS_EXPECTATION_QUARTERS.bind(builder.comment("财报季：分析师预期的外推窗口（期数），线性外推最近 N 期净利润。")
                .defineInRange("expectationQuarters", EARNINGS_EXPECTATION_QUARTERS.def, 1, 12));
        EARNINGS_SURPRISE_CAP_BPS.bind(builder.comment("财报季：预期差钳制（bps），5000 = ±50%。")
                .defineInRange("surpriseCapBps", EARNINGS_SURPRISE_CAP_BPS.def, 100, 100_000));
        EARNINGS_REACTION_BPS.bind(builder.comment("财报季：每 10000bps 预期差对应的股价冲击 bps，30 = 10% 超预期 → +0.3% 股价。")
                .defineInRange("reactionBps", EARNINGS_REACTION_BPS.def, 0, 10_000));
        EARNINGS_MAX_SHOCK_BPS.bind(builder.comment(
                        "财报季：单次财报股价冲击上限（bps），2000 = ±20%，防闪崩。" + "引擎对 shockBps 另有 ±50% 硬钳制，超过 5000 的配置本就无效，故上限设为 5000。")
                .defineInRange("maxShockBps", EARNINGS_MAX_SHOCK_BPS.def, 0, 5_000));
        EARNINGS_FV_PASS_THROUGH_BPS.bind(builder.comment("财报季：冲击传导至 fairValue 的比例（bps），5000 = 50%。")
                .defineInRange("fvPassThroughBps", EARNINGS_FV_PASS_THROUGH_BPS.def, 0, 10_000));
        EARNINGS_HISTORY_KEPT.bind(
                builder.comment("财报季：每家公司保留的季报期数。").defineInRange("historyKept", EARNINGS_HISTORY_KEPT.def, 1, 40));
        EARNINGS_EX_DIVIDEND.bind(
                builder.comment("财报季：分红后是否按每股分红额除权下调股价。").define("exDividend", EARNINGS_EX_DIVIDEND.def));
        builder.pop();

        builder.push("npcEconomy");
        NPC_INVESTORS_ENABLED.bind(builder.comment("NPC 投资者：关闭后 NPC 不交易不投票，已有持仓保留，仍可被交易、分红和交割。")
                .define("npcInvestorsEnabled", NPC_INVESTORS_ENABLED.def));
        NPC_INITIAL_CAPITAL_EACH.bind(builder.comment("每个 NPC 投资者原型首次启用时从 NPC 资金池转入的初始资本。")
                .defineInRange("initialCapitalEach", NPC_INITIAL_CAPITAL_EACH.def, 0L, 100_000_000L));
        NPC_DAILY_BUDGET.bind(builder.comment("NPC 投资者现金低于该值时，每天从 NPC 资金池补足到该值。")
                .defineInRange("dailyBudget", NPC_DAILY_BUDGET.def, 0L, 10_000_000L));
        NPC_POOL_RESERVE_FLOOR.bind(builder.comment("NPC 资金池低于该值时不再注资或补足，避免把资金池拉到央行托底线以下、间接触发增发。")
                .defineInRange("poolReserveFloor", NPC_POOL_RESERVE_FLOOR.def, 0L, Long.MAX_VALUE));
        NPC_MAX_HOLDING_PERCENT_EACH.bind(builder.comment("单个 NPC 原型对单家公司的持股上限（占有表决权股份 %）。短线派按一半计。")
                .defineInRange("maxHoldingPercentEach", NPC_MAX_HOLDING_PERCENT_EACH.def, 0, 49));
        NPC_MAX_COMBINED_HOLDING_PERCENT.bind(builder.comment("全部 NPC 投资者对单家公司的合计持股上限（%），低于 50% 以免 NPC 自动取得控制权。")
                .defineInRange("maxCombinedHoldingPercent", NPC_MAX_COMBINED_HOLDING_PERCENT.def, 0, 49));
        NPC_MAX_FLOAT_PERCENT_PER_DAY.bind(builder.comment("全部 NPC 投资者每天对单只股票的成交量上限（占流通股 %）。")
                .defineInRange("maxFloatPercentPerDay", NPC_MAX_FLOAT_PERCENT_PER_DAY.def, 0, 100));
        NPC_VOTING_ENABLED.bind(builder.comment("NPC 股东是否对提案投票。需要同时开启 npcInvestorsEnabled。")
                .define("npcVotingEnabled", NPC_VOTING_ENABLED.def));
        builder.push("tenderPremium");
        NPC_TENDER_PREMIUM_DIVIDEND.bind(builder.comment("分红派接受要约所需的溢价（要约价 ≥ 公允价 × (1 + 该值%)）。")
                .defineInRange("dividend", NPC_TENDER_PREMIUM_DIVIDEND.def, 0, 1_000));
        NPC_TENDER_PREMIUM_GROWTH.bind(
                builder.comment("成长派接受要约所需的溢价（%）。").defineInRange("growth", NPC_TENDER_PREMIUM_GROWTH.def, 0, 1_000));
        NPC_TENDER_PREMIUM_SPECULATOR.bind(builder.comment("短线派接受要约所需的溢价（%）。")
                .defineInRange("speculator", NPC_TENDER_PREMIUM_SPECULATOR.def, 0, 1_000));
        NPC_TENDER_PREMIUM_VALUE.bind(
                builder.comment("价值派接受要约所需的溢价（%）。").defineInRange("value", NPC_TENDER_PREMIUM_VALUE.def, 0, 1_000));
        builder.pop();
        builder.pop();

        builder.push("macroEconomy");
        MACRO_ENABLED.bind(builder.comment("宏观实体经济总开关：人口需求类别、聚落本地价格、人口增长与迁移、CPI。关闭后村庄、商队、价格指数回到旧逻辑。")
                .define("enabled", MACRO_ENABLED.def));
        MACRO_LABOR_ENABLED.bind(
                builder.comment("公司设施是否需要从附近聚落雇工并发工资；关闭后设施按满员生产。").define("laborEnabled", MACRO_LABOR_ENABLED.def));
        MACRO_MIN_POPULATION.bind(
                builder.comment("聚落人口下限。").defineInRange("minPopulation", MACRO_MIN_POPULATION.def, 1, 10000));
        MACRO_MAX_POPULATION.bind(
                builder.comment("聚落人口上限。").defineInRange("maxPopulation", MACRO_MAX_POPULATION.def, 1, 10000));
        MACRO_GROWTH_MAX_BPS.bind(builder.comment("生活水平满分或零分时的日人口增减率（基点，150 = 1.5%）。")
                .defineInRange("growthMaxBps", MACRO_GROWTH_MAX_BPS.def, 0, 1000));
        MACRO_MIGRATION_MAX_BPS.bind(builder.comment("每个维度每天从最不宜居聚落迁往最宜居聚落的人口比例（基点）。")
                .defineInRange("migrationMaxBps", MACRO_MIGRATION_MAX_BPS.def, 0, 1000));
        MACRO_MIGRATION_GAP_BPS.bind(builder.comment("两地吸引力差距达到该值（基点）才发生迁移。")
                .defineInRange("migrationGapBps", MACRO_MIGRATION_GAP_BPS.def, 0, 10000));
        MACRO_PRICE_STEP_BPS.bind(
                builder.comment("本地价格系数每天最多变化的基点。").defineInRange("priceStepBps", MACRO_PRICE_STEP_BPS.def, 1, 10000));
        MACRO_RATE_DEMAND_SENSITIVITY.bind(
                builder.comment("实际利率每高出中性 1 个百分点，村民非食物消费少百分之几（食物减半），NPC 公司多卖百分之几。0 = 利率不影响需求。消费倾向限制在 85%～115%。")
                        .defineInRange("rateDemandSensitivity", MACRO_RATE_DEMAND_SENSITIVITY.def, 0.0D, 10.0D));
        MACRO_RATE_STOCK_SENSITIVITY.bind(
                builder.comment("实际利率每高出中性 1 个百分点，国际市场定价用的参考库存低百分之几（现有库存显得过剩，价格回落）。0 = 不影响。限制在 80%～120%。")
                        .defineInRange("rateStockSensitivity", MACRO_RATE_STOCK_SENSITIVITY.def, 0.0D, 10.0D));
        MACRO_LABOR_RADIUS.bind(
                builder.comment("机器从多远（格）以内的聚落招工。").defineInRange("laborRadius", MACRO_LABOR_RADIUS.def, 16, 4096));
        MACRO_DEFAULT_WAGE.bind(builder.comment("公司未设定工资时的默认日工资（每人每天）。")
                .defineInRange("defaultWage", MACRO_DEFAULT_WAGE.def, 0L, 1000000L));
        MACRO_BASE_RESERVATION_WAGE.bind(builder.comment("工人接受工作的最低日工资（劳工法可抬高）。")
                .defineInRange("baseReservationWage", MACRO_BASE_RESERVATION_WAGE.def, 0L, 1000000L));
        MACRO_WORLD_FEEDBACK.bind(builder.comment("宏观经济的世界反馈：聚落心情粒子与村民抱怨、罢工工厂冒烟、商队到货烟花、议会/罢工/饥荒全服公告。只影响表现，不影响数值。")
                .define("worldFeedback", MACRO_WORLD_FEEDBACK.def));
        MACRO_NEWS_ENABLED.bind(builder.comment("《金融周报》与号外：每周汇总经济大事，到贸易站或用 /macro news 领取。")
                .define("newsEnabled", MACRO_NEWS_ENABLED.def));
        MACRO_NEWS_INTERVAL_DAYS.bind(builder.comment("周报出刊间隔（MC 天）。")
                .defineInRange("newsIntervalDays", MACRO_NEWS_INTERVAL_DAYS.def, 1, 365));
        MACRO_NEWS_EXTRA_GAP_DAYS.bind(builder.comment("两期号外之间的最短间隔（MC 天）。")
                .defineInRange("newsExtraGapDays", MACRO_NEWS_EXTRA_GAP_DAYS.def, 1, 365));
        MACRO_RADAR_COST.bind(builder.comment("一次商情调查（/macro radar 或“更多操作”）的费用，进 NPC 资金池。")
                .defineInRange("radarCost", MACRO_RADAR_COST.def, 0L, 100000000L));
        MACRO_RADAR_COOLDOWN_DAYS.bind(builder.comment("同一玩家两次商情调查的最短间隔（MC 天）。")
                .defineInRange("radarCooldownDays", MACRO_RADAR_COOLDOWN_DAYS.def, 0, 365));
        builder.pop();

        builder.push("rivals");
        RIVALS_ENABLED.bind(
                builder.comment("NPC 竞争公司：关闭后不新建、不做经营决策；已有公司照常存在、交易和被收购。").define("enabled", RIVALS_ENABLED.def));
        RIVALS_MAX_COMPANIES.bind(builder.comment("无主（NPC）公司总数上限，含 3 家系统公司。")
                .defineInRange("maxCompanies", RIVALS_MAX_COMPANIES.def, 0, 32));
        RIVALS_SPAWN_COOLDOWN_DAYS.bind(builder.comment("两次新建竞争公司、或竞争公司破产后补新公司的最短间隔（MC 天）。")
                .defineInRange("spawnCooldownDays", RIVALS_SPAWN_COOLDOWN_DAYS.def, 1, 365));
        RIVALS_SEED_CAPITAL.bind(builder.comment("新建竞争公司从 NPC 资金池拨付的种子资金；资金池低于 npcEconomy.poolReserveFloor 时不新建。")
                .defineInRange("seedCapital", RIVALS_SEED_CAPITAL.def, 0L, 100_000_000L));
        RIVALS_IPO_ENABLED.bind(
                builder.comment("竞争公司满足条件时是否上市（遵守玩家 IPO 的全部上限）。").define("ipoEnabled", RIVALS_IPO_ENABLED.def));
        RIVALS_CONTRACTS_ENABLED.bind(builder.comment("加工类竞争公司缺原料时是否发布采购合同，和玩家争夺供应。")
                .define("contractsEnabled", RIVALS_CONTRACTS_ENABLED.def));
        RIVALS_HOSTILE_TAKEOVERS.bind(builder.comment("激进/投机型竞争公司对股价明显低于基本面的上市公司建仓并发起敌对要约。默认关闭，单人经营预设开启。")
                .define("hostileTakeovers", RIVALS_HOSTILE_TAKEOVERS.def));
        RIVALS_ACTIVISM_ENABLED.bind(builder.comment("竞争公司持股达到提案资格（25%）后，被投公司亏损或长期不分红时发起提高分红的提案。默认关闭，单人经营预设开启。")
                .define("activismEnabled", RIVALS_ACTIVISM_ENABLED.def));
        builder.pop();

        builder.push("cash");
        CASH_ENABLED.bind(
                builder.comment("实体现金（便士硬币、英镑纸币；账户单位是便士，100 便士 = 1 磅）：可从账户取出、存回账户，并与绿宝石兑换。关闭后现金物品仍在，但不能存取和兑换。")
                        .define("enabled", CASH_ENABLED.def));
        CASH_EMERALD_TO_CASH.bind(builder.comment("玩家卖出 1 颗绿宝石得到的便士。默认 50 ≈ 盔甲匠收 4 个铁锭（铁锭基准价 10）的价值。")
                .defineInRange("emeraldToCash", CASH_EMERALD_TO_CASH.def, 1L, 1_000_000L));
        CASH_CASH_TO_EMERALD.bind(builder.comment("玩家买入 1 颗绿宝石要付的便士；低于 emeraldToCash 时按 emeraldToCash 计，避免来回套利。")
                .defineInRange("cashToEmerald", CASH_CASH_TO_EMERALD.def, 1L, 1_000_000L));
        CASH_EMERALD_IMPACT_BPS.bind(builder.comment("浮动汇率：全服每卖给央行 1 颗绿宝石，买卖价都下跌这么多基点（100 = 1%），每买走 1 颗就上涨这么多。"
                        + "代替每日额度，让刷绿宝石印钱和村民套利越做越不划算。0 = 固定汇率。")
                .defineInRange("emeraldImpactBps", CASH_EMERALD_IMPACT_BPS.def, 0, 1_000));
        CASH_EMERALD_RECOVERY_PERCENT.bind(builder.comment("浮动汇率：价格偏离每个 MC 日向基准价（emeraldToCash / cashToEmerald）回落的百分比。")
                .defineInRange("emeraldRecoveryPercent", CASH_EMERALD_RECOVERY_PERCENT.def, 0, 100));
        builder.pop();

        builder.push("insurance");
        INSURANCE_ACCIDENT_CHANCE.bind(
                builder.comment("每家公司每个 MC 日发生仓库事故（火灾、水淹、鼠患）的概率，百分比。事故毁掉公司仓库里某一种未质押库存的一部分，买了库存灾害险的按保单理赔。0 = 关闭。")
                        .defineInRange("warehouseAccidentChancePercent", INSURANCE_ACCIDENT_CHANCE.def, 0.0D, 100.0D));
        INSURANCE_ACCIDENT_MAX_LOSS.bind(builder.comment("一次仓库事故最多毁掉该种库存的百分比（最少 5%）。")
                .defineInRange("warehouseAccidentMaxLossPercent", INSURANCE_ACCIDENT_MAX_LOSS.def, 5, 100));
        builder.pop();

        builder.push("simpleCenter");
        SIMPLE_STARTER_GRANT.bind(builder.comment("新玩家第一次打开金融中心时送的启动资金（便士），每人一次。0 = 不送。")
                .defineInRange("starterGrant", SIMPLE_STARTER_GRANT.def, 0L, 1_000_000L));
        SIMPLE_STOCK_UNLOCK.bind(builder.comment("钱包达到多少便士后，投资页才能买股票（只影响简单模式的按钮）。")
                .defineInRange("stockUnlockBalance", SIMPLE_STOCK_UNLOCK.def, 0L, 1_000_000_000L));
        SIMPLE_IPO_MIN_VALUATION.bind(builder.comment("公司估值达到多少便士后才能上市。开公司很便宜，这个门槛防止“开公司→上市→提现”刷钱。")
                .defineInRange("ipoMinValuation", SIMPLE_IPO_MIN_VALUATION.def, 0L, 1_000_000_000L));
        SIMPLE_ADVANCED_MODE.bind(
                builder.comment("原有的全部页签（旧界面）：always = 所有人右上角都有“高级”入口；其他值 = 只有 OP 能从“管理”进入。新的四页已经包含全部玩家功能。")
                        .define("advancedMode", SIMPLE_ADVANCED_MODE.def));
        SIMPLE_EXTRAS.bind(builder.comment(
                        "进阶玩法默认按进度解锁：存过理财解锁 growth（进取理财：基金、央行债券），买过股票解锁 bet（押注：看涨看跌股票和商品），"
                                + "公司上市解锁 boss（大老板：股东大会、收购、开银行、保险、工资）。"
                                + "这里列出的玩法不用解锁、直接对所有人打开，逗号分隔；可以写 growth / bet / boss，也可以写单项："
                                + "futures（商品押注）、margin（股票押注）、bonds、fund、shareholder、takeover、bank、insurance、wage；all = 全部直接打开。")
                .define("extras", SIMPLE_EXTRAS.def));
        SIMPLE_DISABLED.bind(builder.comment("整个服务器关掉的进阶玩法，写法同 extras；关掉的玩法解锁了也不能新开，已有的仓位仍可以结束和取回。")
                .define("disabled", SIMPLE_DISABLED.def));
        SIMPLE_WEEKLY_LEADERBOARD.bind(builder.comment("每 7 个 MC 日在聊天栏播报一次身家排行和本周涨得最多的人。金融中心首页的排行榜不受影响。")
                .define("weeklyLeaderboard", SIMPLE_WEEKLY_LEADERBOARD.def));
        builder.pop();

        builder.push("reserve");
        RESERVE_TARGETS.bind(
                builder.comment("央行储备三档原材料的目标库存（件），逗号分隔。库存等于目标时价格是基准价，越少越贵。").define("targets", RESERVE_TARGETS.def));
        RESERVE_RESTOCK_BPS.bind(builder.comment("央行每天最多补目标库存的多少（基点，200 = 2%）。越小，抢购后价格回落越慢。")
                .defineInRange("restockBpsPerDay", RESERVE_RESTOCK_BPS.def, 0, 10_000));
        RESERVE_SURPLUS_DEMAND_BPS.bind(
                builder.comment("库存超过目标时，外部需求每天买走多出部分的多少（基点，2500 = 25%）。" + "公司每天往市场卖几十上百件，太小的话库存越堆越多、价格一路跌。")
                        .defineInRange("surplusDemandBpsPerDay", RESERVE_SURPLUS_DEMAND_BPS.def, 0, 10_000));
        RESERVE_TIER2_VALUE.bind(builder.comment("玩家（含玩家自己的公司）累计成交额（便士）达到多少后，央行开始投放第 2 档（铁、铜、羊毛、皮革）。系统公司的买卖不算。")
                .defineInRange("tier2UnlockValue", RESERVE_TIER2_VALUE.def, 0L, Long.MAX_VALUE));
        RESERVE_TIER3_VALUE.bind(builder.comment("全服累计成交额（便士）达到多少后央行开始投放第 3 档（金、红石、青金石）。")
                .defineInRange("tier3UnlockValue", RESERVE_TIER3_VALUE.def, 0L, Long.MAX_VALUE));
        RESERVE_FLOOR_BPS.bind(builder.comment("底价占基准价的基点（5000 = 一半）：市场收购价低于底价时央行按底价收。")
                .defineInRange("floorBps", RESERVE_FLOOR_BPS.def, 0, 10_000));
        RESERVE_FLOOR_DAILY_CAP.bind(builder.comment("每种商品每天全服最多按底价收多少件，防止自动农场无限取钱。")
                .defineInRange("floorDailyCap", RESERVE_FLOOR_DAILY_CAP.def, 0, 1_000_000));
        RESERVE_PLAYER_GOODS_REFERENCE.bind(builder.comment("玩家货（央行不投放的商品）的参考库存：市场里有这么多时价格是基准价。")
                .defineInRange("playerGoodsReference", RESERVE_PLAYER_GOODS_REFERENCE.def, 1L, 1_000_000L));
        RESERVE_ELASTICITY.bind(builder.comment("价格对库存的敏感度：库存降到目标一半时价格约涨 2^该值 倍（0.6 ≈ 涨 50%）。")
                .defineInRange("priceElasticity", RESERVE_ELASTICITY.def, 0.05D, 2.0D));
        builder.pop();

        COMMON_SPEC = builder.build();
    }

    private FinanceConfig() {}

    public static double defaultDividendRatio() {
        return DEFAULT_DIVIDEND_RATIO.get();
    }

    public static int defaultDividendCycleDays() {
        return DEFAULT_DIVIDEND_CYCLE_DAYS.get();
    }

    public static int bankruptcyRiskDays() {
        return BANKRUPTCY_RISK_DAYS.get();
    }

    public static double bankruptcyCashRiskMultiplier() {
        return BANKRUPTCY_CASH_RISK_MULTIPLIER.get();
    }

    /** 某一档机器每天的维护费；档位超出 1～3 时按最近的一档算。 */
    public static long machineMaintenance(int tier) {
        if (tier <= 1) return MACHINE_MAINTENANCE_TIER1.get();
        if (tier == 2) return MACHINE_MAINTENANCE_TIER2.get();
        return MACHINE_MAINTENANCE_TIER3.get();
    }

    public static int inventoryStorageFeeBasisPoints() {
        return INVENTORY_STORAGE_FEE_BPS.get();
    }

    public static int storageLevelDiscountBasisPoints() {
        return STORAGE_LEVEL_DISCOUNT_BPS.get();
    }

    public static int managementLevelDiscountBasisPoints() {
        return MANAGEMENT_LEVEL_DISCOUNT_BPS.get();
    }

    public static double minProposalParticipationRatio() {
        return MIN_PROPOSAL_PARTICIPATION_RATIO.get();
    }

    public static int maxPriceAlertsPerPlayer() {
        return MAX_PRICE_ALERTS_PER_PLAYER.get();
    }

    public static boolean minecraftFirstMode() {
        return MINECRAFT_FIRST_MODE.get();
    }

    public static boolean requirePhysicalTerminal() {
        return REQUIRE_PHYSICAL_TERMINAL.get();
    }

    public static boolean enablePortableLedger() {
        return ENABLE_PORTABLE_LEDGER.get();
    }

    public static boolean legacyFullScreenKeybind() {
        return LEGACY_FULL_SCREEN_KEYBIND.get();
    }

    public static boolean warehouseCapacityEnabled() {
        return WAREHOUSE_CAPACITY_ENABLED.get();
    }

    public static boolean contractsEnabled() {
        return CONTRACTS_ENABLED.get();
    }

    public static boolean advancedFinanceRequiresTerminal() {
        return ADVANCED_FINANCE_REQUIRES_TERMINAL.get();
    }

    public static boolean adminConsoleRequiresPermission() {
        return ADMIN_CONSOLE_REQUIRES_PERMISSION.get();
    }

    public static double terminalInteractionDistance() {
        return TERMINAL_INTERACTION_DISTANCE.get();
    }

    public static boolean worldEconomyGlobalBroadcasts() {
        return WORLD_ECONOMY_GLOBAL_BROADCASTS.get();
    }

    public static int warehouseCapacity(int tier) {
        int index = Math.max(0, Math.min(2, tier - 1));
        int value = WAREHOUSE_CAPACITIES[index].get();
        if (index > 0) value = Math.max(value, warehouseCapacity(index));
        return value;
    }

    public static int warehouseTransferLimit(int tier) {
        int index = Math.max(0, Math.min(2, tier - 1));
        int value = WAREHOUSE_TRANSFER_LIMITS[index].get();
        return Math.min(warehouseCapacity(tier), value);
    }

    public static int factoryThroughput(int tier) {
        int index = Math.max(0, Math.min(2, tier - 1));
        return FACTORY_THROUGHPUT[index].get();
    }

    public static int logisticsMaxActivePerPlayer() {
        return LOGISTICS_MAX_ACTIVE_PLAYER.get();
    }

    public static int logisticsMaxActivePerCompany() {
        return LOGISTICS_MAX_ACTIVE_COMPANY.get();
    }

    public static int logisticsMaxCargoUnits() {
        return LOGISTICS_MAX_CARGO_UNITS.get();
    }

    public static int logisticsDefaultDeadlineDays() {
        return LOGISTICS_DEADLINE_DAYS.get();
    }

    public static int settlementMaxActivePerPlayer() {
        return SETTLEMENT_MAX_ACTIVE_PLAYER.get();
    }

    public static int settlementMaxOpen() {
        return SETTLEMENT_MAX_OPEN.get();
    }

    public static int settlementDeadlineDays() {
        return SETTLEMENT_DEADLINE_DAYS.get();
    }

    public static int settlementRewardBasisPoints() {
        return SETTLEMENT_REWARD_BPS.get();
    }

    public static boolean explorationEnabled() {
        return EXPLORATION_ENABLED.get();
    }

    public static int explorationCooldownDays() {
        return EXPLORATION_COOLDOWN_DAYS.get();
    }

    public static int explorationMaxDistance() {
        return EXPLORATION_MAX_DISTANCE.get();
    }

    public static int explorationDeadlineDays() {
        return EXPLORATION_DEADLINE_DAYS.get();
    }

    public static long explorationReward() {
        return EXPLORATION_REWARD.get();
    }

    public static int regionalHistoryDays() {
        return REGIONAL_HISTORY_DAYS.get();
    }

    public static int maxCollateralAgreements() {
        return Math.min(4096, COLLATERAL_MAX_ACTIVE.get());
    }

    public static int maxHedgeObjectives() {
        return Math.min(4096, HEDGE_MAX_OBJECTIVES.get());
    }

    public static int collateralDailyBatch() {
        return Math.min(512, RISK_DAILY_BATCH.get());
    }

    public static int collateralInitialLtvBps() {
        return Math.min(6000, COLLATERAL_INITIAL_LTV_BPS.get());
    }

    public static int collateralMaintenanceLtvBps() {
        return Math.max(collateralInitialLtvBps(), COLLATERAL_MAINTENANCE_LTV_BPS.get());
    }

    public static int collateralLiquidationLtvBps() {
        return Math.max(collateralMaintenanceLtvBps(), COLLATERAL_LIQUIDATION_LTV_BPS.get());
    }

    public static int maxConditionalStockOrdersPerPlayer() {
        return MAX_CONDITIONAL_STOCK_ORDERS_PER_PLAYER.get();
    }

    public static double stockMarketMakerSpread() {
        return STOCK_MARKET_MAKER_SPREAD.get();
    }

    /** 发起要约需事前持股比例（基点，如 3000 = 30%） */
    public static int takeoverMinHoldingBps() {
        return TAKEOVER_MIN_HOLDING_BPS.get();
    }

    /** 强制私有化所需持股比例（基点，如 9000 = 90%） */
    public static int takeoverSqueezeOutBps() {
        return TAKEOVER_SQUEEZE_OUT_BPS.get();
    }

    /** 要约价下限倍数（相对 fairValue） */
    public static double takeoverPriceFloorRatio() {
        return TAKEOVER_PRICE_FLOOR_RATIO.get();
    }

    /** 要约价上限倍数（相对 fairValue） */
    public static double takeoverPriceCapRatio() {
        return TAKEOVER_PRICE_CAP_RATIO.get();
    }

    /** 要约期下限（MC 天） */
    public static int takeoverMinDurationDays() {
        return TAKEOVER_MIN_DURATION_DAYS.get();
    }

    /** 要约期上限（MC 天） */
    public static int takeoverMaxDurationDays() {
        return TAKEOVER_MAX_DURATION_DAYS.get();
    }

    public static double stockTargetInventoryRatio() {
        return STOCK_TARGET_INVENTORY_RATIO.get();
    }

    public static double stockMaxInventoryMultiplier() {
        return STOCK_MAX_INVENTORY_MULTIPLIER.get();
    }

    public static double stockMaxTradeFloatRatio() {
        return STOCK_MAX_TRADE_FLOAT_RATIO.get();
    }

    public static double ipoMarketMakerReserveRatio() {
        return IPO_MARKET_MAKER_RESERVE_RATIO.get();
    }

    // Phase 2 做市商报价

    public static boolean marketMakerTargetPriceEnabled() {
        return MM_TARGET_PRICE_STRATEGY_ENABLED.get();
    }

    public static boolean marketMakerInventorySkewEnabled() {
        return MM_INVENTORY_SKEW_STRATEGY_ENABLED.get();
    }

    public static boolean marketMakerVolatilitySpreadEnabled() {
        return MM_VOLATILITY_SPREAD_STRATEGY_ENABLED.get();
    }

    public static double marketMakerSkewFactor() {
        return MM_INVENTORY_SKEW_FACTOR.get();
    }

    public static double marketMakerVolatilityFactor() {
        return MM_VOLATILITY_SPREAD_FACTOR.get();
    }

    public static double marketMakerMaxSkew() {
        return MM_MAX_INVENTORY_SKEW.get();
    }

    public static double marketMakerMaxVolatilityAdjust() {
        return MM_MAX_VOLATILITY_ADJUST.get();
    }

    public static double marketMakerCircuitBreakerThreshold() {
        return MM_CIRCUIT_BREAKER_THRESHOLD.get();
    }

    public static double marketMakerTargetPriceDeviationThreshold() {
        return MM_TARGET_PRICE_DEVIATION_THRESHOLD.get();
    }

    // ---- Phase 4 央行联动 ----

    /** 央行跨市场做市商健康度检查总开关。 */
    public static boolean cbMmHealthCheckEnabled() {
        return CB_MM_HEALTH_CHECK_ENABLED.get();
    }

    /** 央行对期货做市商单次紧急贷款上限。 */
    public static long cbMmEmergencyLoanCap() {
        return CB_MM_EMERGENCY_LOAN_CAP.get();
    }

    /** 紧急贷款每日还款比例（基点）。 */
    public static int cbMmLoanRepaymentBps() {
        return CB_MM_LOAN_REPAYMENT_BPS.get();
    }

    public static int villagePopulation() {
        return VILLAGE_POPULATION.get();
    }

    public static long villagePerCapitaBudget() {
        return VILLAGE_PER_CAPITA_BUDGET.get();
    }

    public static int villageBudgetReserveDays() {
        return VILLAGE_BUDGET_RESERVE_DAYS.get();
    }

    public static int villagePremiumMinBps() {
        return VILLAGE_PREMIUM_MIN_BPS.get();
    }

    public static int villagePremiumMaxBps() {
        return Math.max(villagePremiumMinBps(), VILLAGE_PREMIUM_MAX_BPS.get());
    }

    public static int villageFairToleranceBps() {
        return VILLAGE_FAIR_TOLERANCE_BPS.get();
    }

    public static int villageGougeToleranceBps() {
        return Math.max(villageFairToleranceBps(), VILLAGE_GOUGE_TOLERANCE_BPS.get());
    }

    public static int villageBoycottFavor() {
        return VILLAGE_BOYCOTT_FAVOR.get();
    }

    public static int villageTrustFavor() {
        return VILLAGE_TRUST_FAVOR.get();
    }

    public static int factorySpeedPercent() {
        return FACTORY_SPEED_PERCENT.get();
    }
    // ---- 单人经济：NPC 投资者 ----
    public static boolean npcInvestorsEnabled() {
        return NPC_INVESTORS_ENABLED.get();
    }

    public static long npcInitialCapitalEach() {
        return NPC_INITIAL_CAPITAL_EACH.get();
    }

    public static long npcDailyBudget() {
        return NPC_DAILY_BUDGET.get();
    }

    public static long npcPoolReserveFloor() {
        return NPC_POOL_RESERVE_FLOOR.get();
    }

    public static int npcMaxHoldingPercentEach() {
        return NPC_MAX_HOLDING_PERCENT_EACH.get();
    }

    public static int npcMaxCombinedHoldingPercent() {
        return NPC_MAX_COMBINED_HOLDING_PERCENT.get();
    }

    public static int npcMaxFloatPercentPerDay() {
        return NPC_MAX_FLOAT_PERCENT_PER_DAY.get();
    }

    // ---- 单人经济：NPC 竞争公司 ----
    public static boolean rivalsEnabled() {
        return RIVALS_ENABLED.get();
    }

    public static int rivalsMaxCompanies() {
        return RIVALS_MAX_COMPANIES.get();
    }

    public static int rivalsSpawnCooldownDays() {
        return RIVALS_SPAWN_COOLDOWN_DAYS.get();
    }

    public static long rivalsSeedCapital() {
        return RIVALS_SEED_CAPITAL.get();
    }

    public static boolean rivalsIpoEnabled() {
        return RIVALS_IPO_ENABLED.get();
    }

    public static boolean rivalsContractsEnabled() {
        return RIVALS_CONTRACTS_ENABLED.get();
    }

    public static boolean rivalsHostileTakeovers() {
        return RIVALS_HOSTILE_TAKEOVERS.get();
    }

    public static boolean rivalsActivismEnabled() {
        return RIVALS_ACTIVISM_ENABLED.get();
    }

    public static boolean settlementAutoDiscover() {
        return SETTLEMENT_AUTO_DISCOVER.get();
    }

    public static double insuranceAccidentChancePercent() {
        return INSURANCE_ACCIDENT_CHANCE.get();
    }

    public static int insuranceAccidentMaxLossPercent() {
        return INSURANCE_ACCIDENT_MAX_LOSS.get();
    }

    public static boolean cashEnabled() {
        return CASH_ENABLED.get();
    }

    public static long simpleStarterGrant() {
        return SIMPLE_STARTER_GRANT.get();
    }

    public static long simpleStockUnlockBalance() {
        return SIMPLE_STOCK_UNLOCK.get();
    }

    public static long simpleIpoMinValuation() {
        return SIMPLE_IPO_MIN_VALUATION.get();
    }
    /** player / always / never；其他值按 player 处理。 */
    public static String simpleAdvancedMode() {
        String mode = SIMPLE_ADVANCED_MODE.get();
        return "always".equals(mode) || "never".equals(mode) ? mode : "player";
    }

    public static List<Long> reserveTargets() {
        List<Long> out = new ArrayList<>();
        for (String part : RESERVE_TARGETS.get().split(",")) {
            try {
                long v = Long.parseLong(part.trim());
                if (v > 0 && v <= 10_000_000L) out.add(v);
            } catch (NumberFormatException ignored) {
            }
        }
        return out.isEmpty() ? List.of(1_000L, 500L, 200L) : List.copyOf(out);
    }

    public static int reserveRestockBpsPerDay() {
        return RESERVE_RESTOCK_BPS.get();
    }

    public static int reserveSurplusDemandBpsPerDay() {
        return RESERVE_SURPLUS_DEMAND_BPS.get();
    }

    public static long reserveTier2UnlockValue() {
        return RESERVE_TIER2_VALUE.get();
    }

    public static long reserveTier3UnlockValue() {
        return RESERVE_TIER3_VALUE.get();
    }

    public static int reserveFloorBps() {
        return RESERVE_FLOOR_BPS.get();
    }

    public static int reserveFloorDailyCap() {
        return RESERVE_FLOOR_DAILY_CAP.get();
    }

    public static long reservePlayerGoodsReference() {
        return RESERVE_PLAYER_GOODS_REFERENCE.get();
    }

    public static double reservePriceElasticity() {
        return RESERVE_ELASTICITY.get();
    }

    /** 不用解锁、直接打开的进阶玩法名（小写）；含 all 表示全部直接打开。 */
    public static java.util.Set<String> simpleExtras() {
        return nameSet(SIMPLE_EXTRAS.get());
    }

    public static boolean simpleWeeklyLeaderboard() {
        return SIMPLE_WEEKLY_LEADERBOARD.get();
    }

    /** 整个服务器关掉的进阶玩法名（小写）；含 all 表示全部关掉。 */
    public static java.util.Set<String> simpleDisabled() {
        return nameSet(SIMPLE_DISABLED.get());
    }

    private static java.util.Set<String> nameSet(String csv) {
        java.util.Set<String> out = new java.util.TreeSet<>();
        for (String part : csv.split(",")) {
            String name = part.trim().toLowerCase(java.util.Locale.ROOT);
            if (!name.isEmpty() && name.length() <= 16) out.add(name);
        }
        return out;
    }

    public static long cashEmeraldToCash() {
        return CASH_EMERALD_TO_CASH.get();
    }

    public static long cashCashToEmerald() {
        return CASH_CASH_TO_EMERALD.get();
    }

    public static int cashEmeraldImpactBps() {
        return CASH_EMERALD_IMPACT_BPS.get();
    }

    public static int cashEmeraldRecoveryPercent() {
        return CASH_EMERALD_RECOVERY_PERCENT.get();
    }

    public static boolean npcVotingEnabled() {
        return NPC_VOTING_ENABLED.get();
    }

    public static int npcTenderPremiumPercent(finance.npcinvestor.NpcInvestorArchetype archetype) {
        if (archetype == null) return NPC_TENDER_PREMIUM_DIVIDEND.def;
        return switch (archetype) {
            case DIVIDEND -> NPC_TENDER_PREMIUM_DIVIDEND.get();
            case GROWTH -> NPC_TENDER_PREMIUM_GROWTH.get();
            case SPECULATOR -> NPC_TENDER_PREMIUM_SPECULATOR.get();
            case VALUE -> NPC_TENDER_PREMIUM_VALUE.get();
        };
    }

    // ---- 中央银行 ----
    public static int cbInflationTargetBps() {
        return CB_INFLATION_TARGET_BPS.get();
    }

    public static int cbInflationBandBps() {
        return CB_INFLATION_BAND_BPS.get();
    }

    public static boolean cbReserveAuto() {
        return CB_RESERVE_AUTO.get();
    }

    public static int cbReserveMinBps() {
        return CB_RESERVE_MIN_BPS.get();
    }

    public static int cbReserveMaxBps() {
        return Math.max(cbReserveMinBps(), CB_RESERVE_MAX_BPS.get());
    }

    public static int cbReserveStepBps() {
        return CB_RESERVE_STEP_BPS.get();
    }

    public static boolean cbOmoEnabled() {
        return CB_OMO_ENABLED.get();
    }

    public static int cbOmoMaxDailyBps() {
        return CB_OMO_MAX_DAILY_BPS.get();
    }

    public static int cbOmoExcessTargetBps() {
        return CB_OMO_EXCESS_TARGET_BPS.get();
    }

    public static double cbOmoGapResponse() {
        return CB_OMO_GAP_RESPONSE.get();
    }

    public static boolean cbStabilizationEnabled() {
        return CB_STABILIZATION_ENABLED.get();
    }

    public static int cbStabilizationBandBps() {
        return CB_STABILIZATION_BAND_BPS.get();
    }

    public static int cbStabilizationMaxDailyBps() {
        return CB_STABILIZATION_MAX_DAILY_BPS.get();
    }

    // ---- 宏观实体经济 ----
    public static boolean macroEnabled() {
        return MACRO_ENABLED.get();
    }

    public static boolean macroLaborEnabled() {
        return MACRO_LABOR_ENABLED.get();
    }

    public static int macroMinPopulation() {
        return MACRO_MIN_POPULATION.get();
    }

    public static int macroMaxPopulation() {
        return MACRO_MAX_POPULATION.get();
    }

    public static int macroGrowthMaxBps() {
        return MACRO_GROWTH_MAX_BPS.get();
    }

    public static int macroMigrationMaxBps() {
        return MACRO_MIGRATION_MAX_BPS.get();
    }

    public static int macroMigrationGapBps() {
        return MACRO_MIGRATION_GAP_BPS.get();
    }

    public static int macroPriceStepBps() {
        return MACRO_PRICE_STEP_BPS.get();
    }

    public static double macroRateDemandSensitivity() {
        return MACRO_RATE_DEMAND_SENSITIVITY.get();
    }

    public static double macroRateStockSensitivity() {
        return MACRO_RATE_STOCK_SENSITIVITY.get();
    }

    public static int macroLaborRadius() {
        return MACRO_LABOR_RADIUS.get();
    }

    public static long macroDefaultWage() {
        return MACRO_DEFAULT_WAGE.get();
    }

    public static long macroBaseReservationWage() {
        return MACRO_BASE_RESERVATION_WAGE.get();
    }

    public static boolean macroWorldFeedback() {
        return MACRO_WORLD_FEEDBACK.get();
    }

    public static boolean macroNewsEnabled() {
        return MACRO_NEWS_ENABLED.get();
    }

    public static int macroNewsIntervalDays() {
        return MACRO_NEWS_INTERVAL_DAYS.get();
    }

    public static int macroNewsExtraGapDays() {
        return MACRO_NEWS_EXTRA_GAP_DAYS.get();
    }

    public static long macroRadarCost() {
        return MACRO_RADAR_COST.get();
    }

    public static int macroRadarCooldownDays() {
        return MACRO_RADAR_COOLDOWN_DAYS.get();
    }

    // ---- 商队商路 ----
    public static int caravanMaxRoutesPerPlayer() {
        return CARAVAN_MAX_ROUTES_PER_PLAYER.get();
    }

    public static int caravanMaxQtyPerTrip() {
        return CARAVAN_MAX_QTY_PER_TRIP.get();
    }

    public static long caravanBlocksPerDay() {
        return CARAVAN_BLOCKS_PER_DAY.get();
    }

    public static int caravanRaidBaseBps() {
        return CARAVAN_RAID_BASE_BPS.get();
    }

    public static int caravanRaidMinBps() {
        return CARAVAN_RAID_MIN_BPS.get();
    }

    public static int caravanRaidMaxBps() {
        return CARAVAN_RAID_MAX_BPS.get();
    }

    public static int caravanRaidPerKmBps() {
        return CARAVAN_RAID_PER_KM_BPS.get();
    }

    public static int caravanRaidPerKmCapBps() {
        return CARAVAN_RAID_PER_KM_CAP_BPS.get();
    }

    public static int caravanRaidAlertBonusBps() {
        return CARAVAN_RAID_ALERT_BONUS_BPS.get();
    }

    public static int caravanGuardReduceBps() {
        return CARAVAN_GUARD_REDUCE_BPS.get();
    }

    public static long caravanGuardWage1() {
        return CARAVAN_GUARD_WAGE_1.get();
    }

    public static long caravanGuardWage2() {
        return CARAVAN_GUARD_WAGE_2.get();
    }

    public static long caravanGuardWage3() {
        return CARAVAN_GUARD_WAGE_3.get();
    }

    public static int caravanRaidLossRatioBps() {
        return CARAVAN_RAID_LOSS_RATIO_BPS.get();
    }

    public static int caravanInsureMarginBps() {
        return CARAVAN_INSURE_MARGIN_BPS.get();
    }

    public static int caravanSaturationPerUnitBps() {
        return CARAVAN_SATURATION_PER_UNIT_BPS.get();
    }

    public static int caravanSaturationDecayBps() {
        return CARAVAN_SATURATION_DECAY_BPS.get();
    }

    public static int caravanSellPriceCapMultiplier() {
        return CARAVAN_SELL_PRICE_CAP_MULTIPLIER.get();
    }

    public static long ipoFee() {
        return IPO_FEE.get();
    }

    /** 融资融券最大杠杆倍数（如 2 = 2x） */
    public static int marginMaxLeverage() {
        return MARGIN_MAX_LEVERAGE.get();
    }

    /** 维持保证金率（基点，如 13000 = 130%） */
    public static int marginMaintenanceBps() {
        return MARGIN_MAINTENANCE_BPS.get();
    }

    /** 预警线（基点，如 15000 = 150%） */
    public static int marginWarningBps() {
        return MARGIN_WARNING_BPS.get();
    }

    /** 融资年利率（0.086 = 8.6%） */
    public static double marginFinanceAnnualRate() {
        return MARGIN_FINANCE_ANNUAL_RATE.get();
    }

    /** 融券年利率（0.106 = 10.6%） */
    public static double marginShortAnnualRate() {
        return MARGIN_SHORT_ANNUAL_RATE.get();
    }

    /** 券池总额占流通股比例（0.05 = 5%） */
    public static double marginPoolRatio() {
        return MARGIN_POOL_RATIO.get();
    }

    /** 单人借券上限占券池比例（0.20 = 20%） */
    public static double marginPerPlayerBorrowRatio() {
        return MARGIN_PER_PLAYER_BORROW_RATIO.get();
    }

    public static long capitalProjectGovernanceThreshold() {
        return CAPITAL_PROJECT_GOVERNANCE_THRESHOLD.get();
    }

    public static int capitalProjectMaxDurationDays() {
        return CAPITAL_PROJECT_MAX_DURATION_DAYS.get();
    }

    public static int defaultBenchmarkRateBasisPoints() {
        return DEFAULT_BENCHMARK_RATE_BPS.get();
    }

    public static int minBenchmarkRateBasisPoints() {
        return MIN_BENCHMARK_RATE_BPS_VALUE;
    }

    public static int maxBenchmarkRateBasisPoints() {
        return MAX_BENCHMARK_RATE_BPS_VALUE;
    }

    public static int maxCorporateBonds() {
        return MAX_BONDS.get();
    }

    public static long treasuryBondMinAmount() {
        return TREASURY_BOND_MIN_AMOUNT.get();
    }

    public static long treasuryBondMaxPerPlayer() {
        return TREASURY_BOND_MAX_PER_PLAYER.get();
    }

    public static long treasuryBondMaxIssuePerTerm() {
        return TREASURY_BOND_MAX_ISSUE_PER_TERM.get();
    }

    public static long treasuryBondEarlyFee() {
        return TREASURY_BOND_EARLY_FEE.get();
    }

    public static int maxCompanyLoans() {
        return MAX_LOANS.get();
    }

    public static int loanGraceDays() {
        return LOAN_GRACE_DAYS.get();
    }

    public static int indexBasePoints() {
        return INDEX_BASE_POINTS.get();
    }

    public static int annualMcDays() {
        return ANNUAL_MC_DAYS.get();
    }

    public static int maxBondTermDays() {
        return MAX_BOND_TERM_DAYS.get();
    }

    public static double maxBondFinancingRatio() {
        return MAX_BOND_FINANCING_RATIO.get();
    }

    public static int maxLoanTermDays() {
        return MAX_LOAN_TERM_DAYS.get();
    }

    public static int maxContractRateBasisPoints() {
        return MAX_CONTRACT_RATE_BPS.get();
    }

    public static String policyRateMode() {
        String mode = POLICY_RATE_MODE.get();
        String upper = mode == null ? "" : mode.trim().toUpperCase(java.util.Locale.ROOT);
        return upper.equals("AUTO") || upper.equals("ADVISORY") || upper.equals("MANUAL")
                ? upper
                : POLICY_RATE_MODE.def;
    }

    public static int policyNeutralRateBps() {
        return POLICY_NEUTRAL_RATE_BPS.get();
    }

    public static int policyMeetingIntervalDays() {
        return POLICY_MEETING_INTERVAL_DAYS.get();
    }

    public static int policyGuidanceLeadDays() {
        return POLICY_GUIDANCE_LEAD_DAYS.get();
    }

    public static int policyMaxStepBps() {
        return POLICY_MAX_STEP_BPS.get();
    }

    public static int policyMinStepBps() {
        return POLICY_MIN_STEP_BPS.get();
    }

    public static double policyInflationResponse() {
        return POLICY_INFLATION_RESPONSE.get();
    }

    public static double policySlackResponse() {
        return POLICY_SLACK_RESPONSE.get();
    }

    public static double mediumDefaultRatePercent() {
        return MEDIUM_DEFAULT_RATE.get();
    }

    public static double highDefaultRatePercent() {
        return HIGH_DEFAULT_RATE.get();
    }

    public static int creditAaaMinimumScore() {
        return CREDIT_AAA_SCORE.get();
    }

    public static int creditAaMinimumScore() {
        return Math.min(creditAaaMinimumScore(), CREDIT_AA_SCORE.get());
    }

    public static int creditAMinimumScore() {
        return Math.min(creditAaMinimumScore(), CREDIT_A_SCORE.get());
    }

    public static int creditBbbMinimumScore() {
        return Math.min(creditAMinimumScore(), CREDIT_BBB_SCORE.get());
    }

    public static int creditBbMinimumScore() {
        return Math.min(creditBbbMinimumScore(), CREDIT_BB_SCORE.get());
    }

    public static int creditBMinimumScore() {
        return Math.min(creditBbMinimumScore(), CREDIT_B_SCORE.get());
    }

    public static boolean futuresEnabled() {
        try {
            return FUTURES_ENABLED.get();
        } catch (IllegalStateException ex) {
            return FUTURES_ENABLED.def;
        }
    }

    public static long futuresContractSize() {
        return FUTURES_CONTRACT_SIZE.get();
    }

    public static int futuresInitialMarginBps() {
        return Math.max(3, FUTURES_INITIAL_MARGIN_BPS.get());
    }

    public static int futuresMaintenanceMarginBps() {
        return Math.min(futuresInitialMarginBps() - 1, Math.max(2, FUTURES_MAINTENANCE_MARGIN_BPS.get()));
    }

    public static int futuresLiquidationMarginBps() {
        return Math.min(futuresMaintenanceMarginBps() - 1, Math.max(1, FUTURES_LIQUIDATION_MARGIN_BPS.get()));
    }

    public static long futuresMaxPosition() {
        return FUTURES_MAX_POSITION.get();
    }

    public static int futuresMaxOrders() {
        return FUTURES_MAX_ORDERS.get();
    }

    public static long futuresMinimumTick() {
        return FUTURES_MIN_TICK.get();
    }

    public static int futuresLiquidationSlippageBps() {
        return FUTURES_LIQUIDATION_SLIPPAGE_BPS.get();
    }

    public static long futuresInitialGuaranteeFund() {
        return FUTURES_GUARANTEE_FUND.get();
    }

    public static int futuresFinalSettlementWindowDays() {
        return FUTURES_SETTLEMENT_WINDOW.get();
    }

    public static long futuresMinimumSettlementVolume() {
        return FUTURES_MIN_SETTLEMENT_VOLUME.get();
    }

    public static int futuresMaxSpotDeviationBps() {
        return FUTURES_MAX_SPOT_DEVIATION_BPS.get();
    }

    // Phase 3 期货做市商
    public static double futuresMmSpread() {
        return FUTURES_MM_SPREAD.get();
    }

    public static double futuresMmNetPositionRatio() {
        return FUTURES_MM_NET_POSITION_RATIO.get();
    }

    public static int futuresMmWashTradeWindowMinutes() {
        return FUTURES_MM_WASH_TRADE_WINDOW_MINUTES.get();
    }

    public static int futuresMmWashTradeMaxTrades() {
        return FUTURES_MM_WASH_TRADE_MAX_TRADES.get();
    }

    public static long futuresMmWashTradeMaxNotional() {
        return FUTURES_MM_WASH_TRADE_MAX_NOTIONAL.get();
    }

    public static int futuresMmWashTradeCooldownMinutes() {
        return FUTURES_MM_WASH_TRADE_COOLDOWN_MINUTES.get();
    }

    public static boolean futuresMmCircuitBreakerEnabled() {
        try {
            return FUTURES_MM_CIRCUIT_BREAKER_ENABLED.get();
        } catch (IllegalStateException ex) {
            return FUTURES_MM_CIRCUIT_BREAKER_ENABLED.def;
        }
    }

    public static int futuresMmCircuitBreakerDeviationBps() {
        return FUTURES_MM_CIRCUIT_BREAKER_DEVIATION_BPS.get();
    }

    public static long futuresMmInitialCapital() {
        return FUTURES_MM_INITIAL_CAPITAL.get();
    }

    public static boolean bankingEnabled() {
        try {
            return BANKING_ENABLED.get();
        } catch (IllegalStateException e) {
            return BANKING_ENABLED.def;
        }
    }

    public static int defaultBankCount() {
        return DEFAULT_BANK_COUNT.get();
    }

    public static long bankInitialCapital() {
        return BANK_INITIAL_CAPITAL.get();
    }

    public static int bankDemandSpreadBps() {
        return BANK_DEMAND_SPREAD.get();
    }

    public static int bankTimeSpreadBps() {
        return BANK_TIME_SPREAD.get();
    }

    public static int bankLoanSpreadBps() {
        return BANK_LOAN_SPREAD.get();
    }

    public static int bankDemandReserveBps() {
        return BANK_DEMAND_RESERVE_BPS.get();
    }

    public static int bankTimeReserveBps() {
        return Math.min(bankDemandReserveBps(), BANK_TIME_RESERVE_BPS.get());
    }

    public static long bankMinimumReserve() {
        return BANK_MIN_RESERVE.get();
    }

    public static int bankMinimumCapitalBps() {
        return BANK_MIN_CAPITAL_BPS.get();
    }

    public static int bankSingleBorrowerLimitBps() {
        return BANK_SINGLE_BORROWER_BPS.get();
    }

    public static int bankCentralPenaltyBps() {
        return BANK_CB_PENALTY_BPS.get();
    }

    public static int bankInsuranceFeeBps() {
        return BANK_INSURANCE_FEE_BPS.get();
    }

    public static long bankInsuranceLimit() {
        return BANK_INSURANCE_LIMIT.get();
    }

    public static int bankStressMaxRounds() {
        return BANK_STRESS_ROUNDS.get();
    }

    public static int bankReferenceTtlDays() {
        return BANK_REFERENCE_TTL_DAYS.get();
    }
    // 定期存款
    public static List<Integer> timeDepositTerms() {
        List<Integer> terms = new ArrayList<>();
        for (String part : BANK_TIME_DEPOSIT_TERMS.get().split(",")) {
            try {
                int t = Integer.parseInt(part.trim());
                if (t >= 2 && t <= 3_650 && !terms.contains(t)) terms.add(t);
            } catch (NumberFormatException ignored) {
            }
        }
        if (terms.isEmpty()) terms.addAll(List.of(7, 30, 90));
        Collections.sort(terms);
        return List.copyOf(terms);
    }

    public static long timeDepositMinAmount() {
        return BANK_TIME_DEPOSIT_MIN_AMOUNT.get();
    }

    public static long timeDepositMaxPerPlayer() {
        return BANK_TIME_DEPOSIT_MAX_PER_PLAYER.get();
    }

    public static long timeDepositEarlyFee() {
        return BANK_TIME_DEPOSIT_EARLY_FEE.get();
    }

    public static int earningsQuarterDays() {
        return EARNINGS_QUARTER_DAYS.get();
    }

    public static int earningsExpectationQuarters() {
        return EARNINGS_EXPECTATION_QUARTERS.get();
    }

    public static int earningsSurpriseCapBps() {
        return EARNINGS_SURPRISE_CAP_BPS.get();
    }

    public static int earningsReactionBps() {
        return EARNINGS_REACTION_BPS.get();
    }

    public static int earningsMaxShockBps() {
        return EARNINGS_MAX_SHOCK_BPS.get();
    }

    public static int earningsFvPassThroughBps() {
        return EARNINGS_FV_PASS_THROUGH_BPS.get();
    }

    public static int earningsHistoryKept() {
        return EARNINGS_HISTORY_KEPT.get();
    }

    public static boolean earningsExDividend() {
        return EARNINGS_EX_DIVIDEND.get();
    }

    public static boolean bankRunEnabled() {
        try {
            return BANK_RUN_ENABLED.get();
        } catch (IllegalStateException e) {
            return BANK_RUN_ENABLED.def;
        }
    }

    public static boolean bankRunAutoResolve() {
        try {
            return BANK_RUN_AUTO_RESOLVE.get();
        } catch (IllegalStateException e) {
            return BANK_RUN_AUTO_RESOLVE.def;
        }
    }

    public static int bankRunPanicDecay() {
        return BANK_RUN_PANIC_DECAY.get();
    }

    public static int bankRunMaxDailyShareBps() {
        return BANK_RUN_MAX_SHARE_BPS.get();
    }

    public static int bankRunDailyCapBps() {
        return BANK_RUN_DAILY_CAP_BPS.get();
    }

    public static int bankRunCapPanicThreshold() {
        return BANK_RUN_CAP_PANIC.get();
    }

    public static int bankRunFreezePanicThreshold() {
        return BANK_RUN_FREEZE_PANIC.get();
    }

    public static int bankRunFailPanicThreshold() {
        return BANK_RUN_FAIL_PANIC.get();
    }

    public static int bankRunNpcSeedBps() {
        return BANK_RUN_NPC_SEED_BPS.get();
    }

    public static int bankRunNpcTimeShareBps() {
        return BANK_RUN_NPC_TIME_BPS.get();
    }

    public static int bankRunNpcDefaultBps() {
        return BANK_RUN_NPC_DEFAULT_BPS.get();
    }

    public static int bankRunRumorCooldownDays() {
        return BANK_RUN_RUMOR_COOLDOWN.get();
    }

    public static int bankRunIlliquidDays() {
        return BANK_RUN_ILLIQUID_DAYS.get();
    }

    public static double bankRunRumorChance() {
        return BANK_RUN_RUMOR_CHANCE.get();
    }

    public static long playerBankCharterFee() {
        return PLAYER_BANK_CHARTER_FEE.get();
    }

    public static long playerBankMinCapital() {
        return PLAYER_BANK_MIN_CAPITAL.get();
    }

    public static int playerBankMaxPerPlayer() {
        return PLAYER_BANK_MAX_PER_PLAYER.get();
    }

    public static int playerBankRelatedPartyLimitBps() {
        return PLAYER_BANK_RELATED_PARTY_LIMIT_BPS.get();
    }

    public static long playerBankDailyFine() {
        return PLAYER_BANK_DAILY_FINE.get();
    }

    public static int playerBankDividendLeverageBps() {
        return PLAYER_BANK_DIVIDEND_LEVERAGE_BPS.get();
    }
}
