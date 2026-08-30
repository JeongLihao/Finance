package finance.tutorial;

import finance.network.FinancePacketHandler;
import finance.network.TutorialProgressPacket;
import finance.registry.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Server-authoritative, per-player tutorial milestones stored with player data. */
public final class TutorialProgressService {
    static final String ROOT_TAG = "FinanceTutorial";
    private static final String EVENTS_TAG = "Events";
    private static final String LAST_SYNC_TAG = "LastSyncedStage";
    private static final String LAST_OPTIONAL_SYNC_TAG = "LastSyncedOptionalMask";
    private static final Set<String> RECOGNIZED_EVENTS = Set.of(
            "has_ledger", "wallet_opened", "has_market_terminal", "warehouse_built",
            "warehouse_deposit", "first_trade", "first_contract", "company_member",
            "company_production", "first_shipment", "first_village_help", "field_survey",
            "advanced_finance", "capital_project_complete", "regional_risk_view", "collateral_active",
            "collateral_repaid", "hedge_linked", "insured_risk", "risk_summary_view");
    private static final Map<String, List<String>> ADVANCEMENT_EVENTS = Map.ofEntries(
            Map.entry("first_coin", List.of("has_ledger")),
            Map.entry("portable_finance", List.of("wallet_opened", "has_market_terminal")),
            Map.entry("market_access", List.of("warehouse_built")),
            Map.entry("warehouse_deposit", List.of("warehouse_deposit")),
            Map.entry("public_company", List.of("first_trade")),
            Map.entry("first_contract", List.of("first_contract")),
            Map.entry("company_member", List.of("company_member")),
            Map.entry("company_production", List.of("company_production")),
            Map.entry("first_shipment", List.of("first_shipment")),
            Map.entry("first_village_help", List.of("first_village_help")),
            Map.entry("field_survey", List.of("field_survey")),
            Map.entry("advanced_finance", List.of("advanced_finance")));

    private TutorialProgressService() {}

    public static void record(ServerPlayer player, String event) {
        if (player == null || !isRecognizedEvent(event)) return;
        CompoundTag root = root(player);
        CompoundTag events = root.getCompound(EVENTS_TAG);
        if (!events.getBoolean(event)) {
            events.putBoolean(event, true);
            root.put(EVENTS_TAG, events);
            syncIfChanged(player, false);
        }
    }

    public static void observeInventory(ServerPlayer player) {
        if (contains(player, ModItems.PORTABLE_LEDGER.get())) record(player, "has_ledger");
        if (contains(player, ModItems.MARKET_TERMINAL.get())) record(player, "has_market_terminal");
    }

    public static void sync(ServerPlayer player) {
        importCompletedAdvancements(player);
        observeInventory(player);
        syncIfChanged(player, true);
    }

    public static TutorialStage stage(ServerPlayer player) {
        return TutorialStage.next(completedEvents(player));
    }

    public static int optionalMask(ServerPlayer player) {
        return TutorialOptionalGoal.completedMask(completedEvents(player));
    }

    public static void copy(ServerPlayer original, ServerPlayer replacement) {
        if (original.getPersistentData().contains(ROOT_TAG)) {
            replacement.getPersistentData().put(ROOT_TAG,
                    original.getPersistentData().getCompound(ROOT_TAG).copy());
        }
    }

    private static boolean contains(ServerPlayer player, Item item) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (player.getInventory().getItem(slot).is(item)) return true;
        }
        return false;
    }

    private static CompoundTag root(ServerPlayer player) {
        CompoundTag persistent = player.getPersistentData();
        if (!persistent.contains(ROOT_TAG)) persistent.put(ROOT_TAG, new CompoundTag());
        return persistent.getCompound(ROOT_TAG);
    }

    private static void syncIfChanged(ServerPlayer player, boolean force) {
        if (player.connection == null) return;
        CompoundTag root = root(player);
        TutorialStage stage = stage(player);
        int optionalMask = optionalMask(player);
        if (!force && root.getInt(LAST_SYNC_TAG) == stage.ordinal() + 1
                && root.getInt(LAST_OPTIONAL_SYNC_TAG) == optionalMask + 1) return;
        root.putInt(LAST_SYNC_TAG, stage.ordinal() + 1);
        root.putInt(LAST_OPTIONAL_SYNC_TAG, optionalMask + 1);
        FinancePacketHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new TutorialProgressPacket(stage, optionalMask));
    }

    private static Set<String> completedEvents(ServerPlayer player) {
        CompoundTag events = root(player).getCompound(EVENTS_TAG);
        Set<String> completed = new HashSet<>();
        for (String event : RECOGNIZED_EVENTS) {
            if (events.getBoolean(event)) completed.add(event);
        }
        return completed;
    }

    static boolean isRecognizedEvent(String event) {
        return event != null && RECOGNIZED_EVENTS.contains(event);
    }

    /** One-way migration so worlds played before the HUD do not need to repeat milestones. */
    private static void importCompletedAdvancements(ServerPlayer player) {
        if (player.getServer() == null) return;
        CompoundTag root = root(player);
        CompoundTag events = root.getCompound(EVENTS_TAG);
        for (Map.Entry<String, List<String>> entry : ADVANCEMENT_EVENTS.entrySet()) {
            var advancement = player.getServer().getAdvancements().getAdvancement(
                    ResourceLocation.fromNamespaceAndPath("finance", entry.getKey()));
            if (advancement == null || !player.getAdvancements().getOrStartProgress(advancement).isDone()) continue;
            for (String event : entry.getValue()) events.putBoolean(event, true);
        }
        root.put(EVENTS_TAG, events);
    }
}
