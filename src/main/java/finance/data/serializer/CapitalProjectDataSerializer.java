package finance.data.serializer;

import finance.account.Account;
import finance.account.AccountManager;
import finance.company.CompanyManager;
import finance.company.CompanyFinancingManager;
import finance.company.CompanyFinancingProject;
import finance.debt.CompanyLoan;
import finance.debt.CompanyLoanManager;
import finance.debt.CorporateBond;
import finance.debt.CorporateBondManager;
import finance.debt.LoanLenderType;
import finance.gameplay.company.CompanyFacilityManager;
import finance.gameplay.company.capital.CapitalFundingSource;
import finance.gameplay.company.capital.CapitalProjectManager;
import finance.gameplay.company.capital.CapitalProjectStatus;
import finance.gameplay.company.capital.WorldCapitalProject;
import finance.gameplay.company.capital.WorldCapitalProjectType;
import finance.warehouse.WarehouseManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Defensive, bounded persistence for physical capital projects. */
public final class CapitalProjectDataSerializer {
    public static final String ROOT = "CapitalProjects";
    private static final int VERSION = 1;
    private static final int MAX_MATERIALS = 32;

    private CapitalProjectDataSerializer() {}

    public static void save(CompoundTag root) {
        CompoundTag data = new CompoundTag();
        data.putInt("Version", VERSION);
        ListTag records = new ListTag();
        int count = 0;
        for (WorldCapitalProject project : CapitalProjectManager.projects().values()) {
            if (count++ >= CapitalProjectManager.MAX_PROJECTS) break;
            CompoundTag tag = new CompoundTag();
            tag.putUUID("Id", project.projectId());
            tag.putUUID("Company", project.companyId());
            tag.putString("Type", project.type().name());
            tag.putUUID("Target", project.targetId());
            tag.putUUID("Creator", project.creatorId());
            tag.putLong("CreatedDay", project.createdDay());
            tag.putLong("DeadlineDay", project.deadlineDay());
            tag.putInt("TargetLevel", project.targetLevel());
            tag.putString("FundingSource", project.fundingSource().name());
            tag.putLong("Budget", project.budget());
            tag.putLong("Funded", project.fundedAmount());
            tag.putBoolean("GovernanceRequired", project.governanceRequired());
            putUuid(tag, "Proposal", project.proposalId());
            putUuid(tag, "Loan", project.loanId());
            putUuid(tag, "Bank", project.bankId());
            putUuid(tag, "Bond", project.bondId());
            putUuid(tag, "Financing", project.financingProjectId());
            tag.putBoolean("FundingSettled", project.fundingSettled());
            tag.putString("Status", project.status().name());
            tag.putLong("StatusDay", project.lastStatusChangeDay());
            tag.putString("Failure", project.failureKey());
            ListTag materials = new ListTag();
            for (Map.Entry<Item, Integer> entry : project.materials().entrySet()) {
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(entry.getKey());
                if (id == null || entry.getKey() == Items.AIR) continue;
                CompoundTag material = new CompoundTag();
                material.putString("Item", id.toString());
                material.putInt("Amount", entry.getValue());
                materials.add(material);
            }
            tag.put("Materials", materials);
            ListTag operations = new ListTag();
            for (String key : project.operationKeys()) operations.add(StringTag.valueOf(key));
            tag.put("Operations", operations);
            records.add(tag);
        }
        data.put("Records", records);
        root.put(ROOT, data);
    }

    public static void load(CompoundTag root) {
        CapitalProjectManager.clearDirect();
        if (!root.contains(ROOT, Tag.TAG_COMPOUND)) return;
        ListTag records = root.getCompound(ROOT).getList("Records", Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(records.size(), CapitalProjectManager.MAX_PROJECTS); i++) {
            try {
                restoreRecord(records.getCompound(i));
            } catch (RuntimeException ignored) {
                // Isolate a damaged project. Never guess a refund recipient or alter linked debt.
            }
        }
    }

