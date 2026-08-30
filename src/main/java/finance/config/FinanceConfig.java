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
    private static final long CAPITAL_PROJECT_GOVERNANCE_THRESHOLD_VALUE = 50_000L;
    private static final int CAPITAL_PROJECT_MAX_DURATION_DAYS_VALUE = 60;
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

    private static final ForgeConfigSpec.IntValue DEFAULT_DIVIDEND_CYCLE_DAYS;
    private static final ForgeConfigSpec.DoubleValue DEFAULT_DIVIDEND_RATIO;
    private static final ForgeConfigSpec.IntValue BANKRUPTCY_RISK_DAYS;
    private static final ForgeConfigSpec.DoubleValue BANKRUPTCY_CASH_RISK_MULTIPLIER;
    private static final ForgeConfigSpec.DoubleValue MIN_PROPOSAL_PARTICIPATION_RATIO;
    private static final ForgeConfigSpec.IntValue MAX_PRICE_ALERTS_PER_PLAYER;
    private static final ForgeConfigSpec.IntValue MAX_CONDITIONAL_STOCK_ORDERS_PER_PLAYER;
    private static final ForgeConfigSpec.DoubleValue STOCK_MARKET_MAKER_SPREAD;
    private static final ForgeConfigSpec.LongValue IPO_FEE;
    private static final ForgeConfigSpec.LongValue CAPITAL_PROJECT_GOVERNANCE_THRESHOLD;
    private static final ForgeConfigSpec.IntValue CAPITAL_PROJECT_MAX_DURATION_DAYS;
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
                .comment("公司发起 IPO 时向所有者收取的费用。")
                .defineInRange("ipoFee", IPO_FEE_VALUE, 0L, Long.MAX_VALUE);
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

    public static long ipoFee() {
        return getLong(IPO_FEE, IPO_FEE_VALUE);
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

    private static boolean getBoolean(ForgeConfigSpec.BooleanValue value, boolean fallback) {
        try {
            return value.get();
        } catch (IllegalStateException ex) {
            return fallback;
        }
    }
}
