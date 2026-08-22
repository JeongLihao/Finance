package finance.feedback;

import finance.data.serializer.WorldFeedbackDataSerializer;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WorldEconomyFeedbackServiceTest {
    @AfterEach void clear(){WorldEconomyFeedbackService.clearDirect();}
    @Test void duplicateInfoEventIsSuppressedForTwoDays(){
        var first=event(10);assertTrue(WorldEconomyFeedbackService.record(first));
        assertFalse(WorldEconomyFeedbackService.record(event(11)));
        assertTrue(WorldEconomyFeedbackService.record(event(12)));
    }
    @Test void pendingNotificationsAreBoundedAndPersisted(){
        UUID player=UUID.randomUUID();for(int i=0;i<40;i++)WorldEconomyFeedbackService.queue(player,new FeedbackNotification(i,FeedbackSeverity.INFO,"finance.feedback.price_alert",List.of("x")));
        assertEquals(WorldEconomyFeedbackService.MAX_PENDING_PER_PLAYER,WorldEconomyFeedbackService.unreadCount(player));
        CompoundTag root=new CompoundTag();WorldFeedbackDataSerializer.save(root);WorldEconomyFeedbackService.clearDirect();WorldFeedbackDataSerializer.load(root);
        assertEquals(WorldEconomyFeedbackService.MAX_PENDING_PER_PLAYER,WorldEconomyFeedbackService.unreadCount(player));
    }
    private static WorldEconomyEvent event(long day){return new WorldEconomyEvent("iron-shortage",WorldFeedbackType.MARKET_SHORTAGE,FeedbackSeverity.INFO,"iron",day,"",null,"finance.feedback.market_shortage",List.of("iron","1"),FeedbackAudience.LOCAL,Set.of());}
}
