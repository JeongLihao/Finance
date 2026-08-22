package finance.client;

import finance.gui.CompanyGameplayMenu;
import finance.network.CompanyGameplayActionPacket;
import finance.network.FinancePacketHandler;
import finance.gameplay.company.CompanyMemberRole;
import finance.gameplay.company.CompanyPermission;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.UUID;

public final class CompanyGameplayScreen extends AbstractContainerScreen<CompanyGameplayMenu> {
    private int member,facility; private EditBox target,commodity,quantity,reward; private boolean pending;
    public CompanyGameplayScreen(CompanyGameplayMenu menu,Inventory inventory,Component title){super(menu,inventory,title);imageWidth=320;imageHeight=240;inventoryLabelY=10_000;}
    @Override protected void init(){super.init();target=box(10,184,90,"UUID");commodity=box(104,184,60,"ID");quantity=box(168,184,40,"Qty");reward=box(212,184,50,"Money");quantity.setValue("10");reward.setValue("100");}
    private EditBox box(int x,int y,int w,String hint){EditBox b=new EditBox(font,leftPos+x,topPos+y,w,16,Component.literal(hint));b.setMaxLength(64);addRenderableWidget(b);return b;}
    @Override protected void renderBg(GuiGraphics g,float pt,int mx,int my){g.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xFF333333);g.fill(leftPos+1,topPos+1,leftPos+319,topPos+239,0xFFE8E2D2);}
    @Override protected void renderLabels(GuiGraphics g,int mx,int my){g.drawString(font,font.plainSubstrByWidth(menu.name()+"  "+menu.mode()+"  "+menu.role(),300),10,9,menu.risk()?0xFFA02020:0xFF202020,false);g.drawString(font,Component.translatable("screen.finance.company_gameplay.cash",menu.cash()),10,23,0xFF555555,false);g.drawString(font,Component.translatable("screen.finance.company_gameplay.members"),10,42,0xFF202020,false);int y=56;for(int i=0;i<Math.min(5,menu.members().size());i++){var r=menu.members().get(i);if(i==member)g.fill(8,y-2,154,y+10,0xFFD5E3C7);g.drawString(font,r.playerId().toString().substring(0,8)+" "+r.role(),12,y,0xFF202020,false);y+=12;}g.drawString(font,Component.translatable("screen.finance.company_gameplay.facilities"),165,42,0xFF202020,false);y=56;for(int i=0;i<Math.min(5,menu.facilities().size());i++){var r=menu.facilities().get(i);if(i==facility)g.fill(162,y-2,312,y+10,0xFFD5E3C7);g.drawString(font,font.plainSubstrByWidth(r.id().toString().substring(0,8)+" L"+r.level()+" "+r.status(),142),166,y,0xFF202020,false);y+=12;}g.drawString(font,Component.translatable("screen.finance.company_gameplay.warehouses",menu.warehouses().size()),10,119,0xFF555555,false);g.drawString(font,Component.translatable("screen.finance.company_gameplay.contracts"),10,133,0xFF202020,false);y=146;for(int i=0;i<Math.min(3,menu.contracts().size());i++){var r=menu.contracts().get(i);g.drawString(font,font.plainSubstrByWidth(r.commodity()+" x"+r.quantity()+" / "+r.reward()+" / "+r.status(),300),12,y,0xFF202020,false);y+=11;}if(!menu.statusKey().isBlank())g.drawString(font,font.plainSubstrByWidth(Component.translatable(menu.statusKey()).getString(),300),10,173,0xFF8A4B18,false);boolean invited="INVITED".equals(menu.role());button(g,10,204,72,"mode");button(g,86,204,72,invited?"accept":"leave");button(g,162,204,72,invited?"reject":"invite");button(g,238,204,72,"role");button(g,10,222,72,"remove");button(g,86,222,72,"upgrade");button(g,162,222,72,"procure");button(g,238,222,72,"advanced");}
    private void button(GuiGraphics g,int x,int y,int w,String key){boolean enabled=!pending&&can(actionForKey(key));g.fill(x,y,x+w,y+16,enabled?0xFFD0CAB8:0xFFAAA69A);g.drawCenteredString(font,Component.translatable("screen.finance.company_gameplay.action."+key),x+w/2,y+4,enabled?0xFF202020:0xFF666666);}
    private CompanyGameplayActionPacket.Action actionForKey(String key){return switch(key){case "mode"->CompanyGameplayActionPacket.Action.MODE_NEXT;case "autosell"->CompanyGameplayActionPacket.Action.AUTO_SELL_NEXT;case "accept"->CompanyGameplayActionPacket.Action.ACCEPT_INVITE;case "reject"->CompanyGameplayActionPacket.Action.REJECT_INVITE;case "leave"->CompanyGameplayActionPacket.Action.LEAVE;case "invite"->CompanyGameplayActionPacket.Action.INVITE;case "role"->CompanyGameplayActionPacket.Action.ROLE_NEXT;case "remove"->CompanyGameplayActionPacket.Action.REMOVE_MEMBER;case "upgrade"->CompanyGameplayActionPacket.Action.UPGRADE_FACILITY;case "procure"->CompanyGameplayActionPacket.Action.PUBLISH_CONTRACT;default->CompanyGameplayActionPacket.Action.OPEN_ADVANCED;};}
    private boolean can(CompanyGameplayActionPacket.Action action){if("INVITED".equals(menu.role()))return action==CompanyGameplayActionPacket.Action.ACCEPT_INVITE||action==CompanyGameplayActionPacket.Action.REJECT_INVITE;CompanyMemberRole role;try{role=CompanyMemberRole.valueOf(menu.role());}catch(IllegalArgumentException e){return false;}return switch(action){case MODE_NEXT->role==CompanyMemberRole.OWNER;case AUTO_SELL_NEXT,UPGRADE_FACILITY->role.allows(CompanyPermission.MANAGE_PRODUCTION);case LEAVE->role!=CompanyMemberRole.OWNER;case INVITE,ROLE_NEXT,REMOVE_MEMBER->role.allows(CompanyPermission.MANAGE_MEMBERS);case PUBLISH_CONTRACT->role.allows(CompanyPermission.PUBLISH_CONTRACT);case OPEN_ADVANCED->role.allows(CompanyPermission.OPEN_GOVERNANCE);case ACCEPT_INVITE,REJECT_INVITE->false;};}
    @Override public boolean mouseClicked(double x, double y, int b) {
        if (pending) return true;
        int mx = (int) x - leftPos, my = (int) y - topPos;
        if (mx >= 8 && mx < 154 && my >= 54 && my < 54 + Math.min(5, menu.members().size()) * 12) {
            member = Math.max(0, (my - 54) / 12); return true;
        }
        if (mx >= 162 && mx < 312 && my >= 54 && my < 54 + Math.min(5, menu.facilities().size()) * 12) {
            facility = Math.max(0, (my - 54) / 12); return true;
        }
        if (mx >= 266 && mx < 310 && my >= 184 && my < 200)
            return send(CompanyGameplayActionPacket.Action.AUTO_SELL_NEXT);
        if (mx >= 10 && mx < 310 && (my >= 204 && my < 220 || my >= 222 && my < 238)) {
            int column = (mx - 10) / 76;
            if (column < 0 || column > 3) return true;
            boolean invited = "INVITED".equals(menu.role());
            CompanyGameplayActionPacket.Action action;
            if (my < 220) action = switch (column) {
                case 0 -> CompanyGameplayActionPacket.Action.MODE_NEXT;
                case 1 -> invited ? CompanyGameplayActionPacket.Action.ACCEPT_INVITE : CompanyGameplayActionPacket.Action.LEAVE;
                case 2 -> invited ? CompanyGameplayActionPacket.Action.REJECT_INVITE : CompanyGameplayActionPacket.Action.INVITE;
                default -> CompanyGameplayActionPacket.Action.ROLE_NEXT;
            }; else action = switch (column) {
                case 0 -> CompanyGameplayActionPacket.Action.REMOVE_MEMBER;
                case 1 -> CompanyGameplayActionPacket.Action.UPGRADE_FACILITY;
                case 2 -> CompanyGameplayActionPacket.Action.PUBLISH_CONTRACT;
                default -> CompanyGameplayActionPacket.Action.OPEN_ADVANCED;
            };
            return send(action);
        }
        return super.mouseClicked(x, y, b);
    }

    private boolean send(CompanyGameplayActionPacket.Action action) {
        if (!can(action)) return true;
        UUID id = null;
        try {
            if (action == CompanyGameplayActionPacket.Action.INVITE) id = UUID.fromString(target.getValue());
            else if (action == CompanyGameplayActionPacket.Action.ROLE_NEXT
                    || action == CompanyGameplayActionPacket.Action.REMOVE_MEMBER) id = menu.members().get(member).playerId();
            else if (action == CompanyGameplayActionPacket.Action.UPGRADE_FACILITY) id = menu.facilities().get(facility).id();
        } catch (RuntimeException ignored) { return true; }
        int qty = 0; long money = 0;
        try { qty = Integer.parseInt(quantity.getValue()); money = Long.parseLong(reward.getValue()); }
        catch (NumberFormatException ignored) {}
        FinancePacketHandler.CHANNEL.sendToServer(new CompanyGameplayActionPacket(action, menu.companyId(), id,
                commodity.getValue(), qty, money, UUID.randomUUID().toString()));
        pending = true; return true;
    }
    @Override public void render(GuiGraphics g,int mx,int my,float pt){renderBackground(g);super.render(g,mx,my,pt);boolean enabled=!pending&&can(CompanyGameplayActionPacket.Action.AUTO_SELL_NEXT);g.fill(leftPos+266,topPos+184,leftPos+310,topPos+200,enabled?0xFFD0CAB8:0xFFAAA69A);g.drawCenteredString(font,Component.translatable("screen.finance.company_gameplay.action.autosell",Math.round(menu.autoSellRatio()*100)),leftPos+288,topPos+188,enabled?0xFF202020:0xFF666666);renderTooltip(g,mx,my);}
}
