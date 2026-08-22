package finance.block;

import finance.gameplay.FinanceGameplayOpener;
import finance.gameplay.FinanceTerminalType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;

public class FinanceTerminalBlock extends Block {
    public enum Indicator implements StringRepresentable {
        NORMAL("normal"), SHORTAGE("shortage"), OFFLINE("offline"), RESTRICTED("restricted"), RESOLUTION("resolution");
        private final String name; Indicator(String name){this.name=name;} @Override public String getSerializedName(){return name;}
    }
    public static final EnumProperty<Indicator> INDICATOR=EnumProperty.create("indicator",Indicator.class);
    private final FinanceTerminalType terminalType;

    public FinanceTerminalBlock(Properties properties, FinanceTerminalType terminalType) {
        super(properties);
        this.terminalType = terminalType;
        registerDefaultState(stateDefinition.any().setValue(INDICATOR,Indicator.NORMAL));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(INDICATOR);}
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity placer,ItemStack stack){super.setPlacedBy(level,pos,state,placer,stack);if(!level.isClientSide)finance.gameplay.WorldTerminalRegistry.register(new finance.gameplay.WorldTerminalRegistry.TerminalRecord(level.dimension().location().toString(),pos,terminalType));}
    @Override public void onRemove(BlockState state,Level level,BlockPos pos,BlockState replacement,boolean moving){if(!level.isClientSide&&state.getBlock()!=replacement.getBlock())finance.gameplay.WorldTerminalRegistry.remove(level.dimension().location().toString(),pos);super.onRemove(state,level,pos,replacement,moving);}

    public FinanceTerminalType terminalType() {
        return terminalType;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            FinanceGameplayOpener.openTerminal(serverPlayer, pos, terminalType);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
