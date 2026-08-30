package finance.gameplay.company.capital;

import finance.testutil.MinecraftTestBootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CapitalProjectWorldIntegrationTest {
    @BeforeAll static void bootstrapMinecraft() { MinecraftTestBootstrap.ensureStarted(); }
    @Test void creationSnapshotCannotBeChangedByLaterRequirementMutation() {
        Map<Item, Integer> mutable = new LinkedHashMap<>();
        mutable.put(Items.IRON_INGOT, 8);
        WorldCapitalProject project = new WorldCapitalProject(UUID.randomUUID(), UUID.randomUUID(),
                WorldCapitalProjectType.WAREHOUSE_UPGRADE, UUID.randomUUID(), UUID.randomUUID(),
                0, 30, 2, CapitalFundingSource.RETAINED_EARNINGS, 250, mutable,
                false, CapitalProjectStatus.DRAFT, 0);
        mutable.put(Items.IRON_INGOT, 1);
        assertEquals(8, project.materials().get(Items.IRON_INGOT));
        assertThrows(UnsupportedOperationException.class, () -> project.materials().put(Items.REDSTONE, 1));
    }
}
