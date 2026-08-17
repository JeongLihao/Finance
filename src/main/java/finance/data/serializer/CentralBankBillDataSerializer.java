package finance.data.serializer;

import finance.config.FinanceConfig;
import finance.fixedincome.CentralBankBill;
import finance.fixedincome.CentralBankBillManager;
import finance.fixedincome.CentralBankBillStatus;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.math.BigInteger;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class CentralBankBillDataSerializer {
    private CentralBankBillDataSerializer() { }

    public static void save(CompoundTag root) {
        CompoundTag state = new CompoundTag();
        state.putLong("CumulativeIssuance", CentralBankBillManager.cumulativePolicyIssuance());
        state.putLong("LastIssuance", CentralBankBillManager.lastPolicyIssuance());
        state.putLong("LastIssuanceDay", CentralBankBillManager.lastPolicyIssuanceDay());
        ListTag bills = new ListTag();
        for (CentralBankBill bill : CentralBankBillManager.bills().values()) {
            CompoundTag tag = new CompoundTag(); tag.putUUID("Id", bill.id()); tag.putInt("Term", bill.termDays());
            tag.putInt("RateBps", bill.annualRateBasisPoints()); tag.putLong("IssueDay", bill.issueDay());
            tag.putLong("Maturity", bill.maturityDay()); tag.putString("Status", bill.status().name());
            ListTag holdings = new ListTag();
            for (var entry : bill.principalByPlayer().entrySet()) {
                CompoundTag holding = new CompoundTag(); holding.putUUID("Player", entry.getKey());
                holding.putLong("Principal", entry.getValue()); holdings.add(holding);
            }
            tag.put("Holdings", holdings); bills.add(tag);
        }
        state.put("Bills", bills); root.put("CentralBankBills", state);
    }

    public static void load(CompoundTag root) {
        CentralBankBillManager.clearDirect();
        if (!root.contains("CentralBankBills", Tag.TAG_COMPOUND)) return;
        CompoundTag state = root.getCompound("CentralBankBills");
        CentralBankBillManager.restorePolicyIssuance(state.getLong("CumulativeIssuance"),
                state.getLong("LastIssuance"), state.getLong("LastIssuanceDay"));
        ListTag bills = state.getList("Bills", Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(CentralBankBillManager.MAX_BILLS, bills.size()); i++) {
            CompoundTag tag = bills.getCompound(i);
            UUID id = NbtDataSupport.readUuidOrNull(tag, "Id");
            CentralBankBillStatus status = NbtDataSupport.safeEnum(CentralBankBillStatus.class, tag.getString("Status"), null);
            int term = tag.getInt("Term"), rate = tag.getInt("RateBps");
            long issue = tag.getLong("IssueDay"), maturity = tag.getLong("Maturity");
            if (id == null || status == null || (term != 7 && term != 30 && term != 90)
                    || rate <= 0 || rate > 100_000 || issue < 0 || maturity != safeAdd(issue, term)) continue;
            CentralBankBill bill = new CentralBankBill(id, term, rate, issue, maturity, status);
            Set<UUID> players = new HashSet<>(); BigInteger total = BigInteger.ZERO;
            ListTag holdings = tag.getList("Holdings", Tag.TAG_COMPOUND); boolean valid = holdings.size() <= 10_000;
            for (int h = 0; valid && h < holdings.size(); h++) {
                CompoundTag holding = holdings.getCompound(h); UUID player = NbtDataSupport.readUuidOrNull(holding, "Player");
                long principal = holding.getLong("Principal");
                if (player == null || !players.add(player) || principal <= 0) { valid = false; break; }
                total = total.add(BigInteger.valueOf(principal));
                if (total.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) { valid = false; break; }
                bill.putPrincipalDirect(player, principal);
            }
            if (valid) {
                BigInteger denominator = BigInteger.valueOf((long) FinanceConfig.annualMcDays() * 10_000L);
                BigInteger maturityTotal = total.add(total.multiply(BigInteger.valueOf(rate))
                        .multiply(BigInteger.valueOf(term)).divide(denominator));
                valid = maturityTotal.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) <= 0;
            }
            if (valid && !bill.principalByPlayer().isEmpty()) CentralBankBillManager.putDirect(bill);
        }
    }
    private static long safeAdd(long value, long add) { try { return Math.addExact(value, add); } catch (ArithmeticException ignored) { return -1; } }
}
