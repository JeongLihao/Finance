package finance.advancement;

import com.google.gson.JsonObject;
import finance.FinanceMod;
import net.minecraft.advancements.critereon.AbstractCriterionTriggerInstance;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.DeserializationContext;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** One bounded server-only criterion for concrete finance gameplay actions. */
public final class FinanceProgressTrigger extends SimpleCriterionTrigger<FinanceProgressTrigger.Instance> {
    public static final ResourceLocation ID=ResourceLocation.fromNamespaceAndPath(FinanceMod.MOD_ID,"progress");
    @Override public ResourceLocation getId(){return ID;}
    @Override protected Instance createInstance(JsonObject json,ContextAwarePredicate player,DeserializationContext context){
        String event=json.has("event")?json.get("event").getAsString():"";
        if(event.isBlank()||event.length()>48)throw new IllegalArgumentException("invalid finance progress event");
        return new Instance(player,event);
    }
    public void trigger(ServerPlayer player,String event){if(player!=null&&event!=null)trigger(player,instance->instance.event.equals(event));}
    public static final class Instance extends AbstractCriterionTriggerInstance {
        private final String event;
        public Instance(ContextAwarePredicate player,String event){super(ID,player);this.event=event;}
        @Override public JsonObject serializeToJson(net.minecraft.advancements.critereon.SerializationContext context){JsonObject json=super.serializeToJson(context);json.addProperty("event",event);return json;}
    }
}
