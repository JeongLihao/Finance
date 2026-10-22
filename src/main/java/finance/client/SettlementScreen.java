package finance.client;

import finance.gui.SettlementMenu;
import finance.network.CaravanActionPacket;
import finance.network.FinancePacketHandler;
import finance.network.SettlementActionPacket;
import finance.network.VillageActionPacket;
import finance.util.MoneyFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** 聚落贸易站：公共需求、摊位、商路三页。 */
public final class SettlementScreen extends AbstractContainerScreen<SettlementMenu> {
    static final int PANEL_WIDTH = 320, PANEL_HEIGHT = 220;
    private static final int LIST_Y = 38, ROW_HEIGHT = 14, INFO_Y = 152, MESSAGE_Y = 164, BUTTON_Y = 178;
    private static final int[] QUANTITIES = {16, 32, 64};

    private enum Tab {
        DEMANDS,
        STALLS,
        ROUTES
    }

    /** 摊位页的一行：自己的摊位，或村民想买、自己还没开摊的商品。 */
    private record StallLine(SettlementMenu.StallRow stall, SettlementMenu.WantRow want) {}

    // 跨刷新保留：操作后服务端会重新打开界面，停在原来的页签
    private static Tab lastTab = Tab.DEMANDS;
    private static int destination, goods, quantity = 1, guard = 1;
    private static boolean insured;

    private Tab tab = lastTab;
    private int selected;

    public SettlementScreen(SettlementMenu m, Inventory i, Component t) {
        super(m, i, t);
        imageWidth = PANEL_WIDTH;
        imageHeight = PANEL_HEIGHT;
        inventoryLabelY = 10000;
    }

    @Override
    protected void init() {
        super.init();
        selected = Math.max(0, Math.min(selected, Math.max(0, rowCount() - 1)));
        tabButton(Tab.DEMANDS, "screen.finance.settlement.tab.demands", 170);
        tabButton(Tab.STALLS, "screen.finance.settlement.tab.stalls", 216);
        tabButton(Tab.ROUTES, "screen.finance.settlement.tab.routes", 262);
        switch (tab) {
            case DEMANDS -> initDemands();
            case STALLS -> initStalls();
            case ROUTES -> initRoutes();
        }
    }

    private void tabButton(Tab target, String key, int x) {
        Button button = Button.builder(Component.translatable(key), b -> {
                    tab = target;
                    lastTab = target;
                    selected = 0;
                    rebuildWidgets();
                })
                .bounds(leftPos + x, topPos + 5, 44, 16)
                .build();
        button.active = tab != target;
        addRenderableWidget(button);
    }

    private void button(String key, int x, int y, int width, Runnable action) {
        addRenderableWidget(Button.builder(Component.translatable(key), b -> action.run())
                .bounds(leftPos + x, topPos + y, width, 18)
                .build());
    }

    private void button(Component label, int x, int y, int width, Runnable action) {
        addRenderableWidget(Button.builder(label, b -> action.run())
                .bounds(leftPos + x, topPos + y, width, 18)
                .build());
    }

    // ---------------------------------------------------------------- 公共需求

    private void initDemands() {
        button(
                "screen.finance.settlement.accept",
                170,
                BUTTON_Y + 12,
                65,
                () -> sendDemand(SettlementActionPacket.Action.ACCEPT));
        button(
                "screen.finance.settlement.deliver",
                241,
                BUTTON_Y + 12,
                69,
                () -> sendDemand(SettlementActionPacket.Action.DELIVER));
    }

    private void sendDemand(SettlementActionPacket.Action action) {
        if (menu.rows().isEmpty()) return;
        FinancePacketHandler.CHANNEL.sendToServer(new SettlementActionPacket(
                action,
                menu.settlementId(),
                menu.rows().get(selected).id(),
                UUID.randomUUID().toString()));
    }

    // ---------------------------------------------------------------- 摊位

    private List<StallLine> stallLines() {
        List<StallLine> lines = new ArrayList<>();
        var extras = menu.extras();
        for (var stall : extras.stalls()) lines.add(new StallLine(stall, null));
        for (var want : extras.wants()) {
            boolean open =
                    extras.stalls().stream().anyMatch(s -> s.commodityId().equals(want.commodityId()));
            if (!open && lines.size() < SettlementMenu.MAX_ROWS) lines.add(new StallLine(null, want));
        }
        return lines;
    }

