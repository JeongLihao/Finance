package finance.gameplay.company;

import finance.block.CompanyFactoryControllerBlock;
import finance.block.entity.CompanyFactoryControllerBlockEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;

/** Applies the persisted daily facility state to the sparse world block once per economy day. */
public final class CompanyFacilityWorldFeedbackService {
    private CompanyFacilityWorldFeedbackService() {}

    public static void refresh(MinecraftServer server, long day) {
        if (server == null) return;
        java.util.Set<java.util.UUID> progressed=new java.util.HashSet<>();
        for (CompanyFacilityRecord facility : CompanyFacilityManager.all()) {
            ResourceLocation dimension = ResourceLocation.tryParse(facility.dimensionId());
            if (dimension == null) continue;
            ServerLevel level = server.getLevel(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, dimension));
            if (level == null || !level.isLoaded(facility.blockPos())
                    || !(level.getBlockEntity(facility.blockPos()) instanceof CompanyFactoryControllerBlockEntity entity)
                    || !facility.facilityId().equals(entity.facilityId())) continue;
            var state = level.getBlockState(facility.blockPos());
            if (!(state.getBlock() instanceof CompanyFactoryControllerBlock)) continue;
            CompanyFactoryControllerBlock.updateIndicator(level, facility.blockPos(), facility.facilityId());
            if (facility.status() == CompanyFacilityStatus.ACTIVE && facility.lastProcessedDay() == day) {
                if(progressed.add(facility.companyId())){finance.company.Company company=finance.company.CompanyManager.getCompany(facility.companyId());if(company!=null){net.minecraft.server.level.ServerPlayer owner=server.getPlayerList().getPlayer(company.getOwnerId());if(owner!=null)finance.advancement.FinanceAdvancementTriggers.trigger(owner,"company_production");for(java.util.UUID member:CompanyGameplayManager.profileFor(company).members().keySet()){net.minecraft.server.level.ServerPlayer online=server.getPlayerList().getPlayer(member);if(online!=null)finance.advancement.FinanceAdvancementTriggers.trigger(online,"company_production");}}}
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, facility.blockPos().getX() + .5,
                        facility.blockPos().getY() + 1.1, facility.blockPos().getZ() + .5, 4, .2, .1, .2, 0);
                level.playSound(null, facility.blockPos(), SoundEvents.NOTE_BLOCK_PLING.get(), SoundSource.BLOCKS, .35f, 1.2f);
            }
        }
    }
}
