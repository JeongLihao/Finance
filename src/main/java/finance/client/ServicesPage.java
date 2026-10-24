package finance.client;

import finance.client.PlayerServicesScreen.Btn;
import finance.client.PlayerServicesScreen.Hit;
import finance.network.PlayerServicesResponsePacket;
import finance.playerservice.PlayerServicesService.Action;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * “更多操作”界面的一页：画中间的内容，给出底部的按钮。
 * 页签、输入框、状态行和点击登记在 {@link PlayerServicesScreen}；这里的小方法都转给它，让各页代码保持原样。
 */
abstract class ServicesPage {
    final PlayerServicesScreen screen;

    ServicesPage(PlayerServicesScreen screen) {
        this.screen = screen;
    }

    abstract void render(GuiGraphics g, PlayerServicesResponsePacket data);

    abstract List<Btn> buttons(PlayerServicesResponsePacket data);

    int left() {
        return screen.left();
    }

    int top() {
        return screen.top();
    }

    Font font() {
        return screen.font();
    }

    List<Hit> hits() {
        return screen.hits();
    }

    void line(GuiGraphics g, String text, int row, int color) {
        screen.line(g, text, row, color);
    }

    int wrapped(GuiGraphics g, String text, int x, int row, int width, int maxRows, int color) {
        return screen.wrapped(g, text, x, row, width, maxRows, color);
    }

    String box(int i) {
        return screen.box(i);
    }

    void send(Action action, String text, long a, long b, long c, long d) {
        screen.send(action, text, a, b, c, d);
    }

    void refresh() {
        screen.refresh();
    }
}
