package finance.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WarehouseContractPacketTest {
    @Test void warehouseActionRoundTripsBoundedIntent() {
        UUID warehouse = UUID.randomUUID();
        WarehouseActionPacket packet = new WarehouseActionPacket(WarehouseActionPacket.Action.DEPOSIT,
                warehouse, "iron", 12, "operation-1");
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        WarehouseActionPacket.encode(packet, buffer);
        assertEquals(packet, WarehouseActionPacket.decode(buffer));
    }

    @Test void overlongWarehouseCommodityIsRejectedDuringDecode() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeEnum(WarehouseActionPacket.Action.DEPOSIT);
        buffer.writeUUID(UUID.randomUUID());
        buffer.writeUtf("x".repeat(65), 128);
        buffer.writeVarInt(1);
        buffer.writeUtf("operation", 64);
        assertThrows(RuntimeException.class, () -> WarehouseActionPacket.decode(buffer));
    }

    @Test void contractActionRoundTripsWithoutRewardOrPlayerIdentity() {
        ContractActionPacket packet = new ContractActionPacket(ContractActionPacket.Action.COMPLETE,
                UUID.randomUUID(), UUID.randomUUID(), "operation-2");
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        ContractActionPacket.encode(packet, buffer);
        assertEquals(packet, ContractActionPacket.decode(buffer));
    }

    @Test void invalidContractActionOrdinalIsRejectedDuringDecode() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeVarInt(999);
        assertThrows(RuntimeException.class, () -> ContractActionPacket.decode(buffer));
    }

    @Test void warehouseDecoderRejectsNegativeZeroAndHugeTransferAmounts() {
        for (int amount : new int[] {Integer.MIN_VALUE, -1, 0, Integer.MAX_VALUE}) {
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            buffer.writeEnum(WarehouseActionPacket.Action.WITHDRAW);
            buffer.writeUUID(UUID.randomUUID());
            buffer.writeUtf("iron", 64);
            buffer.writeVarInt(amount);
            buffer.writeUtf("bounded-operation", 64);
            assertThrows(RuntimeException.class, () -> WarehouseActionPacket.decode(buffer));
        }
    }

    @Test void blankOperationKeysAreRejectedAtDecodeBoundary() {
        FriendlyByteBuf warehouse = new FriendlyByteBuf(Unpooled.buffer());
        warehouse.writeEnum(WarehouseActionPacket.Action.DEPOSIT);
        warehouse.writeUUID(UUID.randomUUID()); warehouse.writeUtf("iron", 64);
        warehouse.writeVarInt(1); warehouse.writeUtf("   ", 64);
        assertThrows(RuntimeException.class, () -> WarehouseActionPacket.decode(warehouse));

        FriendlyByteBuf contract = new FriendlyByteBuf(Unpooled.buffer());
        contract.writeEnum(ContractActionPacket.Action.ACCEPT);
        contract.writeUUID(UUID.randomUUID()); contract.writeUUID(UUID.randomUUID()); contract.writeUtf("", 64);
        assertThrows(RuntimeException.class, () -> ContractActionPacket.decode(contract));
    }

    @Test void companyGameplayDecoderRejectsInvalidTargetsAndExtremeContractValues() {
        FriendlyByteBuf missingTarget = companyBuffer(CompanyGameplayActionPacket.Action.UPGRADE_FACILITY,
                new UUID(0, 0), "", 0, 0, "operation");
        assertThrows(RuntimeException.class, () -> CompanyGameplayActionPacket.decode(missingTarget));

        FriendlyByteBuf hugeReward = companyBuffer(CompanyGameplayActionPacket.Action.PUBLISH_CONTRACT,
                UUID.randomUUID(), "iron", 1, Long.MAX_VALUE, "operation");
        assertThrows(RuntimeException.class, () -> CompanyGameplayActionPacket.decode(hugeReward));

        FriendlyByteBuf invalidOrdinal = new FriendlyByteBuf(Unpooled.buffer());
        invalidOrdinal.writeVarInt(999);
        assertThrows(RuntimeException.class, () -> CompanyGameplayActionPacket.decode(invalidOrdinal));
    }

    private static FriendlyByteBuf companyBuffer(CompanyGameplayActionPacket.Action action, UUID target,
                                                  String text, int quantity, long amount, String operationKey) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeEnum(action); buffer.writeUUID(UUID.randomUUID()); buffer.writeUUID(target);
        buffer.writeUtf(text, 64); buffer.writeVarInt(quantity); buffer.writeLong(amount);
        buffer.writeUtf(operationKey, 64); return buffer;
    }
}
