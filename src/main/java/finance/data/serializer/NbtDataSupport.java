package finance.data.serializer;

import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/** Shared defensive readers for persisted economy data. */
final class NbtDataSupport {

    private NbtDataSupport() {
    }

    static UUID readUuidOrNull(CompoundTag tag, String key) {
        try {
            return tag.hasUUID(key) ? tag.getUUID(key) : null;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    static <E extends Enum<E>> E safeEnum(Class<E> enumClass, String value, E fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(enumClass, value);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}