    private void initStalls() {
        List<StallLine> lines = stallLines();
        if (lines.isEmpty()) return;
        StallLine line = lines.get(selected);
        if (line.want() != null) {
            button(
                    "screen.finance.settlement.stall.open",
                    10,
                    BUTTON_Y,
                    100,
                    () -> sendStall(
                            VillageActionPacket.Action.REGISTER_STALL,
                            null,
                            line.want().commodityId(),
                            0,
                            0));
            return;
        }
        var stall = line.stall();
        button(
                "screen.finance.settlement.stall.stock",
                10,
                BUTTON_Y,
                72,
                () -> sendStall(VillageActionPacket.Action.STOCK_STALL, stall.id(), "", 0, 0));
        button(
                "screen.finance.settlement.stall.withdraw",
                86,
                BUTTON_Y,
                72,
                () -> sendStall(VillageActionPacket.Action.WITHDRAW_STALL, stall.id(), "", 0, 0));
        button(
                "screen.finance.settlement.stall.close",
                162,
                BUTTON_Y,
                60,
                () -> sendStall(VillageActionPacket.Action.CLOSE_STALL, stall.id(), "", 0, 0));
        if (stall.fairPrice() > 0) {
            button(
                    "screen.finance.settlement.stall.fair",
                    10,
                    BUTTON_Y + 20,
                    72,
                    () -> sendStall(VillageActionPacket.Action.SET_PRICE, stall.id(), "", stall.fairPrice(), 0));
            button(
                    "screen.finance.settlement.stall.cheaper",
                    86,
                    BUTTON_Y + 20,
                    72,
                    () -> sendStall(
                            VillageActionPacket.Action.SET_PRICE,
                            stall.id(),
                            "",
                            Math.max(1, stall.fairPrice() * 9 / 10),
                            0));
        }
    }

    private void sendStall(VillageActionPacket.Action action, UUID stall, String commodity, long price, int qty) {
        FinancePacketHandler.CHANNEL.sendToServer(new VillageActionPacket(
                action, menu.settlementId().toString(), stall == null ? "" : stall.toString(), commodity, price, qty));
    }

    // ---------------------------------------------------------------- 商路

    private void initRoutes() {
        var extras = menu.extras();
        var routes = extras.routes();
        if (!routes.isEmpty()) {
            var route = routes.get(selected);
            boolean paused = "PAUSED".equals(route.status());
            button(
                    paused ? "screen.finance.settlement.route.resume" : "screen.finance.settlement.route.pause",
                    230,
                    INFO_Y - 2,
                    40,
                    () -> sendRoute(
                            paused ? CaravanActionPacket.Action.RESUME : CaravanActionPacket.Action.PAUSE, route.id()));
            button(
                    "screen.finance.settlement.route.close",
                    272,
                    INFO_Y - 2,
                    40,
                    () -> sendRoute(CaravanActionPacket.Action.CLOSE, route.id()));
        }
        if (extras.destinations().isEmpty() || extras.goods().isEmpty()) return;
        destination = Math.floorMod(destination, extras.destinations().size());
        goods = Math.floorMod(goods, extras.goods().size());
        button(
                Component.translatable(
                        "screen.finance.settlement.route.dest",
                        extras.destinations().get(destination).name()),
                10,
                BUTTON_Y,
                110,
                () -> {
                    destination++;
                    rebuildWidgets();
                });
        button(
                Component.translatable(
                        "screen.finance.settlement.route.goods",
                        extras.goods().get(goods).name()),
                124,
                BUTTON_Y,
                90,
                () -> {
                    goods++;
                    rebuildWidgets();
                });
        button(
                Component.translatable("screen.finance.settlement.route.qty", QUANTITIES[quantity]),
                218,
                BUTTON_Y,
                92,
                () -> {
                    quantity = (quantity + 1) % QUANTITIES.length;
                    rebuildWidgets();
                });
        button(Component.translatable("screen.finance.settlement.route.guard", guard), 10, BUTTON_Y + 20, 80, () -> {
            guard = (guard + 1) % 4;
            rebuildWidgets();
        });
        button(
                Component.translatable(
                        "screen.finance.settlement.route.insure",
                        Component.translatable(
                                insured
                                        ? "screen.finance.settlement.route.yes"
                                        : "screen.finance.settlement.route.no")),
                94,
                BUTTON_Y + 20,
                90,
                () -> {
                    insured = !insured;
                    rebuildWidgets();
                });
        button("screen.finance.settlement.route.create", 218, BUTTON_Y + 20, 92, () -> {
            var dest = extras.destinations().get(destination);
            FinancePacketHandler.CHANNEL.sendToServer(new CaravanActionPacket(
                    CaravanActionPacket.Action.CREATE,
                    menu.settlementId(),
                    UUID.fromString(dest.id()),
                    extras.goods().get(goods).id(),
                    QUANTITIES[quantity],
                    guard,
                    insured));
        });
    }

    private void sendRoute(CaravanActionPacket.Action action, UUID route) {
        FinancePacketHandler.CHANNEL.sendToServer(
                new CaravanActionPacket(action, menu.settlementId(), route, "", 0, 0, false));
    }

    // ---------------------------------------------------------------- 绘制

    private int rowCount() {
        return switch (tab) {
            case DEMANDS -> menu.rows().size();
            case STALLS -> stallLines().size();
            case ROUTES -> menu.extras().routes().size();
        };
    }

