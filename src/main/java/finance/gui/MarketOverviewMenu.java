package finance.gui;

import finance.registry.ModMenus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class MarketOverviewMenu extends AbstractContainerMenu {

    private final List<MarketSnapshot> snapshots;

    public MarketOverviewMenu(int containerId, Inventory inventory, FriendlyByteBuf buffer) {
        this(containerId, MarketSnapshot.readList(buffer));
    }

    public MarketOverviewMenu(int containerId, List<MarketSnapshot> snapshots) {
        super(ModMenus.MARKET_OVERVIEW.get(), containerId);
        this.snapshots = MarketSnapshot.sorted(snapshots);
    }

    public List<MarketSnapshot> getSnapshots() {
        return snapshots;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
