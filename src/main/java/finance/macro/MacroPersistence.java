package finance.macro;

import finance.diagnostic.ModuleHealthRegistry;
import finance.diagnostic.ModuleRunState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.Map;
import java.util.UUID;

/**
 * 宏观经济存档。DataVersion 39 新增根标签 {@value #ROOT}；旧存档没有该标签时按“刚启用”初始化。
 * 单条坏记录跳过并计数，有跳过时把 MACRO_ECONOMY 模块降为只读，不影响其他模块加载。
 */
public final class MacroPersistence {
    public static final String ROOT = "MacroEconomy";
    public static final int VERSION = 1;

    private MacroPersistence() { }

    public static void save(CompoundTag root) {
        CompoundTag data = new CompoundTag();
        data.putInt("Version", VERSION);
        data.putLong("LastProcessedDay", MacroEconomyManager.lastProcessedDay());
        data.putLong("LastLaborDay", MacroEconomyManager.lastLaborDay());

        ListTag settlements = new ListTag();
        for (SettlementEconomy econ : MacroEconomyManager.settlements()) {
            CompoundTag t = new CompoundTag();
            t.putUUID("Id", econ.settlementId);
            t.putInt("Sol", econ.solBps);
            t.putLong("GrowthMilli", econ.growthMilli);
            t.putLong("LastDay", econ.lastProcessedDay);
            t.putBoolean("Unrest", econ.unrest);
            t.putLong("Employed", econ.employed);
            t.putLong("LastIncome", econ.lastIncome);
            t.putLong("IncomeToday", econ.incomeToday);
            CompoundTag fulfil = new CompoundTag();
            econ.lastFulfilment.forEach((c, v) -> fulfil.putDouble(c.name(), v));
            t.put("Fulfilment", fulfil);
            ListTag goods = new ListTag();
            for (Map.Entry<String, GoodBook> e : econ.goods.entrySet()) {
                GoodBook b = e.getValue();
                CompoundTag g = new CompoundTag();
                g.putString("Id", e.getKey());
                g.putInt("Factor", b.factorBps);
                g.putLong("Demand", b.lastDemand);
                g.putLong("Supply", b.lastSupply);
                g.putLong("Consumed", b.lastConsumed);
                g.putLong("Imports", b.lastImports);
                g.putLong("Exports", b.lastExports);
                g.putLong("ImportsToday", b.importsToday);
                g.putLong("ExportsToday", b.exportsToday);
                ListTag hist = new ListTag();
                for (GoodBook.Point p : b.history) {
                    CompoundTag h = new CompoundTag();
                    h.putLong("D", p.day());
                    h.putInt("F", p.factorBps());
                    h.putLong("Dm", p.demand());
                    h.putLong("S", p.supply());
                    h.putLong("C", p.consumed());
                    hist.add(h);
                }
                g.put("History", hist);
                goods.add(g);
            }
            t.put("Goods", goods);
            ListTag hist = new ListTag();
            for (SettlementEconomy.Point p : econ.history) {
                CompoundTag h = new CompoundTag();
                h.putLong("D", p.day());
                h.putInt("Sol", p.sol());
                h.putInt("Pop", p.population());
                h.putLong("Emp", p.employed());
                h.putLong("Inc", p.income());
                hist.add(h);
            }
            t.put("History", hist);
            settlements.add(t);
        }
        data.put("Settlements", settlements);

        ListTag employment = new ListTag();
        MacroEconomyManager.employmentDirect().forEach((id, n) -> {
            CompoundTag t = new CompoundTag();
            t.putUUID("Facility", id);
            t.putLong("Employed", n);
            UUID source = MacroEconomyManager.laborSource(id);
            if (source != null) t.putUUID("Source", source);
            employment.add(t);
        });
        data.put("Employment", employment);

        ListTag wages = new ListTag();
        MacroEconomyManager.wagesDirect().forEach((id, w) -> {
            CompoundTag t = new CompoundTag();
            t.putUUID("Company", id);
            t.putLong("Wage", w);
            wages.add(t);
        });
        data.put("Wages", wages);

        CompoundTag laws = new CompoundTag();
        MacroEconomyManager.laws().forEach((g, l) -> laws.putString(g.name(), l.name()));
        data.put("Laws", laws);
        CompoundTag lobby = new CompoundTag();
        for (InterestGroup ig : InterestGroup.values()) {
            int v = MacroEconomyManager.lobby(ig);
            if (v != 0) lobby.putInt(ig.name(), v);
        }
        data.put("Lobby", lobby);
        MacroEconomyManager.Proposal p = MacroEconomyManager.proposal();
        if (p != null) {
            CompoundTag t = new CompoundTag();
            t.putString("Level", p.level().name());
            t.putUUID("Proposer", p.proposer());
            t.putLong("Start", p.startDay());
            t.putLong("End", p.endDay());
            data.put("Proposal", t);
        }
        data.putBoolean("Strike", MacroEconomyManager.strike());
        data.putBoolean("Unrest", MacroEconomyManager.unrest());

        ListTag snaps = new ListTag();
        for (MacroEconomyManager.Snapshot s : MacroEconomyManager.snapshots()) {
            CompoundTag t = new CompoundTag();
            t.putLong("D", s.day());
            t.putLong("Cpi", s.cpiX100());
            t.putLong("Pop", s.population());
            t.putInt("Sol", s.avgSol());
            t.putLong("Emp", s.employed());
            t.putLong("Wages", s.wages());
            t.putLong("Cons", s.consumptionValue());
            snaps.add(t);
        }
        data.put("Snapshots", snaps);
        ListTag results = new ListTag();
        for (MacroEconomyManager.LawResult r : MacroEconomyManager.lawHistory()) {
            CompoundTag t = new CompoundTag();
            t.putString("Level", r.level().name());
            t.putBoolean("Passed", r.passed());
            t.putLong("Support", r.support());
            t.putLong("Opposition", r.opposition());
            t.putLong("D", r.day());
            results.add(t);
        }
        data.put("LawHistory", results);
        data.put("Fiscal", saveFiscal());
        root.put(ROOT, data);
    }