    private static void restoreRecord(CompoundTag tag) {
        UUID id = NbtDataSupport.readUuidOrNull(tag, "Id");
        UUID companyId = NbtDataSupport.readUuidOrNull(tag, "Company");
        UUID targetId = NbtDataSupport.readUuidOrNull(tag, "Target");
        UUID creatorId = NbtDataSupport.readUuidOrNull(tag, "Creator");
        WorldCapitalProjectType type = NbtDataSupport.safeEnum(WorldCapitalProjectType.class,
                tag.getString("Type"), null);
        CapitalFundingSource source = NbtDataSupport.safeEnum(CapitalFundingSource.class,
                tag.getString("FundingSource"), null);
        CapitalProjectStatus status = NbtDataSupport.safeEnum(CapitalProjectStatus.class,
                tag.getString("Status"), null);
        long created = tag.getLong("CreatedDay"), deadline = tag.getLong("DeadlineDay");
        long budget = tag.getLong("Budget"), funded = tag.getLong("Funded");
        int targetLevel = tag.getInt("TargetLevel");
        if (id == null || companyId == null || targetId == null || creatorId == null || type == null
                || source == null || status == null || created < 0 || deadline <= created
                || budget <= 0 || funded < 0 || funded > budget || targetLevel < 1) return;
        Map<Item, Integer> materials = readMaterials(tag.getList("Materials", Tag.TAG_COMPOUND));
        if (materials.isEmpty()) return;

        UUID escrowId = UUID.nameUUIDFromBytes(("capital-project-escrow:" + id)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Account escrow = AccountManager.getAccounts().get(escrowId);
        long escrowBalance = escrow == null ? 0 : escrow.getBalance();
        boolean settled = tag.getBoolean("FundingSettled");
        boolean escrowMismatch = status.terminal() ? funded != 0 || escrowBalance != 0
                : settled || funded > 0 ? funded != budget || escrow == null || escrowBalance != budget
                : escrowBalance != 0;

        boolean missingReference = CompanyManager.getCompany(companyId) == null
                || type == WorldCapitalProjectType.WAREHOUSE_UPGRADE && WarehouseManager.get(targetId) == null
                || type == WorldCapitalProjectType.FACTORY_UPGRADE && CompanyFacilityManager.get(targetId) == null;
        String financingFailure = status.terminal() ? "" : financingReferenceFailure(tag, source, companyId, budget);
        boolean quarantined = escrowMismatch || missingReference || !financingFailure.isBlank();
        CapitalProjectStatus restoredStatus = quarantined ? CapitalProjectStatus.FAILED_RECOVERABLE : status;
        String failure = escrowMismatch ? "finance.capital_project.escrow_mismatch"
                : missingReference ? "finance.capital_project.reference_missing"
                : !financingFailure.isBlank() ? financingFailure : tag.getString("Failure");
        WorldCapitalProject project = new WorldCapitalProject(id, companyId, type, targetId, creatorId,
                created, deadline, targetLevel, source, budget, materials,
                tag.getBoolean("GovernanceRequired"), restoredStatus, tag.getLong("StatusDay"));
        project.restoreReferences(funded,
                NbtDataSupport.readUuidOrNull(tag, "Proposal"), NbtDataSupport.readUuidOrNull(tag, "Loan"),
                NbtDataSupport.readUuidOrNull(tag, "Bank"), NbtDataSupport.readUuidOrNull(tag, "Bond"),
                NbtDataSupport.readUuidOrNull(tag, "Financing"), settled, failure);
        ListTag operations = tag.getList("Operations", Tag.TAG_STRING);
        for (int op = Math.max(0, operations.size() - WorldCapitalProject.MAX_OPERATION_KEYS);
             op < operations.size(); op++) {
            String key = operations.getString(op);
            if (!key.isBlank() && key.length() <= 96) project.restoreOperation(key);
        }
        CapitalProjectManager.restore(project);
    }

    private static String financingReferenceFailure(CompoundTag tag, CapitalFundingSource source,
                                                     UUID companyId, long budget) {
        CapitalProjectStatus savedStatus = NbtDataSupport.safeEnum(CapitalProjectStatus.class,
                tag.getString("Status"), CapitalProjectStatus.DRAFT);
        boolean fundingStarted = tag.getLong("Funded") > 0 || tag.getBoolean("FundingSettled")
                || savedStatus == CapitalProjectStatus.FUNDING
                || savedStatus == CapitalProjectStatus.FUNDED
                || savedStatus == CapitalProjectStatus.MATERIALS_PENDING
                || savedStatus == CapitalProjectStatus.READY
                || savedStatus == CapitalProjectStatus.FAILED_RECOVERABLE
                || NbtDataSupport.readUuidOrNull(tag, "Loan") != null
                || NbtDataSupport.readUuidOrNull(tag, "Bond") != null
                || NbtDataSupport.readUuidOrNull(tag, "Financing") != null;
        if (!fundingStarted) return "";
        if (source == CapitalFundingSource.RETAINED_EARNINGS) return "";
        if (source == CapitalFundingSource.COMMERCIAL_LOAN) {
            UUID loanId = NbtDataSupport.readUuidOrNull(tag, "Loan");
            UUID bankId = NbtDataSupport.readUuidOrNull(tag, "Bank");
            CompanyLoan loan = loanId == null ? null : CompanyLoanManager.loans().get(loanId);
            if (loan == null) return "finance.capital_project.loan_missing";
            return companyId.equals(loan.companyId()) && loan.lenderType() == LoanLenderType.COMMERCIAL_BANK
                    && bankId != null && bankId.equals(loan.lenderId()) && loan.originalPrincipal() == budget
                    ? "" : "finance.capital_project.loan_mismatch";
        }
        if (source == CapitalFundingSource.CORPORATE_BOND) {
            UUID bondId = NbtDataSupport.readUuidOrNull(tag, "Bond");
            CorporateBond bond = bondId == null ? null : CorporateBondManager.bonds().get(bondId);
            if (bond == null) return "finance.capital_project.bond_missing";
            return companyId.equals(bond.companyId()) ? "" : "finance.capital_project.bond_mismatch";
        }
        UUID financingId = NbtDataSupport.readUuidOrNull(tag, "Financing");
        if (financingId == null) return "finance.capital_project.share_issue_missing";
        CompanyFinancingProject active = CompanyFinancingManager.getProject(financingId);
        CompanyFinancingManager.FinalizedFinancing finalized = CompanyFinancingManager.getFinalized(financingId);
        if (active == null && finalized == null) return "finance.capital_project.share_issue_missing";
        if (active != null) return companyId.equals(active.getCompanyId()) && active.getFundingTarget() == budget
                ? "" : "finance.capital_project.share_issue_mismatch";
        return companyId.equals(finalized.companyId()) && finalized.raisedAmount() >= budget
                ? "" : "finance.capital_project.share_issue_mismatch";
    }

    private static Map<Item, Integer> readMaterials(ListTag list) {
        Map<Item, Integer> materials = new LinkedHashMap<>();
        for (int i = 0; i < Math.min(list.size(), MAX_MATERIALS); i++) {
            CompoundTag row = list.getCompound(i);
            ResourceLocation id = ResourceLocation.tryParse(row.getString("Item"));
            int amount = row.getInt("Amount");
            if (id == null || amount <= 0 || !BuiltInRegistries.ITEM.containsKey(id)) return Map.of();
            Item item = BuiltInRegistries.ITEM.get(id);
            if (item == null || item == Items.AIR || materials.putIfAbsent(item, amount) != null) return Map.of();
        }
        return Map.copyOf(materials);
    }

    private static void putUuid(CompoundTag tag, String key, UUID value) {
        if (value != null) tag.putUUID(key, value);
    }
}