    @Override
    protected void renderBg(GuiGraphics g, float f, int x, int y) {
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF3A2D21);
        g.fill(leftPos + 2, topPos + 2, leftPos + imageWidth - 2, topPos + imageHeight - 2, 0xFFF2E6C9);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        g.drawString(font, clip(title.getString(), 155), 10, 9, 0xFF342515, false);
        switch (tab) {
            case DEMANDS -> renderDemands(g);
            case STALLS -> renderStalls(g);
            case ROUTES -> renderRoutes(g);
        }
        if (!menu.message().isBlank())
            g.drawString(
                    font,
                    clip(Component.translatable(menu.message()).getString(), 300),
                    10,
                    MESSAGE_Y,
                    0xFF27632A,
                    false);
    }

    private void renderDemands(GuiGraphics g) {
        line(
                g,
                Component.translatable(
                        "screen.finance.settlement.summary",
                        FinanceFormat.label(menu.status()),
                        menu.contributionLevel(),
                        menu.contributionPoints()),
                24,
                0xFF5B4936);
        List<String> rows = new ArrayList<>();
        for (var r : menu.rows())
            rows.add(Component.translatable(
                            "screen.finance.settlement.row",
                            r.theme(),
                            r.commodity(),
                            r.quantity(),
                            r.reward(),
                            r.deadline(),
                            FinanceFormat.label(r.status()))
                    .getString());
        if (!list(g, rows, "screen.finance.settlement.empty")) return;
        var r = menu.rows().get(selected);
        line(
                g,
                Component.translatable(
                        "screen.finance.settlement.regional",
                        FinanceFormat.label(r.pressure()),
                        r.shortageScore() / 100.0,
                        r.localPremiumBps() / 100.0,
                        r.priceReliable() ? "OK" : "LOW"),
                INFO_Y,
                0xFF7A4A16);
    }

    private void renderStalls(GuiGraphics g) {
        line(
                g,
                Component.translatable(
                        "screen.finance.settlement.stalls.favor", menu.extras().favor()),
                24,
                0xFF5B4936);
        List<String> rows = new ArrayList<>();
        for (StallLine l : stallLines()) {
            if (l.stall() != null) {
                var s = l.stall();
                rows.add(Component.translatable(
                                "screen.finance.settlement.stall.row",
                                s.name(),
                                MoneyFormat.pounds(s.price()),
                                s.stock(),
                                s.fairPrice() > 0 ? MoneyFormat.pounds(s.fairPrice()) : "-")
                        .getString());
            } else {
                var w = l.want();
                rows.add(Component.translatable(
                                "screen.finance.settlement.want.row",
                                w.name(),
                                w.dailyNeed(),
                                MoneyFormat.pounds(w.fairPrice()))
                        .getString());
            }
        }
        list(g, rows, "screen.finance.settlement.stalls.empty");
        line(g, Component.translatable("screen.finance.settlement.stalls.hint"), INFO_Y, 0xFF7A4A16);
    }

    private void renderRoutes(GuiGraphics g) {
        var extras = menu.extras();
        line(g, Component.translatable("screen.finance.settlement.routes.hint"), 24, 0xFF5B4936);
        List<String> rows = new ArrayList<>();
        for (var r : extras.routes())
            rows.add(Component.translatable(
                            "screen.finance.settlement.route.row",
                            r.destination(),
                            r.commodity(),
                            r.quantity(),
                            r.guard(),
                            Component.translatable(
                                    r.insured()
                                            ? "screen.finance.settlement.route.insured"
                                            : "screen.finance.settlement.route.uninsured"),
                            Component.translatable("screen.finance.settlement.route.status." + r.status()),
                            MoneyFormat.signed(r.totalProfit()))
                    .getString());
        list(
                g,
                rows,
                extras.destinations().isEmpty()
                        ? "screen.finance.settlement.routes.no_destination"
                        : "screen.finance.settlement.routes.empty");
    }

    /** 画列表，选中行高亮；没有内容时显示 emptyKey。返回是否有内容。 */
    private boolean list(GuiGraphics g, List<String> rows, String emptyKey) {
        if (rows.isEmpty()) {
            line(g, Component.translatable(emptyKey), LIST_Y + 4, 0xFF77705F);
            return false;
        }
        int y = LIST_Y;
        for (int i = 0; i < rows.size(); i++) {
            if (i == selected) g.fill(8, y - 2, 312, y + 10, 0xFFD8E7C8);
            g.drawString(font, clip(rows.get(i), 300), 12, y, 0xFF342515, false);
            y += ROW_HEIGHT;
        }
        return true;
    }

    private void line(GuiGraphics g, Component text, int y, int color) {
        g.drawString(font, clip(text.getString(), 300), 10, y, color, false);
    }

    @Override
    public boolean mouseClicked(double x, double y, int b) {
        int start = topPos + LIST_Y;
        int row = (int) Math.floor((y - start + 2) / ROW_HEIGHT);
        if (y >= start - 2 && x >= leftPos + 8 && x < leftPos + 312 && row >= 0 && row < rowCount()) {
            selected = row;
            rebuildWidgets();
            return true;
        }
        return super.mouseClicked(x, y, b);
    }

    private String clip(String s, int w) {
        while (font.width(s) > w && s.length() > 1) s = s.substring(0, s.length() - 2) + "…";
        return s;
    }
}
