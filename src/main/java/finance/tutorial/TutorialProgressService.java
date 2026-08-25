package finance.tutorial;

import finance.network.FinancePacketHandler;
import finance.network.TutorialProgressPacket;
import finance.registry.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashSet;
import java.util.Set;

/** Server-authoritative, per-player tutorial milestones stored with player data. */
public final class TutorialProgressService {
    static final String ROOT_TAG = "FinanceTutorial";
    private static final String EVENTS_TAG = "Events";
    private static final String LAST_SYNC_TAG = "LastSyncedStage";

    private TutorialProgressService() {}

    public static void record(ServerPlayer player, String event) {
        if (player == null || event == null || event.isBlank()) return;
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
        observeInventory(player);
        syncIfChanged(player, true);
    }

    public static TutorialStage stage(ServerPlayer player) {
        CompoundTag events = root(player).getCompound(EVENTS_TAG);
        Set<String> completed = new HashSet<>(events.getAllKeys());
        completed.removeIf(key -> !events.getBoolean(key));
        return TutorialStage.next(completed);
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
        if (!force && root.getInt(LAST_SYNC_TAG) == stage.ordinal() + 1) return;
        root.putInt(LAST_SYNC_TAG, stage.ordinal() + 1);
        FinancePacketHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new TutorialProgressPacket(stage));
    }
}
