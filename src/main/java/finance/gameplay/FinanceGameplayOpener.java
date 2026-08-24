package finance.gameplay;

import finance.block.FinanceTerminalBlock;
import finance.block.WarehouseControllerBlock;
import finance.block.CompanyDeskBlock;
import finance.block.CompanyFactoryControllerBlock;
import finance.block.BoardroomTableBlock;
import finance.config.FinanceConfig;
import finance.gui.FinanceGuiOpener;
import finance.gui.WalletGuiOpener;
import finance.gui.WarehouseGuiOpener;
import finance.gui.MarketOverviewGuiOpener;
import finance.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

public final class FinanceGameplayOpener {
    private FinanceGameplayOpener() {}

    public static GameplayActionResult openPortableLedger(ServerPlayer player) {
        boolean verified = holdsLedger(player);
        FinanceAccessContext context = context(player, FinanceTerminalType.PORTABLE_LEDGER,
                FinanceScreenMode.WALLET, null, verified);
        FinanceAccessDecision decision = FinanceAccessService.validatePortableLedger(context, FinanceAccessPolicy.current());
        return openDecision(player, decision, FinanceTerminalType.PORTABLE_LEDGER);
    }

    public static GameplayActionResult openShortcut(ServerPlayer player) {
        if (FinanceConfig.minecraftFirstMode()) {
            if (!FinanceConfig.enablePortableLedger()) return deny(player, "finance.access.portable_disabled");
            if (!hasLedger(player)) return deny(player, "finance.access.ledger_required");
            FinanceAccessContext context = context(player, FinanceTerminalType.PORTABLE_LEDGER,
                    FinanceScreenMode.WALLET, null, true);
            return openDecision(player, FinanceAccessService.validatePortableLedger(
                    context, FinanceAccessPolicy.current()), FinanceTerminalType.PORTABLE_LEDGER);
        }
        return openLegacy(player);
    }

    public static GameplayActionResult openTerminal(ServerPlayer player, BlockPos pos,
                                                     FinanceTerminalType claimedType) {
        if (player == null || claimedType == null || !claimedType.isPhysicalTerminal()) {
            return player == null ? GameplayActionResult.failure("finance.access.invalid_request")
                    : deny(player, "finance.access.invalid_request");
        }
        FinanceTerminalType actualType = terminalTypeAt(player, pos);
        boolean verified = actualType != null && actualType == claimedType;
        FinanceScreenMode mode = actualType == null ? FinanceAccessService.modeFor(claimedType)
                : FinanceAccessService.modeFor(actualType);
        FinanceAccessContext context = context(player, claimedType, mode, pos, verified);
        FinanceAccessDecision decision = FinanceAccessService.validateTerminal(context, FinanceAccessPolicy.current());
        return openDecision(player, decision, claimedType);
    }

    public static GameplayActionResult openLegacy(ServerPlayer player) {
        FinanceAccessContext context = context(player, FinanceTerminalType.LEGACY_FULL_SCREEN,
                FinanceScreenMode.ADVANCED, null, true);
        return openDecision(player, FinanceAccessService.authorize(context, FinanceAccessPolicy.current()),
                FinanceTerminalType.LEGACY_FULL_SCREEN);
    }

    public static boolean isValidTerminalSession(ServerPlayer player, FinanceTerminalType type,
                                                 String dimensionId, BlockPos pos) {
        if (type == null || !type.isPhysicalTerminal()) return true;
        if (pos == null) return false;
        if (!player.serverLevel().dimension().location().toString().equals(dimensionId)) return false;
        if (terminalTypeAt(player, pos) != type) return false;
        double max = FinanceConfig.terminalInteractionDistance();
        return player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= max * max;
    }

    private static GameplayActionResult openDecision(ServerPlayer player, FinanceAccessDecision decision,
                                                      FinanceTerminalType sourceType) {
        if (!decision.allowed()) return deny(player, decision.messageKey());
        if (decision.screenMode() == FinanceScreenMode.WALLET) {
            WalletGuiOpener.open(player);
        } else if (decision.screenMode() == FinanceScreenMode.MARKET) {
            MarketOverviewGuiOpener.open(player, decision.sourcePos());
        } else if (decision.screenMode() == FinanceScreenMode.WAREHOUSE) {
            if (!WarehouseGuiOpener.open(player, decision.sourcePos())) {
                return deny(player, "finance.warehouse.invalid_session");
            }
        } else {
            FinanceGuiOpener.open(player, decision.screenMode(), sourceType, decision.sourcePos());
        }
        switch(sourceType){
            case COMPANY_DESK,BOARDROOM_TABLE->finance.advancement.FinanceAdvancementTriggers.trigger(player,"company_member");
            case SECURITIES_TERMINAL->finance.advancement.FinanceAdvancementTriggers.trigger(player,"advanced_finance");
            default->{}
        }
        return GameplayActionResult.success("finance.access.opened", false);
    }

    private static GameplayActionResult deny(ServerPlayer player, String key) {
        // Terminal interaction feedback belongs on the HUD. Keeping expected access
        // denials out of chat also prevents repeated right-clicks from polluting logs.
        player.displayClientMessage(Component.translatable(key), true);
        return GameplayActionResult.failure(key);
    }

    private static FinanceAccessContext context(ServerPlayer player, FinanceTerminalType type,
                                                FinanceScreenMode mode, BlockPos pos, boolean verified) {
        double distance = pos == null ? 0.0D
                : player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
        return new FinanceAccessContext(player.getUUID(), type, mode,
                player.serverLevel().dimension().location().toString(), pos,
                player.serverLevel().getGameTime(), permissionLevel(player), verified, true, distance);
    }

    private static FinanceTerminalType terminalTypeAt(ServerPlayer player, BlockPos pos) {
        if (pos == null || !player.serverLevel().isLoaded(pos)) return null;
        Block block = player.serverLevel().getBlockState(pos).getBlock();
        if (block instanceof FinanceTerminalBlock terminal) return terminal.terminalType();
        if (block instanceof WarehouseControllerBlock) return FinanceTerminalType.WAREHOUSE_CONTROLLER;
        if (block instanceof CompanyDeskBlock || block instanceof CompanyFactoryControllerBlock) return FinanceTerminalType.COMPANY_DESK;
        if (block instanceof BoardroomTableBlock) return FinanceTerminalType.BOARDROOM_TABLE;
        return null;
    }

    private static boolean holdsLedger(ServerPlayer player) {
        return isLedger(player.getMainHandItem()) || isLedger(player.getOffhandItem());
    }

    private static boolean hasLedger(ServerPlayer player) {
        return holdsLedger(player) || player.getInventory().contains(new ItemStack(ModItems.PORTABLE_LEDGER.get()));
    }

    private static boolean isLedger(ItemStack stack) {
        return !stack.isEmpty() && stack.is(ModItems.PORTABLE_LEDGER.get());
    }

    private static int permissionLevel(ServerPlayer player) {
        return player.hasPermissions(2) ? 2 : player.hasPermissions(1) ? 1 : 0;
    }
}
