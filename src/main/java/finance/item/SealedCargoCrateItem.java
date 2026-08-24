package finance.item;

import finance.block.entity.WarehouseControllerBlockEntity;
import finance.logistics.Shipment;
import finance.logistics.ShipmentActionResult;
import finance.logistics.ShipmentService;
import finance.registry.ModItems;
import finance.warehouse.CommodityItemResolver;
import finance.warehouse.WarehouseManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.particles.ParticleTypes;

/** A bearer token for cargo; NBT labels are never an inventory authority. */
public final class SealedCargoCrateItem extends Item {
    public static final int TOKEN_VERSION = 1;
    private static final String VERSION = "FinanceCargoVersion";
    private static final String SHIPMENT = "ShipmentId";
    private static final String TOKEN = "ShipmentToken";
    private static final String DESTINATION = "DestinationWarehouse";
    private static final String LABEL = "CargoLabel";
    private static final String QUANTITY = "CargoQuantity";

    public SealedCargoCrateItem(Properties properties) { super(properties); }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player rawPlayer = context.getPlayer();
        Level level = context.getLevel();
        if (rawPlayer == null) return InteractionResult.PASS;
        BlockEntity blockEntity = level.getBlockEntity(context.getClickedPos());
        if (!(blockEntity instanceof WarehouseControllerBlockEntity controller)) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(rawPlayer instanceof ServerPlayer player)) return InteractionResult.FAIL;
        controller.claimIfNeeded(player.getUUID());
        var clicked = WarehouseManager.registerOrRecover(player, controller);
        if (clicked == null) return message(player, "finance.logistics.invalid_route", false);

        ItemStack crate = context.getItemInHand();
        UUID shipmentId = readUuid(crate, SHIPMENT);
        UUID tokenId = readUuid(crate, TOKEN);
        if (shipmentId != null || tokenId != null) {
            if (!validVersion(crate)) return message(player, "finance.logistics.invalid_cargo", false);
            ShipmentActionResult result = ShipmentService.unload(player, shipmentId, tokenId,
                    clicked.warehouseId(), UUID.randomUUID().toString());
            if (result.success()) clearCargo(crate);
            return message(player, result.messageKey(), result.success());
        }

        UUID destinationId = readUuid(crate, DESTINATION);
        if (destinationId == null) {
            ShipmentActionResult recovered = ShipmentService.recover(player, clicked.warehouseId(),
                    UUID.randomUUID().toString());
            if (recovered.success()) {
                seal(crate, recovered.shipment());
                return message(player, recovered.messageKey(), true);
            }
            crate.getOrCreateTag().putInt(VERSION, TOKEN_VERSION);
            crate.getOrCreateTag().putUUID(DESTINATION, clicked.warehouseId());
            return message(player, "finance.logistics.destination_bound", true);
        }
        if (destinationId.equals(clicked.warehouseId()))
            return message(player, "finance.logistics.destination_selected", false);

        ItemStack selector = player.getOffhandItem();
        String commodityId = CommodityItemResolver.commodityId(selector.getItem());
        if (selector.isEmpty() || commodityId == null)
            return message(player, "finance.logistics.offhand_selector", false);
        ShipmentActionResult result = ShipmentService.load(player, clicked.warehouseId(), destinationId,
                commodityId, selector.getCount(), null, UUID.randomUUID().toString());
        if (result.success()) seal(crate, result.shipment());
        return message(player, result.messageKey(), result.success());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player.isShiftKeyDown() && readUuid(stack, SHIPMENT) == null
                && readUuid(stack, DESTINATION) != null) {
            stack.getOrCreateTag().remove(DESTINATION);
            player.displayClientMessage(Component.translatable("finance.logistics.destination_cleared"), true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public boolean canFitInsideContainerItems() { return false; }

    @Override
    public void onDestroyed(ItemEntity entity, DamageSource source) {
        markLost(entity, "cargo item destroyed");
        super.onDestroyed(entity, source);
    }

    public static boolean markLost(ItemEntity entity, String reason) {
        if (entity == null) return false;
        ItemStack stack = entity.getItem();
        UUID shipmentId = readUuid(stack, SHIPMENT);
        boolean changed = markLost(stack, reason);
        if (changed) {
            Shipment shipment = finance.logistics.ShipmentManager.get(shipmentId);
            if (shipment != null) finance.feedback.WorldEconomyFeedbackService.queue(shipment.carrierId(),
                    new finance.feedback.FeedbackNotification(Math.max(0, entity.level().getGameTime() / 24_000L),
                            finance.feedback.FeedbackSeverity.WARNING, "finance.feedback.cargo_lost",
                            java.util.List.of(shipment.id().toString().substring(0, 8))));
        }
        return changed;
    }

    public static boolean markLost(ItemStack stack, String reason) {
        if (stack == null || stack.isEmpty() || !stack.is(ModItems.SEALED_CARGO_CRATE.get()) || !validVersion(stack)) return false;
        return ShipmentService.markLost(readUuid(stack, SHIPMENT), readUuid(stack, TOKEN), reason);
    }

    public static boolean isSealed(ItemStack stack) {
        return validVersion(stack) && readUuid(stack, SHIPMENT) != null && readUuid(stack, TOKEN) != null;
    }

    public static void seal(ItemStack stack, Shipment shipment) {
        if (stack == null || shipment == null) return;
        clearCargo(stack);
        var tag = stack.getOrCreateTag();
        tag.putInt(VERSION, TOKEN_VERSION);
        tag.putUUID(SHIPMENT, shipment.id());
        tag.putUUID(TOKEN, shipment.tokenId());
        tag.putString(LABEL, shipment.commodityId());
        tag.putInt(QUANTITY, shipment.quantity());
    }

    private static void clearCargo(ItemStack stack) {
        if (stack == null || !stack.hasTag()) return;
        var tag = stack.getTag();
        tag.remove(SHIPMENT);
        tag.remove(TOKEN);
        tag.remove(DESTINATION);
        tag.remove(LABEL);
        tag.remove(QUANTITY);
        tag.putInt(VERSION, TOKEN_VERSION);
    }

    private static UUID readUuid(ItemStack stack, String key) {
        try { return stack != null && stack.hasTag() && stack.getTag().hasUUID(key) ? stack.getTag().getUUID(key) : null; }
        catch (IllegalArgumentException ignored) { return null; }
    }

    private static boolean validVersion(ItemStack stack) {
        return stack != null && stack.hasTag() && stack.getTag().getInt(VERSION) == TOKEN_VERSION;
    }

    private static InteractionResult message(ServerPlayer player, String key, boolean success) {
        player.displayClientMessage(Component.translatable(key), true);
        if (success) {
            player.playNotifySound(SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.PLAYERS, .55F, 1.2F);
            player.serverLevel().sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    player.getX(), player.getY() + 1D, player.getZ(), 4, .2D, .25D, .2D, 0D);
        }
        return success ? InteractionResult.CONSUME : InteractionResult.FAIL;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        UUID shipment = readUuid(stack, SHIPMENT);
        UUID destination = readUuid(stack, DESTINATION);
        if (shipment != null) tooltip.add(Component.translatable("tooltip.finance.sealed_cargo_crate.sealed",
                safeLabel(stack), safeQuantity(stack)));
        else if (destination != null) tooltip.add(Component.translatable("tooltip.finance.sealed_cargo_crate.bound",
                destination.toString().substring(0, 8)));
        else tooltip.add(Component.translatable("tooltip.finance.sealed_cargo_crate.empty"));
        tooltip.add(Component.translatable("tooltip.finance.sealed_cargo_crate.authority"));
    }

    private static String safeLabel(ItemStack stack) {
        String value = stack.hasTag() ? stack.getTag().getString(LABEL) : "?";
        return value.length() <= 64 ? value : value.substring(0, 64);
    }
    private static int safeQuantity(ItemStack stack) {
        return stack.hasTag() ? Math.max(0, stack.getTag().getInt(QUANTITY)) : 0;
    }
}
