package finance.block;

import finance.block.entity.CompanyDeskBlockEntity;
import finance.gui.CompanyGameplayGuiOpener;
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
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public final class CompanyDeskBlock extends BaseEntityBlock {
    public CompanyDeskBlock(Properties properties) { super(properties); }
    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer server && level.getBlockEntity(pos) instanceof CompanyDeskBlockEntity desk) {
            if (desk.bindOrValidate(server)) CompanyGameplayGuiOpener.open(server, pos);
            else server.sendSystemMessage(net.minecraft.network.chat.Component.translatable("finance.company_gameplay.desk_denied"));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) { if (!level.isClientSide && level.getBlockEntity(pos) instanceof CompanyDeskBlockEntity desk) desk.unbindLocation(); super.playerWillDestroy(level, pos, state, player); }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Nullable @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new CompanyDeskBlockEntity(pos, state); }
}
