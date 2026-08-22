package finance.block;

import finance.block.entity.BoardroomTableBlockEntity;
import finance.gameplay.FinanceGameplayOpener;
import finance.gameplay.FinanceTerminalType;
import finance.gameplay.WorldTerminalRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public final class BoardroomTableBlock extends BaseEntityBlock {
    public BoardroomTableBlock(Properties properties){super(properties);}
    @Override public InteractionResult use(BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        if(!level.isClientSide&&player instanceof ServerPlayer server&&level.getBlockEntity(pos) instanceof BoardroomTableBlockEntity table){
            if(table.bindOrValidate(server))FinanceGameplayOpener.openTerminal(server,pos,FinanceTerminalType.BOARDROOM_TABLE);
            else server.sendSystemMessage(net.minecraft.network.chat.Component.translatable("finance.boardroom.bind_denied"));
        }return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,@Nullable LivingEntity placer,ItemStack stack){super.setPlacedBy(level,pos,state,placer,stack);if(!level.isClientSide){if(level.getBlockEntity(pos) instanceof BoardroomTableBlockEntity table)table.resetUntrustedPlacement();WorldTerminalRegistry.register(new WorldTerminalRegistry.TerminalRecord(level.dimension().location().toString(),pos.immutable(),FinanceTerminalType.BOARDROOM_TABLE));}}
    @Override public void onRemove(BlockState state,Level level,BlockPos pos,BlockState replacement,boolean moving){if(!level.isClientSide&&state.getBlock()!=replacement.getBlock())WorldTerminalRegistry.remove(level.dimension().location().toString(),pos);super.onRemove(state,level,pos,replacement,moving);}
    @Override public RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
    @Nullable @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new BoardroomTableBlockEntity(pos,state);}
}
