package finance.client.chart;

import finance.chart.Candlestick;
import finance.chart.IndicatorService;
import finance.chart.MacdIndicator;
import finance.chart.RelativeStrengthIndex;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

public final class TechnicalIndicatorChart {
    private TechnicalIndicatorChart() {}

    public static void renderMacd(GuiGraphics g, Font font, List<Candlestick> bars,
                                  int x, int y, int width, int height) {
        panel(g, x, y, width, height);
        MacdIndicator.Result value = MacdIndicator.calculate(IndicatorService.closes(bars));
        double range = maxAbs(value.dif(), value.dea(), value.histogram());
        if (range <= 0) { g.drawCenteredString(font, "Not enough MACD data", x + width / 2, y + height / 2, 0xFF666666); return; }
        int zero = y + height / 2;
        g.fill(x + 2, zero, x + width - 2, zero + 1, 0xFFAAAAAA);
        double slot = (double) (width - 8) / Math.max(1, bars.size());
        for (int i = 0; i < value.histogram().length; i++) {
            if (!Double.isFinite(value.histogram()[i])) continue;
            int px = x + 4 + (int) ((i + .5) * slot);
            int py = zero - (int) Math.round(value.histogram()[i] / range * (height / 2 - 7));
            g.fill(px, Math.min(zero, py), px + 1, Math.max(zero + 1, py), value.histogram()[i] >= 0 ? 0xFF2D9A4B : 0xFFC44747);
        }
        line(g, value.dif(), x, y, width, height, -range, range, 0xFFE19B32);
        line(g, value.dea(), x, y, width, height, -range, range, 0xFF4F78C4);
        g.drawString(font, "MACD  DIF / DEA", x + 5, y + 3, 0xFF555555, false);
    }

    public static void renderRsi(GuiGraphics g, Font font, List<Candlestick> bars,
                                 int x, int y, int width, int height) {
        panel(g, x, y, width, height);
        double[] rsi = RelativeStrengthIndex.calculate(IndicatorService.closes(bars), 14);
        int y70 = scale(70, y + 8, y + height - 8, 0, 100);
        int y30 = scale(30, y + 8, y + height - 8, 0, 100);
        g.fill(x + 2, y70, x + width - 2, y70 + 1, 0x55C44747);
        g.fill(x + 2, y30, x + width - 2, y30 + 1, 0x552D9A4B);
        line(g, rsi, x, y, width, height, 0, 100, 0xFF7B4BB7);
        g.drawString(font, "RSI14   70 / 30", x + 5, y + 3, 0xFF555555, false);
    }

    private static void line(GuiGraphics g, double[] values, int x, int y, int width, int height,
                             double min, double max, int color) {
        double slot = (double) (width - 8) / Math.max(1, values.length);
        Integer previousX = null, previousY = null;
        for (int i = 0; i < values.length; i++) {
            if (!Double.isFinite(values[i])) continue;
            int px = x + 4 + (int) ((i + .5) * slot);
            int py = scale(values[i], y + 8, y + height - 8, min, max);
            if (previousX != null) pixelLine(g, previousX, previousY, px, py, color);
            previousX = px; previousY = py;
        }
    }

    private static int scale(double value, int top, int bottom, double min, double max) {
        double ratio = Math.max(0, Math.min(1, (value - min) / Math.max(1e-9, max - min)));
        return bottom - (int) Math.round(ratio * (bottom - top));
    }

    private static double maxAbs(double[]... arrays) {
        double max = 0;
        for (double[] values : arrays) for (double value : values) if (Double.isFinite(value)) max = Math.max(max, Math.abs(value));
        return max;
    }

    private static void pixelLine(GuiGraphics g, int x1, int y1, int x2, int y2, int color) {
        int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        for (int i = 0; i <= steps; i++) {
            int px = x1 + (x2 - x1) * i / Math.max(1, steps);
            int py = y1 + (y2 - y1) * i / Math.max(1, steps);
            g.fill(px, py, px + 1, py + 1, color);
        }
    }

    private static void panel(GuiGraphics g, int x, int y, int width, int height) {
        g.fill(x, y, x + width, y + height, 0xFFF5F1E5);
        g.fill(x, y, x + width, y + 1, 0xFF555555);
        g.fill(x, y + height - 1, x + width, y + height, 0xFF555555);
    }
}
