package finance.gameplay.company.capital;

import finance.testutil.MinecraftTestBootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CapitalProjectLongRunSimulationTest {
    @BeforeAll static void bootstrapMinecraft() { MinecraftTestBootstrap.ensureStarted(); }
    @AfterEach void cleanup() { CapitalProjectManager.clearDirect(); }

    @Test void registryRemainsHardBoundedAtOneThousandTwentyFourProjects() {
        UUID company = UUID.randomUUID(), owner = UUID.randomUUID();
        for (int i = 0; i < CapitalProjectManager.MAX_PROJECTS; i++) {
            assertTrue(CapitalProjectManager.register(CapitalProjectManagerTest.project(company,
                    UUID.randomUUID(), owner, CapitalProjectStatus.COMPLETED)));
        }
        assertTrue(CapitalProjectManager.register(CapitalProjectManagerTest.project(company,
                UUID.randomUUID(), owner, CapitalProjectStatus.COMPLETED)));
        assertEquals(CapitalProjectManager.MAX_PROJECTS, CapitalProjectManager.projects().size());
    }
}
