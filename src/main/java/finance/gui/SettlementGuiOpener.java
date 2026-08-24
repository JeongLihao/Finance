package finance.gui;

import finance.block.entity.SettlementTradeStationBlockEntity;
import finance.settlement.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraftforge.network.NetworkHooks;
import java.util.*;

public final class SettlementGuiOpener {private SettlementGuiOpener(){}
    public static boolean open(ServerPlayer player,BlockPos pos,String message){if(!(player.serverLevel().getBlockEntity(pos) instanceof SettlementTradeStationBlockEntity station)||pos.distSqr(player.blockPosition())>64D)return false;SettlementRecord s=SettlementService.register(player,station);if(s==null)return false;long day=player.serverLevel().getGameTime()/24000L;if(SettlementManager.forSettlement(s.id()).stream().noneMatch(d->!d.status().terminal()))SettlementService.generate(s,day);List<SettlementMenu.DemandRow> rows=SettlementManager.publicRows(s.id(),player.getUUID()).stream().map(d->new SettlementMenu.DemandRow(d.id(),d.commodityId(),d.theme(),d.quantity(),d.reward(),d.deadlineDay(),d.status().name(),player.getUUID().equals(d.acceptedPlayerId()))).toList();MenuProvider provider=new MenuProvider(){public Component getDisplayName(){return Component.translatable("screen.finance.settlement");}public SettlementMenu createMenu(int id,net.minecraft.world.entity.player.Inventory inv,net.minecraft.world.entity.player.Player p){return new SettlementMenu(id,s.id(),s.displayName(),s.status().name(),s.level(player.getUUID()),s.points(player.getUUID()),rows,message);}};NetworkHooks.openScreen(player,provider,b->SettlementMenu.write(b,s.id(),s.displayName(),s.status().name(),s.level(player.getUUID()),s.points(player.getUUID()),rows,message));updateIndicator(player,s,pos);return true;}
    private static void updateIndicator(ServerPlayer p,SettlementRecord s,BlockPos pos){var state=p.serverLevel().getBlockState(pos);if(!(state.getBlock() instanceof finance.block.SettlementTradeStationBlock))return;finance.block.SettlementTradeStationBlock.Indicator indicator=s.status()==SettlementStatus.RAID_ALERT||s.status()==SettlementStatus.DISABLED?finance.block.SettlementTradeStationBlock.Indicator.RED:SettlementManager.forSettlement(s.id()).stream().anyMatch(d->d.status()==DemandStatus.OPEN)?finance.block.SettlementTradeStationBlock.Indicator.GREEN:finance.block.SettlementTradeStationBlock.Indicator.YELLOW;if(state.getValue(finance.block.SettlementTradeStationBlock.INDICATOR)!=indicator)p.serverLevel().setBlock(pos,state.setValue(finance.block.SettlementTradeStationBlock.INDICATOR,indicator),3);}
}
