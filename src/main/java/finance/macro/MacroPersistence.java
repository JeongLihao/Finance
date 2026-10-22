package finance.macro;

import finance.diagnostic.ModuleHealthRegistry;
import finance.diagnostic.ModuleRunState;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * 宏观经济存档。DataVersion 39 新增根标签 {@value #ROOT}；旧存档没有该标签时按“刚启用”初始化。
 * 单条坏记录跳过并计数，有跳过时把 MACRO_ECONOMY 模块降为只读，不影响其他模块加载。
 */
public final class MacroPersistence {
    public static final String ROOT = "MacroEconomy";
    public static final int VERSION = 1;

    private MacroPersistence() {}

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
            t.putLong("Employed", econ.employed);
            t.putLong("LastIncome", econ.lastIncome);
            t.putLong("IncomeToday", econ.incomeToday);
            t.putLong("LastWages", econ.lastWages);
            t.putLong("WagesToday", econ.wagesToday);
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
        CompoundTag ach = new CompoundTag();
        ach.putLong("LastDay", EconomyAchievements.lastProcessedDay());
        ListTag pending = new ListTag();
        EconomyAchievements.pendingDirect().forEach((player, events) -> {
            CompoundTag t = new CompoundTag();
            t.putUUID("Player", player);
            ListTag list = new ListTag();
            for (String e : events) list.add(net.minecraft.nbt.StringTag.valueOf(e));
            t.put("Events", list);
            pending.add(t);
        });
        ach.put("Pending", pending);
        data.put("Achievements", ach);
        data.put("News", saveNews());
        data.putDouble("CpiLink", MacroIndicators.link());
        root.put(ROOT, data);
    }

    private static ListTag strings(java.util.List<String> values) {
        ListTag list = new ListTag();
        for (String v : values) list.add(net.minecraft.nbt.StringTag.valueOf(v));
        return list;
    }

    private static java.util.List<String> readStrings(ListTag list) {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (Tag t : list) out.add(t.getAsString());
        return out;
    }

    private static ListTag personal(Map<UUID, java.util.List<String>> map) {
        ListTag list = new ListTag();
        map.forEach((player, lines) -> {
            CompoundTag t = new CompoundTag();
            t.putUUID("Player", player);
            t.put("Lines", strings(lines));
            list.add(t);
        });
        return list;
    }

    private static Map<UUID, java.util.List<String>> readPersonal(ListTag list) {
        Map<UUID, java.util.List<String>> out = new java.util.LinkedHashMap<>();
        for (Tag raw : list) {
            CompoundTag t = (CompoundTag) raw;
            out.put(t.getUUID("Player"), java.util.List.copyOf(readStrings(t.getList("Lines", Tag.TAG_STRING))));
        }
        return out;
    }

    private static CompoundTag saveNews() {
        CompoundTag t = new CompoundTag();
        t.putInt("Counter", NewsService.counter());
        t.putLong("LastWeekly", NewsService.lastWeeklyDay());
        t.putLong("LastExtra", NewsService.lastExtraDay());
        ListTag pool = new ListTag();
        for (NewsService.Item i : NewsService.pool()) {
            CompoundTag c = new CompoundTag();
            c.putLong("D", i.day());
            c.putInt("Score", i.score());
            c.putString("Headline", i.headline());
            c.putString("Body", i.body());
            pool.add(c);
        }
        t.put("Pool", pool);
        ListTag issues = new ListTag();
        for (NewsService.Issue i : NewsService.archive()) {
            CompoundTag c = new CompoundTag();
            c.putInt("Number", i.number());
            c.putLong("D", i.day());
            c.putBoolean("Extra", i.extra());
            c.putString("Headline", i.headline());
            c.put("Pages", strings(i.pages()));
            c.put("Personal", personal(i.personal()));
            ListTag claimed = new ListTag();
            for (UUID id : NewsService.claimedDirect().getOrDefault(i.number(), java.util.Set.of())) {
                claimed.add(net.minecraft.nbt.NbtUtils.createUUID(id));
            }
            c.put("Claimed", claimed);
            issues.add(c);
        }
        t.put("Issues", issues);
        t.put("Personal", personal(NewsService.personalDirect()));
        ListTag radar = new ListTag();
        NewsService.radarDirect().forEach((id, day) -> {
            CompoundTag c = new CompoundTag();
            c.putUUID("Player", id);
            c.putLong("D", day);
            radar.add(c);
        });
        t.put("Radar", radar);
        return t;
    }

    /** 读报社子标签，返回跳过的坏记录数。 */
    private static int loadNews(CompoundTag t) {
        int skipped = 0;
        java.util.List<NewsService.Item> pool = new java.util.ArrayList<>();
        for (Tag raw : t.getList("Pool", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) raw;
            String headline = c.getString("Headline");
            if (headline.isBlank()) {
                skipped++;
                continue;
            }
            pool.add(new NewsService.Item(c.getLong("D"), c.getInt("Score"), headline, c.getString("Body")));
        }
        java.util.List<NewsService.Issue> issues = new java.util.ArrayList<>();
        Map<Integer, java.util.Set<UUID>> claimed = new java.util.LinkedHashMap<>();
        for (Tag raw : t.getList("Issues", Tag.TAG_COMPOUND)) {
            try {
                CompoundTag c = (CompoundTag) raw;
                int number = c.getInt("Number");
                java.util.List<String> pages = readStrings(c.getList("Pages", Tag.TAG_STRING));
                if (number <= 0 || pages.isEmpty()) {
                    skipped++;
                    continue;
                }
                issues.add(new NewsService.Issue(
                        number,
                        c.getLong("D"),
                        c.getBoolean("Extra"),
                        c.getString("Headline"),
                        java.util.List.copyOf(pages),
                        readPersonal(c.getList("Personal", Tag.TAG_COMPOUND))));
                java.util.Set<UUID> ids = new java.util.LinkedHashSet<>();
                for (Tag id : c.getList("Claimed", Tag.TAG_INT_ARRAY)) ids.add(net.minecraft.nbt.NbtUtils.loadUUID(id));
                claimed.put(number, ids);
            } catch (Exception e) {
                skipped++;
            }
        }
        Map<UUID, Long> radar = new java.util.LinkedHashMap<>();
        for (Tag raw : t.getList("Radar", Tag.TAG_COMPOUND)) {
            try {
                CompoundTag c = (CompoundTag) raw;
                radar.put(c.getUUID("Player"), c.getLong("D"));
            } catch (Exception e) {
                skipped++;
            }
        }
        Map<UUID, java.util.List<String>> personal;
        try {
            personal = readPersonal(t.getList("Personal", Tag.TAG_COMPOUND));
        } catch (Exception e) {
            personal = Map.of();
            skipped++;
        }
        NewsService.restore(
                t.getInt("Counter"),
                t.contains("LastWeekly", Tag.TAG_LONG) ? t.getLong("LastWeekly") : Long.MIN_VALUE,
                t.contains("LastExtra", Tag.TAG_LONG) ? t.getLong("LastExtra") : Long.MIN_VALUE,
                pool,
                issues,
                claimed,
                personal,
                radar);
        return skipped;
    }

    public static void load(CompoundTag root) {
        MacroEconomyManager.clearDirect();
        if (!root.contains(ROOT, Tag.TAG_COMPOUND)) return;
        CompoundTag data = root.getCompound(ROOT);
        boolean invalid = data.getInt("Version") != VERSION;
        int skipped = 0;
        if (data.contains("LastProcessedDay", Tag.TAG_LONG))
            MacroEconomyManager.setLastProcessedDay(data.getLong("LastProcessedDay"));
        if (data.contains("LastLaborDay", Tag.TAG_LONG))
            MacroEconomyManager.setLastLaborDay(data.getLong("LastLaborDay"));

        ListTag settlements = data.getList("Settlements", Tag.TAG_COMPOUND);
        skipped += Math.max(0, settlements.size() - MacroEconomyManager.MAX_SETTLEMENTS);
        for (int i = 0; i < Math.min(settlements.size(), MacroEconomyManager.MAX_SETTLEMENTS); i++) {
            try {
                CompoundTag t = settlements.getCompound(i);
                SettlementEconomy econ = MacroEconomyManager.getOrCreate(t.getUUID("Id"));
                if (econ == null) {
                    skipped++;
                    continue;
                }
                int sol = t.getInt("Sol");
                if (sol < 0 || sol > MacroFormulas.MAX_SOL) {
                    skipped++;
                    sol = MacroFormulas.clampSol(sol);
                }
                econ.solBps = sol;
                econ.growthMilli = Math.max(-999, Math.min(999, t.getLong("GrowthMilli")));
                econ.lastProcessedDay = t.getLong("LastDay");
                econ.employed = Math.max(0, t.getLong("Employed"));
                econ.lastIncome = Math.max(0, t.getLong("LastIncome"));
                econ.incomeToday = Math.max(0, t.getLong("IncomeToday"));
                econ.lastWages = Math.max(0, t.getLong("LastWages"));
                econ.wagesToday = Math.max(0, t.getLong("WagesToday"));
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
                        if (id.isBlank() || id.length() > 64) {
                            skipped++;
                            continue;
                        }
                        GoodBook b = econ.book(id);
                        if (b == null) {
                            skipped++;
                            continue;
                        }
                        int factor = g.getInt("Factor");
                        if (factor < MacroFormulas.MIN_FACTOR_BPS || factor > MacroFormulas.MAX_FACTOR_BPS) skipped++;
                        b.restore(
                                factor,
                                g.getLong("Demand"),
                                g.getLong("Supply"),
                                g.getLong("Consumed"),
                                g.getLong("Imports"),
                                g.getLong("Exports"));
                        b.importsToday = Math.max(0, g.getLong("ImportsToday"));
                        b.exportsToday = Math.max(0, g.getLong("ExportsToday"));
                        ListTag hist = g.getList("History", Tag.TAG_COMPOUND);
                        for (int k = Math.max(0, hist.size() - GoodBook.HISTORY_DAYS); k < hist.size(); k++) {
                            CompoundTag h = hist.getCompound(k);
                            b.history.addLast(new GoodBook.Point(
                                    h.getLong("D"),
                                    MacroFormulas.clampFactor(h.getInt("F")),
                                    Math.max(0, h.getLong("Dm")),
                                    Math.max(0, h.getLong("S")),
                                    Math.max(0, h.getLong("C"))));
                        }
                    } catch (Exception e) {
                        skipped++;
                    }
                }
                ListTag hist = t.getList("History", Tag.TAG_COMPOUND);
                for (int k = Math.max(0, hist.size() - SettlementEconomy.HISTORY_DAYS); k < hist.size(); k++) {
                    CompoundTag h = hist.getCompound(k);
                    econ.history.addLast(new SettlementEconomy.Point(
                            h.getLong("D"),
                            MacroFormulas.clampSol(h.getInt("Sol")),
                            Math.max(0, h.getInt("Pop")),
                            Math.max(0, h.getLong("Emp")),
                            Math.max(0, h.getLong("Inc"))));
                }
            } catch (Exception e) {
                skipped++;
            }
        }

        for (Tag raw : data.getList("Employment", Tag.TAG_COMPOUND)) {
            try {
                CompoundTag t = (CompoundTag) raw;
                long n = t.getLong("Employed");
                if (n < 0) {
                    skipped++;
                    continue;
                }
                MacroEconomyManager.setEmployment(
                        t.getUUID("Facility"), n, t.hasUUID("Source") ? t.getUUID("Source") : null);
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
        ListTag snaps = data.getList("Snapshots", Tag.TAG_COMPOUND);
        for (int i = Math.max(0, snaps.size() - MacroEconomyManager.MAX_SNAPSHOTS); i < snaps.size(); i++) {
            CompoundTag t = snaps.getCompound(i);
            MacroEconomyManager.addSnapshot(new MacroEconomyManager.Snapshot(
                    t.getLong("D"),
                    Math.max(0, t.getLong("Cpi")),
                    Math.max(0, t.getLong("Pop")),
                    MacroFormulas.clampSol(t.getInt("Sol")),
                    Math.max(0, t.getLong("Emp")),
                    Math.max(0, t.getLong("Wages")),
                    Math.max(0, t.getLong("Cons"))));
        }
        if (data.contains("Achievements", Tag.TAG_COMPOUND)) {
            CompoundTag ach = data.getCompound("Achievements");
            Map<UUID, java.util.Set<String>> pending = new java.util.LinkedHashMap<>();
            for (Tag raw : ach.getList("Pending", Tag.TAG_COMPOUND)) {
                try {
                    CompoundTag t = (CompoundTag) raw;
                    java.util.Set<String> events = new java.util.LinkedHashSet<>();
                    for (Tag e : t.getList("Events", Tag.TAG_STRING)) events.add(e.getAsString());
                    pending.put(t.getUUID("Player"), events);
                } catch (Exception e) {
                    skipped++;
                }
            }
            EconomyAchievements.restore(
                    ach.contains("LastDay", Tag.TAG_LONG) ? ach.getLong("LastDay") : Long.MIN_VALUE, pending);
        }
        if (data.contains("News", Tag.TAG_COMPOUND)) skipped += loadNews(data.getCompound("News"));
        if (data.contains("CpiLink", Tag.TAG_DOUBLE)) MacroIndicators.restoreLink(data.getDouble("CpiLink"));
        if (invalid || skipped > 0) {
            ModuleHealthRegistry.restrict(
                    ModuleHealthRegistry.Module.MACRO_ECONOMY,
                    ModuleRunState.READ_ONLY,
                    "宏观经济存档 " + skipped + " 条损坏已跳过",
                    -1);
        }
    }
}
