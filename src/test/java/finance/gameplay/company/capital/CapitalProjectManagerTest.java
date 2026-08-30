package finance.gameplay.company.capital;

import finance.testutil.MinecraftTestBootstrap;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CapitalProjectManagerTest {
    @BeforeAll static void bootstrapMinecraft() { MinecraftTestBootstrap.ensureStarted(); }
    @AfterEach void cleanup() { CapitalProjectManager.clearDirect(); }

    @Test void enforcesTwoActiveProjectsPerCompanyAndOnePerTarget() {
        UUID company = UUID.randomUUID(), owner = UUID.randomUUID();
        WorldCapitalProject first = project(company, UUID.randomUUID(), owner, CapitalProjectStatus.DRAFT);
        WorldCapitalProject second = project(company, UUID.randomUUID(), owner, CapitalProjectStatus.FUNDING);
        assertTrue(CapitalProjectManager.register(first));
        assertFalse(CapitalProjectManager.register(project(company, first.targetId(), owner, CapitalProjectStatus.DRAFT)));
        assertTrue(CapitalProjectManager.register(second));
        assertFalse(CapitalProjectManager.register(project(company, UUID.randomUUID(), owner, CapitalProjectStatus.DRAFT)));
        assertEquals(2, CapitalProjectManager.activeCountForCompany(company));
    }

    @Test void terminalHistoryDoesNotConsumeActiveCompanyCapacity() {
        UUID company = UUID.randomUUID(), owner = UUID.randomUUID();
        assertTrue(CapitalProjectManager.register(project(company, UUID.randomUUID(), owner,
                CapitalProjectStatus.COMPLETED)));
        assertTrue(CapitalProjectManager.register(project(company, UUID.randomUUID(), owner,
                CapitalProjectStatus.DRAFT)));
        assertEquals(1, CapitalProjectManager.activeCountForCompany(company));
    }

    @Test void oldestTerminalHistoryIsPrunedWhenRegistryIsFull() {
        UUID company = UUID.randomUUID(), owner = UUID.randomUUID();
        WorldCapitalProject oldest = project(company, UUID.randomUUID(), owner, CapitalProjectStatus.COMPLETED);
        assertTrue(CapitalProjectManager.register(oldest));
        for (int i = 1; i < CapitalProjectManager.MAX_PROJECTS; i++) {
            assertTrue(CapitalProjectManager.register(project(company, UUID.randomUUID(), owner,
                    CapitalProjectStatus.COMPLETED)));
        }
        WorldCapitalProject active = project(company, UUID.randomUUID(), owner, CapitalProjectStatus.DRAFT);
        assertTrue(CapitalProjectManager.register(active));
        assertNull(CapitalProjectManager.get(oldest.projectId()));
        assertSame(active, CapitalProjectManager.get(active.projectId()));
        assertEquals(CapitalProjectManager.MAX_PROJECTS, CapitalProjectManager.projects().size());
    }

    static WorldCapitalProject project(UUID company, UUID target, UUID owner, CapitalProjectStatus status) {
        return new WorldCapitalProject(UUID.randomUUID(), company, WorldCapitalProjectType.WAREHOUSE_UPGRADE,
                target, owner, 1, 31, 2, CapitalFundingSource.RETAINED_EARNINGS, 250,
                Map.of(Items.IRON_INGOT, 8), false, status, 1);
    }
}
