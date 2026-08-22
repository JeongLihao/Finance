# Finance 服务器配置说明

配置类型：Forge COMMON 配置。  
配置文件由 Forge 在运行环境中生成，修改后建议重启服务器；部分参数在代码中每次使用时读取，Forge 完成配置重载后也能生效，但生产服仍建议重启以避免玩家观察到半周期变化。

## 默认配置示例

```toml
[gameplay]
	# 默认通过世界中的账本和终端进入经济系统；不会停止已有金融合同日结。
	minecraftFirstMode = true
	requirePhysicalTerminal = true
	enablePortableLedger = true
	# 设为 true 可恢复 F 键直接打开旧完整界面。
	legacyFullScreenKeybind = false
	warehouseCapacityEnabled = true
	contractsEnabled = true
	playerDrivenCompanyProduction = true
	# 旧公司仍可继续原自动经营模式。
	allowLegacyAutomaticCompanyProduction = true
	# 新公司默认 HYBRID；开启后改为纯 PLAYER_DRIVEN。
	newCompaniesPlayerDrivenOnly = false
	# HYBRID 当天没有设施成功生产时的低效旧式兜底比例。
	hybridLegacyFallbackRatio = 0.25
	advancedFinanceRequiresTerminal = true
	adminConsoleRequiresPermission = true
	# 终端菜单的最大持续交互距离（方块）。
	terminalInteractionDistance = 8.0
	# 是否允许真正重大经济事件向全服广播；关闭后本地与参与者通知仍保留。
	worldEconomyGlobalBroadcasts = true

[company]
	# 公司没有单独分红策略时使用的默认分红比例。0.40 = 40%
	defaultDividendRatio = 0.40
	# 公司没有单独分红策略时使用的默认分红周期，单位为 MC 天。
	defaultDividendCycleDays = 7
	# 上市公司现金低于安全线后，持续多少个 MC 天触发破产。
	bankruptcyRiskDays = 3
	# 破产现金安全线倍率。安全线 = 预计每日运营成本 × 此倍率。
	bankruptcyCashRiskMultiplier = 1.0
	# 发起 IPO 时向公司所有者收取的费用。
	ipoFee = 5000

[proposal]
	# 股东提案最低参与率。0.25 = 至少 25% 投票权参与，提案才可能通过。
	minParticipationRatio = 0.25

[orders]
	# 每名玩家最多保留的未触发价格提醒数量。
	maxPriceAlertsPerPlayer = 20
	# 每名玩家最多保留的股票条件委托数量。
	maxConditionalStockOrdersPerPlayer = 20

[stockMarketMaker]
	# 股票做市商价差。0.02 = fairValue 上下 2% 形成 bid / ask。
	spread = 0.02

[financialProducts]
	# 默认基准年利率，单位为基点；500 = 5.00%。只影响之后创建的合约。
	defaultBenchmarkRateBasisPoints = 500
	# 债券、贷款和央行票据年化计算使用的 MC 天数。
	annualMcDays = 365
	# 公司债与贷款的世界级合约数量上限。
	maxCorporateBonds = 256
	maxCompanyLoans = 256
	# 公司债/贷款期限和合同利率硬上限。
	maxBondTermDays = 3650
	maxLoanTermDays = 3650
maxContractRateBasisPoints = 10000

[futures]
	# 是否允许创建新期货合约和提交新订单。
	enabled = true
	# 每手合约代表的商品单位数。
	contractSize = 10
	# 初始、维持和强平保证金率，单位为基点。安全关系为 initial > maintenance > liquidation > 0。
	initialMarginBasisPoints = 2000
	maintenanceMarginBasisPoints = 1200
	liquidationMarginBasisPoints = 800
	maxPositionPerContract = 10000
	maxOrders = 4096
	minimumPriceTick = 1
	# 系统无流动性强平的不利滑点上限，500 = 5%。
	liquidationSlippageBasisPoints = 500
	initialGuaranteeFund = 100000
	finalSettlementWindowDays = 3
	minimumSettlementVolume = 10
	maxSettlementSpotDeviationBasisPoints = 2000

[banking]
	# 是否允许新的商业银行存款、贷款等业务；关闭不会删除既有账务。
	enabled = true
	# 默认系统银行数量，范围 2～4。
	defaultBankCount = 3
	# 每家系统银行初始实收资本；创建时以等额准备金资产明确入账。
	initialCapital = 1000000
	# 活期、定期和贷款相对基准利率的利差，单位为基点。
	demandDepositSpreadBasisPoints = 300
	timeDepositSpreadBasisPoints = 100
	loanSpreadBasisPoints = 250
	# 活期/定期法定准备金率与最低绝对准备金。
	demandReserveBasisPoints = 1000
	timeReserveBasisPoints = 500
	minimumReserve = 10000
	# 最低资本充足率与单一借款人占权益上限。
	minimumCapitalBasisPoints = 800
	singleBorrowerLimitBasisPoints = 2500
	# 央行最后贷款人惩罚利差。
	centralBankPenaltyBasisPoints = 300
	# 存款保险费率及同一客户、同一家银行的合并保障上限。
	insuranceFeeBasisPoints = 5
	insuranceLimitPerCustomer = 100000
	# 压力测试同业传染最大轮数。
	stressTestMaxRounds = 8
```

`gameplay` 项只控制业务入口和玩法方式，不会删除旧存档资产，也不会暂停已存在订单、债券、贷款、保单、分红或破产清算。默认模式下，携带金融账本按 F 会打开轻量钱包；商品、公司、银行、证券与管理功能需要对应世界终端。菜单会持续校验终端方块、维度和距离。若需要恢复旧完整入口，可设置 `minecraftFirstMode = false`，或仅设置 `legacyFullScreenKeybind = true`。

央行票据当前固定提供 7、30、90 MC 日三档，期限溢价分别为 25、100、200 基点；这些值和债券二级市场的订单/历史硬上限属于协议与业务安全边界，不支持运行中配置重载。调整 `annualMcDays` 会影响之后的计息和仍在运行的按日计算，生产服应完整重启并避免在合约周期中途修改。

## 调参建议

- 想让公司更容易活下来：提高 `bankruptcyRiskDays`，或降低 `bankruptcyCashRiskMultiplier`。
- 想减少刷屏式提醒和委托：降低 `maxPriceAlertsPerPlayer` 与 `maxConditionalStockOrdersPerPlayer`。
- 想降低做市商对玩家的自动成交能力：提高 `stockMarketMaker.spread`。
- 想让股东投票更像正式治理：提高 `proposal.minParticipationRatio`。
- 想减少公司上市速度：提高 `company.ipoFee`。
- 想降低期货杠杆：提高 `futures.initialMarginBasisPoints`，并保持初始率高于维持率和强平率。
- 想降低单笔成交操纵日结的可能：提高 `futures.minimumSettlementVolume`，或降低 `maxSettlementSpotDeviationBasisPoints`。
- 想抑制银行扩张：提高准备金率或最低资本充足率、降低单一借款人上限。降低保险上限只影响之后的处置计算，不会删除存款。
- 银行初始资本和默认银行数量只用于首次创建；修改配置不会重写已有银行。利差变化只影响新定期存款与新贷款报价，已有合同利率保持锁定。
- 修改合约乘数、保证金率、保障基金或结算窗口后应完整重启；已开仓合约不会重写历史成交价。
