# 服务器迁移指南（v0.4.1）

1. 在旧服务器正常停服，复制完整世界、`config`、世界内 `serverconfig` 和模组列表。
2. 新服务器使用 Java 17、Minecraft 1.20.1、Forge 47.4.16 与 Finance v0.4.1。
3. 首次启动前保留只读备份；不要提前删除未知的 Finance SavedData。
4. 启动后执行 `/finance status`、`/finance diagnose`，再抽查账户、订单、库存、公司、股票、债务、基金、期货、保险、仓库和合同。
5. 旧公司没有经营档案时安全进入 `LEGACY_AUTOMATIC`；旧商品没有仓库时资产仍可访问，不会因容量不足被删除。

自动迁移矩阵覆盖 DataVersion 13、15、17、19 至 29，并验证升级后再次加载不增发资产。跨 Minecraft 大版本、低于 13 的存档和向旧 JAR 降级未声明为兼容。