    private static CompoundTag saveFiscal() {
        CompoundTag t = new CompoundTag();
        t.putLong("Debt", Treasury.debt());
        t.putBoolean("Austerity", Treasury.austerity());
        t.putLong("LastDay", Treasury.lastProcessedDay());
        t.putLong("Issuance", Treasury.cumulativeIssuance());
        t.put("Today", flows(Treasury.today()));
        ListTag hist = new ListTag();
        for (Treasury.Day d : Treasury.history()) {
            CompoundTag h = new CompoundTag();
            h.putLong("D", d.day());
            h.putLong("Balance", d.balance());
            h.putLong("Debt", d.debt());
            h.putBoolean("Austerity", d.austerity());
            h.put("Flows", flows(d.flows()));
            hist.add(h);
        }
        t.put("History", hist);
        return t;
    }

    private static CompoundTag flows(Map<Treasury.Item, Long> flows) {
        CompoundTag t = new CompoundTag();
        flows.forEach((k, v) -> t.putLong(k.name(), v));
        return t;
    }

    private static Map<Treasury.Item, Long> readFlows(CompoundTag t) {
        Map<Treasury.Item, Long> out = new java.util.EnumMap<>(Treasury.Item.class);
        for (Treasury.Item item : Treasury.Item.values()) {
            if (t.contains(item.name(), Tag.TAG_LONG) && t.getLong(item.name()) > 0) out.put(item, t.getLong(item.name()));
        }
        return out;
    }

    /** 读财政子标签，返回跳过的坏记录数。 */
    private static int loadFiscal(CompoundTag t) {
        int skipped = 0;
        long debt = t.getLong("Debt");
        if (debt < 0) skipped++;
        Treasury.setDebt(debt);
        Treasury.setAusterity(t.getBoolean("Austerity"));
        if (t.contains("LastDay", Tag.TAG_LONG)) Treasury.setLastProcessedDay(t.getLong("LastDay"));
        Treasury.restoreIssuance(t.getLong("Issuance"));
        readFlows(t.getCompound("Today")).forEach(Treasury::restoreToday);
        ListTag hist = t.getList("History", Tag.TAG_COMPOUND);
        for (int i = Math.max(0, hist.size() - Treasury.HISTORY_DAYS); i < hist.size(); i++) {
            try {
                CompoundTag h = hist.getCompound(i);
                Treasury.restoreDay(new Treasury.Day(h.getLong("D"), h.getLong("Balance"), Math.max(0, h.getLong("Debt")),
                        Map.copyOf(readFlows(h.getCompound("Flows"))), h.getBoolean("Austerity")));
            } catch (Exception e) {
                skipped++;
            }
        }
        return skipped;
    }

