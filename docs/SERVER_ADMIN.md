# 服务器运维：迁移、回退与存档兼容

## 迁移到新服务器

1. 旧服务器正常停服，复制完整世界、`config`、世界内 `serverconfig` 和模组列表。
2. 新服务器使用 Java 17、Minecraft 1.20.1、Forge 47.4.16 和同版本 Finance。客户端与服务端必须是同一个 Finance 版本。
3. 首次启动前保留一份只读备份，不要删除不认识的 Finance SavedData。
4. 启动后执行 `/finance status`、`/finance diagnose`，再抽查账户、订单、库存、公司、股票、债务、基金、期货、保险、仓库和合同。
5. 旧公司没有经营档案时会进入 `LEGACY_AUTOMATIC`；旧商品没有仓库时仍可访问，不会因容量不足被删除。

自动化测试覆盖从 DataVersion 13、15、17、19 起的直接升级和重复加载，并确认升级后不会多出资产。跨 Minecraft 大版本、低于 13 的存档和降级到旧 JAR 都不保证兼容。

## 出问题时

**操作前**：停服，备份整个世界目录（尤其是 `data/finance_data.dat` 和商品库存 SavedData），记下 `/finance status`、`/finance diagnose` 的输出，保留当前 JAR、Forge 版本和 `serverconfig/finance-common.toml`。

**兼容模式**：在 `serverconfig/finance-common.toml` 设 `gameplay.minecraftFirstMode=false` 可恢复旧的完整金融入口。关闭 `contractsEnabled` 只拦新合同，已有合同照常交付、到期和退款。配置要停服修改，然后完整重启。

**模块降级**：一致性检查失败时，模块会自动切成只读或暂停。仓库只许取不许存，合同只许退款，公司暂停生产。修好根因、`/finance diagnose` 没有 ERROR/FATAL 后，再执行：

- `/finance resume warehouse`
- `/finance resume contract`
- `/finance resume company_gameplay`

不要直接改 NBT 去猜资产归属，诊断还在报错时也不要强行恢复。

**降级版本**：新存档可能含有旧 JAR 不认识的字段，旧 JAR 写回时不保证安全。真要降级，必须用降级前的备份，不要让旧 JAR 打开唯一一份新存档。恢复后依次核对玩家余额和冻结资金、NPC 与央行、公司现金、仓库托管、合同 escrow、挂单和公司行动 escrow。总量对不上就保持停服，从最后一份一致的备份恢复。

## 存档兼容规则（开发者）

- 已有 NBT key 不改名；确实要改时保留旧 key 的读取，并写明确的迁移。
- `EconomySavedData.DATA_VERSION` 只在存档格式真正变化时加 1，当前是 42。
- 新字段必须有安全默认值。单条坏记录要隔离跳过，不能让整个存档加载失败。
- 关闭某个功能只能拒绝新业务，不能拦住退款、到期、撤单、付息、分红和破产清算。
- 旧存档缺少某个新数据根节点（如 `Warehouses`、`FinanceContracts`、`CompanyGameplay`、`Logistics`、`Settlements`、`Exploration`）时按空数据加载，不凭空创建资金。
- 加载顺序：先账户和商品，再仓库、公司玩法，最后合同，这样才能校验 escrow 和商品引用。
