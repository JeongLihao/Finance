package finance.block.entity;

import finance.company.Company;
import finance.company.CompanyManager;
import finance.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

/** Stable venue binding. It grants no governance authority; proposal services remain authoritative. */
public final class BoardroomTableBlockEntity extends BlockEntity {
    private UUID boardroomId=UUID.randomUUID(), companyId, boundBy;
    public BoardroomTableBlockEntity(BlockPos pos,BlockState state){super(ModBlockEntities.BOARDROOM_TABLE.get(),pos,state);}
    public UUID companyId(){return companyId;}
    public void resetUntrustedPlacement(){boardroomId=UUID.randomUUID();companyId=null;boundBy=null;setChanged();}
    public boolean bindOrValidate(ServerPlayer player){
        if(player==null)return false;
        if(companyId!=null){Company company=CompanyManager.getCompany(companyId);return company!=null&&company.isPublic();}
        Company company=CompanyManager.getCompanyByOwner(player.getUUID());
        if(company==null||!company.isPublic())return false;
        companyId=company.getCompanyId();boundBy=player.getUUID();setChanged();return true;
    }
    @Override protected void saveAdditional(CompoundTag tag){super.saveAdditional(tag);tag.putUUID("BoardroomId",boardroomId);if(companyId!=null)tag.putUUID("CompanyId",companyId);if(boundBy!=null)tag.putUUID("BoundBy",boundBy);}
    @Override public void load(CompoundTag tag){super.load(tag);boardroomId=tag.hasUUID("BoardroomId")?tag.getUUID("BoardroomId"):UUID.randomUUID();companyId=tag.hasUUID("CompanyId")?tag.getUUID("CompanyId"):null;boundBy=tag.hasUUID("BoundBy")?tag.getUUID("BoundBy"):null;}
}
