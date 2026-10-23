package finance.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 一个配置项：默认值和配置规格放在一起，默认值只写一次。
 * 声明时给默认值，{@link FinanceConfig} 建规格时用 {@code bind} 接上 Forge 的配置值；
 * 配置还没加载（单元测试、模组构造早期）或读到 null 时用默认值。
 */
abstract class ConfigOption<T> {
    private ForgeConfigSpec.ConfigValue<T> value;

    void bind(ForgeConfigSpec.ConfigValue<T> value) {
        this.value = value;
    }

    protected T read(T fallback) {
        if (value == null) return fallback;
        try {
            T current = value.get();
            return current == null ? fallback : current;
        } catch (IllegalStateException notLoaded) {
            return fallback;
        }
    }

    static final class IntOption extends ConfigOption<Integer> {
        final int def;

        IntOption(int def) {
            this.def = def;
        }

        int get() {
            return read(def);
        }
    }

    static final class LongOption extends ConfigOption<Long> {
        final long def;

        LongOption(long def) {
            this.def = def;
        }

        long get() {
            return read(def);
        }
    }

    static final class DoubleOption extends ConfigOption<Double> {
        final double def;

        DoubleOption(double def) {
            this.def = def;
        }

        double get() {
            return read(def);
        }
    }

    static final class BoolOption extends ConfigOption<Boolean> {
        final boolean def;

        BoolOption(boolean def) {
            this.def = def;
        }

        boolean get() {
            return read(def);
        }
    }

    static final class StringOption extends ConfigOption<String> {
        final String def;

        StringOption(String def) {
            this.def = def;
        }

        String get() {
            return read(def);
        }
    }
}
