package finance.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
    // ---- 要约收购（takeover） ----
    private static final int TAKEOVER_MIN_HOLDING_BPS_VALUE = 3_000; // 发起要约需事前持股 30%
    private static final int TAKEOVER_SQUEEZE_OUT_BPS_VALUE = 9_000; // 强制私有化线 90%
    private static final double TAKEOVER_PRICE_FLOOR_RATIO_VALUE = 0.8D; // 要约价下限 = 0.8 × fairValue
    private static final double TAKEOVER_PRICE_CAP_RATIO_VALUE = 1.5D; // 要约价上限 = 1.5 × fairValue
    private static final int TAKEOVER_MIN_DURATION_DAYS_VALUE = 3; // 要约期下限（MC 天）
    private static final int TAKEOVER_MAX_DURATION_DAYS_VALUE = 14; // 要约期上限（MC 天）
    // ---- 融资融券（margin） ----
    private static final int MARGIN_MAX_LEVERAGE_VALUE = 2; // 最大杠杆倍数 2x（初始保证金率 50%）
    private static final int MARGIN_MAINTENANCE_BPS_VALUE = 13_000; // 维持保证金率 130%
    private static final int MARGIN_WARNING_BPS_VALUE = 15_000; // 预警线 150%
    private static final double MARGIN_FINANCE_ANNUAL_RATE_VALUE = 0.086D; // 融资年利率 8.6%
    private static final double MARGIN_SHORT_ANNUAL_RATE_VALUE = 0.106D; // 融券年利率 10.6%
    private static final double MARGIN_POOL_RATIO_VALUE = 0.05D; // 券池 = 流通股 5%
    private static final double MARGIN_PER_PLAYER_BORROW_RATIO_VALUE = 0.20D; // 单人借券上限 = 券池 20%
    private static final double STOCK_TARGET_INVENTORY_RATIO_VALUE = 0.05D;
    private static final double STOCK_MAX_INVENTORY_MULTIPLIER_VALUE = 2.0D;
    private static final double STOCK_MAX_TRADE_FLOAT_RATIO_VALUE = 0.01D;
    private static final double IPO_MARKET_MAKER_RESERVE_RATIO_VALUE = 0.05D;
    // Phase 2 做市商报价
    private static final boolean MM_TARGET_PRICE_STRATEGY_ENABLED_VALUE = true;
    private static final boolean MM_INVENTORY_SKEW_STRATEGY_ENABLED_VALUE = true;
    private static final boolean MM_VOLATILITY_SPREAD_STRATEGY_ENABLED_VALUE = true;
    private static final double MM_INVENTORY_SKEW_FACTOR_VALUE = 0.5D;
    private static final double MM_VOLATILITY_SPREAD_FACTOR_VALUE = 0.5D;
    private static final double MM_MAX_INVENTORY_SKEW_VALUE = 0.10D;
    private static final double MM_MAX_VOLATILITY_ADJUST_VALUE = 0.10D;
    private static final double MM_CIRCUIT_BREAKER_THRESHOLD_VALUE = 0.30D;
    private static final double MM_TARGET_PRICE_DEVIATION_THRESHOLD_VALUE = 0.05D;
    // Phase 4 央行联动
    private static final boolean CB_MM_HEALTH_CHECK_ENABLED_VALUE = true;
    private static final long CB_MM_EMERGENCY_LOAN_CAP_VALUE = 5_000_000L;
    private static final int CB_MM_LOAN_REPAYMENT_BPS_VALUE = 2_000;
    private static final int FACTORY_COOK_TIME_BREAD_VALUE = 200;
    private static final int FACTORY_COOK_TIME_IRON_VALUE = 200;
    private static final int FACTORY_COOK_TIME_PLANKS_VALUE = 100;
    private static final long IPO_FEE_VALUE = 5_000L;
    private static final long CAPITAL_PROJECT_GOVERNANCE_THRESHOLD_VALUE = 50_000L;
    private static final int CAPITAL_PROJECT_MAX_DURATION_DAYS_VALUE = 60;
    private static final int DEFAULT_BENCHMARK_RATE_BPS_VALUE = 500;
    private static final int MIN_BENCHMARK_RATE_BPS_VALUE = 0;
    private static final int MAX_BENCHMARK_RATE_BPS_VALUE = 5_000;
    private static final int MAX_BONDS_VALUE = 256;
    private static final long TREASURY_BOND_MIN_AMOUNT_VALUE = 1_000L;
    private static final long TREASURY_BOND_MAX_PER_PLAYER_VALUE = 2_000_000L;
    private static final long TREASURY_BOND_MAX_ISSUE_PER_TERM_VALUE = 10_000_000L;
    private static final long TREASURY_BOND_EARLY_FEE_VALUE = 0L;
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
    // Phase 3 期货做市商
    private static final double FUTURES_MM_SPREAD_VALUE = 0.015D;
    private static final double FUTURES_MM_NET_POSITION_RATIO_VALUE = 0.10D;
    private static final int FUTURES_MM_WASH_TRADE_WINDOW_MINUTES_VALUE = 5;
    private static final int FUTURES_MM_WASH_TRADE_MAX_TRADES_VALUE = 6;
    private static final long FUTURES_MM_WASH_TRADE_MAX_NOTIONAL_VALUE = 50_000L;
    private static final int FUTURES_MM_WASH_TRADE_COOLDOWN_MINUTES_VALUE = 30;
    private static final boolean FUTURES_MM_CIRCUIT_BREAKER_ENABLED_VALUE = true;
    private static final int FUTURES_MM_CIRCUIT_BREAKER_DEVIATION_BPS_VALUE = 3_000;
    private static final long FUTURES_MM_INITIAL_CAPITAL_VALUE = 500_000L;
    // ---- 单人经济：NPC 投资者 ----
    private static final boolean NPC_INVESTORS_ENABLED_VALUE = true;
    private static final long NPC_INITIAL_CAPITAL_EACH_VALUE = 50_000L;
    private static final long NPC_DAILY_BUDGET_VALUE = 5_000L;
    private static final long NPC_POOL_RESERVE_FLOOR_VALUE = 1_000_000L;
    private static final int NPC_MAX_HOLDING_PERCENT_EACH_VALUE = 20;
    private static final int NPC_MAX_COMBINED_HOLDING_PERCENT_VALUE = 49;
    private static final int NPC_MAX_FLOAT_PERCENT_PER_DAY_VALUE = 2;
    private static final boolean NPC_VOTING_ENABLED_VALUE = true;
    private static final int NPC_TENDER_PREMIUM_DIVIDEND_VALUE = 35;
    private static final int NPC_TENDER_PREMIUM_GROWTH_VALUE = 30;
    private static final int NPC_TENDER_PREMIUM_SPECULATOR_VALUE = 10;
    private static final int NPC_TENDER_PREMIUM_VALUE_VALUE = 25;
    // ---- 商队商路 ----
    private static final int CARAVAN_MAX_ROUTES_PER_PLAYER_VALUE = 8;
    private static final int CARAVAN_MAX_QTY_PER_TRIP_VALUE = 1_024;
    private static final long CARAVAN_BLOCKS_PER_DAY_VALUE = 5_000L;
    private static final int CARAVAN_RAID_BASE_BPS_VALUE = 800;
    private static final int CARAVAN_RAID_MIN_BPS_VALUE = 100;
    private static final int CARAVAN_RAID_MAX_BPS_VALUE = 3_000;
    private static final int CARAVAN_RAID_PER_KM_BPS_VALUE = 20;
    private static final int CARAVAN_RAID_PER_KM_CAP_BPS_VALUE = 2_000;
    private static final int CARAVAN_RAID_ALERT_BONUS_BPS_VALUE = 3_000;
    private static final int CARAVAN_GUARD_REDUCE_BPS_VALUE = 2_000;
    private static final long CARAVAN_GUARD_WAGE_1_VALUE = 500L;
    private static final long CARAVAN_GUARD_WAGE_2_VALUE = 1_500L;
    private static final long CARAVAN_GUARD_WAGE_3_VALUE = 4_000L;
    private static final int CARAVAN_RAID_LOSS_RATIO_BPS_VALUE = 3_000;
    private static final int CARAVAN_INSURE_MARGIN_BPS_VALUE = 12_000;
    private static final int CARAVAN_SATURATION_PER_UNIT_BPS_VALUE = 2;
    private static final int CARAVAN_SATURATION_DECAY_BPS_VALUE = 1_500;
    private static final int CARAVAN_SELL_PRICE_CAP_MULTIPLIER_VALUE = 3;
    private static final boolean BANKING_ENABLED_VALUE=true;private static final int DEFAULT_BANK_COUNT_VALUE=3;
    private static final long BANK_INITIAL_CAPITAL_VALUE=5_000_000L,BANK_MIN_RESERVE_VALUE=10_000L,BANK_INSURANCE_LIMIT_VALUE=100_000L;
    private static final int BANK_DEMAND_SPREAD_VALUE=150,BANK_TIME_SPREAD_VALUE=75,BANK_LOAN_SPREAD_VALUE=250,
            BANK_DEMAND_RESERVE_BPS_VALUE=1_000,BANK_TIME_RESERVE_BPS_VALUE=500,BANK_MIN_CAPITAL_BPS_VALUE=800,
            BANK_SINGLE_BORROWER_BPS_VALUE=2_500,BANK_CB_PENALTY_BPS_VALUE=500,BANK_INSURANCE_FEE_BPS_VALUE=5,
            BANK_STRESS_ROUNDS_VALUE=8,BANK_REFERENCE_TTL_DAYS_VALUE=30;
    private static final String BANK_TIME_DEPOSIT_TERMS_VALUE="7,30,90";private static final long BANK_TIME_DEPOSIT_MIN_AMOUNT_VALUE=1_000L,BANK_TIME_DEPOSIT_MAX_PER_PLAYER_VALUE=1_000_000L,BANK_TIME_DEPOSIT_EARLY_FEE_VALUE=0L; // 定期存款
    private static final boolean BANK_RUN_ENABLED_VALUE=true,BANK_RUN_AUTO_RESOLVE_VALUE=true;
    private static final int BANK_RUN_PANIC_DECAY_VALUE=6,BANK_RUN_MAX_SHARE_BPS_VALUE=2_000,
            BANK_RUN_DAILY_CAP_BPS_VALUE=1_000,BANK_RUN_CAP_PANIC_VALUE=60,BANK_RUN_FREEZE_PANIC_VALUE=70,
            BANK_RUN_FAIL_PANIC_VALUE=85,BANK_RUN_NPC_SEED_BPS_VALUE=4_000,BANK_RUN_NPC_TIME_BPS_VALUE=3_000,
            BANK_RUN_NPC_DEFAULT_BPS_VALUE=800,BANK_RUN_RUMOR_COOLDOWN_VALUE=3,BANK_RUN_ILLIQUID_DAYS_VALUE=3;
    private static final double BANK_RUN_RUMOR_CHANCE_VALUE=0.25;
    private static final long PLAYER_BANK_CHARTER_FEE_VALUE=1_000_000L,PLAYER_BANK_MIN_CAPITAL_VALUE=10_000_000L,
            PLAYER_BANK_DAILY_FINE_VALUE=50_000L;
    private static final int PLAYER_BANK_MAX_PER_PLAYER_VALUE=1,PLAYER_BANK_RELATED_PARTY_LIMIT_BPS_VALUE=0,
            PLAYER_BANK_DIVIDEND_LEVERAGE_BPS_VALUE=800;
    private static final boolean MINECRAFT_FIRST_MODE_VALUE = true;
    private static final boolean REQUIRE_PHYSICAL_TERMINAL_VALUE = true;
    private static final boolean ENABLE_PORTABLE_LEDGER_VALUE = true;
    private static final boolean LEGACY_FULL_SCREEN_KEYBIND_VALUE = false;
    private static final boolean WAREHOUSE_CAPACITY_ENABLED_VALUE = true;
    private static final boolean CONTRACTS_ENABLED_VALUE = true;
    private static final boolean PLAYER_DRIVEN_COMPANY_PRODUCTION_VALUE = true;
    private static final boolean ALLOW_LEGACY_AUTOMATIC_COMPANY_PRODUCTION_VALUE = true;
    private static final boolean NEW_COMPANIES_PLAYER_DRIVEN_ONLY_VALUE = false;
    private static final double HYBRID_LEGACY_FALLBACK_RATIO_VALUE = 0.25D;
    private static final boolean ADVANCED_FINANCE_REQUIRES_TERMINAL_VALUE = true;
    private static final boolean ADMIN_CONSOLE_REQUIRES_PERMISSION_VALUE = true;
    private static final double TERMINAL_INTERACTION_DISTANCE_VALUE = 8.0D;
    private static final boolean WORLD_ECONOMY_GLOBAL_BROADCASTS_VALUE = true;
    private static final int EARNINGS_QUARTER_DAYS_VALUE = 30;
    private static final int EARNINGS_EXPECTATION_QUARTERS_VALUE = 3;
    private static final int EARNINGS_SURPRISE_CAP_BPS_VALUE = 5_000;
    private static final int EARNINGS_REACTION_BPS_VALUE = 30;
    private static final int EARNINGS_MAX_SHOCK_BPS_VALUE = 2_000;
    private static final int EARNINGS_FV_PASS_THROUGH_BPS_VALUE = 5_000;
    private static final int EARNINGS_HISTORY_KEPT_VALUE = 8;
    private static final boolean EARNINGS_EX_DIVIDEND_VALUE = true;
    private static final int[] WAREHOUSE_CAPACITY_VALUES = {1_024, 4_096, 16_384};
    private static final int[] WAREHOUSE_TRANSFER_VALUES = {64, 256, 1_024};
    private static final int[] FACTORY_THROUGHPUT_VALUES = {1, 2, 4};
    private static final int[] FACTORY_MAINTENANCE_BPS_VALUES = {10_000, 9_000, 8_000};
    private static final int LOGISTICS_MAX_ACTIVE_PLAYER_VALUE = 8;
    private static final int LOGISTICS_MAX_ACTIVE_COMPANY_VALUE = 32;
    private static final int LOGISTICS_MAX_CARGO_UNITS_VALUE = 1_024;
    private static final int LOGISTICS_DEADLINE_DAYS_VALUE = 14;
    private static final int SETTLEMENT_MAX_ACTIVE_PLAYER_VALUE = 4;
    private static final int SETTLEMENT_MAX_OPEN_VALUE = 8;
    private static final int SETTLEMENT_DEADLINE_DAYS_VALUE = 5;
    private static final int SETTLEMENT_REWARD_BPS_VALUE = 12_000;
    private static final boolean EXPLORATION_ENABLED_VALUE = true;
    private static final boolean EXPLORATION_WORLDGEN_ENABLED_VALUE = false;
    private static final int EXPLORATION_COOLDOWN_DAYS_VALUE = 2;
    private static final int EXPLORATION_MAX_DISTANCE_VALUE = 4_096;
    private static final int EXPLORATION_DEADLINE_DAYS_VALUE = 7;
    private static final long EXPLORATION_REWARD_VALUE = 250L;
    private static final int REGIONAL_HISTORY_DAYS_VALUE=120,COLLATERAL_MAX_ACTIVE_VALUE=4096,
            HEDGE_MAX_OBJECTIVES_VALUE=4096,RISK_DAILY_BATCH_VALUE=128,COLLATERAL_INITIAL_LTV_BPS_VALUE=6000,
            COLLATERAL_MAINTENANCE_LTV_BPS_VALUE=7500,COLLATERAL_LIQUIDATION_LTV_BPS_VALUE=9000;
    private static final int VILLAGE_POPULATION_VALUE=30;
    private static final long VILLAGE_PER_CAPITA_BUDGET_VALUE=100L;
    private static final int VILLAGE_BUDGET_RESERVE_DAYS_VALUE=3;
    private static final int VILLAGE_PREMIUM_MIN_BPS_VALUE=7500,VILLAGE_PREMIUM_MAX_BPS_VALUE=14000;
    private static final int VILLAGE_FAIR_TOLERANCE_BPS_VALUE=12000,VILLAGE_GOUGE_TOLERANCE_BPS_VALUE=15000;
    private static final int VILLAGE_BOYCOTT_FAVOR_VALUE=-50,VILLAGE_TRUST_FAVOR_VALUE=50;

    private static final ForgeConfigSpec.IntValue DEFAULT_DIVIDEND_CYCLE_DAYS;
    private static final ForgeConfigSpec.DoubleValue DEFAULT_DIVIDEND_RATIO;
    private static final ForgeConfigSpec.IntValue BANKRUPTCY_RISK_DAYS;
    private static final ForgeConfigSpec.DoubleValue BANKRUPTCY_CASH_RISK_MULTIPLIER;
    private static final ForgeConfigSpec.DoubleValue MIN_PROPOSAL_PARTICIPATION_RATIO;
    private static final ForgeConfigSpec.IntValue MAX_PRICE_ALERTS_PER_PLAYER;
    private static final ForgeConfigSpec.IntValue MAX_CONDITIONAL_STOCK_ORDERS_PER_PLAYER;
    private static final ForgeConfigSpec.DoubleValue STOCK_MARKET_MAKER_SPREAD;
    private static final ForgeConfigSpec.IntValue TAKEOVER_MIN_HOLDING_BPS, TAKEOVER_SQUEEZE_OUT_BPS,
            TAKEOVER_MIN_DURATION_DAYS, TAKEOVER_MAX_DURATION_DAYS;
    private static final ForgeConfigSpec.DoubleValue TAKEOVER_PRICE_FLOOR_RATIO, TAKEOVER_PRICE_CAP_RATIO;
    private static final ForgeConfigSpec.IntValue MARGIN_MAX_LEVERAGE, MARGIN_MAINTENANCE_BPS, MARGIN_WARNING_BPS;
    private static final ForgeConfigSpec.DoubleValue MARGIN_FINANCE_ANNUAL_RATE, MARGIN_SHORT_ANNUAL_RATE,
            MARGIN_POOL_RATIO, MARGIN_PER_PLAYER_BORROW_RATIO;
    private static final ForgeConfigSpec.DoubleValue STOCK_TARGET_INVENTORY_RATIO;
    private static final ForgeConfigSpec.DoubleValue STOCK_MAX_INVENTORY_MULTIPLIER;
    private static final ForgeConfigSpec.DoubleValue STOCK_MAX_TRADE_FLOAT_RATIO;
    private static final ForgeConfigSpec.DoubleValue IPO_MARKET_MAKER_RESERVE_RATIO;
    // Phase 2 做市商报价
    private static final ForgeConfigSpec.BooleanValue MM_TARGET_PRICE_STRATEGY_ENABLED;
    private static final ForgeConfigSpec.BooleanValue MM_INVENTORY_SKEW_STRATEGY_ENABLED;
    private static final ForgeConfigSpec.BooleanValue MM_VOLATILITY_SPREAD_STRATEGY_ENABLED;
    private static final ForgeConfigSpec.DoubleValue MM_INVENTORY_SKEW_FACTOR;
    private static final ForgeConfigSpec.DoubleValue MM_VOLATILITY_SPREAD_FACTOR;
    private static final ForgeConfigSpec.DoubleValue MM_MAX_INVENTORY_SKEW;
    private static final ForgeConfigSpec.DoubleValue MM_MAX_VOLATILITY_ADJUST;
    private static final ForgeConfigSpec.DoubleValue MM_CIRCUIT_BREAKER_THRESHOLD;
    private static final ForgeConfigSpec.DoubleValue MM_TARGET_PRICE_DEVIATION_THRESHOLD;
    // Phase 4 央行联动
    private static final ForgeConfigSpec.BooleanValue CB_MM_HEALTH_CHECK_ENABLED;
    private static final ForgeConfigSpec.LongValue CB_MM_EMERGENCY_LOAN_CAP;
    private static final ForgeConfigSpec.IntValue CB_MM_LOAN_REPAYMENT_BPS;
    private static final ForgeConfigSpec.IntValue FACTORY_COOK_TIME_BREAD;
    private static final ForgeConfigSpec.IntValue FACTORY_COOK_TIME_IRON;
    private static final ForgeConfigSpec.IntValue FACTORY_COOK_TIME_PLANKS;
    private static final ForgeConfigSpec.BooleanValue NPC_INVESTORS_ENABLED;
    private static final ForgeConfigSpec.LongValue NPC_INITIAL_CAPITAL_EACH;
    private static final ForgeConfigSpec.LongValue NPC_DAILY_BUDGET;
    private static final ForgeConfigSpec.LongValue NPC_POOL_RESERVE_FLOOR;
    private static final ForgeConfigSpec.IntValue NPC_MAX_HOLDING_PERCENT_EACH;
    private static final ForgeConfigSpec.IntValue NPC_MAX_COMBINED_HOLDING_PERCENT;
    private static final ForgeConfigSpec.IntValue NPC_MAX_FLOAT_PERCENT_PER_DAY;
    private static final ForgeConfigSpec.BooleanValue NPC_VOTING_ENABLED;
    private static final ForgeConfigSpec.IntValue NPC_TENDER_PREMIUM_DIVIDEND;
    private static final ForgeConfigSpec.IntValue NPC_TENDER_PREMIUM_GROWTH;
    private static final ForgeConfigSpec.IntValue NPC_TENDER_PREMIUM_SPECULATOR;
    private static final ForgeConfigSpec.IntValue NPC_TENDER_PREMIUM_VALUE;
    private static final ForgeConfigSpec.IntValue CARAVAN_MAX_ROUTES_PER_PLAYER;
    private static final ForgeConfigSpec.IntValue CARAVAN_MAX_QTY_PER_TRIP;
    private static final ForgeConfigSpec.LongValue CARAVAN_BLOCKS_PER_DAY;
    private static final ForgeConfigSpec.IntValue CARAVAN_RAID_BASE_BPS;
    private static final ForgeConfigSpec.IntValue CARAVAN_RAID_MIN_BPS;
    private static final ForgeConfigSpec.IntValue CARAVAN_RAID_MAX_BPS;
    private static final ForgeConfigSpec.IntValue CARAVAN_RAID_PER_KM_BPS;
    private static final ForgeConfigSpec.IntValue CARAVAN_RAID_PER_KM_CAP_BPS;
    private static final ForgeConfigSpec.IntValue CARAVAN_RAID_ALERT_BONUS_BPS;
    private static final ForgeConfigSpec.IntValue CARAVAN_GUARD_REDUCE_BPS;
    private static final ForgeConfigSpec.LongValue CARAVAN_GUARD_WAGE_1;
    private static final ForgeConfigSpec.LongValue CARAVAN_GUARD_WAGE_2;
    private static final ForgeConfigSpec.LongValue CARAVAN_GUARD_WAGE_3;
    private static final ForgeConfigSpec.IntValue CARAVAN_RAID_LOSS_RATIO_BPS;
    private static final ForgeConfigSpec.IntValue CARAVAN_INSURE_MARGIN_BPS;
    private static final ForgeConfigSpec.IntValue CARAVAN_SATURATION_PER_UNIT_BPS;
    private static final ForgeConfigSpec.IntValue CARAVAN_SATURATION_DECAY_BPS;
    private static final ForgeConfigSpec.IntValue CARAVAN_SELL_PRICE_CAP_MULTIPLIER;
    private static final ForgeConfigSpec.IntValue EARNINGS_QUARTER_DAYS, EARNINGS_EXPECTATION_QUARTERS,
            EARNINGS_SURPRISE_CAP_BPS, EARNINGS_REACTION_BPS, EARNINGS_MAX_SHOCK_BPS,
            EARNINGS_FV_PASS_THROUGH_BPS, EARNINGS_HISTORY_KEPT;
    private static final ForgeConfigSpec.BooleanValue EARNINGS_EX_DIVIDEND;
    private static final ForgeConfigSpec.LongValue IPO_FEE;
    private static final ForgeConfigSpec.LongValue CAPITAL_PROJECT_GOVERNANCE_THRESHOLD;
    private static final ForgeConfigSpec.IntValue CAPITAL_PROJECT_MAX_DURATION_DAYS;
    private static final ForgeConfigSpec.IntValue DEFAULT_BENCHMARK_RATE_BPS;
    private static final ForgeConfigSpec.ConfigValue<String> POLICY_RATE_MODE;
    private static final ForgeConfigSpec.IntValue POLICY_NEUTRAL_RATE_BPS;
    private static final ForgeConfigSpec.IntValue POLICY_MEETING_INTERVAL_DAYS;
    private static final ForgeConfigSpec.IntValue POLICY_GUIDANCE_LEAD_DAYS;
    private static final ForgeConfigSpec.IntValue POLICY_MAX_STEP_BPS;
    private static final ForgeConfigSpec.IntValue POLICY_MIN_STEP_BPS;
    private static final ForgeConfigSpec.DoubleValue POLICY_INFLATION_RESPONSE;
    private static final ForgeConfigSpec.DoubleValue POLICY_SLACK_RESPONSE;
    private static final ForgeConfigSpec.IntValue MAX_BONDS;
    private static final ForgeConfigSpec.LongValue TREASURY_BOND_MIN_AMOUNT;
    private static final ForgeConfigSpec.LongValue TREASURY_BOND_MAX_PER_PLAYER;
    private static final ForgeConfigSpec.LongValue TREASURY_BOND_MAX_ISSUE_PER_TERM;
    private static final ForgeConfigSpec.LongValue TREASURY_BOND_EARLY_FEE;
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
    // Phase 3 期货做市商
    private static final ForgeConfigSpec.DoubleValue FUTURES_MM_SPREAD, FUTURES_MM_NET_POSITION_RATIO;
    private static final ForgeConfigSpec.IntValue FUTURES_MM_WASH_TRADE_WINDOW_MINUTES, FUTURES_MM_WASH_TRADE_MAX_TRADES,
            FUTURES_MM_WASH_TRADE_COOLDOWN_MINUTES, FUTURES_MM_CIRCUIT_BREAKER_DEVIATION_BPS;
    private static final ForgeConfigSpec.LongValue FUTURES_MM_WASH_TRADE_MAX_NOTIONAL, FUTURES_MM_INITIAL_CAPITAL;
    private static final ForgeConfigSpec.BooleanValue FUTURES_MM_CIRCUIT_BREAKER_ENABLED;
    private static final ForgeConfigSpec.BooleanValue BANKING_ENABLED;private static final ForgeConfigSpec.LongValue BANK_INITIAL_CAPITAL,BANK_MIN_RESERVE,BANK_INSURANCE_LIMIT;
    private static final ForgeConfigSpec.IntValue DEFAULT_BANK_COUNT,BANK_DEMAND_SPREAD,BANK_TIME_SPREAD,BANK_LOAN_SPREAD,BANK_DEMAND_RESERVE_BPS,BANK_TIME_RESERVE_BPS,BANK_MIN_CAPITAL_BPS,BANK_SINGLE_BORROWER_BPS,BANK_CB_PENALTY_BPS,BANK_INSURANCE_FEE_BPS,BANK_STRESS_ROUNDS,BANK_REFERENCE_TTL_DAYS;
    private static final ForgeConfigSpec.ConfigValue<String> BANK_TIME_DEPOSIT_TERMS;private static final ForgeConfigSpec.LongValue BANK_TIME_DEPOSIT_MIN_AMOUNT,BANK_TIME_DEPOSIT_MAX_PER_PLAYER,BANK_TIME_DEPOSIT_EARLY_FEE; // 定期存款
    private static final ForgeConfigSpec.BooleanValue BANK_RUN_ENABLED,BANK_RUN_AUTO_RESOLVE;
    private static final ForgeConfigSpec.IntValue BANK_RUN_PANIC_DECAY,BANK_RUN_MAX_SHARE_BPS,BANK_RUN_DAILY_CAP_BPS,BANK_RUN_CAP_PANIC,BANK_RUN_FREEZE_PANIC,BANK_RUN_FAIL_PANIC,BANK_RUN_NPC_SEED_BPS,BANK_RUN_NPC_TIME_BPS,BANK_RUN_NPC_DEFAULT_BPS,BANK_RUN_RUMOR_COOLDOWN,BANK_RUN_ILLIQUID_DAYS;
    private static final ForgeConfigSpec.DoubleValue BANK_RUN_RUMOR_CHANCE;
    private static final ForgeConfigSpec.LongValue PLAYER_BANK_CHARTER_FEE,PLAYER_BANK_MIN_CAPITAL,PLAYER_BANK_DAILY_FINE;
    private static final ForgeConfigSpec.IntValue PLAYER_BANK_MAX_PER_PLAYER,PLAYER_BANK_RELATED_PARTY_LIMIT_BPS,PLAYER_BANK_DIVIDEND_LEVERAGE_BPS;
    private static final ForgeConfigSpec.BooleanValue MINECRAFT_FIRST_MODE;
    private static final ForgeConfigSpec.BooleanValue REQUIRE_PHYSICAL_TERMINAL;
    private static final ForgeConfigSpec.BooleanValue ENABLE_PORTABLE_LEDGER;
    private static final ForgeConfigSpec.BooleanValue LEGACY_FULL_SCREEN_KEYBIND;
    private static final ForgeConfigSpec.BooleanValue WAREHOUSE_CAPACITY_ENABLED;
    private static final ForgeConfigSpec.BooleanValue CONTRACTS_ENABLED;
    private static final ForgeConfigSpec.BooleanValue PLAYER_DRIVEN_COMPANY_PRODUCTION;
    private static final ForgeConfigSpec.BooleanValue ALLOW_LEGACY_AUTOMATIC_COMPANY_PRODUCTION;
    private static final ForgeConfigSpec.BooleanValue NEW_COMPANIES_PLAYER_DRIVEN_ONLY;
    private static final ForgeConfigSpec.DoubleValue HYBRID_LEGACY_FALLBACK_RATIO;
    private static final ForgeConfigSpec.BooleanValue ADVANCED_FINANCE_REQUIRES_TERMINAL;
    private static final ForgeConfigSpec.BooleanValue ADMIN_CONSOLE_REQUIRES_PERMISSION;
    private static final ForgeConfigSpec.DoubleValue TERMINAL_INTERACTION_DISTANCE;
    private static final ForgeConfigSpec.BooleanValue WORLD_ECONOMY_GLOBAL_BROADCASTS;
    private static final ForgeConfigSpec.IntValue[] WAREHOUSE_CAPACITIES = new ForgeConfigSpec.IntValue[3];
    private static final ForgeConfigSpec.IntValue[] WAREHOUSE_TRANSFER_LIMITS = new ForgeConfigSpec.IntValue[3];
    private static final ForgeConfigSpec.IntValue[] FACTORY_THROUGHPUT = new ForgeConfigSpec.IntValue[3];
    private static final ForgeConfigSpec.IntValue[] FACTORY_MAINTENANCE_BPS = new ForgeConfigSpec.IntValue[3];
    private static final ForgeConfigSpec.IntValue LOGISTICS_MAX_ACTIVE_PLAYER;
    private static final ForgeConfigSpec.IntValue LOGISTICS_MAX_ACTIVE_COMPANY;
    private static final ForgeConfigSpec.IntValue LOGISTICS_MAX_CARGO_UNITS;
    private static final ForgeConfigSpec.IntValue LOGISTICS_DEADLINE_DAYS;
    private static final ForgeConfigSpec.IntValue SETTLEMENT_MAX_ACTIVE_PLAYER, SETTLEMENT_MAX_OPEN,
            SETTLEMENT_DEADLINE_DAYS, SETTLEMENT_REWARD_BPS;
    private static final ForgeConfigSpec.BooleanValue EXPLORATION_ENABLED, EXPLORATION_WORLDGEN_ENABLED;
    private static final ForgeConfigSpec.IntValue EXPLORATION_COOLDOWN_DAYS, EXPLORATION_MAX_DISTANCE,
            EXPLORATION_DEADLINE_DAYS;
    private static final ForgeConfigSpec.LongValue EXPLORATION_REWARD;
    private static final ForgeConfigSpec.IntValue REGIONAL_HISTORY_DAYS,COLLATERAL_MAX_ACTIVE,
            HEDGE_MAX_OBJECTIVES,RISK_DAILY_BATCH,COLLATERAL_INITIAL_LTV_BPS,
            COLLATERAL_MAINTENANCE_LTV_BPS,COLLATERAL_LIQUIDATION_LTV_BPS;
    private static final ForgeConfigSpec.IntValue VILLAGE_POPULATION,VILLAGE_BUDGET_RESERVE_DAYS,
            VILLAGE_PREMIUM_MIN_BPS,VILLAGE_PREMIUM_MAX_BPS,VILLAGE_FAIR_TOLERANCE_BPS,
            VILLAGE_GOUGE_TOLERANCE_BPS,VILLAGE_BOYCOTT_FAVOR,VILLAGE_TRUST_FAVOR;
    private static final ForgeConfigSpec.LongValue VILLAGE_PER_CAPITA_BUDGET;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.push("gameplay");
        MINECRAFT_FIRST_MODE = builder
                .comment("默认使用 Minecraft-first 入口；金融合同与日结不会因此停止。")
                .define("minecraftFirstMode", MINECRAFT_FIRST_MODE_VALUE);
        REQUIRE_PHYSICAL_TERMINAL = builder
                .comment("市场、仓库、银行、公司和监管界面是否要求服务端验证实体终端。")
                .define("requirePhysicalTerminal", REQUIRE_PHYSICAL_TERMINAL_VALUE);
        ENABLE_PORTABLE_LEDGER = builder
                .comment("是否允许随身金融账本入口。")
                .define("enablePortableLedger", ENABLE_PORTABLE_LEDGER_VALUE);
        LEGACY_FULL_SCREEN_KEYBIND = builder
                .comment("是否允许旧快捷键直接打开完整金融界面；默认关闭以引导使用世界终端。")
                .define("legacyFullScreenKeybind", LEGACY_FULL_SCREEN_KEYBIND_VALUE);
        WAREHOUSE_CAPACITY_ENABLED = builder
                .comment("是否对新的世界仓库启用容量限制；历史托管资产不会被删除。")
                .define("warehouseCapacityEnabled", WAREHOUSE_CAPACITY_ENABLED_VALUE);
        CONTRACTS_ENABLED = builder
                .comment("是否允许创建新的世界合同；关闭不会吞掉既有托管或到期结算。")
                .define("contractsEnabled", CONTRACTS_ENABLED_VALUE);
        PLAYER_DRIVEN_COMPANY_PRODUCTION = builder
                .comment("Minecraft-first 模式下新公司是否优先采用玩家参与生产。")
                .define("playerDrivenCompanyProduction", PLAYER_DRIVEN_COMPANY_PRODUCTION_VALUE);
        ALLOW_LEGACY_AUTOMATIC_COMPANY_PRODUCTION = builder
                .comment("是否保留旧公司的自动经营兼容路径。")
                .define("allowLegacyAutomaticCompanyProduction", ALLOW_LEGACY_AUTOMATIC_COMPANY_PRODUCTION_VALUE);
        NEW_COMPANIES_PLAYER_DRIVEN_ONLY = builder
                .comment("新公司是否直接使用纯玩家驱动模式；关闭时默认使用带低效兜底的 HYBRID。")
                .define("newCompaniesPlayerDrivenOnly", NEW_COMPANIES_PLAYER_DRIVEN_ONLY_VALUE);
        HYBRID_LEGACY_FALLBACK_RATIO = builder
                .comment("HYBRID 公司当天没有设施成功生产时的旧式兜底产量比例。")
                .defineInRange("hybridLegacyFallbackRatio", HYBRID_LEGACY_FALLBACK_RATIO_VALUE, 0.0D, 1.0D);
        ADVANCED_FINANCE_REQUIRES_TERMINAL = builder
                .comment("股票、债券、基金、期货等高级金融界面是否要求证券终端。")
                .define("advancedFinanceRequiresTerminal", ADVANCED_FINANCE_REQUIRES_TERMINAL_VALUE);
        ADMIN_CONSOLE_REQUIRES_PERMISSION = builder
                .comment("央行控制台是否要求服务端 2 级管理员权限。")
                .define("adminConsoleRequiresPermission", ADMIN_CONSOLE_REQUIRES_PERMISSION_VALUE);
        TERMINAL_INTERACTION_DISTANCE = builder
                .comment("玩家可持续使用金融终端的最大距离（方块）。超出距离会关闭菜单。")
                .defineInRange("terminalInteractionDistance", TERMINAL_INTERACTION_DISTANCE_VALUE, 1.0D, 32.0D);
        WORLD_ECONOMY_GLOBAL_BROADCASTS = builder
                .comment("是否允许真正重大的经济事件向全服广播；局部与参与者通知不受此项影响。")
                .define("worldEconomyGlobalBroadcasts", WORLD_ECONOMY_GLOBAL_BROADCASTS_VALUE);
        builder.push("facilityTiers");
        for (int tier = 1; tier <= 3; tier++) {
            int index = tier - 1;
            WAREHOUSE_CAPACITIES[index] = builder
                    .comment("仓库等级 " + tier + " 的总容量。重载不会删除超额资产。")
                    .defineInRange("warehouseTier" + tier + "Capacity", WAREHOUSE_CAPACITY_VALUES[index], 64, 1_000_000);
            WAREHOUSE_TRANSFER_LIMITS[index] = builder
                    .comment("仓库等级 " + tier + " 的单次存取上限。")
                    .defineInRange("warehouseTier" + tier + "TransferLimit", WAREHOUSE_TRANSFER_VALUES[index], 1, 1_000_000);
            FACTORY_THROUGHPUT[index] = builder
                    .comment("工厂等级 " + tier + " 的生产批次倍率。")
                    .defineInRange("factoryTier" + tier + "Throughput", FACTORY_THROUGHPUT_VALUES[index], 1, 16);
            FACTORY_MAINTENANCE_BPS[index] = builder
                    .comment("工厂等级 " + tier + " 的维护费倍率，10000 表示 100%。")
                    .defineInRange("factoryTier" + tier + "MaintenanceBasisPoints", FACTORY_MAINTENANCE_BPS_VALUES[index], 1_000, 20_000);
        }
        builder.pop();
        builder.push("logistics");
        LOGISTICS_MAX_ACTIVE_PLAYER = builder.comment("每名玩家最多关联的未结束运单。")
                .defineInRange("maxActivePerPlayer", LOGISTICS_MAX_ACTIVE_PLAYER_VALUE, 1, 64);
        LOGISTICS_MAX_ACTIVE_COMPANY = builder.comment("每家公司最多保留的未结束运单。")
                .defineInRange("maxActivePerCompany", LOGISTICS_MAX_ACTIVE_COMPANY_VALUE, 1, 256);
        LOGISTICS_MAX_CARGO_UNITS = builder.comment("单个密封货箱可承载的最大商品单位；仍受源仓库单次吞吐限制。")
                .defineInRange("maxCargoUnits", LOGISTICS_MAX_CARGO_UNITS_VALUE, 1, 16_384);
        LOGISTICS_DEADLINE_DAYS = builder.comment("普通运单默认提示期限，单位为 MC 天；超期不会删除运输托管。")
                .defineInRange("defaultDeadlineDays", LOGISTICS_DEADLINE_DAYS_VALUE, 1, 365);
        builder.pop();
        builder.push("settlements");
        SETTLEMENT_MAX_ACTIVE_PLAYER = builder.comment("每名玩家最多同时接受的聚落需求。")
                .defineInRange("maxActivePerPlayer", SETTLEMENT_MAX_ACTIVE_PLAYER_VALUE, 1, 32);
        SETTLEMENT_MAX_OPEN = builder.comment("每个聚落最多保留的未结束公共需求。")
                .defineInRange("maxOpenPerSettlement", SETTLEMENT_MAX_OPEN_VALUE, 1, 16);
        SETTLEMENT_DEADLINE_DAYS = builder.comment("本地需求默认期限，单位为 MC 天。")
                .defineInRange("demandDeadlineDays", SETTLEMENT_DEADLINE_DAYS_VALUE, 1, 30);
        SETTLEMENT_REWARD_BPS = builder.comment("本地需求相对可信市场采购价的奖励比例，10000=100%。")
                .defineInRange("rewardBasisPoints", SETTLEMENT_REWARD_BPS_VALUE, 5_000, 20_000);
        builder.pop();
        builder.push("exploration");
        EXPLORATION_ENABLED = builder.comment("是否允许玩家通过调查板创建有真实预算托管的探索任务。")
                .define("enabled", EXPLORATION_ENABLED_VALUE);
        EXPLORATION_WORLDGEN_ENABLED = builder.comment("实验性遗迹世界生成总开关。首版默认关闭；模板资源仍可由数据包或管理员使用。修改后需完整重启服务器，/reload 不会重生成旧区块。")
                .define("worldgenEnabled", EXPLORATION_WORLDGEN_ENABLED_VALUE);
        EXPLORATION_COOLDOWN_DAYS = builder.comment("同一玩家申请新调查任务的冷却，单位为 MC 天。")
                .defineInRange("requestCooldownDays", EXPLORATION_COOLDOWN_DAYS_VALUE, 0, 30);
        EXPLORATION_MAX_DISTANCE = builder.comment("调查板只会在此半径内选择已登记目标；不会加载或扫描新区块。")
                .defineInRange("maximumTargetDistance", EXPLORATION_MAX_DISTANCE_VALUE, 128, 16_384);
        EXPLORATION_DEADLINE_DAYS = builder.comment("调查任务有效期，单位为 MC 天。")
                .defineInRange("deadlineDays", EXPLORATION_DEADLINE_DAYS_VALUE, 1, 30);
        EXPLORATION_REWARD = builder.comment("每次调查完成奖励；申请时从 NPC 市场账户转入独立托管。")
                .defineInRange("reward", EXPLORATION_REWARD_VALUE, 1L, 100_000L);
        builder.pop();
        builder.push("regionalRiskFinance");
        REGIONAL_HISTORY_DAYS=builder.comment("区域商品指标保留天数。缩短后会在下一次日结逐步裁剪；修改配置后建议重启服务器。")
                .defineInRange("regionalHistoryDays",REGIONAL_HISTORY_DAYS_VALUE,7,120);
        COLLATERAL_MAX_ACTIVE=builder.comment("世界中库存质押记录的软上限；硬上限仍为 4096，降低上限不会删除既有质押。")
                .defineInRange("maxCollateralAgreements",COLLATERAL_MAX_ACTIVE_VALUE,1,4096);
        HEDGE_MAX_OBJECTIVES=builder.comment("世界中公司经营对冲目标的软上限；硬上限仍为 4096。")
                .defineInRange("maxHedgeObjectives",HEDGE_MAX_OBJECTIVES_VALUE,1,4096);
        RISK_DAILY_BATCH=builder.comment("每个 MC 日最多重估的质押协议数量；采用轮转游标，不会永久遗漏后排协议。")
                .defineInRange("dailyCollateralBatch",RISK_DAILY_BATCH_VALUE,1,512);
        COLLATERAL_INITIAL_LTV_BPS=builder.comment("新库存质押贷款的初始 LTV，基点。")
                .defineInRange("collateralInitialLtvBasisPoints",COLLATERAL_INITIAL_LTV_BPS_VALUE,1000,6000);
        COLLATERAL_MAINTENANCE_LTV_BPS=builder.comment("库存质押触发追保的 LTV，基点；运行时不低于初始 LTV。")
                .defineInRange("collateralMaintenanceLtvBasisPoints",COLLATERAL_MAINTENANCE_LTV_BPS_VALUE,2000,9500);
        COLLATERAL_LIQUIDATION_LTV_BPS=builder.comment("追保至少一个完整 MC 日后允许清算的 LTV，基点；运行时不低于维持线。")
                .defineInRange("collateralLiquidationLtvBasisPoints",COLLATERAL_LIQUIDATION_LTV_BPS_VALUE,3000,10000);
        builder.pop();
        builder.pop();

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
                .comment("公司发起 IPO 时向所有者收取的费用；下限钳制 5000，服主配 0 会让小号每号净赚 5000（P0-1）。")
                .defineInRange("ipoFee", IPO_FEE_VALUE, 5_000L, Long.MAX_VALUE);
        CAPITAL_PROJECT_GOVERNANCE_THRESHOLD = builder
                .comment("资本项目预算达到或超过该金额时必须显式授权；上市公司走股东提案，非上市公司由所有者确认。")
                .defineInRange("capitalProjectGovernanceThreshold", CAPITAL_PROJECT_GOVERNANCE_THRESHOLD_VALUE, 0L, Long.MAX_VALUE);
        CAPITAL_PROJECT_MAX_DURATION_DAYS = builder
                .comment("资本项目自创建到截止的最长 MC 天数。")
                .defineInRange("capitalProjectMaxDurationDays", CAPITAL_PROJECT_MAX_DURATION_DAYS_VALUE, 7, 365);
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
        BANK_REFERENCE_TTL_DAYS=builder.comment("账本幂等引用保留天数（TTL），过期自动清理；须大于任何合法重试窗口。").defineInRange("referenceTtlDays",BANK_REFERENCE_TTL_DAYS_VALUE,7,365);
        BANK_TIME_DEPOSIT_TERMS=builder.comment("定期存款 定期存款允许的期限档，MC天，逗号分隔。").define("timeDepositTerms",BANK_TIME_DEPOSIT_TERMS_VALUE); // 定期存款
        BANK_TIME_DEPOSIT_MIN_AMOUNT=builder.comment("定期存款 定期存款起存金额。").defineInRange("timeDepositMinAmount",BANK_TIME_DEPOSIT_MIN_AMOUNT_VALUE,0L,Long.MAX_VALUE); // 定期存款
        BANK_TIME_DEPOSIT_MAX_PER_PLAYER=builder.comment("定期存款 单人跨所有银行ACTIVE定期存单本金合计上限。").defineInRange("timeDepositMaxPerPlayer",BANK_TIME_DEPOSIT_MAX_PER_PLAYER_VALUE,1L,Long.MAX_VALUE); // 定期存款
        BANK_TIME_DEPOSIT_EARLY_FEE=builder.comment("定期存款 提前支取手续费，从返还本金中扣除，记手续费收入。").defineInRange("timeDepositEarlyFee",BANK_TIME_DEPOSIT_EARLY_FEE_VALUE,0L,Long.MAX_VALUE); // 定期存款
        BANK_RUN_ENABLED=builder.comment("是否启用银行挤兑玩法（恐慌度→挤兑动态→处置）；关闭则恐慌冻结、不再产生挤兑。").define("runEnabled",BANK_RUN_ENABLED_VALUE);
        BANK_RUN_AUTO_RESOLVE=builder.comment("银行进入处置状态后是否在日终自动触发桥接银行处置；关闭则只走管理手动处置。").define("runAutoResolve",BANK_RUN_AUTO_RESOLVE_VALUE);
        BANK_RUN_PANIC_DECAY=builder.comment("无压力信号时恐慌度每日衰减。").defineInRange("runPanicDecayPerDay",BANK_RUN_PANIC_DECAY_VALUE,0,100);
        BANK_RUN_MAX_SHARE_BPS=builder.comment("恐慌度100时NPC零售池单日最大流出比例，基点。").defineInRange("runMaxDailyShareBasisPoints",BANK_RUN_MAX_SHARE_BPS_VALUE,0,10_000);
        BANK_RUN_DAILY_CAP_BPS=builder.comment("挤兑档位下全行单日提现预算占存款比例，基点。").defineInRange("runDailyCapBasisPoints",BANK_RUN_DAILY_CAP_BPS_VALUE,1,10_000);
        BANK_RUN_CAP_PANIC=builder.comment("启动排队限提的恐慌度阈值。").defineInRange("runCapPanicThreshold",BANK_RUN_CAP_PANIC_VALUE,0,100);
        BANK_RUN_FREEZE_PANIC=builder.comment("同业市场冻结对该银行放贷的恐慌度阈值。").defineInRange("runFreezePanicThreshold",BANK_RUN_FREEZE_PANIC_VALUE,0,100);
        BANK_RUN_FAIL_PANIC=builder.comment("非流动性死亡判定的恐慌度阈值。").defineInRange("runFailPanicThreshold",BANK_RUN_FAIL_PANIC_VALUE,0,100);
        BANK_RUN_NPC_SEED_BPS=builder.comment("NPC零售池播种规模占银行初始资本比例，基点。").defineInRange("runNpcSeedBasisPoints",BANK_RUN_NPC_SEED_BPS_VALUE,0,10_000);
        BANK_RUN_NPC_TIME_BPS=builder.comment("NPC零售池中定期占比，基点。").defineInRange("runNpcTimeShareBasisPoints",BANK_RUN_NPC_TIME_BPS_VALUE,0,10_000);
        BANK_RUN_NPC_DEFAULT_BPS=builder.comment("恐慌度100时NPC贷款单日违约率上限，基点。").defineInRange("runNpcDefaultBasisPoints",BANK_RUN_NPC_DEFAULT_BPS_VALUE,0,10_000);
        BANK_RUN_RUMOR_COOLDOWN=builder.comment("同一银行两次谣言事件最小间隔天数。").defineInRange("runRumorCooldownDays",BANK_RUN_RUMOR_COOLDOWN_VALUE,0,30);
        BANK_RUN_ILLIQUID_DAYS=builder.comment("非流动性死亡所需连续天数。").defineInRange("runIlliquidDays",BANK_RUN_ILLIQUID_DAYS_VALUE,1,30);
        BANK_RUN_RUMOR_CHANCE=builder.comment("谣言事件触发概率（0~1）；仅在真实风险存在时掷骰。").defineInRange("runRumorChance",BANK_RUN_RUMOR_CHANCE_VALUE,0.0,1.0);
        builder.pop();

        builder.push("playerBank");
        PLAYER_BANK_CHARTER_FEE=builder.comment("玩家申请银行牌照费用（一次性，从钱包扣除并销毁）。").defineInRange("charterFee",PLAYER_BANK_CHARTER_FEE_VALUE,0L,Long.MAX_VALUE);
        PLAYER_BANK_MIN_CAPITAL=builder.comment("玩家银行最低实缴资本金。").defineInRange("minCapital",PLAYER_BANK_MIN_CAPITAL_VALUE,1L,Long.MAX_VALUE);
        PLAYER_BANK_MAX_PER_PLAYER=builder.comment("每名玩家最多可开设的银行数量。").defineInRange("maxPerPlayer",PLAYER_BANK_MAX_PER_PLAYER_VALUE,1,8);
        PLAYER_BANK_RELATED_PARTY_LIMIT_BPS=builder.comment("玩家银行向所有者关联公司贷款上限（基点 / 银行权益）；0 为硬拒绝。").defineInRange("relatedPartyLimitBasisPoints",PLAYER_BANK_RELATED_PARTY_LIMIT_BPS_VALUE,0,10_000);
        PLAYER_BANK_DAILY_FINE=builder.comment("玩家银行处于 RESTRICTED 状态时的每日监管罚金。").defineInRange("restrictedDailyFine",PLAYER_BANK_DAILY_FINE_VALUE,0L,Long.MAX_VALUE);
        PLAYER_BANK_DIVIDEND_LEVERAGE_BPS=builder.comment("玩家银行分红后权益不得低于存款总额的比例（基点，默认 800=8%）；防范分红掏空后破产转嫁保险基金。").defineInRange("dividendLeverageBasisPoints",PLAYER_BANK_DIVIDEND_LEVERAGE_BPS_VALUE,0,10_000);
        builder.pop();

        builder.push("financialProducts");
        DEFAULT_BENCHMARK_RATE_BPS = builder
                .comment("基准年利率，单位为基点；500 表示 5%。修改后只影响新债券和新贷款。")
                .defineInRange("defaultBenchmarkRateBasisPoints", DEFAULT_BENCHMARK_RATE_BPS_VALUE,
                        MIN_BENCHMARK_RATE_BPS_VALUE, MAX_BENCHMARK_RATE_BPS_VALUE);
        POLICY_RATE_MODE = builder
                .comment("基准利率政策模式：AUTO=议息日按规则自动调息；ADVISORY=规则只给建议，需管理员确认；MANUAL=纯手动。")
                .define("policyRateMode", "AUTO");
        POLICY_NEUTRAL_RATE_BPS = builder
                .comment("政策规则中的中性利率（基点），经济处于均衡时利率向其回归。")
                .defineInRange("policyNeutralRateBps", 500, MIN_BENCHMARK_RATE_BPS_VALUE, MAX_BENCHMARK_RATE_BPS_VALUE);
        POLICY_MEETING_INTERVAL_DAYS = builder
                .comment("议息周期，单位为 MC 天。")
                .defineInRange("policyMeetingIntervalDays", 30, 1, 3650);
        POLICY_GUIDANCE_LEAD_DAYS = builder
                .comment("议息日前瞻指引提前天数（只给方向，不承诺幅度）。")
                .defineInRange("policyGuidanceLeadDays", 3, 0, 30);
        POLICY_MAX_STEP_BPS = builder
                .comment("单次议息最大调整幅度（基点），防规则抽风。")
                .defineInRange("policyMaxStepBps", 100, 1, 2000);
        POLICY_MIN_STEP_BPS = builder
                .comment("小于此幅度的建议视为噪音，不调息。")
                .defineInRange("policyMinStepBps", 25, 1, 500);
        POLICY_INFLATION_RESPONSE = builder
                .comment("通胀缺口反应系数（类 Taylor 规则，经典值 1.5）。")
                .defineInRange("policyInflationResponse", 1.5D, 0.0D, 5.0D);
        POLICY_SLACK_RESPONSE = builder
                .comment("衰退指标反应系数。")
                .defineInRange("policySlackResponse", 0.5D, 0.0D, 5.0D);
        MAX_BONDS = builder
                .comment("单个世界最多保留的公司债券合约数量，超过上限时拒绝新发行。")
                .defineInRange("maxCorporateBonds", MAX_BONDS_VALUE, 1, 10_000);
        TREASURY_BOND_MIN_AMOUNT = builder
                .comment("国债起认金额。")
                .defineInRange("treasuryBondMinAmount", TREASURY_BOND_MIN_AMOUNT_VALUE, 1L, Long.MAX_VALUE);
        TREASURY_BOND_MAX_PER_PLAYER = builder
                .comment("单名玩家国债 ACTIVE 本金合计上限。")
                .defineInRange("treasuryBondMaxPerPlayer", TREASURY_BOND_MAX_PER_PLAYER_VALUE, 0L, Long.MAX_VALUE);
        TREASURY_BOND_MAX_ISSUE_PER_TERM = builder
                .comment("每期限档国债未到期发行余额上限。")
                .defineInRange("treasuryBondMaxIssuePerTerm", TREASURY_BOND_MAX_ISSUE_PER_TERM_VALUE, 0L, Long.MAX_VALUE);
        TREASURY_BOND_EARLY_FEE = builder
                .comment("国债提前赎回手续费，从本金扣除且不超过本金。")
                .defineInRange("treasuryBondEarlyFee", TREASURY_BOND_EARLY_FEE_VALUE, 0L, Long.MAX_VALUE);
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

        // Phase 3 期货做市商
        builder.push("futuresMarketMaker");
        FUTURES_MM_SPREAD = builder
                .comment("期货做市商基础价差。0.015 = 理论期货价上下 1.5% 形成 bid / ask。")
                .defineInRange("spread", FUTURES_MM_SPREAD_VALUE, 0.0D, 1.0D);
        FUTURES_MM_NET_POSITION_RATIO = builder
                .comment("期货做市商净持仓上限比例：|净持仓| ≤ 合约持仓总量 × 该比例。超限后只提供减仓方向报价。")
                .defineInRange("netPositionRatio", FUTURES_MM_NET_POSITION_RATIO_VALUE, 0.0D, 1.0D);
        FUTURES_MM_WASH_TRADE_WINDOW_MINUTES = builder
                .comment("对敲检测窗口（分钟）：同一玩家在窗口内与做市商双向成交超阈值则告警并临时只报单边。")
                .defineInRange("washTradeWindowMinutes", FUTURES_MM_WASH_TRADE_WINDOW_MINUTES_VALUE, 1, 1440);
        FUTURES_MM_WASH_TRADE_MAX_TRADES = builder
                .comment("对敲检测触发笔数阈值：窗口内与做市商双向成交笔数超过此值触发告警。")
                .defineInRange("washTradeMaxTrades", FUTURES_MM_WASH_TRADE_MAX_TRADES_VALUE, 1, 100_000);
        FUTURES_MM_WASH_TRADE_MAX_NOTIONAL = builder
                .comment("对敲检测触发名义金额阈值：窗口内与做市商双向成交名义金额超过此值触发告警。")
                .defineInRange("washTradeMaxNotional", FUTURES_MM_WASH_TRADE_MAX_NOTIONAL_VALUE, 1L, Long.MAX_VALUE);
        FUTURES_MM_WASH_TRADE_COOLDOWN_MINUTES = builder
                .comment("对敲告警后单边报价冷却时长（分钟）。")
                .defineInRange("washTradeCooldownMinutes", FUTURES_MM_WASH_TRADE_COOLDOWN_MINUTES_VALUE, 1, 10080);
        FUTURES_MM_CIRCUIT_BREAKER_ENABLED = builder
                .comment("期货做市商熔断开关：结算参考价偏离理论期货价超阈值时暂停报价。")
                .define("circuitBreakerEnabled", FUTURES_MM_CIRCUIT_BREAKER_ENABLED_VALUE);
        FUTURES_MM_CIRCUIT_BREAKER_DEVIATION_BPS = builder
                .comment("期货做市商熔断偏离阈值，单位为基点。3000 = 偏离理论价 ±30% 熔断。")
                .defineInRange("circuitBreakerDeviationBasisPoints", FUTURES_MM_CIRCUIT_BREAKER_DEVIATION_BPS_VALUE, 0, 100_000);
        FUTURES_MM_INITIAL_CAPITAL = builder
                .comment("期货做市商保证金账户目标注资额：从做市商总账户划转，绝不凭空印钱；总账户余额不足时按实际可划转额注资。")
                .defineInRange("initialCapital", FUTURES_MM_INITIAL_CAPITAL_VALUE, 0L, Long.MAX_VALUE);
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
        STOCK_TARGET_INVENTORY_RATIO = builder
                .comment("股票做市商目标库存比例：流通股 × 该比例。库存偏离目标时触发报价偏斜（Phase 2）。")
                .defineInRange("targetInventoryRatio", STOCK_TARGET_INVENTORY_RATIO_VALUE, 0.0D, 1.0D);
        STOCK_MAX_INVENTORY_MULTIPLIER = builder
                .comment("股票做市商库存上限倍数：目标库存 × 该倍数。库存达上限后只卖不买。")
                .defineInRange("maxInventoryMultiplier", STOCK_MAX_INVENTORY_MULTIPLIER_VALUE, 1.0D, 10.0D);
        STOCK_MAX_TRADE_FLOAT_RATIO = builder
                .comment("股票做市商单笔成交上限比例：流通股 × 该比例（另受目标库存 20% 与现金可支撑量约束）。")
                .defineInRange("maxTradeFloatRatio", STOCK_MAX_TRADE_FLOAT_RATIO_VALUE, 0.0D, 1.0D);
        IPO_MARKET_MAKER_RESERVE_RATIO = builder
                .comment("IPO 做市商预留比例：发行量 × 该比例以发行价划入做市商库存，资金从做市商总账户支付给公司。")
                .defineInRange("ipoMarketMakerReserveRatio", IPO_MARKET_MAKER_RESERVE_RATIO_VALUE, 0.0D, 1.0D);
        builder.pop();

        // Phase 2 做市商报价
        builder.push("marketMakerQuotes");
        MM_TARGET_PRICE_STRATEGY_ENABLED = builder
                .comment("做市商均值回归策略（TargetPriceStrategy）开关：现价偏离中间价超阈值时把报价往回拉。")
                .define("targetPriceStrategyEnabled", MM_TARGET_PRICE_STRATEGY_ENABLED_VALUE);
        MM_INVENTORY_SKEW_STRATEGY_ENABLED = builder
                .comment("做市商库存偏斜策略（InventorySkewStrategy）开关：库存高于目标下移报价，低于目标上移。")
                .define("inventorySkewStrategyEnabled", MM_INVENTORY_SKEW_STRATEGY_ENABLED_VALUE);
        MM_VOLATILITY_SPREAD_STRATEGY_ENABLED = builder
                .comment("做市商波动率价差策略（VolatilitySpreadStrategy）开关：近期波动大时加宽价差自保。")
                .define("volatilitySpreadStrategyEnabled", MM_VOLATILITY_SPREAD_STRATEGY_ENABLED_VALUE);
        MM_INVENTORY_SKEW_FACTOR = builder
                .comment("库存偏斜系数：skew = 该系数 × (库存/目标 - 1)，双边报价整体平移 mid × skew。")
                .defineInRange("inventorySkewFactor", MM_INVENTORY_SKEW_FACTOR_VALUE, 0.0D, 5.0D);
        MM_VOLATILITY_SPREAD_FACTOR = builder
                .comment("波动率价差系数：volAdj = 该系数 × 近期波动率，双边各加宽 mid × volAdj。")
                .defineInRange("volatilitySpreadFactor", MM_VOLATILITY_SPREAD_FACTOR_VALUE, 0.0D, 5.0D);
        MM_MAX_INVENTORY_SKEW = builder
                .comment("库存偏斜上限：|skew| 不超过该值，防止极端库存把报价推离中间价太远。")
                .defineInRange("maxInventorySkew", MM_MAX_INVENTORY_SKEW_VALUE, 0.0D, 0.5D);
        MM_MAX_VOLATILITY_ADJUST = builder
                .comment("波动率价差上限：volAdj 不超过该值。")
                .defineInRange("maxVolatilityAdjust", MM_MAX_VOLATILITY_ADJUST_VALUE, 0.0D, 0.5D);
        MM_CIRCUIT_BREAKER_THRESHOLD = builder
                .comment("熔断阈值：|现价 - 参考价| / 参考价超过该值时做市商暂停报价（商品参考价=基准价，股票参考价=fairValue），回归后自动恢复。")
                .defineInRange("circuitBreakerThreshold", MM_CIRCUIT_BREAKER_THRESHOLD_VALUE, 0.0D, 2.0D);
        MM_TARGET_PRICE_DEVIATION_THRESHOLD = builder
                .comment("目标价偏离阈值：现价偏离中间价超过该比例时均值回归策略介入。")
                .defineInRange("targetPriceDeviationThreshold", MM_TARGET_PRICE_DEVIATION_THRESHOLD_VALUE, 0.0D, 1.0D);
        // Phase 4 央行联动
        builder.push("centralBankMarketMaker");
        CB_MM_HEALTH_CHECK_ENABLED = builder
                .comment("央行跨市场做市商健康度检查总开关（随每日央行干预执行）。关闭后不再发放紧急贷款、扣回还款，也不再做库存触顶告警。")
                .define("healthCheckEnabled", CB_MM_HEALTH_CHECK_ENABLED_VALUE);
        CB_MM_EMERGENCY_LOAN_CAP = builder
                .comment("央行对期货做市商单次紧急贷款上限。贷款记入央行紧急贷款账本；做市商总账户余额健康时按还款比例逐日扣回。")
                .defineInRange("emergencyLoanCap", CB_MM_EMERGENCY_LOAN_CAP_VALUE, 0L, Long.MAX_VALUE);
        CB_MM_LOAN_REPAYMENT_BPS = builder
                .comment("紧急贷款每日还款比例（基点）：做市商总账户余额高于健康线时，按超出部分的该比例划扣还款。2000 = 20%。")
                .defineInRange("loanRepaymentBasisPoints", CB_MM_LOAN_REPAYMENT_BPS_VALUE, 0, 10_000);
        builder.pop();

        builder.push("takeover");
        TAKEOVER_MIN_HOLDING_BPS = builder
                .comment("发起要约收购需事前持有的有表决权股份比例（基点）。3000 = 30%。")
                .defineInRange("minHoldingBps", TAKEOVER_MIN_HOLDING_BPS_VALUE, 0, 10_000);
        TAKEOVER_SQUEEZE_OUT_BPS = builder
                .comment("强制私有化（挤出小股东）所需的持股比例（基点）。9000 = 90%。")
                .defineInRange("squeezeOutBps", TAKEOVER_SQUEEZE_OUT_BPS_VALUE, 5_001, 10_000);
        TAKEOVER_PRICE_FLOOR_RATIO = builder
                .comment("要约价下限倍数（相对发起时 fairValue）。防恶意压价掠夺小股东。")
                .defineInRange("priceFloorRatio", TAKEOVER_PRICE_FLOOR_RATIO_VALUE, 0.01D, 10.0D);
        TAKEOVER_PRICE_CAP_RATIO = builder
                .comment("要约价上限倍数（相对发起时 fairValue）。防拉高套现。")
                .defineInRange("priceCapRatio", TAKEOVER_PRICE_CAP_RATIO_VALUE, 0.01D, 10.0D);
        TAKEOVER_MIN_DURATION_DAYS = builder
                .comment("要约期下限（MC 天）。")
                .defineInRange("minDurationDays", TAKEOVER_MIN_DURATION_DAYS_VALUE, 1, 90);
        TAKEOVER_MAX_DURATION_DAYS = builder
                .comment("要约期上限（MC 天）。停牌是有代价的武器，不宜过长。")
                .defineInRange("maxDurationDays", TAKEOVER_MAX_DURATION_DAYS_VALUE, 1, 90);
        builder.pop();

        builder.push("margin");
        MARGIN_MAX_LEVERAGE = builder
                .comment("融资融券最大杠杆倍数。2 = 2x（初始保证金率 50%）。")
                .defineInRange("maxLeverage", MARGIN_MAX_LEVERAGE_VALUE, 1, 10);
        MARGIN_MAINTENANCE_BPS = builder
                .comment("维持保证金率（基点）。13000 = 130%，跌破强制平仓。")
                .defineInRange("maintenanceBps", MARGIN_MAINTENANCE_BPS_VALUE, 10_001, 100_000);
        MARGIN_WARNING_BPS = builder
                .comment("预警线（基点）。15000 = 150%，跌破仅预警不强平。")
                .defineInRange("warningBps", MARGIN_WARNING_BPS_VALUE, 10_001, 100_000);
        MARGIN_FINANCE_ANNUAL_RATE = builder
                .comment("融资年利率。0.086 = 8.6%，按日单利计提。不可为负。")
                .defineInRange("financeAnnualRate", MARGIN_FINANCE_ANNUAL_RATE_VALUE, 0.0D, 1.0D);
        MARGIN_SHORT_ANNUAL_RATE = builder
                .comment("融券年利率。0.106 = 10.6%，按借出时市值计提。不可为负。")
                .defineInRange("shortAnnualRate", MARGIN_SHORT_ANNUAL_RATE_VALUE, 0.0D, 1.0D);
        MARGIN_POOL_RATIO = builder
                .comment("券池总额占流通股比例。0.05 = 5%，系统出借硬上限（防无限印钞）。")
                .defineInRange("poolRatio", MARGIN_POOL_RATIO_VALUE, 0.0D, 1.0D);
        MARGIN_PER_PLAYER_BORROW_RATIO = builder
                .comment("单人借券上限占券池比例。0.20 = 20%。")
                .defineInRange("perPlayerBorrowRatio", MARGIN_PER_PLAYER_BORROW_RATIO_VALUE, 0.0D, 1.0D);
        builder.pop();

        builder.push("villageEconomy");
        VILLAGE_POPULATION = builder.comment("每聚落默认村庄人口。")
                .defineInRange("villagePopulation", VILLAGE_POPULATION_VALUE, 1, 10_000);
        VILLAGE_PER_CAPITA_BUDGET = builder.comment("村民人均日预算。")
                .defineInRange("villagePerCapitaBudget", VILLAGE_PER_CAPITA_BUDGET_VALUE, 1L, 100_000L);
        VILLAGE_BUDGET_RESERVE_DAYS = builder.comment("村庄预算上限 = 人均日预算 × 人口 × 天数。")
                .defineInRange("villageBudgetReserveDays", VILLAGE_BUDGET_RESERVE_DAYS_VALUE, 1, 30);
        VILLAGE_PREMIUM_MIN_BPS = builder.comment("村庄溢价下限，基点；7500 = 0.75x。")
                .defineInRange("villagePremiumMinBps", VILLAGE_PREMIUM_MIN_BPS_VALUE, 1_000, 10_000);
        VILLAGE_PREMIUM_MAX_BPS = builder.comment("村庄溢价上限，基点；14000 = 1.40x。")
                .defineInRange("villagePremiumMaxBps", VILLAGE_PREMIUM_MAX_BPS_VALUE, 10_000, 30_000);
        VILLAGE_FAIR_TOLERANCE_BPS = builder.comment("公允容忍，基点；12000 = 1.2x WTP。")
                .defineInRange("villageFairToleranceBps", VILLAGE_FAIR_TOLERANCE_BPS_VALUE, 10_000, 20_000);
        VILLAGE_GOUGE_TOLERANCE_BPS = builder.comment("宰客线，基点；15000 = 1.5x WTP。")
                .defineInRange("villageGougeToleranceBps", VILLAGE_GOUGE_TOLERANCE_BPS_VALUE, 10_000, 30_000);
        VILLAGE_BOYCOTT_FAVOR = builder.comment("抵制好感阈值：村民好感 ≤ 该值时拒绝该玩家全部摊位。"
                        + " 注意：设为 0 会抵制所有新玩家（新玩家好感为 0，0 ≤ 0 恒成立）。")
                .defineInRange("villageBoycottFavor", VILLAGE_BOYCOTT_FAVOR_VALUE, -100, 0);
        VILLAGE_TRUST_FAVOR = builder.comment("信赖好感阈值。")
                .defineInRange("villageTrustFavor", VILLAGE_TRUST_FAVOR_VALUE, 0, 100);
        builder.pop();

        builder.push("factory");
        FACTORY_COOK_TIME_BREAD = builder
                .comment("工厂方块：小麦→面包耗时（tick）。")
                .defineInRange("breadCookTime", FACTORY_COOK_TIME_BREAD_VALUE, 1, 72_000);
        FACTORY_COOK_TIME_IRON = builder
                .comment("工厂方块：铁矿石→铁锭耗时（tick）。")
                .defineInRange("ironCookTime", FACTORY_COOK_TIME_IRON_VALUE, 1, 72_000);
        FACTORY_COOK_TIME_PLANKS = builder
                .comment("工厂方块：原木→木板耗时（tick）。")
                .defineInRange("planksCookTime", FACTORY_COOK_TIME_PLANKS_VALUE, 1, 72_000);
        builder.pop();

        builder.push("caravan");
        CARAVAN_MAX_ROUTES_PER_PLAYER = builder
                .comment("每名玩家商队路线上限。")
                .defineInRange("maxRoutesPerPlayer", CARAVAN_MAX_ROUTES_PER_PLAYER_VALUE, 1, 64);
        CARAVAN_MAX_QTY_PER_TRIP = builder
                .comment("商队每趟货物数量上限。")
                .defineInRange("maxQtyPerTrip", CARAVAN_MAX_QTY_PER_TRIP_VALUE, 1, 1_000_000);
        CARAVAN_BLOCKS_PER_DAY = builder
                .comment("商队每日行进格数（运输天数 = 1 + 距离/该值）。")
                .defineInRange("blocksPerDay", CARAVAN_BLOCKS_PER_DAY_VALUE, 100L, 1_000_000L);
        CARAVAN_RAID_BASE_BPS = builder
                .comment("被劫基础概率（基点）。")
                .defineInRange("raidBaseBps", CARAVAN_RAID_BASE_BPS_VALUE, 0, 10_000);
        CARAVAN_RAID_MIN_BPS = builder
                .comment("被劫概率下限（基点）。")
                .defineInRange("raidMinBps", CARAVAN_RAID_MIN_BPS_VALUE, 0, 10_000);
        CARAVAN_RAID_MAX_BPS = builder
                .comment("被劫概率上限（基点）。")
                .defineInRange("raidMaxBps", CARAVAN_RAID_MAX_BPS_VALUE, 0, 10_000);
        CARAVAN_RAID_PER_KM_BPS = builder
                .comment("每公里增加的被劫概率（基点）。")
                .defineInRange("raidPerKmBps", CARAVAN_RAID_PER_KM_BPS_VALUE, 0, 10_000);
        CARAVAN_RAID_PER_KM_CAP_BPS = builder
                .comment("距离带来的被劫概率上限（基点）。")
                .defineInRange("raidPerKmCapBps", CARAVAN_RAID_PER_KM_CAP_BPS_VALUE, 0, 10_000);
        CARAVAN_RAID_ALERT_BONUS_BPS = builder
                .comment("任一端点处于 RAID_ALERT 时的被劫加成（基点）。")
                .defineInRange("raidAlertBonusBps", CARAVAN_RAID_ALERT_BONUS_BPS_VALUE, 0, 10_000);
        CARAVAN_GUARD_REDUCE_BPS = builder
                .comment("每级保镖降低的被劫概率（基点）。")
                .defineInRange("guardReduceBps", CARAVAN_GUARD_REDUCE_BPS_VALUE, 0, 10_000);
        CARAVAN_GUARD_WAGE_1 = builder
                .comment("1 级保镖每趟工资。")
                .defineInRange("guardWage1", CARAVAN_GUARD_WAGE_1_VALUE, 0L, 1_000_000L);
        CARAVAN_GUARD_WAGE_2 = builder
                .comment("2 级保镖每趟工资。")
                .defineInRange("guardWage2", CARAVAN_GUARD_WAGE_2_VALUE, 0L, 1_000_000L);
        CARAVAN_GUARD_WAGE_3 = builder
                .comment("3 级保镖每趟工资。")
                .defineInRange("guardWage3", CARAVAN_GUARD_WAGE_3_VALUE, 0L, 1_000_000L);
        CARAVAN_RAID_LOSS_RATIO_BPS = builder
                .comment("被劫时丢失货物的比例（基点），默认 3000 = 30%。")
                .defineInRange("raidLossRatioBps", CARAVAN_RAID_LOSS_RATIO_BPS_VALUE, 0, 10_000);
        CARAVAN_INSURE_MARGIN_BPS = builder
                .comment("保费边际（基点）：保费 = 期望赔付 × 该值/10000，须 > 10000 保证系统不亏。")
                .defineInRange("insureMarginBps", CARAVAN_INSURE_MARGIN_BPS_VALUE, 10_000, 100_000);
        CARAVAN_SATURATION_PER_UNIT_BPS = builder
                .comment("每单位到货增加的路线饱和度（基点）。")
                .defineInRange("saturationPerUnitBps", CARAVAN_SATURATION_PER_UNIT_BPS_VALUE, 0, 10_000);
        CARAVAN_SATURATION_DECAY_BPS = builder
                .comment("路线饱和度每日衰减（基点）。")
                .defineInRange("saturationDecayBps", CARAVAN_SATURATION_DECAY_BPS_VALUE, 0, 10_000);
        CARAVAN_SELL_PRICE_CAP_MULTIPLIER = builder
                .comment("商队买卖价相对 basePrice 的上限倍数。")
                .defineInRange("sellPriceCapMultiplier", CARAVAN_SELL_PRICE_CAP_MULTIPLIER_VALUE, 1, 100);
        builder.pop();

        builder.push("earnings");
        EARNINGS_QUARTER_DAYS = builder
                .comment("财报季：季度天数；每家上市公司每季度发布一次季报。")
                .defineInRange("quarterDays", EARNINGS_QUARTER_DAYS_VALUE, 1, 365);
        EARNINGS_EXPECTATION_QUARTERS = builder
                .comment("财报季：分析师预期的外推窗口（期数），线性外推最近 N 期净利润。")
                .defineInRange("expectationQuarters", EARNINGS_EXPECTATION_QUARTERS_VALUE, 1, 12);
        EARNINGS_SURPRISE_CAP_BPS = builder
                .comment("财报季：预期差钳制（bps），5000 = ±50%。")
                .defineInRange("surpriseCapBps", EARNINGS_SURPRISE_CAP_BPS_VALUE, 100, 100_000);
        EARNINGS_REACTION_BPS = builder
                .comment("财报季：每 10000bps 预期差对应的股价冲击 bps，30 = 10% 超预期 → +0.3% 股价。")
                .defineInRange("reactionBps", EARNINGS_REACTION_BPS_VALUE, 0, 10_000);
        EARNINGS_MAX_SHOCK_BPS = builder
                .comment("财报季：单次财报股价冲击上限（bps），2000 = ±20%，防闪崩。"
                        + "引擎对 shockBps 另有 ±50% 硬钳制，超过 5000 的配置本就无效，故上限设为 5000。")
                .defineInRange("maxShockBps", EARNINGS_MAX_SHOCK_BPS_VALUE, 0, 5_000);
        EARNINGS_FV_PASS_THROUGH_BPS = builder
                .comment("财报季：冲击传导至 fairValue 的比例（bps），5000 = 50%。")
                .defineInRange("fvPassThroughBps", EARNINGS_FV_PASS_THROUGH_BPS_VALUE, 0, 10_000);
        EARNINGS_HISTORY_KEPT = builder
                .comment("财报季：每家公司保留的季报期数。")
                .defineInRange("historyKept", EARNINGS_HISTORY_KEPT_VALUE, 1, 40);
        EARNINGS_EX_DIVIDEND = builder
                .comment("财报季：分红后是否按每股分红额除权下调股价。")
                .define("exDividend", EARNINGS_EX_DIVIDEND_VALUE);
        builder.pop();

        builder.push("npcEconomy");
        NPC_INVESTORS_ENABLED = builder
                .comment("NPC 投资者：关闭后 NPC 不交易不投票，已有持仓保留，仍可被交易、分红和交割。")
                .define("npcInvestorsEnabled", NPC_INVESTORS_ENABLED_VALUE);
        NPC_INITIAL_CAPITAL_EACH = builder
                .comment("每个 NPC 投资者原型首次启用时从 NPC 资金池转入的初始资本。")
                .defineInRange("initialCapitalEach", NPC_INITIAL_CAPITAL_EACH_VALUE, 0L, 100_000_000L);
        NPC_DAILY_BUDGET = builder
                .comment("NPC 投资者现金低于该值时，每天从 NPC 资金池补足到该值。")
                .defineInRange("dailyBudget", NPC_DAILY_BUDGET_VALUE, 0L, 10_000_000L);
        NPC_POOL_RESERVE_FLOOR = builder
                .comment("NPC 资金池低于该值时不再注资或补足，避免把资金池拉到央行托底线以下、间接触发增发。")
                .defineInRange("poolReserveFloor", NPC_POOL_RESERVE_FLOOR_VALUE, 0L, Long.MAX_VALUE);
        NPC_MAX_HOLDING_PERCENT_EACH = builder
                .comment("单个 NPC 原型对单家公司的持股上限（占有表决权股份 %）。短线派按一半计。")
                .defineInRange("maxHoldingPercentEach", NPC_MAX_HOLDING_PERCENT_EACH_VALUE, 0, 49);
        NPC_MAX_COMBINED_HOLDING_PERCENT = builder
                .comment("全部 NPC 投资者对单家公司的合计持股上限（%），低于 50% 以免 NPC 自动取得控制权。")
                .defineInRange("maxCombinedHoldingPercent", NPC_MAX_COMBINED_HOLDING_PERCENT_VALUE, 0, 49);
        NPC_MAX_FLOAT_PERCENT_PER_DAY = builder
                .comment("全部 NPC 投资者每天对单只股票的成交量上限（占流通股 %）。")
                .defineInRange("maxFloatPercentPerDay", NPC_MAX_FLOAT_PERCENT_PER_DAY_VALUE, 0, 100);
        NPC_VOTING_ENABLED = builder
                .comment("NPC 股东是否对提案投票。需要同时开启 npcInvestorsEnabled。")
                .define("npcVotingEnabled", NPC_VOTING_ENABLED_VALUE);
        builder.push("tenderPremium");
        NPC_TENDER_PREMIUM_DIVIDEND = builder
                .comment("分红派接受要约所需的溢价（要约价 ≥ 公允价 × (1 + 该值%)）。")
                .defineInRange("dividend", NPC_TENDER_PREMIUM_DIVIDEND_VALUE, 0, 1_000);
        NPC_TENDER_PREMIUM_GROWTH = builder
                .comment("成长派接受要约所需的溢价（%）。")
                .defineInRange("growth", NPC_TENDER_PREMIUM_GROWTH_VALUE, 0, 1_000);
        NPC_TENDER_PREMIUM_SPECULATOR = builder
                .comment("短线派接受要约所需的溢价（%）。")
                .defineInRange("speculator", NPC_TENDER_PREMIUM_SPECULATOR_VALUE, 0, 1_000);
        NPC_TENDER_PREMIUM_VALUE = builder
                .comment("价值派接受要约所需的溢价（%）。")
                .defineInRange("value", NPC_TENDER_PREMIUM_VALUE_VALUE, 0, 1_000);
        builder.pop();
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

    public static boolean minecraftFirstMode() { return getBoolean(MINECRAFT_FIRST_MODE, MINECRAFT_FIRST_MODE_VALUE); }
    public static boolean requirePhysicalTerminal() { return getBoolean(REQUIRE_PHYSICAL_TERMINAL, REQUIRE_PHYSICAL_TERMINAL_VALUE); }
    public static boolean enablePortableLedger() { return getBoolean(ENABLE_PORTABLE_LEDGER, ENABLE_PORTABLE_LEDGER_VALUE); }
    public static boolean legacyFullScreenKeybind() { return getBoolean(LEGACY_FULL_SCREEN_KEYBIND, LEGACY_FULL_SCREEN_KEYBIND_VALUE); }
    public static boolean warehouseCapacityEnabled() { return getBoolean(WAREHOUSE_CAPACITY_ENABLED, WAREHOUSE_CAPACITY_ENABLED_VALUE); }
    public static boolean contractsEnabled() { return getBoolean(CONTRACTS_ENABLED, CONTRACTS_ENABLED_VALUE); }
    public static boolean playerDrivenCompanyProduction() { return getBoolean(PLAYER_DRIVEN_COMPANY_PRODUCTION, PLAYER_DRIVEN_COMPANY_PRODUCTION_VALUE); }
    public static boolean allowLegacyAutomaticCompanyProduction() { return getBoolean(ALLOW_LEGACY_AUTOMATIC_COMPANY_PRODUCTION, ALLOW_LEGACY_AUTOMATIC_COMPANY_PRODUCTION_VALUE); }
    public static boolean newCompaniesPlayerDrivenOnly() { return getBoolean(NEW_COMPANIES_PLAYER_DRIVEN_ONLY, NEW_COMPANIES_PLAYER_DRIVEN_ONLY_VALUE); }
    public static double hybridLegacyFallbackRatio() { return getDouble(HYBRID_LEGACY_FALLBACK_RATIO, HYBRID_LEGACY_FALLBACK_RATIO_VALUE); }
    public static boolean advancedFinanceRequiresTerminal() { return getBoolean(ADVANCED_FINANCE_REQUIRES_TERMINAL, ADVANCED_FINANCE_REQUIRES_TERMINAL_VALUE); }
    public static boolean adminConsoleRequiresPermission() { return getBoolean(ADMIN_CONSOLE_REQUIRES_PERMISSION, ADMIN_CONSOLE_REQUIRES_PERMISSION_VALUE); }
    public static double terminalInteractionDistance() { return getDouble(TERMINAL_INTERACTION_DISTANCE, TERMINAL_INTERACTION_DISTANCE_VALUE); }
    public static boolean worldEconomyGlobalBroadcasts() { return getBoolean(WORLD_ECONOMY_GLOBAL_BROADCASTS, WORLD_ECONOMY_GLOBAL_BROADCASTS_VALUE); }
    public static int warehouseCapacity(int tier) {
        int index = Math.max(0, Math.min(2, tier - 1));
        int value = getInt(WAREHOUSE_CAPACITIES[index], WAREHOUSE_CAPACITY_VALUES[index]);
        if (index > 0) value = Math.max(value, warehouseCapacity(index));
        return value;
    }
    public static int warehouseTransferLimit(int tier) {
        int index = Math.max(0, Math.min(2, tier - 1));
        int value = getInt(WAREHOUSE_TRANSFER_LIMITS[index], WAREHOUSE_TRANSFER_VALUES[index]);
        return Math.min(warehouseCapacity(tier), value);
    }
    public static int factoryThroughput(int tier) {
        int index = Math.max(0, Math.min(2, tier - 1));
        return getInt(FACTORY_THROUGHPUT[index], FACTORY_THROUGHPUT_VALUES[index]);
    }
    public static int factoryMaintenanceBasisPoints(int tier) {
        int index = Math.max(0, Math.min(2, tier - 1));
        return getInt(FACTORY_MAINTENANCE_BPS[index], FACTORY_MAINTENANCE_BPS_VALUES[index]);
    }
    public static int logisticsMaxActivePerPlayer() {
        return getInt(LOGISTICS_MAX_ACTIVE_PLAYER, LOGISTICS_MAX_ACTIVE_PLAYER_VALUE);
    }
    public static int logisticsMaxActivePerCompany() {
        return getInt(LOGISTICS_MAX_ACTIVE_COMPANY, LOGISTICS_MAX_ACTIVE_COMPANY_VALUE);
    }
    public static int logisticsMaxCargoUnits() {
        return getInt(LOGISTICS_MAX_CARGO_UNITS, LOGISTICS_MAX_CARGO_UNITS_VALUE);
    }
    public static int logisticsDefaultDeadlineDays() {
        return getInt(LOGISTICS_DEADLINE_DAYS, LOGISTICS_DEADLINE_DAYS_VALUE);
    }
    public static int settlementMaxActivePerPlayer(){return getInt(SETTLEMENT_MAX_ACTIVE_PLAYER,SETTLEMENT_MAX_ACTIVE_PLAYER_VALUE);}
    public static int settlementMaxOpen(){return getInt(SETTLEMENT_MAX_OPEN,SETTLEMENT_MAX_OPEN_VALUE);}
    public static int settlementDeadlineDays(){return getInt(SETTLEMENT_DEADLINE_DAYS,SETTLEMENT_DEADLINE_DAYS_VALUE);}
    public static int settlementRewardBasisPoints(){return getInt(SETTLEMENT_REWARD_BPS,SETTLEMENT_REWARD_BPS_VALUE);}
    public static boolean explorationEnabled(){return getBoolean(EXPLORATION_ENABLED,EXPLORATION_ENABLED_VALUE);}
    public static boolean explorationWorldgenEnabled(){return getBoolean(EXPLORATION_WORLDGEN_ENABLED,EXPLORATION_WORLDGEN_ENABLED_VALUE);}
    public static int explorationCooldownDays(){return getInt(EXPLORATION_COOLDOWN_DAYS,EXPLORATION_COOLDOWN_DAYS_VALUE);}
    public static int explorationMaxDistance(){return getInt(EXPLORATION_MAX_DISTANCE,EXPLORATION_MAX_DISTANCE_VALUE);}
    public static int explorationDeadlineDays(){return getInt(EXPLORATION_DEADLINE_DAYS,EXPLORATION_DEADLINE_DAYS_VALUE);}
    public static long explorationReward(){return getLong(EXPLORATION_REWARD,EXPLORATION_REWARD_VALUE);}
    public static int regionalHistoryDays(){return getInt(REGIONAL_HISTORY_DAYS,REGIONAL_HISTORY_DAYS_VALUE);}
    public static int maxCollateralAgreements(){return Math.min(4096,getInt(COLLATERAL_MAX_ACTIVE,COLLATERAL_MAX_ACTIVE_VALUE));}
    public static int maxHedgeObjectives(){return Math.min(4096,getInt(HEDGE_MAX_OBJECTIVES,HEDGE_MAX_OBJECTIVES_VALUE));}
    public static int collateralDailyBatch(){return Math.min(512,getInt(RISK_DAILY_BATCH,RISK_DAILY_BATCH_VALUE));}
    public static int collateralInitialLtvBps(){return Math.min(6000,getInt(COLLATERAL_INITIAL_LTV_BPS,COLLATERAL_INITIAL_LTV_BPS_VALUE));}
    public static int collateralMaintenanceLtvBps(){return Math.max(collateralInitialLtvBps(),getInt(COLLATERAL_MAINTENANCE_LTV_BPS,COLLATERAL_MAINTENANCE_LTV_BPS_VALUE));}
    public static int collateralLiquidationLtvBps(){return Math.max(collateralMaintenanceLtvBps(),getInt(COLLATERAL_LIQUIDATION_LTV_BPS,COLLATERAL_LIQUIDATION_LTV_BPS_VALUE));}

    public static int maxConditionalStockOrdersPerPlayer() {
        return getInt(MAX_CONDITIONAL_STOCK_ORDERS_PER_PLAYER, MAX_CONDITIONAL_STOCK_ORDERS_PER_PLAYER_VALUE);
    }

    public static double stockMarketMakerSpread() {
        return getDouble(STOCK_MARKET_MAKER_SPREAD, STOCK_MARKET_MAKER_SPREAD_VALUE);
    }

    /** 发起要约需事前持股比例（基点，如 3000 = 30%） */
    public static int takeoverMinHoldingBps() {
        return getInt(TAKEOVER_MIN_HOLDING_BPS, TAKEOVER_MIN_HOLDING_BPS_VALUE);
    }

    /** 强制私有化所需持股比例（基点，如 9000 = 90%） */
    public static int takeoverSqueezeOutBps() {
        return getInt(TAKEOVER_SQUEEZE_OUT_BPS, TAKEOVER_SQUEEZE_OUT_BPS_VALUE);
    }

    /** 要约价下限倍数（相对 fairValue） */
    public static double takeoverPriceFloorRatio() {
        return getDouble(TAKEOVER_PRICE_FLOOR_RATIO, TAKEOVER_PRICE_FLOOR_RATIO_VALUE);
    }

    /** 要约价上限倍数（相对 fairValue） */
    public static double takeoverPriceCapRatio() {
        return getDouble(TAKEOVER_PRICE_CAP_RATIO, TAKEOVER_PRICE_CAP_RATIO_VALUE);
    }

    /** 要约期下限（MC 天） */
    public static int takeoverMinDurationDays() {
        return getInt(TAKEOVER_MIN_DURATION_DAYS, TAKEOVER_MIN_DURATION_DAYS_VALUE);
    }

    /** 要约期上限（MC 天） */
    public static int takeoverMaxDurationDays() {
        return getInt(TAKEOVER_MAX_DURATION_DAYS, TAKEOVER_MAX_DURATION_DAYS_VALUE);}
    public static double stockTargetInventoryRatio() {
        return getDouble(STOCK_TARGET_INVENTORY_RATIO, STOCK_TARGET_INVENTORY_RATIO_VALUE);
    }

    public static double stockMaxInventoryMultiplier() {
        return getDouble(STOCK_MAX_INVENTORY_MULTIPLIER, STOCK_MAX_INVENTORY_MULTIPLIER_VALUE);
    }

    public static double stockMaxTradeFloatRatio() {
        return getDouble(STOCK_MAX_TRADE_FLOAT_RATIO, STOCK_MAX_TRADE_FLOAT_RATIO_VALUE);
    }

    public static double ipoMarketMakerReserveRatio() {
        return getDouble(IPO_MARKET_MAKER_RESERVE_RATIO, IPO_MARKET_MAKER_RESERVE_RATIO_VALUE);
    }

    // Phase 2 做市商报价

    public static boolean marketMakerTargetPriceEnabled() {
        return getBoolean(MM_TARGET_PRICE_STRATEGY_ENABLED, MM_TARGET_PRICE_STRATEGY_ENABLED_VALUE);
    }

    public static boolean marketMakerInventorySkewEnabled() {
        return getBoolean(MM_INVENTORY_SKEW_STRATEGY_ENABLED, MM_INVENTORY_SKEW_STRATEGY_ENABLED_VALUE);
    }

    public static boolean marketMakerVolatilitySpreadEnabled() {
        return getBoolean(MM_VOLATILITY_SPREAD_STRATEGY_ENABLED, MM_VOLATILITY_SPREAD_STRATEGY_ENABLED_VALUE);
    }

    public static double marketMakerSkewFactor() {
        return getDouble(MM_INVENTORY_SKEW_FACTOR, MM_INVENTORY_SKEW_FACTOR_VALUE);
    }

    public static double marketMakerVolatilityFactor() {
        return getDouble(MM_VOLATILITY_SPREAD_FACTOR, MM_VOLATILITY_SPREAD_FACTOR_VALUE);
    }

    public static double marketMakerMaxSkew() {
        return getDouble(MM_MAX_INVENTORY_SKEW, MM_MAX_INVENTORY_SKEW_VALUE);
    }

    public static double marketMakerMaxVolatilityAdjust() {
        return getDouble(MM_MAX_VOLATILITY_ADJUST, MM_MAX_VOLATILITY_ADJUST_VALUE);
    }

    public static double marketMakerCircuitBreakerThreshold() {
        return getDouble(MM_CIRCUIT_BREAKER_THRESHOLD, MM_CIRCUIT_BREAKER_THRESHOLD_VALUE);
    }

    public static double marketMakerTargetPriceDeviationThreshold() {
        return getDouble(MM_TARGET_PRICE_DEVIATION_THRESHOLD, MM_TARGET_PRICE_DEVIATION_THRESHOLD_VALUE);
    }

    // ---- Phase 4 央行联动 ----

    /** 央行跨市场做市商健康度检查总开关。 */
    public static boolean cbMmHealthCheckEnabled() {
        return getBoolean(CB_MM_HEALTH_CHECK_ENABLED, CB_MM_HEALTH_CHECK_ENABLED_VALUE);
    }

    /** 央行对期货做市商单次紧急贷款上限。 */
    public static long cbMmEmergencyLoanCap() {
        return getLong(CB_MM_EMERGENCY_LOAN_CAP, CB_MM_EMERGENCY_LOAN_CAP_VALUE);
    }

    /** 紧急贷款每日还款比例（基点）。 */
    public static int cbMmLoanRepaymentBps() {
        return getInt(CB_MM_LOAN_REPAYMENT_BPS, CB_MM_LOAN_REPAYMENT_BPS_VALUE);}
    public static int villagePopulation() {
        return getInt(VILLAGE_POPULATION, VILLAGE_POPULATION_VALUE);
    }

    public static long villagePerCapitaBudget() {
        return getLong(VILLAGE_PER_CAPITA_BUDGET, VILLAGE_PER_CAPITA_BUDGET_VALUE);
    }

    public static int villageBudgetReserveDays() {
        return getInt(VILLAGE_BUDGET_RESERVE_DAYS, VILLAGE_BUDGET_RESERVE_DAYS_VALUE);
    }

    public static int villagePremiumMinBps() {
        return getInt(VILLAGE_PREMIUM_MIN_BPS, VILLAGE_PREMIUM_MIN_BPS_VALUE);
    }

    public static int villagePremiumMaxBps() {
        return Math.max(villagePremiumMinBps(),
                getInt(VILLAGE_PREMIUM_MAX_BPS, VILLAGE_PREMIUM_MAX_BPS_VALUE));
    }

    public static int villageFairToleranceBps() {
        return getInt(VILLAGE_FAIR_TOLERANCE_BPS, VILLAGE_FAIR_TOLERANCE_BPS_VALUE);
    }

    public static int villageGougeToleranceBps() {
        return Math.max(villageFairToleranceBps(),
                getInt(VILLAGE_GOUGE_TOLERANCE_BPS, VILLAGE_GOUGE_TOLERANCE_BPS_VALUE));
    }

    public static int villageBoycottFavor() {
        return getInt(VILLAGE_BOYCOTT_FAVOR, VILLAGE_BOYCOTT_FAVOR_VALUE);
    }

    public static int villageTrustFavor() {
        return getInt(VILLAGE_TRUST_FAVOR, VILLAGE_TRUST_FAVOR_VALUE);}
    /**
     * 工厂方块配方耗时（tick）。未知配方 id 返回 fallback。
     */
    public static int factoryCookTime(String recipeId, int fallback) {
        int safeFallback = Math.max(1, Math.min(72_000, fallback));
        if (recipeId == null) return safeFallback;
        return switch (recipeId) {
            case "wheat_to_bread" -> getInt(FACTORY_COOK_TIME_BREAD, safeFallback);
            case "iron_ore_to_ingot" -> getInt(FACTORY_COOK_TIME_IRON, safeFallback);
            case "oak_log_to_planks" -> getInt(FACTORY_COOK_TIME_PLANKS, safeFallback);
            default -> safeFallback;
        };}
    // ---- 单人经济：NPC 投资者 ----
    public static boolean npcInvestorsEnabled() {
        return getBoolean(NPC_INVESTORS_ENABLED, NPC_INVESTORS_ENABLED_VALUE);
    }

    public static long npcInitialCapitalEach() {
        return getLong(NPC_INITIAL_CAPITAL_EACH, NPC_INITIAL_CAPITAL_EACH_VALUE);
    }

    public static long npcDailyBudget() {
        return getLong(NPC_DAILY_BUDGET, NPC_DAILY_BUDGET_VALUE);
    }

    public static long npcPoolReserveFloor() {
        return getLong(NPC_POOL_RESERVE_FLOOR, NPC_POOL_RESERVE_FLOOR_VALUE);
    }

    public static int npcMaxHoldingPercentEach() {
        return getInt(NPC_MAX_HOLDING_PERCENT_EACH, NPC_MAX_HOLDING_PERCENT_EACH_VALUE);
    }

    public static int npcMaxCombinedHoldingPercent() {
        return getInt(NPC_MAX_COMBINED_HOLDING_PERCENT, NPC_MAX_COMBINED_HOLDING_PERCENT_VALUE);
    }

    public static int npcMaxFloatPercentPerDay() {
        return getInt(NPC_MAX_FLOAT_PERCENT_PER_DAY, NPC_MAX_FLOAT_PERCENT_PER_DAY_VALUE);
    }

    public static boolean npcVotingEnabled() {
        return getBoolean(NPC_VOTING_ENABLED, NPC_VOTING_ENABLED_VALUE);
    }

    public static int npcTenderPremiumPercent(finance.npcinvestor.NpcInvestorArchetype archetype) {
        if (archetype == null) return NPC_TENDER_PREMIUM_DIVIDEND_VALUE;
        return switch (archetype) {
            case DIVIDEND -> getInt(NPC_TENDER_PREMIUM_DIVIDEND, NPC_TENDER_PREMIUM_DIVIDEND_VALUE);
            case GROWTH -> getInt(NPC_TENDER_PREMIUM_GROWTH, NPC_TENDER_PREMIUM_GROWTH_VALUE);
            case SPECULATOR -> getInt(NPC_TENDER_PREMIUM_SPECULATOR, NPC_TENDER_PREMIUM_SPECULATOR_VALUE);
            case VALUE -> getInt(NPC_TENDER_PREMIUM_VALUE, NPC_TENDER_PREMIUM_VALUE_VALUE);
        };
    }

    // ---- 商队商路 ----
    public static int caravanMaxRoutesPerPlayer() {
        return getInt(CARAVAN_MAX_ROUTES_PER_PLAYER, CARAVAN_MAX_ROUTES_PER_PLAYER_VALUE);
    }

    public static int caravanMaxQtyPerTrip() {
        return getInt(CARAVAN_MAX_QTY_PER_TRIP, CARAVAN_MAX_QTY_PER_TRIP_VALUE);
    }

    public static long caravanBlocksPerDay() {
        return getLong(CARAVAN_BLOCKS_PER_DAY, CARAVAN_BLOCKS_PER_DAY_VALUE);
    }

    public static int caravanRaidBaseBps() {
        return getInt(CARAVAN_RAID_BASE_BPS, CARAVAN_RAID_BASE_BPS_VALUE);
    }

    public static int caravanRaidMinBps() {
        return getInt(CARAVAN_RAID_MIN_BPS, CARAVAN_RAID_MIN_BPS_VALUE);
    }

    public static int caravanRaidMaxBps() {
        return getInt(CARAVAN_RAID_MAX_BPS, CARAVAN_RAID_MAX_BPS_VALUE);
    }

    public static int caravanRaidPerKmBps() {
        return getInt(CARAVAN_RAID_PER_KM_BPS, CARAVAN_RAID_PER_KM_BPS_VALUE);
    }

    public static int caravanRaidPerKmCapBps() {
        return getInt(CARAVAN_RAID_PER_KM_CAP_BPS, CARAVAN_RAID_PER_KM_CAP_BPS_VALUE);
    }

    public static int caravanRaidAlertBonusBps() {
        return getInt(CARAVAN_RAID_ALERT_BONUS_BPS, CARAVAN_RAID_ALERT_BONUS_BPS_VALUE);
    }

    public static int caravanGuardReduceBps() {
        return getInt(CARAVAN_GUARD_REDUCE_BPS, CARAVAN_GUARD_REDUCE_BPS_VALUE);
    }

    public static long caravanGuardWage1() {
        return getLong(CARAVAN_GUARD_WAGE_1, CARAVAN_GUARD_WAGE_1_VALUE);
    }

    public static long caravanGuardWage2() {
        return getLong(CARAVAN_GUARD_WAGE_2, CARAVAN_GUARD_WAGE_2_VALUE);
    }

    public static long caravanGuardWage3() {
        return getLong(CARAVAN_GUARD_WAGE_3, CARAVAN_GUARD_WAGE_3_VALUE);
    }

    public static int caravanRaidLossRatioBps() {
        return getInt(CARAVAN_RAID_LOSS_RATIO_BPS, CARAVAN_RAID_LOSS_RATIO_BPS_VALUE);
    }

    public static int caravanInsureMarginBps() {
        return getInt(CARAVAN_INSURE_MARGIN_BPS, CARAVAN_INSURE_MARGIN_BPS_VALUE);
    }

    public static int caravanSaturationPerUnitBps() {
        return getInt(CARAVAN_SATURATION_PER_UNIT_BPS, CARAVAN_SATURATION_PER_UNIT_BPS_VALUE);
    }

    public static int caravanSaturationDecayBps() {
        return getInt(CARAVAN_SATURATION_DECAY_BPS, CARAVAN_SATURATION_DECAY_BPS_VALUE);
    }

    public static int caravanSellPriceCapMultiplier() {
        return getInt(CARAVAN_SELL_PRICE_CAP_MULTIPLIER, CARAVAN_SELL_PRICE_CAP_MULTIPLIER_VALUE);
    }

    public static long ipoFee() {
        return getLong(IPO_FEE, IPO_FEE_VALUE);
    }

    /** 融资融券最大杠杆倍数（如 2 = 2x） */
    public static int marginMaxLeverage() {
        return getInt(MARGIN_MAX_LEVERAGE, MARGIN_MAX_LEVERAGE_VALUE);
    }

    /** 维持保证金率（基点，如 13000 = 130%） */
    public static int marginMaintenanceBps() {
        return getInt(MARGIN_MAINTENANCE_BPS, MARGIN_MAINTENANCE_BPS_VALUE);
    }

    /** 预警线（基点，如 15000 = 150%） */
    public static int marginWarningBps() {
        return getInt(MARGIN_WARNING_BPS, MARGIN_WARNING_BPS_VALUE);
    }

    /** 融资年利率（0.086 = 8.6%） */
    public static double marginFinanceAnnualRate() {
        return getDouble(MARGIN_FINANCE_ANNUAL_RATE, MARGIN_FINANCE_ANNUAL_RATE_VALUE);
    }

    /** 融券年利率（0.106 = 10.6%） */
    public static double marginShortAnnualRate() {
        return getDouble(MARGIN_SHORT_ANNUAL_RATE, MARGIN_SHORT_ANNUAL_RATE_VALUE);
    }

    /** 券池总额占流通股比例（0.05 = 5%） */
    public static double marginPoolRatio() {
        return getDouble(MARGIN_POOL_RATIO, MARGIN_POOL_RATIO_VALUE);
    }

    /** 单人借券上限占券池比例（0.20 = 20%） */
    public static double marginPerPlayerBorrowRatio() {
        return getDouble(MARGIN_PER_PLAYER_BORROW_RATIO, MARGIN_PER_PLAYER_BORROW_RATIO_VALUE);
    }

    public static long capitalProjectGovernanceThreshold() {
        return getLong(CAPITAL_PROJECT_GOVERNANCE_THRESHOLD, CAPITAL_PROJECT_GOVERNANCE_THRESHOLD_VALUE);
    }

    public static int capitalProjectMaxDurationDays() {
        return getInt(CAPITAL_PROJECT_MAX_DURATION_DAYS, CAPITAL_PROJECT_MAX_DURATION_DAYS_VALUE);
    }

    public static int defaultBenchmarkRateBasisPoints() {
        return getInt(DEFAULT_BENCHMARK_RATE_BPS, DEFAULT_BENCHMARK_RATE_BPS_VALUE);
    }

    public static int minBenchmarkRateBasisPoints() { return MIN_BENCHMARK_RATE_BPS_VALUE; }
    public static int maxBenchmarkRateBasisPoints() { return MAX_BENCHMARK_RATE_BPS_VALUE; }
    public static int maxCorporateBonds() { return getInt(MAX_BONDS, MAX_BONDS_VALUE); }
    public static long treasuryBondMinAmount() { return getLong(TREASURY_BOND_MIN_AMOUNT, TREASURY_BOND_MIN_AMOUNT_VALUE); }
    public static long treasuryBondMaxPerPlayer() { return getLong(TREASURY_BOND_MAX_PER_PLAYER, TREASURY_BOND_MAX_PER_PLAYER_VALUE); }
    public static long treasuryBondMaxIssuePerTerm() { return getLong(TREASURY_BOND_MAX_ISSUE_PER_TERM, TREASURY_BOND_MAX_ISSUE_PER_TERM_VALUE); }
    public static long treasuryBondEarlyFee() { return getLong(TREASURY_BOND_EARLY_FEE, TREASURY_BOND_EARLY_FEE_VALUE); }
    public static int maxCompanyLoans() { return getInt(MAX_LOANS, MAX_LOANS_VALUE); }
    public static int loanGraceDays() { return getInt(LOAN_GRACE_DAYS, LOAN_GRACE_DAYS_VALUE); }
    public static int indexBasePoints() { return getInt(INDEX_BASE_POINTS, INDEX_BASE_POINTS_VALUE); }
    public static int annualMcDays() { return getInt(ANNUAL_MC_DAYS, ANNUAL_MC_DAYS_VALUE); }
    public static int maxBondTermDays() { return getInt(MAX_BOND_TERM_DAYS, MAX_BOND_TERM_DAYS_VALUE); }
    public static double maxBondFinancingRatio() { return getDouble(MAX_BOND_FINANCING_RATIO, MAX_BOND_FINANCING_RATIO_VALUE); }
    public static int maxLoanTermDays() { return getInt(MAX_LOAN_TERM_DAYS, MAX_LOAN_TERM_DAYS_VALUE); }
    public static int maxContractRateBasisPoints() { return getInt(MAX_CONTRACT_RATE_BPS, MAX_CONTRACT_RATE_BPS_VALUE); }
    public static String policyRateMode() {
        String mode;
        try {
            mode = POLICY_RATE_MODE.get();
        } catch (IllegalStateException e) {
            return "AUTO";
        }
        if (mode == null) return "AUTO";
        String upper = mode.trim().toUpperCase(java.util.Locale.ROOT);
        return (upper.equals("AUTO") || upper.equals("ADVISORY") || upper.equals("MANUAL")) ? upper : "AUTO";
    }
    public static int policyNeutralRateBps() { return getInt(POLICY_NEUTRAL_RATE_BPS, 500); }
    public static int policyMeetingIntervalDays() { return getInt(POLICY_MEETING_INTERVAL_DAYS, 30); }
    public static int policyGuidanceLeadDays() { return getInt(POLICY_GUIDANCE_LEAD_DAYS, 3); }
    public static int policyMaxStepBps() { return getInt(POLICY_MAX_STEP_BPS, 100); }
    public static int policyMinStepBps() { return getInt(POLICY_MIN_STEP_BPS, 25); }
    public static double policyInflationResponse() { return getDouble(POLICY_INFLATION_RESPONSE, 1.5D); }
    public static double policySlackResponse() { return getDouble(POLICY_SLACK_RESPONSE, 0.5D); }
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

    // Phase 3 期货做市商
    public static double futuresMmSpread() { return getDouble(FUTURES_MM_SPREAD, FUTURES_MM_SPREAD_VALUE); }
    public static double futuresMmNetPositionRatio() { return getDouble(FUTURES_MM_NET_POSITION_RATIO, FUTURES_MM_NET_POSITION_RATIO_VALUE); }
    public static int futuresMmWashTradeWindowMinutes() { return getInt(FUTURES_MM_WASH_TRADE_WINDOW_MINUTES, FUTURES_MM_WASH_TRADE_WINDOW_MINUTES_VALUE); }
    public static int futuresMmWashTradeMaxTrades() { return getInt(FUTURES_MM_WASH_TRADE_MAX_TRADES, FUTURES_MM_WASH_TRADE_MAX_TRADES_VALUE); }
    public static long futuresMmWashTradeMaxNotional() { return getLong(FUTURES_MM_WASH_TRADE_MAX_NOTIONAL, FUTURES_MM_WASH_TRADE_MAX_NOTIONAL_VALUE); }
    public static int futuresMmWashTradeCooldownMinutes() { return getInt(FUTURES_MM_WASH_TRADE_COOLDOWN_MINUTES, FUTURES_MM_WASH_TRADE_COOLDOWN_MINUTES_VALUE); }
    public static boolean futuresMmCircuitBreakerEnabled() { try { return FUTURES_MM_CIRCUIT_BREAKER_ENABLED.get(); } catch (IllegalStateException ex) { return FUTURES_MM_CIRCUIT_BREAKER_ENABLED_VALUE; } }
    public static int futuresMmCircuitBreakerDeviationBps() { return getInt(FUTURES_MM_CIRCUIT_BREAKER_DEVIATION_BPS, FUTURES_MM_CIRCUIT_BREAKER_DEVIATION_BPS_VALUE); }
    public static long futuresMmInitialCapital() { return getLong(FUTURES_MM_INITIAL_CAPITAL, FUTURES_MM_INITIAL_CAPITAL_VALUE); }
    public static boolean bankingEnabled(){try{return BANKING_ENABLED.get();}catch(IllegalStateException e){return BANKING_ENABLED_VALUE;}}public static int defaultBankCount(){return getInt(DEFAULT_BANK_COUNT,DEFAULT_BANK_COUNT_VALUE);}public static long bankInitialCapital(){return getLong(BANK_INITIAL_CAPITAL,BANK_INITIAL_CAPITAL_VALUE);}public static int bankDemandSpreadBps(){return getInt(BANK_DEMAND_SPREAD,BANK_DEMAND_SPREAD_VALUE);}public static int bankTimeSpreadBps(){return getInt(BANK_TIME_SPREAD,BANK_TIME_SPREAD_VALUE);}public static int bankLoanSpreadBps(){return getInt(BANK_LOAN_SPREAD,BANK_LOAN_SPREAD_VALUE);}public static int bankDemandReserveBps(){return getInt(BANK_DEMAND_RESERVE_BPS,BANK_DEMAND_RESERVE_BPS_VALUE);}public static int bankTimeReserveBps(){return Math.min(bankDemandReserveBps(),getInt(BANK_TIME_RESERVE_BPS,BANK_TIME_RESERVE_BPS_VALUE));}public static long bankMinimumReserve(){return getLong(BANK_MIN_RESERVE,BANK_MIN_RESERVE_VALUE);}public static int bankMinimumCapitalBps(){return getInt(BANK_MIN_CAPITAL_BPS,BANK_MIN_CAPITAL_BPS_VALUE);}public static int bankSingleBorrowerLimitBps(){return getInt(BANK_SINGLE_BORROWER_BPS,BANK_SINGLE_BORROWER_BPS_VALUE);}public static int bankCentralPenaltyBps(){return getInt(BANK_CB_PENALTY_BPS,BANK_CB_PENALTY_BPS_VALUE);}public static int bankInsuranceFeeBps(){return getInt(BANK_INSURANCE_FEE_BPS,BANK_INSURANCE_FEE_BPS_VALUE);}public static long bankInsuranceLimit(){return getLong(BANK_INSURANCE_LIMIT,BANK_INSURANCE_LIMIT_VALUE);}public static int bankStressMaxRounds(){return getInt(BANK_STRESS_ROUNDS,BANK_STRESS_ROUNDS_VALUE);}public static int bankReferenceTtlDays(){return getInt(BANK_REFERENCE_TTL_DAYS,BANK_REFERENCE_TTL_DAYS_VALUE);}
    // 定期存款
    public static List<Integer> timeDepositTerms(){List<Integer> terms=new ArrayList<>();for(String part:getString(BANK_TIME_DEPOSIT_TERMS,BANK_TIME_DEPOSIT_TERMS_VALUE).split(",")){try{int t=Integer.parseInt(part.trim());if(t>=2&&t<=3_650&&!terms.contains(t))terms.add(t);}catch(NumberFormatException ignored){}}if(terms.isEmpty())terms.addAll(List.of(7,30,90));Collections.sort(terms);return List.copyOf(terms);}
    public static long timeDepositMinAmount(){return getLong(BANK_TIME_DEPOSIT_MIN_AMOUNT,BANK_TIME_DEPOSIT_MIN_AMOUNT_VALUE);}
    public static long timeDepositMaxPerPlayer(){return getLong(BANK_TIME_DEPOSIT_MAX_PER_PLAYER,BANK_TIME_DEPOSIT_MAX_PER_PLAYER_VALUE);}
    public static long timeDepositEarlyFee(){return getLong(BANK_TIME_DEPOSIT_EARLY_FEE,BANK_TIME_DEPOSIT_EARLY_FEE_VALUE);}
    public static int earningsQuarterDays() { return getInt(EARNINGS_QUARTER_DAYS, EARNINGS_QUARTER_DAYS_VALUE); }
    public static int earningsExpectationQuarters() { return getInt(EARNINGS_EXPECTATION_QUARTERS, EARNINGS_EXPECTATION_QUARTERS_VALUE); }
    public static int earningsSurpriseCapBps() { return getInt(EARNINGS_SURPRISE_CAP_BPS, EARNINGS_SURPRISE_CAP_BPS_VALUE); }
    public static int earningsReactionBps() { return getInt(EARNINGS_REACTION_BPS, EARNINGS_REACTION_BPS_VALUE); }
    public static int earningsMaxShockBps() { return getInt(EARNINGS_MAX_SHOCK_BPS, EARNINGS_MAX_SHOCK_BPS_VALUE); }
    public static int earningsFvPassThroughBps() { return getInt(EARNINGS_FV_PASS_THROUGH_BPS, EARNINGS_FV_PASS_THROUGH_BPS_VALUE); }
    public static int earningsHistoryKept() { return getInt(EARNINGS_HISTORY_KEPT, EARNINGS_HISTORY_KEPT_VALUE); }
    public static boolean earningsExDividend() { return getBoolean(EARNINGS_EX_DIVIDEND, EARNINGS_EX_DIVIDEND_VALUE); }
    public static boolean bankRunEnabled(){try{return BANK_RUN_ENABLED.get();}catch(IllegalStateException e){return BANK_RUN_ENABLED_VALUE;}}
    public static boolean bankRunAutoResolve(){try{return BANK_RUN_AUTO_RESOLVE.get();}catch(IllegalStateException e){return BANK_RUN_AUTO_RESOLVE_VALUE;}}
    public static int bankRunPanicDecay(){return getInt(BANK_RUN_PANIC_DECAY,BANK_RUN_PANIC_DECAY_VALUE);}
    public static int bankRunMaxDailyShareBps(){return getInt(BANK_RUN_MAX_SHARE_BPS,BANK_RUN_MAX_SHARE_BPS_VALUE);}
    public static int bankRunDailyCapBps(){return getInt(BANK_RUN_DAILY_CAP_BPS,BANK_RUN_DAILY_CAP_BPS_VALUE);}
    public static int bankRunCapPanicThreshold(){return getInt(BANK_RUN_CAP_PANIC,BANK_RUN_CAP_PANIC_VALUE);}
    public static int bankRunFreezePanicThreshold(){return getInt(BANK_RUN_FREEZE_PANIC,BANK_RUN_FREEZE_PANIC_VALUE);}
    public static int bankRunFailPanicThreshold(){return getInt(BANK_RUN_FAIL_PANIC,BANK_RUN_FAIL_PANIC_VALUE);}
    public static int bankRunNpcSeedBps(){return getInt(BANK_RUN_NPC_SEED_BPS,BANK_RUN_NPC_SEED_BPS_VALUE);}
    public static int bankRunNpcTimeShareBps(){return getInt(BANK_RUN_NPC_TIME_BPS,BANK_RUN_NPC_TIME_BPS_VALUE);}
    public static int bankRunNpcDefaultBps(){return getInt(BANK_RUN_NPC_DEFAULT_BPS,BANK_RUN_NPC_DEFAULT_BPS_VALUE);}
    public static int bankRunRumorCooldownDays(){return getInt(BANK_RUN_RUMOR_COOLDOWN,BANK_RUN_RUMOR_COOLDOWN_VALUE);}
    public static int bankRunIlliquidDays(){return getInt(BANK_RUN_ILLIQUID_DAYS,BANK_RUN_ILLIQUID_DAYS_VALUE);}
    public static double bankRunRumorChance(){return getDouble(BANK_RUN_RUMOR_CHANCE,BANK_RUN_RUMOR_CHANCE_VALUE);}
