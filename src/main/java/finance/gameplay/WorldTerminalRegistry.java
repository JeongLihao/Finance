package finance.gameplay;

import finance.data.EconomySavedData;
import net.minecraft.core.BlockPos;

import java.util.*;

/** Sparse persisted index of Finance venue blocks; it never scans loaded chunks. */
public final class WorldTerminalRegistry {
    public static final int MAX_TERMINALS = 4_096;
    public record TerminalRecord(String dimensionId, BlockPos position, FinanceTerminalType type) {
        public TerminalRecord { if(dimensionId==null||dimensionId.isBlank()||dimensionId.length()>128||position==null||type==null||!type.isPhysicalTerminal())throw new IllegalArgumentException("invalid terminal");position=position.immutable(); }
        public String key(){return dimensionId+":"+position.asLong();}
    }
    private static final Map<String,TerminalRecord> TERMINALS=new LinkedHashMap<>();
    private WorldTerminalRegistry(){}
    public static Collection<TerminalRecord> all(){return List.copyOf(TERMINALS.values());}
    public static List<TerminalRecord> byType(FinanceTerminalType type){return TERMINALS.values().stream().filter(r->r.type()==type).toList();}
    public static synchronized boolean register(TerminalRecord record){if(record==null)return false;if(!TERMINALS.containsKey(record.key())&&TERMINALS.size()>=MAX_TERMINALS)return false;TERMINALS.put(record.key(),record);EconomySavedData.markDirty();return true;}
    public static synchronized boolean restore(TerminalRecord record){if(record==null||TERMINALS.size()>=MAX_TERMINALS||TERMINALS.containsKey(record.key()))return false;TERMINALS.put(record.key(),record);return true;}
    public static synchronized void remove(String dimension,BlockPos pos){if(dimension!=null&&pos!=null&&TERMINALS.remove(dimension+":"+pos.asLong())!=null)EconomySavedData.markDirty();}
    public static void clearDirect(){TERMINALS.clear();}
}
