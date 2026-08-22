package finance.registry;

import finance.FinanceMod;
import finance.block.entity.WarehouseControllerBlockEntity;
import finance.block.entity.CompanyDeskBlockEntity;
import finance.block.entity.CompanyFactoryControllerBlockEntity;
import finance.block.entity.BoardroomTableBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlockEntities {
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, FinanceMod.MOD_ID);

    public static final RegistryObject<BlockEntityType<WarehouseControllerBlockEntity>> WAREHOUSE_CONTROLLER =
            BLOCK_ENTITIES.register("warehouse_controller",
                    () -> BlockEntityType.Builder.of(WarehouseControllerBlockEntity::new,
                            ModBlocks.WAREHOUSE_CONTROLLER.get()).build(null));
    public static final RegistryObject<BlockEntityType<CompanyDeskBlockEntity>> COMPANY_DESK =
            BLOCK_ENTITIES.register("company_desk", () -> BlockEntityType.Builder.of(CompanyDeskBlockEntity::new,
                    ModBlocks.COMPANY_DESK.get()).build(null));
    public static final RegistryObject<BlockEntityType<CompanyFactoryControllerBlockEntity>> COMPANY_FACTORY_CONTROLLER =
            BLOCK_ENTITIES.register("company_factory_controller", () -> BlockEntityType.Builder.of(
                    CompanyFactoryControllerBlockEntity::new, ModBlocks.COMPANY_FACTORY_CONTROLLER.get()).build(null));
    public static final RegistryObject<BlockEntityType<BoardroomTableBlockEntity>> BOARDROOM_TABLE =
            BLOCK_ENTITIES.register("boardroom_table",()->BlockEntityType.Builder.of(
                    BoardroomTableBlockEntity::new,ModBlocks.BOARDROOM_TABLE.get()).build(null));

    private ModBlockEntities() {}

    public static void register(IEventBus bus) {
        BLOCK_ENTITIES.register(bus);
    }
}
