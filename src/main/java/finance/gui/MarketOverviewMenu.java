package finance.gui;

import finance.registry.ModMenus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import finance.gameplay.FinanceGameplayOpener;
import finance.gameplay.FinanceTerminalType;

import java.util.List;

public class MarketOverviewMenu extends AbstractContainerMenu {

    private final List<MarketSnapshot> snapshots;
    private final String dimensionId;
    private final BlockPos sourcePos;
    private final Opportunity opportunity;

    public MarketOverviewMenu(int containerId, Inventory inventory, FriendlyByteBuf buffer) {
        this(containerId, MarketSnapshot.readList(buffer), buffer.readUtf(128), buffer.readBlockPos(),
                Opportunity.read(buffer));
    }

    public MarketOverviewMenu(int containerId, List<MarketSnapshot> snapshots) {
        this(containerId, snapshots, "", BlockPos.ZERO, Opportunity.EMPTY);
    }

    public MarketOverviewMenu(int containerId, List<MarketSnapshot> snapshots, String dimensionId,
                              BlockPos sourcePos, Opportunity opportunity) {
        super(ModMenus.MARKET_OVERVIEW.get(), containerId);
        this.snapshots = MarketSnapshot.sorted(snapshots);
        this.dimensionId = dimensionId == null ? "" : dimensionId;
        this.sourcePos = sourcePos == null ? BlockPos.ZERO : sourcePos.immutable();
        this.opportunity = opportunity == null ? Opportunity.EMPTY : opportunity;
    }

    public List<MarketSnapshot> getSnapshots() {
        return snapshots;
    }

    public Opportunity opportunity() { return opportunity; }

    public static void write(FriendlyByteBuf buffer, List<MarketSnapshot> rows, String dimensionId,
                             BlockPos sourcePos, Opportunity opportunity) {
        MarketSnapshot.writeList(buffer, rows);
        buffer.writeUtf(dimensionId == null ? "" : dimensionId, 128);
        buffer.writeBlockPos(sourcePos == null ? BlockPos.ZERO : sourcePos);
        (opportunity == null ? Opportunity.EMPTY : opportunity).write(buffer);
    }

    @Override
    public boolean stillValid(Player player) {
        return player instanceof ServerPlayer serverPlayer && FinanceGameplayOpener.isValidTerminalSession(
                serverPlayer, FinanceTerminalType.MARKET_TERMINAL, dimensionId, sourcePos);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    public record Opportunity(String shortageId, int shortageStock, String contractId,
                              long contractReward, String deliverableId, long deliverableReward,
                              String moverId, double moverChange) {
        public static final Opportunity EMPTY = new Opportunity("", 0, "", 0, "", 0, "", 0);
        private void write(FriendlyByteBuf b) { b.writeUtf(shortageId,64);b.writeVarInt(Math.max(0,shortageStock));b.writeUtf(contractId,64);b.writeVarLong(Math.max(0,contractReward));b.writeUtf(deliverableId,64);b.writeVarLong(Math.max(0,deliverableReward));b.writeUtf(moverId,64);b.writeDouble(Double.isFinite(moverChange)?moverChange:0); }
        private static Opportunity read(FriendlyByteBuf b) { return new Opportunity(b.readUtf(64),nonNegative(b.readVarInt()),b.readUtf(64),b.readVarLong(),b.readUtf(64),b.readVarLong(),b.readUtf(64),finite(b.readDouble())); }
        private static int nonNegative(int value){if(value<0)throw new IllegalArgumentException("negative stock");return value;}
        private static double finite(double value){if(!Double.isFinite(value))throw new IllegalArgumentException("invalid change");return value;}
    }
}
