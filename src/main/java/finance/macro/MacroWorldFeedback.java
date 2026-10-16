package finance.macro;

import finance.config.FinanceConfig;
import finance.gameplay.company.CompanyFacilityManager;
import finance.gameplay.company.CompanyFacilityRecord;
import finance.gameplay.company.CompanyFacilityStatus;
import finance.settlement.SettlementManager;
import finance.settlement.SettlementRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 让宏观经济“看得见”：只做粒子、音效、烟花和消息，不改任何经济数值。
 *
 * <ul>
 *   <li>环境效果（每 {@value #AMBIENT_INTERVAL} tick，只处理已加载且附近有玩家的贸易站和工厂）：
 *       生活好的聚落冒开心粒子，挨饿或骚乱的聚落冒愤怒粒子，村民在动作栏抱怨最缺的东西；
 *       罢工时工厂冒浓烟、村民摇头，缺人的工厂冒烟。</li>
 *   <li>全服公告（议会表决、罢工/骚乱开始与结束、饥荒开始与结束、国库紧缩）：先进队列，
 *       下一个服务器 tick 统一发出并配钟声或号角音效。</li>
 *   <li>商队到货：目的地贸易站放一枚烟花并敲钟，在线的商队主人收到盈亏消息。</li>
 * </ul>
 */
public final class MacroWorldFeedback {
    static final int AMBIENT_INTERVAL = 100;
    static final double NEAR = 24.0;
    /** 同一玩家在同一聚落听到抱怨的最短间隔（tick）。 */
    static final long COMPLAINT_COOLDOWN = 1_200;
    static final double FAMINE_LINE = 0.5;
    static final int HAPPY_SOL = 7_000;
    static final int MAX_QUEUE = 64;

    /** 公告的音效基调。 */
    public enum Tone { GOOD, BAD, NEUTRAL }

    /** 一条待发公告。 */
    public record Announcement(String key, List<String> args, Tone tone) { }

    private static final ArrayDeque<Announcement> QUEUE = new ArrayDeque<>();
    private static final LinkedHashMap<String, Long> COMPLAINTS = new LinkedHashMap<>();
    private static final Map<UUID, Boolean> FAMINE = new HashMap<>();
    private static boolean lastStrike;
    private static boolean lastUnrest;
    private static boolean lastAusterity;
    /** 开服后的第一次日结只记基线、不发公告，避免把重启前就存在的罢工、饥荒再公告一遍。 */
    private static boolean primed;

    private MacroWorldFeedback() { }

    static boolean enabled() {
        return FinanceConfig.macroWorldFeedback() && MacroEconomyService.active();
    }

    // ================================================================ 公告

    public static void announce(String key, Tone tone, String... args) {
        if (key == null) return;
        while (QUEUE.size() >= MAX_QUEUE) QUEUE.removeFirst();
        QUEUE.addLast(new Announcement(key, List.of(args), tone == null ? Tone.NEUTRAL : tone));
    }

    public static List<Announcement> queued() { return new ArrayList<>(QUEUE); }

    /** 测试用：只清空公告队列，保留状态基线。 */
    static void clearDirectQueueForTest() { QUEUE.clear(); }

    /** 宏观日结末尾调用：比较昨天和今天，状态翻转时发公告。 */
    static void onDayClosed() {
        // 报社和视觉反馈各自有开关：两者都关时才不必追踪状态翻转
        if (!enabled() && !NewsService.enabled()) return;
        boolean politics = FinanceConfig.macroPoliticsEnabled();
        boolean strike = politics && MacroEconomyManager.strike();
        boolean unrest = politics && MacroEconomyManager.unrest();
        boolean austerity = MacroFiscalService.active() && Treasury.austerity();
        boolean speak = primed;
        if (speak && strike != lastStrike) {
            if (strike) news(80, "工人大罢工！", "工会满意度跌破底线，工人放下工具，雇工产量减半。提高工资或改善劳工法才能让他们回来。", true);
            else news(35, "罢工落幕，工人复工", "工会情绪缓和，工厂恢复生产。", false);
        }
        if (speak && unrest != lastUnrest) {
            if (unrest) news(70, "民众走上街头", "乡村民众和小资产阶级的不满爆发，人口增长放缓，有人开始离开。", true);
            else news(35, "街头恢复平静", "抗议平息，生活回到正轨。", false);
        }
        lastStrike = strike;
        lastUnrest = unrest;
        if (speak && austerity && !lastAusterity) news(75, "国库见底，福利停发", "国库债务触及上限，政府开始紧缩，福利停止发放。", true);
        lastAusterity = austerity;
        for (SettlementEconomy econ : MacroEconomyManager.settlements()) {
            Double food = econ.lastFulfilment.get(NeedCategory.FOOD);
            if (food == null) continue;
            boolean famine = food < FAMINE_LINE;
            boolean before = FAMINE.getOrDefault(econ.settlementId, false);
            SettlementRecord s = SettlementManager.get(econ.settlementId);
            String name = s == null ? "?" : s.displayName();
            if (speak && famine && !before) news(90, name + "闹饥荒", String.format("%s的粮食只够 %d%%，村民在挨饿。谁能运来粮食？",
                    name, Math.round(food * 100)), true);
            if (speak && !famine && before) news(40, name + "饥荒结束", name + "的粮仓重新满了。", false);
            FAMINE.put(econ.settlementId, famine);
        }
        primed = true;
    }

    /** 法律表决结束（PoliticsService 调用）。 */
    static void onLawResolved(LawLevel level, boolean passed, long support, long opposition) {
        if ((!enabled() && !NewsService.enabled()) || level == null) return;
        String what = level.group().displayName() + "改为「" + level.displayName() + "」";
        if (passed) news(70, "议会通过：" + what, String.format("支持 %d，反对 %d。新法律即日生效。", support, opposition), true);
        else news(50, "法案受挫：" + what + "被否决", String.format("支持 %d，反对 %d。", support, opposition), true);
    }

    /**
     * 报社开启时交给报社（大事出号外，小事只上周报）；报社关闭时退回原来的全服公告。
     */
    private static void news(int score, String headline, String body, boolean major) {
        if (NewsService.enabled()) {
            NewsService.report(score, headline, body, major);
        } else if (major && enabled()) {
            announce("finance.macro.announce.brief", Tone.BAD, headline);
        }
    }

    // ================================================================ 商队

    /** 商队到货（CaravanService 调用）：目的地放烟花、敲钟，主人收到盈亏消息。 */
    public static void onCaravanArrived(MinecraftServer server, UUID owner, SettlementRecord dest, String commodityName,
                                        int soldQty, long profit) {
        if (dest == null) return;
        // 个人新闻属于报社，不受视觉反馈开关影响
        if (NewsService.enabled()) {
            NewsService.recordPersonal(owner, String.format("商队抵达%s，卖出%s ×%d，盈亏 %+d", dest.displayName(), commodityName, soldQty, profit));
        }
        if (server == null || !enabled()) return;
        ServerLevel level = level(server, dest.dimensionId());
        BlockPos pos = dest.anchor();
        if (level != null && level.isLoaded(pos) && anyPlayerNear(level, pos, 64)) {
            ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
            CompoundTag fireworks = new CompoundTag();
            CompoundTag explosion = new CompoundTag();
            explosion.putByte("Type", (byte) 1);
            explosion.putIntArray("Colors", new int[]{profit >= 0 ? 0xFFD700 : 0xC0392B});
            explosion.putBoolean("Flicker", true);
            ListTag explosions = new ListTag();
            explosions.add(explosion);
            fireworks.put("Explosions", explosions);
            fireworks.putByte("Flight", (byte) 1);
            rocket.getOrCreateTag().put("Fireworks", fireworks);
            level.addFreshEntity(new FireworkRocketEntity(level, pos.getX() + .5, pos.getY() + 1.2, pos.getZ() + .5, rocket));
            level.playSound(null, pos, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1.0f, 1.0f);
        }
        ServerPlayer player = owner == null ? null : server.getPlayerList().getPlayer(owner);
        if (player != null) {
            player.sendSystemMessage(Component.translatable("finance.macro.caravan_arrived", dest.displayName(), commodityName,
                    String.valueOf(soldQty), (profit >= 0 ? "+" : "") + profit));
        }
    }

    // ================================================================ tick

    /** 每个服务器 tick 调用：先发公告，再按间隔刷新环境效果。 */
    public static void tick(MinecraftServer server) {
        if (server == null) return;
        flush(server);
        if (!enabled()) return;
        long time = server.overworld().getGameTime();
        if (time % AMBIENT_INTERVAL != 0) return;
        for (SettlementEconomy econ : MacroEconomyManager.settlements()) ambientSettlement(server, econ, time);
        boolean strike = FinanceConfig.macroPoliticsEnabled() && MacroEconomyManager.strike();
        for (CompanyFacilityRecord facility : CompanyFacilityManager.all()) ambientFacility(server, facility, strike, time);
    }

    private static void flush(MinecraftServer server) {
        while (!QUEUE.isEmpty()) {
            Announcement a = QUEUE.removeFirst();
            Component message = Component.translatable(a.key(), a.args().toArray());
            SoundEvent sound = switch (a.tone()) {
                case GOOD -> SoundEvents.BELL_BLOCK;
                case BAD -> SoundEvents.BELL_RESONATE;
                case NEUTRAL -> SoundEvents.VILLAGER_WORK_LIBRARIAN;
            };
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                p.sendSystemMessage(message);
                p.playNotifySound(sound, SoundSource.MASTER, 0.8f, 1.0f);
            }
        }
    }

    private static void ambientSettlement(MinecraftServer server, SettlementEconomy econ, long time) {
        SettlementRecord s = SettlementManager.get(econ.settlementId);
        if (s == null || !MacroEconomyService.usable(s)) return;
        ServerLevel level = level(server, s.dimensionId());
        BlockPos pos = s.anchor();
        if (level == null || !level.isLoaded(pos) || !anyPlayerNear(level, pos, NEAR)) return;
        Mood mood = mood(econ);
        if (mood == Mood.ANGRY) particles(level, pos, ParticleTypes.ANGRY_VILLAGER, 3);
        else if (mood == Mood.HAPPY) particles(level, pos, ParticleTypes.HAPPY_VILLAGER, 4);
        String complaint = complaintKey(econ);
        if (complaint == null) return;
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) > NEAR * NEAR) continue;
            String key = p.getUUID() + "|" + econ.settlementId;
            Long last = COMPLAINTS.get(key);
            if (last != null && time - last < COMPLAINT_COOLDOWN) continue;
            COMPLAINTS.put(key, time);
            while (COMPLAINTS.size() > 1_024) COMPLAINTS.remove(COMPLAINTS.keySet().iterator().next());
            p.displayClientMessage(Component.translatable(complaint, s.displayName()), true);
            if (mood == Mood.ANGRY) p.playNotifySound(SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 0.6f, 1.0f);
            else if (mood == Mood.HAPPY) p.playNotifySound(SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 0.5f, 1.0f);
        }
    }

    private static void ambientFacility(MinecraftServer server, CompanyFacilityRecord facility, boolean strike, long time) {
        boolean understaffed = facility.status() == CompanyFacilityStatus.UNDERSTAFFED;
        boolean striking = strike && MacroLaborService.active() && MacroEconomyManager.employment(facility.facilityId()) > 0;
        if (!understaffed && !striking) return;
        ServerLevel level = level(server, facility.dimensionId());
        BlockPos pos = facility.blockPos();
        if (level == null || !level.isLoaded(pos) || !anyPlayerNear(level, pos, NEAR)) return;
        particles(level, pos, striking ? ParticleTypes.CAMPFIRE_COSY_SMOKE : ParticleTypes.SMOKE, striking ? 2 : 4);
        if (striking && time % (AMBIENT_INTERVAL * 3) == 0) {
            level.playSound(null, pos, SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 0.8f, 0.8f);
            for (ServerPlayer p : level.players()) {
                if (p.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) <= NEAR * NEAR) {
                    p.displayClientMessage(Component.translatable("finance.macro.factory_strike"), true);
                }
            }
        }
    }

    // ================================================================ 判定（纯函数，便于测试）

    enum Mood { HAPPY, CALM, ANGRY }

    static Mood mood(SettlementEconomy econ) {
        if (econ == null) return Mood.CALM;
        Double food = econ.lastFulfilment.get(NeedCategory.FOOD);
        if (econ.unrest || (food != null && food < FAMINE_LINE)) return Mood.ANGRY;
        if (econ.solBps >= HAPPY_SOL) return Mood.HAPPY;
        return Mood.CALM;
    }

    /** 村民该抱怨什么：骚乱 → 要上街；否则满足率最低且低于 60% 的类别；过得好时偶尔夸两句；都不满足返回 null。 */
    static String complaintKey(SettlementEconomy econ) {
        if (econ == null) return null;
        if (econ.unrest) return "finance.macro.complaint.unrest";
        NeedCategory worst = null;
        double worstValue = 0.6;
        for (NeedCategory c : NeedCategory.values()) {
            Double f = econ.lastFulfilment.get(c);
            if (f != null && f < worstValue) {
                worstValue = f;
                worst = c;
            }
        }
        if (worst != null) return "finance.macro.complaint." + worst.name().toLowerCase(java.util.Locale.ROOT);
        return econ.solBps >= HAPPY_SOL ? "finance.macro.complaint.happy" : null;
    }

    // ================================================================ 工具

    private static ServerLevel level(MinecraftServer server, String dimensionId) {
        ResourceLocation id = ResourceLocation.tryParse(dimensionId);
        return id == null ? null : server.getLevel(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, id));
    }

    private static boolean anyPlayerNear(ServerLevel level, BlockPos pos, double radius) {
        double r2 = radius * radius;
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) <= r2) return true;
        }
        return false;
    }

    private static void particles(ServerLevel level, BlockPos pos, ParticleOptions type, int count) {
        level.sendParticles(type, pos.getX() + .5, pos.getY() + 1.3, pos.getZ() + .5, count, .4, .25, .4, 0.01);
    }

    public static void clearDirect() {
        QUEUE.clear();
        COMPLAINTS.clear();
        FAMINE.clear();
        lastStrike = false;
        lastUnrest = false;
        lastAusterity = false;
        primed = false;
    }
}
