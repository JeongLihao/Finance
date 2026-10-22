package finance.block;

import finance.block.entity.CompanyFactoryControllerBlockEntity;
import finance.gameplay.company.CompanyFacilityManager;
import finance.gui.CompanyGameplayGuiOpener;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public final class CompanyFactoryControllerBlock extends BaseEntityBlock {
    public enum Indicator implements StringRepresentable {
        ACTIVE("active"),
        MISSING_INPUT("missing_input"),
        OUTPUT_FULL("output_full"),
        RISK("risk"),
        PROJECT_PENDING("project_pending"),
        OFF("off");
        private final String name;

        Indicator(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public static final EnumProperty<Indicator> INDICATOR = EnumProperty.create("indicator", Indicator.class);
    private static final FacingShapes SHAPE = FacingShapes.of(
            new double[] {1, 0, 1, 15, 13, 15}, new double[] {0, 13, 0, 16, 14, 16}, new double[] {6, 14, 6, 10, 16, 10
            });

    public CompanyFactoryControllerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition
                .any()
                .setValue(INDICATOR, Indicator.OFF)
                .setValue(FacingShapes.FACING, Direction.NORTH));
    }

    @Override
    public InteractionResult use(
            BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide
                && player instanceof ServerPlayer server
                && level.getBlockEntity(pos) instanceof CompanyFactoryControllerBlockEntity factory) {
            if (factory.registerOrValidate(server)) {
                if (player.isShiftKeyDown()) {
                    // 蹲下右键：当前方式 · 岗位 · 缺什么
                    var facility = CompanyFacilityManager.get(factory.facilityId());
                    server.displayClientMessage(
                            finance.gameplay.company.factory.FactoryMethodService.inspect(
                                    server.serverLevel(),
                                    facility,
                                    finance.cycle.EconomyCycleService.currentMcDay(server.server)),
                            true);
                } else CompanyGameplayGuiOpener.open(server, pos);
            } else
                server.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable("finance.company_gameplay.factory_denied"),
                        true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof CompanyFactoryControllerBlockEntity factory)
            CompanyFacilityManager.disable(factory.facilityId());
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    public static void updateIndicator(Level level, BlockPos pos, java.util.UUID facilityId) {
        if (level == null || level.isClientSide || facilityId == null) return;
        var facility = CompanyFacilityManager.get(facilityId);
        if (facility == null) return;
        boolean pending =
                finance.gameplay.company.capital.CapitalProjectManager.forCompany(facility.companyId()).stream()
                        .anyMatch(project -> facilityId.equals(project.targetId())
                                && !project.status().terminal());
        Indicator next = pending
                ? Indicator.PROJECT_PENDING
                : switch (facility.status()) {
                    case ACTIVE -> Indicator.ACTIVE;
                    case MISSING_INPUT, UNDERSTAFFED, EQUIPMENT_MISSING -> Indicator.MISSING_INPUT;
                    case OUTPUT_FULL -> Indicator.OUTPUT_FULL;
                    case BANKRUPTCY_HOLD -> Indicator.RISK;
                    case DISABLED, ORPHANED -> Indicator.OFF;
                };
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof CompanyFactoryControllerBlock && state.getValue(INDICATOR) != next)
            level.setBlock(pos, state.setValue(INDICATOR, next), 3);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return FacingShapes.facingPlacer(defaultBlockState(), context);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return FacingShapes.rotate(state, rotation);
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return FacingShapes.mirror(state, mirror);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE.get(state);
    }

    @Override
    protected void createBlockStateDefinition(
            StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(INDICATOR, FacingShapes.FACING);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CompanyFactoryControllerBlockEntity(pos, state);
    }
}