    public static void load(CompoundTag root) {
        MacroEconomyManager.clearDirect();
        if (!root.contains(ROOT, Tag.TAG_COMPOUND)) return;
        CompoundTag data = root.getCompound(ROOT);
        boolean invalid = data.getInt("Version") != VERSION;
        int skipped = 0;
        if (data.contains("LastProcessedDay", Tag.TAG_LONG)) MacroEconomyManager.setLastProcessedDay(data.getLong("LastProcessedDay"));
        if (data.contains("LastLaborDay", Tag.TAG_LONG)) MacroEconomyManager.setLastLaborDay(data.getLong("LastLaborDay"));

        ListTag settlements = data.getList("Settlements", Tag.TAG_COMPOUND);
        skipped += Math.max(0, settlements.size() - MacroEconomyManager.MAX_SETTLEMENTS);
        for (int i = 0; i < Math.min(settlements.size(), MacroEconomyManager.MAX_SETTLEMENTS); i++) {
            try {
                CompoundTag t = settlements.getCompound(i);
                SettlementEconomy econ = MacroEconomyManager.getOrCreate(t.getUUID("Id"));
                if (econ == null) { skipped++; continue; }
                int sol = t.getInt("Sol");
                if (sol < 0 || sol > MacroFormulas.MAX_SOL) { skipped++; sol = MacroFormulas.clampSol(sol); }
                econ.solBps = sol;
                econ.growthMilli = Math.max(-999, Math.min(999, t.getLong("GrowthMilli")));
                econ.lastProcessedDay = t.getLong("LastDay");
                econ.unrest = t.getBoolean("Unrest");
                econ.employed = Math.max(0, t.getLong("Employed"));
                econ.lastIncome = Math.max(0, t.getLong("LastIncome"));
                econ.incomeToday = Math.max(0, t.getLong("IncomeToday"));
                CompoundTag fulfil = t.getCompound("Fulfilment");
                for (NeedCategory c : NeedCategory.values()) {
                    if (fulfil.contains(c.name(), Tag.TAG_DOUBLE)) {
                        econ.lastFulfilment.put(c, Math.max(0, Math.min(1, fulfil.getDouble(c.name()))));
                    }
                }
                ListTag goods = t.getList("Goods", Tag.TAG_COMPOUND);
                for (int j = 0; j < goods.size(); j++) {
                    try {
                        CompoundTag g = goods.getCompound(j);
                        String id = g.getString("Id");
                        if (id.isBlank() || id.length() > 64) { skipped++; continue; }
                        GoodBook b = econ.book(id);
                        if (b == null) { skipped++; continue; }
                        int factor = g.getInt("Factor");
                        if (factor < MacroFormulas.MIN_FACTOR_BPS || factor > MacroFormulas.MAX_FACTOR_BPS) skipped++;
                        b.restore(factor, g.getLong("Demand"), g.getLong("Supply"), g.getLong("Consumed"),
                                g.getLong("Imports"), g.getLong("Exports"));
                        b.importsToday = Math.max(0, g.getLong("ImportsToday"));
                        b.exportsToday = Math.max(0, g.getLong("ExportsToday"));
                        ListTag hist = g.getList("History", Tag.TAG_COMPOUND);
                        for (int k = Math.max(0, hist.size() - GoodBook.HISTORY_DAYS); k < hist.size(); k++) {
                            CompoundTag h = hist.getCompound(k);
                            b.history.addLast(new GoodBook.Point(h.getLong("D"), MacroFormulas.clampFactor(h.getInt("F")),
                                    Math.max(0, h.getLong("Dm")), Math.max(0, h.getLong("S")), Math.max(0, h.getLong("C"))));
                        }
                    } catch (Exception e) {
                        skipped++;
                    }
                }
                ListTag hist = t.getList("History", Tag.TAG_COMPOUND);
                for (int k = Math.max(0, hist.size() - SettlementEconomy.HISTORY_DAYS); k < hist.size(); k++) {
                    CompoundTag h = hist.getCompound(k);
                    econ.history.addLast(new SettlementEconomy.Point(h.getLong("D"), MacroFormulas.clampSol(h.getInt("Sol")),
                            Math.max(0, h.getInt("Pop")), Math.max(0, h.getLong("Emp")), Math.max(0, h.getLong("Inc"))));
                }
            } catch (Exception e) {
                skipped++;
            }
        }

        for (Tag raw : data.getList("Employment", Tag.TAG_COMPOUND)) {
            try {
                CompoundTag t = (CompoundTag) raw;
                long n = t.getLong("Employed");
                if (n < 0) { skipped++; continue; }
                MacroEconomyManager.setEmployment(t.getUUID("Facility"), n, t.hasUUID("Source") ? t.getUUID("Source") : null);
            } catch (Exception e) {
                skipped++;
            }
        }
        for (Tag raw : data.getList("Wages", Tag.TAG_COMPOUND)) {
            try {
                CompoundTag t = (CompoundTag) raw;
                if (!MacroEconomyManager.setWage(t.getUUID("Company"), t.getLong("Wage"))) skipped++;
            } catch (Exception e) {
                skipped++;
            }
        }
        CompoundTag laws = data.getCompound("Laws");
        for (LawGroup g : LawGroup.values()) {
            if (!laws.contains(g.name(), Tag.TAG_STRING)) continue;
            try {
                LawLevel level = LawLevel.valueOf(laws.getString(g.name()));
                if (level.group() == g) MacroEconomyManager.setLaw(level); else skipped++;
            } catch (Exception e) {
                skipped++;
            }
        }
        CompoundTag lobby = data.getCompound("Lobby");
        for (InterestGroup ig : InterestGroup.values()) {
            if (lobby.contains(ig.name(), Tag.TAG_INT)) {
                int v = lobby.getInt(ig.name());
                MacroEconomyManager.setLobby(ig, Math.max(-PoliticsService.MAX_LOBBY, Math.min(PoliticsService.MAX_LOBBY, v)));
            }
        }
        if (data.contains("Proposal", Tag.TAG_COMPOUND)) {
            try {
                CompoundTag t = data.getCompound("Proposal");
                MacroEconomyManager.setProposal(new MacroEconomyManager.Proposal(LawLevel.valueOf(t.getString("Level")),
                        t.getUUID("Proposer"), t.getLong("Start"), t.getLong("End")));
            } catch (Exception e) {
                skipped++;
            }
        }
        MacroEconomyManager.setUnrestFlags(data.getBoolean("Strike"), data.getBoolean("Unrest"));
        ListTag snaps = data.getList("Snapshots", Tag.TAG_COMPOUND);
        for (int i = Math.max(0, snaps.size() - MacroEconomyManager.MAX_SNAPSHOTS); i < snaps.size(); i++) {
            CompoundTag t = snaps.getCompound(i);
            MacroEconomyManager.addSnapshot(new MacroEconomyManager.Snapshot(t.getLong("D"), Math.max(0, t.getLong("Cpi")),
                    Math.max(0, t.getLong("Pop")), MacroFormulas.clampSol(t.getInt("Sol")), Math.max(0, t.getLong("Emp")),
                    Math.max(0, t.getLong("Wages")), Math.max(0, t.getLong("Cons"))));
        }
        for (Tag raw : data.getList("LawHistory", Tag.TAG_COMPOUND)) {
            try {
                CompoundTag t = (CompoundTag) raw;
                MacroEconomyManager.addLawResult(new MacroEconomyManager.LawResult(LawLevel.valueOf(t.getString("Level")),
                        t.getBoolean("Passed"), t.getLong("Support"), t.getLong("Opposition"), t.getLong("D")));
            } catch (Exception e) {
                skipped++;
            }
        }
        if (data.contains("Fiscal", Tag.TAG_COMPOUND)) skipped += loadFiscal(data.getCompound("Fiscal"));
        if (invalid || skipped > 0) {
            ModuleHealthRegistry.restrict(ModuleHealthRegistry.Module.MACRO_ECONOMY,
                    ModuleRunState.READ_ONLY, "宏观经济存档 " + skipped + " 条损坏已跳过", -1);
        }
    }
}
