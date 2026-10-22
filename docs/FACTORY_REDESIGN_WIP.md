# 工厂重做第 3 步（未完成）

分支 `feat/factory-step3`，基于 main `7bdc052`。主代码能编译、能打包，**测试还没改完，`gradlew build` 会失败**。

## 整体设计

玩家公司的产量全部来自实物：

- 第 1 步（已在 main）：加工机（原来的工厂方块）、公司收货箱，机器归属公司，16 格内有公司仓库就自动取料、存货；玩家公司不再每天抽象生产，NPC 公司照旧。
- 第 2 步（已在 main）：农场机、伐木机、采矿机、剪毛机，只收世界里真实存在的东西。
- 第 3 步（本分支）：机器登记到公司名下，接入雇工、停业险、资本项目、管理界面；删掉旧的厂区设备计数 / 三档工艺 / 抽象日结体系。

## 本分支已经做的

- 每台公司机器放下时登记一条 `CompanyFacilityRecord`，拆掉时注销；每家公司最多 64 台。设施类型改为 `PROCESSOR / FARM / LUMBER / MINE / SHEAR`，去掉绑定仓库字段和旧厂房状态。
- 档次 = 设施等级 1～3。机器速度 = `factoryThroughput(档次) ×（50% + 50% × 在岗比例）`，附近没有村庄算满员（`CompanyMachineBlockEntity.refresh/steps`）。
- 每台机器每档 3 个岗位（`MacroLaborService.JOBS_PER_TIER`），雇工照旧按聚落分配。
- 机器每秒把状态写进设施记录：加工机缺料 / 缺燃料 → `MISSING_INPUT`，成品满 → `OUTPUT_FULL`；采集机没活干算正常，仓满 → `OUTPUT_FULL`，矿挖到底 → `DISABLED`。停业险照旧读这些状态。删掉 `UNDERSTAFFED`、`EQUIPMENT_MISSING`。
- 删除：`gameplay/company/factory/*`（设备、厂区、生产方式、档位、FactoryService 等）、`CompanyProductionService`、`CompanyFacilityWorldFeedbackService`、`FactoryOutlineRenderer`、工厂控制器方块及其资源、`CompanyGameplayLongRunSimulationService`。
- 公司日结：玩家公司只自动卖库存，NPC 公司 `produce()` + `autoTrade()`。
- 公司办公桌界面的设施列表改为机器清单（种类、档次、状态、在岗/岗位、坐标），去掉厂房页、"升级设施"按钮和"选生产方式"操作。
- 宏观长期模拟改用"每家公司 3 台 1 档机器开满一天"的替身生产；删掉工业化场景测试。
- 成就“工业革命”改为有机器升到 3 档，“手艺人”改为三台以上机器都还是 1 档且盈利；宏观页显示机器按档次计数。
- `company_production` 进度和公司成员配方奖励改指向新机器。

## 还要做的

1. **修测试**：下面这些测试还在用 `CompanyFacilityType.FACTORY_CONTROLLER`、`CompanyProductionService`、旧构造函数（多一个仓库参数）或被删的状态：
   `Phase6MigrationAcceptanceTest`、`WorldLifecycleIsolationTest`、`CapitalProjectServiceTest`、`CompanyGameplayPersistenceTest`、`CompanyOperatingSnapshotServiceTest`、`WorldInsuranceIntegrationTest`、`EconomyAchievementsTest`、`MacroLaborServiceTest`（产量随在岗比例的断言要改成速度断言或删掉）、`ReviewFixesTest`、`FacilityStatusLabelTest`、`MinecraftFirstPerformanceAuditTest`、`ReleaseCandidateScaleFixtureTest`、`WarehouseTierTest` 等。跑 `gradlew build` 逐个修。
2. **机器升档按钮**：加工机和采集机界面加“升档”（菜单按钮 id，服务端调 `CompanyUpgradeService.upgrade(player, facilityId, key)`），界面上显示当前档次和下一档要求（`CompanyUpgradeRequirementService.requirement`）。档次已经通过数据槽同步到客户端（`tier()`）。
3. **“生产启动”进度**：原来由被删的 `CompanyFacilityWorldFeedbackService` 触发；改为机器第一次把产品存进公司仓库时触发 `FinanceAdvancementTriggers.trigger(player, "company_production")`。`FinanceMod` 登录时的判断也要改成“有 ACTIVE 机器”。
4. **文字**：补 `screen.finance.company_gameplay.machine`（参数：种类、档次、状态、在岗、岗位、坐标）和 `screen.finance.company_gameplay.machine.<processor|farm|lumber|mine|shear>`；资本项目里 `capital_action.create_factory` 改成“机器升档”；删掉 `screen.finance.factory.method.*`、`equipment.*`、`tier.*`、工厂控制器、厂房页相关的旧 key；Ponder 场景 `company_production` 的文字改成机器版。
5. **配置清理**：`companyFacilities` 分组、`factoryMaintenanceBasisPoints`、`playerDrivenCompanyProduction`、`allowLegacyAutomaticCompanyProduction`、`hybridLegacyFallbackRatio` 等旧选项；`CompanyOperatingMode` 的 HYBRID / PLAYER_DRIVEN 区分可以一并简化。
6. **网络协议和存档版本**：`CompanyGameplayMenu` 设施行和操作枚举改了格式，协议号升到 30（另一会话已用到 29）；设施存档格式变了，`DATA_VERSION` 升到 43。
7. **文档**：更新 README、CHANGELOG、`docs/COMPANY_FACILITIES.md`（基本要重写或删掉）、`docs/MACRO_ECONOMY.md` 里讲厂房和岗位的部分；删掉本文件。

做完 1～7 并通过 `gradlew build` 后合并回 main。