public static long playerBankCharterFee(){return getLong(PLAYER_BANK_CHARTER_FEE,PLAYER_BANK_CHARTER_FEE_VALUE);}
public static long playerBankMinCapital(){return getLong(PLAYER_BANK_MIN_CAPITAL,PLAYER_BANK_MIN_CAPITAL_VALUE);}
public static int playerBankMaxPerPlayer(){return getInt(PLAYER_BANK_MAX_PER_PLAYER,PLAYER_BANK_MAX_PER_PLAYER_VALUE);}
public static int playerBankRelatedPartyLimitBps(){return getInt(PLAYER_BANK_RELATED_PARTY_LIMIT_BPS,PLAYER_BANK_RELATED_PARTY_LIMIT_BPS_VALUE);}
public static long playerBankDailyFine(){return getLong(PLAYER_BANK_DAILY_FINE,PLAYER_BANK_DAILY_FINE_VALUE);}
public static int playerBankDividendLeverageBps(){return getInt(PLAYER_BANK_DIVIDEND_LEVERAGE_BPS,PLAYER_BANK_DIVIDEND_LEVERAGE_BPS_VALUE);}

    private static String getString(ForgeConfigSpec.ConfigValue<String> value, String fallback) {
        try {
            String v = value.get();
            return v == null ? fallback : v;
        } catch (IllegalStateException ex) {
            return fallback;
        }
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

    private static boolean getBoolean(ForgeConfigSpec.BooleanValue value, boolean fallback) {
        try {
            return value.get();
        } catch (IllegalStateException ex) {
            return fallback;
        }
    }
}
