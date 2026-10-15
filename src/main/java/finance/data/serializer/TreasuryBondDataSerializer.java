package finance.data.serializer;

import com.mojang.logging.LogUtils;
import finance.account.AccountManager;
import finance.account.TransactionRecord;
import finance.account.TransactionType;
import finance.config.FinanceConfig;
import finance.fixedincome.TreasuryBond;
import finance.fixedincome.TreasuryBondManager;
import finance.fixedincome.TreasuryBondStatus;
import finance.market.CentralBank;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.slf4j.Logger;

import java.math.BigInteger;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class TreasuryBondDataSerializer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private TreasuryBondDataSerializer() { }

    public static void save(CompoundTag root) {
        CompoundTag state = new CompoundTag();
        state.putLong("CumulativeIssuance", TreasuryBondManager.cumulativeIssuance());
        state.putLong("LastIssuance", TreasuryBondManager.lastIssuance());
        state.putLong("LastIssuanceDay", TreasuryBondManager.lastIssuanceDay());
        ListTag bonds = new ListTag();
        for (TreasuryBond bond : TreasuryBondManager.bonds().values()) {
            // MATURED 债券已在兑付时从注册表移除；此处防御性跳过，防 NBT 无限增长
            if (bond.status() == TreasuryBondStatus.MATURED) continue;
            CompoundTag tag = new CompoundTag(); tag.putUUID("Id", bond.id()); tag.putInt("Term", bond.termDays());
            tag.putInt("RateBps", bond.annualRateBasisPoints()); tag.putLong("IssueDay", bond.issueDay());
            tag.putLong("Maturity", bond.maturityDay()); tag.putString("Status", bond.status().name());
            ListTag holdings = new ListTag();
            for (var entry : bond.principalByPlayer().entrySet()) {
                CompoundTag holding = new CompoundTag(); holding.putUUID("Player", entry.getKey());
                holding.putLong("Principal", entry.getValue()); holdings.add(holding);
            }
            tag.put("Holdings", holdings); bonds.add(tag);
        }
        state.put("Bonds", bonds); root.put("TreasuryBonds", state);
    }

    public static void load(CompoundTag root) {
        TreasuryBondManager.clearDirect();
        if (!root.contains("TreasuryBonds", Tag.TAG_COMPOUND)) return;
        CompoundTag state = root.getCompound("TreasuryBonds");
        TreasuryBondManager.restoreIssuance(state.getLong("CumulativeIssuance"),
                state.getLong("LastIssuance"), state.getLong("LastIssuanceDay"));
        ListTag bonds = state.getList("Bonds", Tag.TAG_COMPOUND);
        int droppedMatured = 0, droppedOversize = 0;
        for (int i = 0; i < Math.min(TreasuryBondManager.MAX_BONDS, bonds.size()); i++) {
            CompoundTag tag = bonds.getCompound(i);
            UUID id = NbtDataSupport.readUuidOrNull(tag, "Id");
            TreasuryBondStatus status = NbtDataSupport.safeEnum(TreasuryBondStatus.class, tag.getString("Status"), null);
            int term = tag.getInt("Term"), rate = tag.getInt("RateBps");
            long issue = tag.getLong("IssueDay"), maturity = tag.getLong("Maturity");
            if (id == null || status == null || (term != 30 && term != 90 && term != 180)
                    || rate <= 0 || rate > 100_000 || issue < 0 || maturity != safeAdd(issue, term)) continue;
            // 旧版本残留：MATURED 意味着已全额兑付、无欠款，直接丢弃（新版本兑付完成即从注册表移除）
            if (status == TreasuryBondStatus.MATURED) { droppedMatured++; continue; }
            TreasuryBond bond = new TreasuryBond(id, term, rate, issue, maturity, status);
            Set<UUID> players = new HashSet<>(); BigInteger total = BigInteger.ZERO;
            ListTag holdings = tag.getList("Holdings", Tag.TAG_COMPOUND);
            if (holdings.size() > 10_000) {
                // 整单丢弃：记警告＋审计（本金去向见认购 TransactionRecord）
                LOGGER.warn("国债 {} 持仓条目 {} 超过上限 10000，数据恢复时整单丢弃", id, holdings.size());
                AccountManager.addTransactionRecord(new TransactionRecord(CentralBank.UUID, CentralBank.UUID, 0,
                        TransactionType.TREASURY_BOND_MATURITY, CentralBank.UUID,
                        "国债数据恢复：持仓超限丢弃/" + id, 0));
                droppedOversize++; continue;
            }
            boolean valid = true;
            for (int h = 0; valid && h < holdings.size(); h++) {
                CompoundTag holding = holdings.getCompound(h); UUID player = NbtDataSupport.readUuidOrNull(holding, "Player");
                long principal = holding.getLong("Principal");
                if (player == null || !players.add(player) || principal <= 0) { valid = false; break; }
                total = total.add(BigInteger.valueOf(principal));
                if (total.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) { valid = false; break; }
                bond.putPrincipalDirect(player, principal);
            }
            if (valid) {
                BigInteger denominator = BigInteger.valueOf((long) FinanceConfig.annualMcDays() * 10_000L);
                BigInteger maturityTotal = total.add(total.multiply(BigInteger.valueOf(rate))
                        .multiply(BigInteger.valueOf(term)).divide(denominator));
                valid = maturityTotal.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) <= 0;
            }
            if (valid && !bond.principalByPlayer().isEmpty()) TreasuryBondManager.putDirect(bond);
        }
        if (droppedMatured > 0) LOGGER.info("国债数据恢复：丢弃 {} 只旧版本 MATURED 残留债券（已全额兑付）", droppedMatured);
        if (droppedOversize > 0) LOGGER.warn("国债数据恢复：{} 只债券因持仓超限被丢弃（已记审计）", droppedOversize);
    }
    private static long safeAdd(long value, long add) { try { return Math.addExact(value, add); } catch (ArithmeticException ignored) { return -1; } }
}
