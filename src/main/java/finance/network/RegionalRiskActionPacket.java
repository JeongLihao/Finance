package finance.network;

import finance.collateral.*;
import finance.company.*;
import finance.cycle.EconomyCycleService;
import finance.gameplay.*;
import finance.gameplay.company.*;
import finance.gui.FinanceMenu;
import finance.hedge.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record RegionalRiskActionPacket(Action action,UUID targetId,String commodity,long quantity,long amount,
                                       int deadlineDays,String operationKey){
    public enum Action{APPLY_COLLATERAL,SUPPLEMENT_COLLATERAL,REPAY_COLLATERAL,CREATE_INPUT_HEDGE,CREATE_OUTPUT_HEDGE,CANCEL_HEDGE}
    public RegionalRiskActionPacket{commodity=commodity==null?"":commodity.trim().toLowerCase(java.util.Locale.ROOT);operationKey=operationKey==null?"":operationKey.trim();}
    public static void encode(RegionalRiskActionPacket p,FriendlyByteBuf b){b.writeEnum(p.action);b.writeBoolean(p.targetId!=null);if(p.targetId!=null)b.writeUUID(p.targetId);b.writeUtf(p.commodity,64);b.writeLong(p.quantity);b.writeLong(p.amount);b.writeVarInt(p.deadlineDays);b.writeUtf(p.operationKey,48);}
    public static RegionalRiskActionPacket decode(FriendlyByteBuf b){var p=new RegionalRiskActionPacket(b.readEnum(Action.class),b.readBoolean()?b.readUUID():null,b.readUtf(64),b.readLong(),b.readLong(),b.readVarInt(),b.readUtf(48));if(p.deadlineDays<0||p.deadlineDays>3650)throw new IllegalArgumentException("deadline");return p;}
    public static void handle(RegionalRiskActionPacket p,Supplier<NetworkEvent.Context>s){var c=s.get();c.enqueueWork(()->run(p,c.getSender()));c.setPacketHandled(true);}
    private static void run(RegionalRiskActionPacket p,ServerPlayer player){if(player==null||p.action==null||!(player.containerMenu instanceof FinanceMenu menu)||!menu.stillValid(player)||p.operationKey.isBlank()||!MarketDataRequestLimiter.allow(player.getUUID(),player.server.getTickCount(),"regional-risk-action:"+p.action)){return;}boolean collateral=p.action==Action.APPLY_COLLATERAL||p.action==Action.SUPPLEMENT_COLLATERAL||p.action==Action.REPAY_COLLATERAL;if(collateral&&(menu.getInitialMode()!=FinanceScreenMode.BANK||menu.getSourceType()!=FinanceTerminalType.BANK_COUNTER)){GuiFeedbackPacket.send(player,"库存质押必须在有效银行柜台办理");return;}if(!collateral&&menu.getInitialMode()!=FinanceScreenMode.ADVANCED){GuiFeedbackPacket.send(player,"对冲目标必须在证券终端办理");return;}long day=EconomyCycleService.currentMcDay(player.server);FinanceMenu.CompanyInfo selected=menu.getPlayerCompany();Company company=selected==null?null:CompanyManager.getCompany(selected.companyId());if(company==null||!CompanyMembershipService.hasPermission(company.getCompanyId(),player.getUUID(),CompanyPermission.MANAGE_PRODUCTION)){GuiFeedbackPacket.send(player,"目标公司或权限无效");return;}String message;
        switch(p.action){
            case APPLY_COLLATERAL->{if(company==null||p.targetId==null||p.quantity<=0||p.quantity>1_000_000||p.commodity.isBlank())message="质押参数或公司权限无效";else message=InventoryCollateralService.apply(player.getUUID(),company.getCompanyId(),p.targetId,p.commodity,(int)p.quantity,day,p.operationKey).message();}
            case SUPPLEMENT_COLLATERAL->{message=p.targetId!=null&&p.quantity>0&&p.quantity<=1_000_000?InventoryCollateralService.supplement(player.getUUID(),p.targetId,(int)p.quantity,day,p.operationKey).message():"补充抵押参数无效";}
            case REPAY_COLLATERAL->{InventoryCollateralAgreement a=InventoryCollateralManager.get(p.targetId);Company owner=a==null?null:CompanyManager.getCompany(a.companyId());if(a==null||owner==null||p.amount<=0||!CompanyMembershipService.hasPermission(a.companyId(),player.getUUID(),CompanyPermission.SPEND_COMPANY_CASH))message="还款参数或权限无效";else{message=finance.debt.CompanyLoanManager.repay(owner.getOwnerId(),a.loanId(),p.amount,day).message();InventoryCollateralService.processDay(day);}}
            case CREATE_INPUT_HEDGE,CREATE_OUTPUT_HEDGE->{if(company==null||p.targetId==null||p.quantity<=0||p.deadlineDays<1)message="对冲参数无效";else{long deadline;try{deadline=Math.addExact(day,p.deadlineDays);}catch(ArithmeticException e){message="截止日溢出";break;}message=CompanyHedgeService.create(player.getUUID(),company.getCompanyId(),p.targetId,p.action==Action.CREATE_INPUT_HEDGE?HedgeObjectiveType.INPUT_COST:HedgeObjectiveType.OUTPUT_PRICE,p.quantity,day,deadline,p.operationKey).message();}}
            case CANCEL_HEDGE->message=p.targetId==null?"目标无效":CompanyHedgeService.cancel(player.getUUID(),p.targetId).message();
            default->message="未知风险管理操作";
        }if(message.equals("finance.collateral.active"))finance.tutorial.TutorialProgressService.record(player,"collateral_active");else if(message.equals("还款成功"))finance.tutorial.TutorialProgressService.record(player,"collateral_repaid");else if(message.equals("finance.hedge.created_personal"))finance.tutorial.TutorialProgressService.record(player,"hedge_linked");GuiFeedbackPacket.send(player,message);
    }
}
