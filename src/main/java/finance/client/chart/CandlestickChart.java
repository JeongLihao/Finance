package finance.client.chart;

import finance.chart.Candlestick;
import finance.chart.MovingAverage;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class CandlestickChart {

    private static final int UP = 0xFF2D9A4B;
    private static final int DOWN = 0xFFC44747;
    private static final int FLAT = 0xFF777777;
    private static final int MA5 = 0xFFE19B32;
    private static final int MA10 = 0xFF4F78C4;

    private CandlestickChart() {
    }

    public static void render(GuiGraphics graphics, Font font, List<Candlestick> input,
                              int x, int y, int width, int height, int mouseX, int mouseY) {
        graphics.fill(x, y, x + width, y + height, 0xFFF5F1E5);
        outline(graphics, x, y, width, height, 0xFF555555);
        if (input == null || input.isEmpty() || width < 40 || height < 40) {
            graphics.drawCenteredString(font, "暂无成交历史", x + width / 2, y + height / 2 - 4, 0xFF666666);
            return;
        }

        List<Candlestick> bars = input.size() > 120 ? input.subList(input.size() - 120, input.size()) : input;
        int volumeHeight = Math.max(14, height / 4);
        int priceTop = y + 8;
        int priceBottom = y + height - volumeHeight - 5;
        long min = Long.MAX_VALUE;
        long max = 0;
        long maxVolume = 0;
        for (Candlestick bar : bars) {
            min = Math.min(min, bar.low());
            max = Math.max(max, bar.high());
            maxVolume = Math.max(maxVolume, bar.volume());
        }
        if (min <= 0 || max <= 0) return;
        double range = Math.max(1.0, (double) max - min);
        double slot = (double) (width - 8) / bars.size();
        int bodyWidth = Math.max(1, Math.min(6, (int) Math.floor(slot * 0.62)));

        int hovered = -1;
        for (int index = 0; index < bars.size(); index++) {
            Candlestick bar = bars.get(index);
            int centerX = x + 4 + (int) Math.floor((index + 0.5) * slot);
            int highY = scalePrice(bar.high(), min, range, priceTop, priceBottom);
            int lowY = scalePrice(bar.low(), min, range, priceTop, priceBottom);
            int openY = scalePrice(bar.open(), min, range, priceTop, priceBottom);
            int closeY = scalePrice(bar.close(), min, range, priceTop, priceBottom);
            int color = bar.close() > bar.open() ? UP : bar.close() < bar.open() ? DOWN : FLAT;
            graphics.fill(centerX, highY, centerX + 1, lowY + 1, color);
            int bodyTop = Math.min(openY, closeY);
            int bodyBottom = Math.max(openY, closeY);
            graphics.fill(centerX - bodyWidth / 2, bodyTop,
                    centerX + (bodyWidth + 1) / 2, Math.max(bodyTop + 1, bodyBottom + 1), color);
            if (maxVolume > 0 && bar.volume() > 0) {
                int volumePixels = Math.max(1, (int) Math.round((double) bar.volume() / maxVolume * (volumeHeight - 4)));
                graphics.fill(centerX - bodyWidth / 2, y + height - 3 - volumePixels,
                        centerX + (bodyWidth + 1) / 2, y + height - 3, color & 0x88FFFFFF);
            }
            if (mouseX >= centerX - Math.max(2, (int) Math.ceil(slot / 2))
                    && mouseX <= centerX + Math.max(2, (int) Math.ceil(slot / 2))
                    && mouseY >= y && mouseY <= y + height) hovered = index;
        }

        drawMovingAverage(graphics, MovingAverage.simple(bars, 5), x, slot, min, range, priceTop, priceBottom, MA5);
        drawMovingAverage(graphics, MovingAverage.simple(bars, 10), x, slot, min, range, priceTop, priceBottom, MA10);
        graphics.drawString(font, "MA5", x + 5, y + 2, MA5, false);
        graphics.drawString(font, "MA10", x + 36, y + 2, MA10, false);
        graphics.drawString(font, Long.toString(max), x + width - font.width(Long.toString(max)) - 3, y + 2, 0xFF666666, false);
        graphics.drawString(font, Long.toString(min), x + width - font.width(Long.toString(min)) - 3,
                priceBottom - font.lineHeight, 0xFF666666, false);
        String firstDay = "D" + bars.get(0).mcDay();
        String lastDay = "D" + bars.get(bars.size() - 1).mcDay();
        graphics.drawString(font, firstDay, x + 3, y + height - font.lineHeight - 1, 0xFF666666, false);
        graphics.drawString(font, lastDay, x + width - font.width(lastDay) - 3,
                y + height - font.lineHeight - 1, 0xFF666666, false);

        if (hovered >= 0) {
            Candlestick bar = bars.get(hovered);
            long previous = hovered > 0 ? bars.get(hovered - 1).close() : bar.open();
            double changePercent = previous <= 0 ? 0 : (double) (bar.close() - previous) / previous * 100.0;
            graphics.renderTooltip(font, Component.literal("第" + bar.mcDay() + "天  开" + bar.open()
                    + " 高" + bar.high() + " 低" + bar.low() + " 收" + bar.close() + " 量" + bar.volume()
                    + " " + String.format(java.util.Locale.ROOT, "%+.1f%%", changePercent)),
                    mouseX, mouseY);
        }
    }

    private static void drawMovingAverage(GuiGraphics graphics, double[] averages,
                                          int x, double slot, long min, double range,
                                          int top, int bottom, int color) {
        int previousX = 0;
        int previousY = 0;
        for (int index = 0; index < averages.length; index++) {
            double average = averages[index];
            if (!Double.isFinite(average)) continue;
            int pointX = x + 4 + (int) Math.floor((index + 0.5) * slot);
            int pointY = scalePrice(average, min, range, top, bottom);
            if (previousX != 0) line(graphics, previousX, previousY, pointX, pointY, color);
            previousX = pointX;
            previousY = pointY;
        }
    }

    private static int scalePrice(double price, long min, double range, int top, int bottom) {
        double ratio = Math.max(0, Math.min(1, (price - min) / range));
        return bottom - (int) Math.round(ratio * Math.max(1, bottom - top));
    }

    private static void line(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color) {
        int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        if (steps == 0) return;
        for (int step = 0; step <= steps; step++) {
            int px = x1 + (x2 - x1) * step / steps;
            int py = y1 + (y2 - y1) * step / steps;
            graphics.fill(px, py, px + 1, py + 1, color);
        }
    }

    private static void outline(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }
}
