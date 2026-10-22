package finance.gui;

import finance.block.entity.SettlementTradeStationBlockEntity;
import finance.settlement.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraftforge.network.NetworkHooks;

public final class SettlementGuiOpener {
    private SettlementGuiOpener() {}

    public static boolean open(ServerPlayer player, BlockPos pos, String message) {
        if (!(player.serverLevel().getBlockEntity(pos) instanceof SettlementTradeStationBlockEntity station)
                || pos.distSqr(player.blockPosition()) > 64D) return false;
        SettlementRecord s = SettlementService.register(player, station);
        if (s == null) return false;
        long day = player.serverLevel().getGameTime() / 24000L;
        if (SettlementManager.forSettlement(s.id()).stream()
                .noneMatch(d -> !d.status().terminal())) SettlementService.generate(s, day);
        List<SettlementMenu.DemandRow> rows = SettlementManager.publicRows(s.id(), player.getUUID()).stream()
                .map(d -> {
                    var metric = finance.regional.RegionalCommodityMetricsManager.latest(
                            new finance.regional.RegionalCommodityKey(s.dimensionId(), s.id(), d.commodityId()));
                    return new SettlementMenu.DemandRow(
                            d.id(),
                            d.commodityId(),
                            d.theme(),
                            d.quantity(),
                            d.reward(),
                            d.deadlineDay(),
                            d.status().name(),
                            player.getUUID().equals(d.acceptedPlayerId()),
                            metric == null ? 10000 : metric.localPremiumBps(),
                            metric == null ? 0 : metric.smoothedShortageScore(),
                            metric == null
                                    ? finance.regional.RegionalSupplyPressure.BALANCED.name()
                                    : metric.pressure().name(),
                            metric != null && metric.priceReliable());
                })
                .toList();
        SettlementMenu.Extras extras = extras(player, s);
        MenuProvider provider = new MenuProvider() {
            public Component getDisplayName() {
                return Component.translatable("screen.finance.settlement");
            }

            public SettlementMenu createMenu(
                    int id,
                    net.minecraft.world.entity.player.Inventory inv,
                    net.minecraft.world.entity.player.Player p) {
                return new SettlementMenu(
                        id,
                        s.id(),
                        s.displayName(),
                        s.status().name(),
                        s.level(player.getUUID()),
                        s.points(player.getUUID()),
                        rows,
                        message,
                        extras);
            }
        };
        NetworkHooks.openScreen(
                player,
                provider,
                b -> SettlementMenu.write(
                        b,
                        s.id(),
                        s.displayName(),
                        s.status().name(),
                        s.level(player.getUUID()),
                        s.points(player.getUUID()),
                        rows,
                        message,
                        extras));
        updateIndicator(player, s, pos);
        return true;
    }

    /** “摊位”“商路”两页的数据：村民想买什么、我的摊位、我从这里出发的商路、能去哪些聚落、能运什么。 */
    private static SettlementMenu.Extras extras(ServerPlayer player, SettlementRecord here) {
        UUID me = player.getUUID();
        var village = finance.village.VillageManager.getOrCreate(here.id());
        int favor = village == null ? 0 : village.favorOf(me);
        Map<String, Long> needOf = new HashMap<>();
        List<SettlementMenu.WantRow> wants = new ArrayList<>();
        for (var w : finance.village.VillageService.wants(here.id())) {
            if (!finance.warehouse.CommodityItemResolver.resolve(w.commodityId())
                    .valid()) continue;
            needOf.put(w.commodityId(), w.dailyNeed());
            wants.add(new SettlementMenu.WantRow(
                    w.commodityId(), goodsName(w.commodityId()), w.dailyNeed(), w.fairPrice()));
        }
        List<SettlementMenu.StallRow> stalls = new ArrayList<>();
        for (var stall : finance.village.VillageManager.stallsForSettlement(here.id())) {
            if (!me.equals(stall.ownerId())) continue;
            long fair = finance.village.VillageService.fairPrice(
                    here.id(), stall.commodityId(), needOf.getOrDefault(stall.commodityId(), 1L));
            stalls.add(new SettlementMenu.StallRow(
                    stall.id(),
                    stall.commodityId(),
                    goodsName(stall.commodityId()),
                    stall.unitPrice(),
                    stall.stock(),
                    fair));
        }
        List<SettlementMenu.RouteRow> routes = new ArrayList<>();
        for (var route : finance.caravan.CaravanManager.routesForOwner(me)) {
            if (!here.id().equals(route.sourceSettlementId())
                    || route.status() == finance.caravan.CaravanRoute.Status.CLOSED) continue;
            SettlementRecord dest = SettlementManager.get(route.destSettlementId());
            routes.add(new SettlementMenu.RouteRow(
                    route.id(),
                    dest == null ? "?" : dest.displayName(),
                    goodsName(route.commodityId()),
                    route.qtyPerTrip(),
                    route.guardLevel(),
                    route.insured(),
                    route.status().name(),
                    route.totalProfit()));
        }
        List<SettlementMenu.Choice> destinations = SettlementManager.settlements().values().stream()
                .filter(o -> !o.id().equals(here.id()) && o.dimensionId().equals(here.dimensionId()))
                .filter(o -> o.status() == SettlementStatus.ACTIVE || o.status() == SettlementStatus.REBUILDING)
                .sorted(Comparator.comparingDouble(o -> o.anchor().distSqr(here.anchor())))
                .map(o -> new SettlementMenu.Choice(o.id().toString(), o.displayName()))
                .toList();
        List<SettlementMenu.Choice> goods = finance.commodity.CommodityRegistry.getAllCommodities().stream()
                .filter(c -> finance.warehouse.CommodityItemResolver.resolve(c.getId())
                        .valid())
                .sorted(Comparator.comparing(finance.commodity.Commodity::getId))
                .map(c -> new SettlementMenu.Choice(c.getId(), c.getDisplayName()))
                .toList();
        return new SettlementMenu.Extras(favor, wants, stalls, routes, destinations, goods);
    }

    private static String goodsName(String commodityId) {
        var commodity = finance.commodity.CommodityRegistry.getCommodity(commodityId);
        return commodity == null ? commodityId : commodity.getDisplayName();
    }

    private static void updateIndicator(ServerPlayer p, SettlementRecord s, BlockPos pos) {
        var state = p.serverLevel().getBlockState(pos);
        if (!(state.getBlock() instanceof finance.block.SettlementTradeStationBlock)) return;
        finance.block.SettlementTradeStationBlock.Indicator indicator = s.status() == SettlementStatus.RAID_ALERT
                        || s.status() == SettlementStatus.DISABLED
                ? finance.block.SettlementTradeStationBlock.Indicator.RED
                : SettlementManager.forSettlement(s.id()).stream().anyMatch(d -> d.status() == DemandStatus.OPEN)
                        ? finance.block.SettlementTradeStationBlock.Indicator.GREEN
                        : finance.block.SettlementTradeStationBlock.Indicator.YELLOW;
        if (state.getValue(finance.block.SettlementTradeStationBlock.INDICATOR) != indicator)
            p.serverLevel()
                    .setBlock(pos, state.setValue(finance.block.SettlementTradeStationBlock.INDICATOR, indicator), 3);
    }
}
