package finance.block;

import finance.block.entity.CompanyFactoryControllerBlockEntity;
import finance.gui.CompanyGameplayGuiOpener;
import finance.gameplay.company.CompanyFacilityManager;
import finance.gameplay.company.CompanyUpgradeRequirementService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public final class CompanyFactoryControllerBlock extends BaseEntityBlock {
    public enum Indicator implements StringRepresentable {
        ACTIVE("active"), MISSING_INPUT("missing_input"), OUTPUT_FULL("output_full"), RISK("risk"), OFF("off");
        private final String name; Indicator(String name){this.name=name;} @Override public String getSerializedName(){return name;}
    }
    public static final EnumProperty<Indicator> INDICATOR = EnumProperty.create("indicator", Indicator.class);
    public CompanyFactoryControllerBlock(Properties properties) { super(properties); registerDefaultState(stateDefinition.any().setValue(INDICATOR, Indicator.OFF)); }
    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer server && level.getBlockEntity(pos) instanceof CompanyFactoryControllerBlockEntity factory) {
            if (factory.registerOrValidate(server)) {
                if (player.isShiftKeyDown()) {
                    var facility = CompanyFacilityManager.get(factory.facilityId());
                    var company = facility == null ? null : finance.company.CompanyManager.getCompany(facility.companyId());
                    CompanyUpgradeRequirementService.Requirement requirement = company == null || facility == null ? null
                            : CompanyUpgradeRequirementService.requirement(company.getType(), facility.type(),
                            facility.productionLevel());
                    server.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                            "finance.company_gameplay.inspect", facility == null ? 0 : facility.productionLevel(),
                            facility == null ? "-" : facility.status().name(),
                            CompanyUpgradeRequirementService.summary(requirement)), true);
                } else CompanyGameplayGuiOpener.open(server, pos);
            }
            else server.displayClientMessage(net.minecraft.network.chat.Component.translatable("finance.company_gameplay.factory_denied"), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) { if (!level.isClientSide && level.getBlockEntity(pos) instanceof CompanyFactoryControllerBlockEntity factory) CompanyFacilityManager.disable(factory.facilityId()); super.playerWillDestroy(level, pos, state, player); }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) { builder.add(INDICATOR); }
    @Nullable @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new CompanyFactoryControllerBlockEntity(pos, state); }
}
