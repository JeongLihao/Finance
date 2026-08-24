package finance.item;

import finance.exploration.*;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import java.util.*;

public final class SurveyNoteItem extends Item {
    private static final String ASSIGNMENT="ExplorationAssignment";
    public SurveyNoteItem(Properties properties){super(properties);}
    public static void bind(ItemStack stack,UUID id){if(stack!=null&&id!=null)stack.getOrCreateTag().putUUID(ASSIGNMENT,id);}
    public static UUID assignmentId(ItemStack stack){return stack!=null&&stack.hasTag()&&stack.getTag().hasUUID(ASSIGNMENT)?stack.getTag().getUUID(ASSIGNMENT):null;}
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){ItemStack stack=player.getItemInHand(hand);if(!level.isClientSide&&player instanceof ServerPlayer sp){UUID id=assignmentId(stack);ExplorationAssignment a=ExplorationManager.get(id);if(player.isShiftKeyDown()){ExplorationResult result=ExplorationService.cancel(sp,id);sp.displayClientMessage(Component.translatable(result.messageKey()),true);}else if(a==null||!a.playerId().equals(player.getUUID())||a.status()!=ExplorationStatus.ACTIVE)sp.displayClientMessage(Component.translatable("finance.exploration.invalid_note"),true);else if(!a.dimensionId().equals(level.dimension().location().toString()))sp.displayClientMessage(Component.translatable("finance.exploration.other_dimension",a.dimensionId()),true);else sp.displayClientMessage(Component.translatable("finance.exploration.hint",ExplorationService.direction(a,player.blockPosition()),ExplorationService.distance(a,player.blockPosition()),a.theme()),true);}return InteractionResultHolder.sidedSuccess(stack,level.isClientSide);}
    @Override public void appendHoverText(ItemStack stack,@Nullable Level level,List<Component> tooltip,TooltipFlag flag){tooltip.add(Component.translatable("item.finance.survey_note.tooltip").withStyle(ChatFormatting.GRAY));tooltip.add(Component.translatable("item.finance.survey_note.cancel").withStyle(ChatFormatting.DARK_GRAY));}
}
