package finance.gameplay.company.capital;

import finance.account.AccountManager;
import finance.account.TransactionRecord;
import finance.account.TransactionType;
import finance.commodity.CommodityInventoryManager;
import finance.company.Company;
import finance.company.CompanyManager;
import finance.company.CompanyProposal;
import finance.company.CompanyProposalManager;
import finance.company.CompanyProposalType;
import finance.config.FinanceConfig;
import finance.data.EconomySavedData;
import finance.diagnostic.ModuleHealthRegistry;
import finance.gameplay.company.CompanyFacilityRecord;
import finance.gameplay.company.CompanyFacilityManager;
import finance.gameplay.company.CompanyInventoryFacade;
import finance.gameplay.company.CompanyMembershipService;
import finance.gameplay.company.CompanyPermission;
import finance.gameplay.company.CompanyUpgradeRequirementService;
import finance.gameplay.company.CompanyUpgradeService;
import finance.money.MoneyEndpoints;
import finance.money.MoneyTransferResult;
import finance.money.MoneyTransferService;
import finance.warehouse.CommodityItemResolver;
import finance.warehouse.PhysicalMaterialTransaction;
import finance.warehouse.WarehouseManager;
import finance.warehouse.WarehouseRecord;
import finance.warehouse.WarehouseUpgradeRequirementService;
import finance.warehouse.WarehouseUpgradeService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Authoritative state machine for world-backed company capital projects. */
public final class CapitalProjectService {
    // 36 UUID chars + ":capital:" + 48 client chars stays within the persisted 96-char key bound.
    private static final int MAX_OPERATION_KEY_LENGTH = 48;

    private CapitalProjectService() {}

    public static synchronized CapitalProjectActionResult create(ServerPlayer player, UUID companyId,
                                                                  WorldCapitalProjectType type, UUID targetId,
                                                                  CapitalFundingSource source, long day,
                                                                  String operationKey) {
        if (!moduleAvailable() || player == null || companyId == null || type == null || targetId == null
                || source == null || day < 0 || !validKey(operationKey))
            return fail("finance.capital_project.invalid_request");
        Company company = CompanyManager.getCompany(companyId);
        if (company == null || company.isBankruptcyRisk()
                || !CompanyMembershipService.hasPermission(companyId, player.getUUID(), CompanyPermission.SPEND_COMPANY_CASH)
                || !CompanyMembershipService.hasPermission(companyId, player.getUUID(), CompanyPermission.MANAGE_PRODUCTION))
            return fail("finance.capital_project.no_permission");

        String replayKey = scoped(player.getUUID(), operationKey);
        for (WorldCapitalProject existing : CapitalProjectManager.forCompany(companyId)) {
            if (existing.hasOperation(replayKey)) return CapitalProjectActionResult.ok(existing.projectId(),
                    "finance.capital_project.duplicate_operation");
        }

        FrozenRequirement requirement = requirementFor(player, company, type, targetId);
        if (requirement == null) return fail("finance.capital_project.invalid_target");
        if (CapitalProjectManager.activeCountForCompany(companyId) >= CapitalProjectManager.MAX_ACTIVE_PER_COMPANY
                || CapitalProjectManager.hasActiveForTarget(targetId))
            return fail("finance.capital_project.project_limit");

        boolean governance = company.isPublic()
                || requirement.budget() >= FinanceConfig.capitalProjectGovernanceThreshold();
        long duration = Math.max(7, FinanceConfig.capitalProjectMaxDurationDays());
        long deadline;
        try {
            deadline = Math.addExact(day, duration);
        } catch (ArithmeticException overflow) {
            return fail("finance.capital_project.invalid_request");
        }
        CapitalProjectStatus initial = governance
                ? CapitalProjectStatus.AUTHORIZATION_REQUIRED : CapitalProjectStatus.DRAFT;
        WorldCapitalProject project = new WorldCapitalProject(UUID.randomUUID(), companyId, type,
                targetId, player.getUUID(), day, deadline, requirement.targetLevel(), source,
                requirement.budget(), requirement.materials(), governance, initial, day);
        if (!CapitalProjectManager.register(project)) return fail("finance.capital_project.project_limit");
        project.recordOperation(replayKey);
        refreshIndicator(player, project);
        EconomySavedData.markDirty();
        return CapitalProjectActionResult.ok(project.projectId(), governance
                ? "finance.capital_project.authorization_required" : "finance.capital_project.created");
    }

    public static synchronized CapitalProjectActionResult authorize(UUID actor, UUID projectId,
                                                                     UUID proposalId, long day,
                                                                     String operationKey) {
        CapitalProjectActionResult replay = replayed(actor, projectId, operationKey);
        if (replay != null) return replay;
        WorldCapitalProject project = mutableProject(actor, projectId, operationKey,
                CapitalProjectStatus.AUTHORIZATION_REQUIRED);
        if (project == null) return fail("finance.capital_project.authorization_denied");
        CompanyProposal proposal = CompanyProposalManager.getProposal(proposalId);
        if (proposal == null || !proposal.isExecutableAuthorization(CompanyProposalType.CAPITAL_PROJECT,
                project.companyId()) || proposal.getValue1() != project.budget()
                || proposal.getValue2() != project.type().ordinal())
            return fail("finance.capital_project.authorization_invalid");
        if (!CompanyProposalManager.markExecuted(proposalId, "资本项目授权已由项目 " + project.projectId() + " 消费"))
            return fail("finance.capital_project.authorization_invalid");
        project.setProposalId(proposalId);
        project.setStatus(CapitalProjectStatus.DRAFT, day);
        project.setFailureKey("");
        project.recordOperation(scoped(actor, operationKey));
        EconomySavedData.markDirty();
        return CapitalProjectActionResult.ok(projectId, "finance.capital_project.authorized");
    }

    public static synchronized CapitalProjectActionResult propose(UUID actor, UUID projectId, long day,
                                                                   String operationKey) {
        CapitalProjectActionResult replay = replayed(actor, projectId, operationKey);
        if (replay != null) return replay;
        WorldCapitalProject project = mutableProject(actor, projectId, operationKey,
                CapitalProjectStatus.AUTHORIZATION_REQUIRED);
        if (project == null || day < 0 || day > (project == null ? -1 : project.deadlineDay()))
            return fail("finance.capital_project.authorization_denied");
        Company company = CompanyManager.getCompany(project.companyId());
        if (company == null) return fail("finance.capital_project.company_unavailable");
        String replayKey = scoped(actor, operationKey);
        if (!company.isPublic()) {
            if (!company.getOwnerId().equals(actor)) return fail("finance.capital_project.authorization_denied");
            project.setStatus(CapitalProjectStatus.DRAFT, day);
            project.recordOperation(replayKey);
            EconomySavedData.markDirty();
            return CapitalProjectActionResult.ok(projectId, "finance.capital_project.owner_authorized");
        }
        if (project.proposalId() != null) return CapitalProjectActionResult.ok(projectId,
                "finance.capital_project.proposal_exists");
        long endDay;
        try { endDay = Math.addExact(day, 3); }
        catch (ArithmeticException overflow) { return fail("finance.capital_project.authorization_invalid"); }
        CompanyProposalManager.Result result = CompanyProposalManager.createProposal(actor, project.companyId(),
                CompanyProposalType.CAPITAL_PROJECT, project.projectId().toString(), project.budget(),
                project.type().ordinal(), 0, day, endDay, 0.60D);
        if (!result.success()) return fail("finance.capital_project.proposal_failed");
        List<CompanyProposal> proposals = CompanyProposalManager.getProposalsForCompany(project.companyId());
        if (proposals.isEmpty()) return fail("finance.capital_project.proposal_failed");
        CompanyProposal created = proposals.get(proposals.size() - 1);
        if (created.getType() != CompanyProposalType.CAPITAL_PROJECT
                || created.getValue1() != project.budget() || created.getValue2() != project.type().ordinal())
            return fail("finance.capital_project.proposal_failed");
        project.setProposalId(created.getProposalId());
        project.recordOperation(replayKey);
        EconomySavedData.markDirty();
        return CapitalProjectActionResult.ok(projectId, "finance.capital_project.proposal_created");
    }

    public static synchronized CapitalProjectActionResult startFunding(UUID actor, UUID projectId,
                                                                        UUID bankId, long day,
                                                                        String operationKey) {
        CapitalProjectActionResult replay = replayed(actor, projectId, operationKey);
        if (replay != null) return replay;
        WorldCapitalProject project = mutableProject(actor, projectId, operationKey, CapitalProjectStatus.DRAFT);
        if (project == null || day < 0 || day > (project == null ? -1 : project.deadlineDay()))
            return fail("finance.capital_project.funding_denied");
        Company company = CompanyManager.getCompany(project.companyId());
        if (company == null || company.isBankruptcyRisk()) return fail("finance.capital_project.company_unavailable");
        CapitalProjectActionResult result = adapter(project.fundingSource()).initiate(project, company, bankId, day);
        if (!result.success()) {
            project.setFailureKey(result.messageKey());
            EconomySavedData.markDirty();
            return result;
        }
        if (project.fundingSettled() && project.fundedAmount() == project.budget()) {
            project.setStatus(CapitalProjectStatus.MATERIALS_PENDING, day);
        } else {
            project.setStatus(CapitalProjectStatus.FUNDING, day);
        }
        project.setFailureKey("");
        project.recordOperation(scoped(actor, operationKey));
        EconomySavedData.markDirty();
        return CapitalProjectActionResult.ok(projectId, project.status() == CapitalProjectStatus.FUNDING
                ? "finance.capital_project.funding_started" : "finance.capital_project.materials_pending");
    }

    public static synchronized void processDay(long day) {
        if (!moduleAvailable() || day < 0) return;
        boolean changed = false;
        for (WorldCapitalProject project : CapitalProjectManager.projects().values()) {
            if (project.status().terminal() || project.status() == CapitalProjectStatus.FAILED_RECOVERABLE) continue;
            if (day > project.deadlineDay()) {
                failRecoverably(project, "finance.capital_project.deadline_expired", day);
                changed = true;
                continue;
            }
            if (project.status() != CapitalProjectStatus.FUNDING) continue;
            Company company = CompanyManager.getCompany(project.companyId());
            if (company == null) {
                failRecoverably(project, "finance.capital_project.company_missing", day);
                changed = true;
                continue;
            }
            CapitalFundingAdapter.FundingSync sync = adapter(project.fundingSource()).sync(project, company, day);
            if (sync.state() == CapitalFundingAdapter.SyncState.FUNDED) {
                project.setStatus(CapitalProjectStatus.MATERIALS_PENDING, day);
                project.setFailureKey("");
                changed = true;
            } else if (sync.state() == CapitalFundingAdapter.SyncState.FAILED) {
                failRecoverably(project, sync.messageKey(), day);
                changed = true;
            }
        }
        if (changed) EconomySavedData.markDirty();
    }

    public static synchronized CapitalProjectActionResult execute(ServerPlayer player, UUID projectId,
                                                                   long day, String operationKey) {
        if (!moduleAvailable() || player == null || day < 0 || !validKey(operationKey))
            return fail("finance.capital_project.invalid_request");
        WorldCapitalProject project = CapitalProjectManager.get(projectId);
        if (project == null) return fail("finance.capital_project.missing");
        String replayKey = scoped(player.getUUID(), operationKey);
        if (project.hasOperation(replayKey)) return CapitalProjectActionResult.ok(projectId,
                project.status() == CapitalProjectStatus.COMPLETED
                        ? "finance.capital_project.completed" : "finance.capital_project.duplicate_operation");
        if (day > project.deadlineDay()) {
            failRecoverably(project, "finance.capital_project.deadline_expired", day);
            EconomySavedData.markDirty();
            return fail("finance.capital_project.deadline_expired");
        }
        if (project.status() != CapitalProjectStatus.MATERIALS_PENDING
                && project.status() != CapitalProjectStatus.READY)
            return fail("finance.capital_project.not_ready");
        Company company = CompanyManager.getCompany(project.companyId());
        if (company == null || company.isBankruptcyRisk()
                || !CompanyMembershipService.hasPermission(project.companyId(), player.getUUID(),
                CompanyPermission.MANAGE_PRODUCTION)) return fail("finance.capital_project.no_permission");
        if (!validPhysicalTarget(player, project)) return fail("finance.capital_project.invalid_target");
        finance.account.Account escrowAccount = AccountManager.getOrCreateSystemAccount(project.escrowAccountId());
        if (escrowAccount.getBalance() != project.budget()
                || project.fundedAmount() != project.budget()) {
            failRecoverably(project, "finance.capital_project.escrow_mismatch", day);
            EconomySavedData.markDirty();
            return fail("finance.capital_project.escrow_mismatch");
        }

        MaterialTransaction material = planMaterials(player, company, project.materials());
        if (material == null) {
            project.setStatus(CapitalProjectStatus.MATERIALS_PENDING, day);
            project.setFailureKey("finance.capital_project.materials_insufficient");
            EconomySavedData.markDirty();
            return fail("finance.capital_project.materials_insufficient");
        }
        project.setStatus(CapitalProjectStatus.READY, day);
        if (!material.commit(player)) {
            project.setStatus(CapitalProjectStatus.MATERIALS_PENDING, day);
            project.setFailureKey("finance.capital_project.materials_changed");
            return fail("finance.capital_project.materials_changed");
        }
        if (!AccountManager.withdraw(project.escrowAccountId(), project.budget())) {
            compensateMaterials(player, material);
            failRecoverably(project, "finance.capital_project.escrow_changed", day);
            EconomySavedData.markDirty();
            return fail("finance.capital_project.escrow_changed");
        }
        String facilityOperation = "capital:" + project.projectId();
        if (!commitUpgrade(player, project, facilityOperation)) {
            if (!AccountManager.deposit(project.escrowAccountId(), project.budget())
                    || !compensateMaterials(player, material))
                throw new IllegalStateException("capital project compensation failed");
            project.setStatus(CapitalProjectStatus.MATERIALS_PENDING, day);
            project.setFailureKey("finance.capital_project.target_changed");
            EconomySavedData.markDirty();
            return fail("finance.capital_project.target_changed");
        }
        project.setFundedAmount(0);
        project.setStatus(CapitalProjectStatus.COMPLETED, day);
        project.setFailureKey("");
        project.recordOperation(replayKey);
        AccountManager.addTransactionRecord(new TransactionRecord(company.getCompanyId(), project.targetId(),
                project.budget(), TransactionType.CAPITAL_PROJECT_COMPLETE, player.getUUID(),
                company.getName() + "/" + project.type().name(), project.targetLevel()));
        finance.tutorial.TutorialProgressService.record(player, "capital_project_complete");
        refreshIndicator(player, project);
        playCompletionFeedback(player, project);
        EconomySavedData.markDirty();
        return CapitalProjectActionResult.ok(projectId, "finance.capital_project.completed");
    }

    /** Resume only a fully funded project whose escrow can be proven intact. */
    public static synchronized CapitalProjectActionResult recover(UUID actor, UUID projectId, long day,
                                                                   String operationKey) {
        CapitalProjectActionResult replay = replayed(actor, projectId, operationKey);
        if (replay != null) return replay;
        WorldCapitalProject project = mutableProject(actor, projectId, operationKey,
                CapitalProjectStatus.FAILED_RECOVERABLE);
        if (project == null || day < 0) return fail("finance.capital_project.recovery_denied");
        if (day > project.deadlineDay()) return fail("finance.capital_project.deadline_expired");
        Company company = CompanyManager.getCompany(project.companyId());
        if (company == null || company.isBankruptcyRisk())
            return fail("finance.capital_project.company_unavailable");
        if (!referenceMatches(project)) return fail("finance.capital_project.reference_missing");
        long escrow = AccountManager.getOrCreateSystemAccount(project.escrowAccountId()).getBalance();
        if (escrow != project.budget() || project.fundedAmount() != project.budget())
            return fail("finance.capital_project.recovery_escrow_required");
        project.setFundingSettled(true);
        project.setStatus(CapitalProjectStatus.MATERIALS_PENDING, day);
        project.setFailureKey("");
        project.recordOperation(scoped(actor, operationKey));
        EconomySavedData.markDirty();
        return CapitalProjectActionResult.ok(projectId, "finance.capital_project.recovered");
    }

    public static synchronized CapitalProjectActionResult cancel(UUID actor, UUID projectId, long day,
                                                                  String operationKey) {
        return cancel(actor, null, projectId, day, operationKey);
    }

    public static synchronized CapitalProjectActionResult cancel(ServerPlayer player, UUID projectId, long day,
                                                                  String operationKey) {
        return cancel(player == null ? null : player.getUUID(), player, projectId, day, operationKey);
    }

    private static CapitalProjectActionResult cancel(UUID actor, ServerPlayer player, UUID projectId, long day,
                                                      String operationKey) {
        if (!moduleAvailable() || !validKey(operationKey)) return fail("finance.capital_project.invalid_request");
        WorldCapitalProject project = CapitalProjectManager.get(projectId);
        Company company = project == null ? null : CompanyManager.getCompany(project.companyId());
        if (project == null || company == null || !mayManage(project.companyId(), actor))
            return fail("finance.capital_project.cancel_denied");
        String key = scoped(actor, operationKey);
        if (project.hasOperation(key)) return CapitalProjectActionResult.ok(projectId,
                "finance.capital_project.duplicate_operation");
        if (project.status().terminal()) return fail("finance.capital_project.cancel_denied");
        long escrow = AccountManager.getOrCreateSystemAccount(project.escrowAccountId()).getBalance();
        if (escrow < 0 || escrow > project.budget() || escrow != project.fundedAmount()) {
            failRecoverably(project, "finance.capital_project.escrow_mismatch", day);
            EconomySavedData.markDirty();
            return fail("finance.capital_project.escrow_mismatch");
        }
        if (escrow > 0) {
            MoneyTransferResult refund = MoneyTransferService.transfer(MoneyEndpoints.account(project.escrowAccountId()),
                    MoneyEndpoints.company(company), escrow);
            if (!refund.success()) {
                failRecoverably(project, "finance.capital_project.refund_failed", day);
                EconomySavedData.markDirty();
                return fail("finance.capital_project.refund_failed");
            }
            AccountManager.addTransactionRecord(new TransactionRecord(project.escrowAccountId(), company.getCompanyId(),
                    escrow, TransactionType.CAPITAL_PROJECT_REFUND, actor, company.getName(), 1));
        }
        project.setFundedAmount(0);
        project.setStatus(CapitalProjectStatus.CANCELLED, day);
        project.setFailureKey("");
        project.recordOperation(key);
        refreshIndicator(player, project);
        EconomySavedData.markDirty();
        return CapitalProjectActionResult.ok(projectId, "finance.capital_project.cancelled");
    }

    private static FrozenRequirement requirementFor(ServerPlayer player, Company company,
                                                     WorldCapitalProjectType type, UUID targetId) {
        if (type == WorldCapitalProjectType.WAREHOUSE_UPGRADE) {
            WarehouseRecord record = WarehouseManager.get(targetId);
            if (record == null) return null;
            WarehouseUpgradeRequirementService.Requirement requirement =
                    WarehouseUpgradeRequirementService.requirement(record.tier());
            if (requirement == null || WarehouseUpgradeService.validCapitalTarget(player, targetId,
                    company.getCompanyId(), requirement.targetTier().level()) == null) return null;
            return new FrozenRequirement(requirement.cash(), requirement.targetTier().level(), requirement.materials());
        }
        CompanyFacilityRecord facility = CompanyFacilityManager.get(targetId);
        if (facility == null) return null;
        CompanyUpgradeRequirementService.Requirement requirement = CompanyUpgradeRequirementService.requirement(
                company.getType(), facility.type(), facility.productionLevel());
        int targetLevel = facility.productionLevel() + 1;
        if (requirement == null || CompanyUpgradeService.validCapitalTarget(player, targetId,
                company.getCompanyId(), targetLevel) == null) return null;
        return new FrozenRequirement(requirement.cash(), targetLevel, requirement.materials());
    }

    private static boolean validPhysicalTarget(ServerPlayer player, WorldCapitalProject project) {
        return project.type() == WorldCapitalProjectType.WAREHOUSE_UPGRADE
                ? WarehouseUpgradeService.validCapitalTarget(player, project.targetId(), project.companyId(),
                project.targetLevel()) != null
                : CompanyUpgradeService.validCapitalTarget(player, project.targetId(), project.companyId(),
                project.targetLevel()) != null;
    }

    private static boolean referenceMatches(WorldCapitalProject project) {
        if (project.type() == WorldCapitalProjectType.WAREHOUSE_UPGRADE) {
            WarehouseRecord record = WarehouseManager.get(project.targetId());
            return record != null && project.companyId().equals(record.companyId())
                    && record.tier().level() + 1 == project.targetLevel();
        }
        CompanyFacilityRecord facility = CompanyFacilityManager.get(project.targetId());
        return facility != null && project.companyId().equals(facility.companyId())
                && facility.productionLevel() + 1 == project.targetLevel();
    }

    private static boolean commitUpgrade(ServerPlayer player, WorldCapitalProject project, String operationKey) {
        return project.type() == WorldCapitalProjectType.WAREHOUSE_UPGRADE
                ? WarehouseUpgradeService.commitCapitalUpgrade(player, project.targetId(), project.companyId(),
                project.targetLevel(), operationKey)
                : CompanyUpgradeService.commitCapitalUpgrade(player, project.targetId(), project.companyId(),
                project.targetLevel(), operationKey);
    }

    private static void refreshIndicator(ServerPlayer player, WorldCapitalProject project) {
        if (project == null) return;
        if (project.type() == WorldCapitalProjectType.WAREHOUSE_UPGRADE) {
            WarehouseRecord record = WarehouseManager.get(project.targetId());
            if (record != null && player != null)
                finance.block.WarehouseControllerBlock.updateIndicator(player.serverLevel(), record.blockPos(),
                        record.warehouseId());
        } else {
            CompanyFacilityRecord facility = CompanyFacilityManager.get(project.targetId());
            if (facility != null && player != null)
                finance.block.CompanyFactoryControllerBlock.updateIndicator(player.serverLevel(), facility.blockPos(),
                        facility.facilityId());
        }
    }

    private static void playCompletionFeedback(ServerPlayer player, WorldCapitalProject project) {
        net.minecraft.core.BlockPos pos = project.type() == WorldCapitalProjectType.WAREHOUSE_UPGRADE
                ? WarehouseManager.get(project.targetId()).blockPos()
                : CompanyFacilityManager.get(project.targetId()).blockPos();
        player.serverLevel().playSound(null, pos, net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP,
                net.minecraft.sounds.SoundSource.BLOCKS, 0.8F, 1.1F);
        player.serverLevel().sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER,
                pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D,
                12, 0.35D, 0.35D, 0.35D, 0.02D);
    }

    private static MaterialTransaction planMaterials(ServerPlayer player, Company company, Map<Item, Integer> materials) {
        Map<String, Integer> custody = new LinkedHashMap<>();
        Map<Item, Integer> inventory = new LinkedHashMap<>();
        UUID custodyId = CompanyInventoryFacade.custodyId(company.getCompanyId());
        for (Map.Entry<Item, Integer> entry : materials.entrySet()) {
            String commodityId = CommodityItemResolver.commodityId(entry.getKey());
            if (commodityId == null) inventory.merge(entry.getKey(), entry.getValue(), Math::addExact);
            else custody.merge(commodityId, entry.getValue(), Math::addExact);
        }
        for (Map.Entry<String, Integer> entry : custody.entrySet()) {
            if (CommodityInventoryManager.getCommodityAmount(custodyId, entry.getKey()) < entry.getValue()) return null;
        }
        List<finance.warehouse.InventoryTransactionService.RemovalPlan> plans = inventory.isEmpty()
                ? List.of() : PhysicalMaterialTransaction.plan(player, inventory);
        return plans == null ? null : new MaterialTransaction(custodyId, Map.copyOf(custody), plans);
    }

    private static boolean compensateMaterials(ServerPlayer player, MaterialTransaction transaction) {
        return transaction.rollback(player);
    }

    private static CapitalFundingAdapter adapter(CapitalFundingSource source) {
        return switch (source) {
            case RETAINED_EARNINGS -> RetainedEarningsFunding.INSTANCE;
            case COMMERCIAL_LOAN -> CommercialLoanFunding.INSTANCE;
            case CORPORATE_BOND -> CorporateBondFunding.INSTANCE;
            case SHARE_ISSUE -> ShareIssueFunding.INSTANCE;
        };
    }

    private static WorldCapitalProject mutableProject(UUID actor, UUID projectId, String key,
                                                       CapitalProjectStatus required) {
        if (!moduleAvailable() || !validKey(key)) return null;
        WorldCapitalProject project = CapitalProjectManager.get(projectId);
        if (project == null || project.status() != required || !mayManage(project.companyId(), actor)
                || project.hasOperation(scoped(actor, key))) return null;
        return project;
    }

    private static CapitalProjectActionResult replayed(UUID actor, UUID projectId, String key) {
        if (!moduleAvailable() || !validKey(key)) return null;
        WorldCapitalProject project = CapitalProjectManager.get(projectId);
        if (project == null || !mayManage(project.companyId(), actor)) return null;
        return project.hasOperation(scoped(actor, key))
                ? CapitalProjectActionResult.ok(projectId, "finance.capital_project.duplicate_operation") : null;
    }

    private static boolean mayManage(UUID companyId, UUID actor) {
        return actor != null && CompanyMembershipService.hasPermission(companyId, actor,
                CompanyPermission.SPEND_COMPANY_CASH);
    }

    private static boolean moduleAvailable() {
        return ModuleHealthRegistry.mayWrite(ModuleHealthRegistry.Module.COMPANY_GAMEPLAY);
    }

    private static boolean validKey(String key) {
        return key != null && !key.isBlank() && key.length() <= MAX_OPERATION_KEY_LENGTH;
    }

    private static String scoped(UUID actor, String key) {
        return actor + ":capital:" + key;
    }

    private static CapitalProjectActionResult fail(String key) {
        return CapitalProjectActionResult.fail(key);
    }

    private static void failRecoverably(WorldCapitalProject project, String key, long day) {
        project.setStatus(CapitalProjectStatus.FAILED_RECOVERABLE, day);
        project.setFailureKey(key);
    }

    private record FrozenRequirement(long budget, int targetLevel, Map<Item, Integer> materials) {}

    private record MaterialTransaction(UUID custodyId, Map<String, Integer> custody,
                                       List<finance.warehouse.InventoryTransactionService.RemovalPlan> playerPlans) {
        boolean commit(ServerPlayer player) {
            Map<String, Integer> removed = new LinkedHashMap<>();
            for (Map.Entry<String, Integer> entry : custody.entrySet()) {
                if (!CommodityInventoryManager.removeCommodity(custodyId, entry.getKey(), entry.getValue())) {
                    removed.forEach((id, amount) -> CommodityInventoryManager.addCommodity(custodyId, id, amount));
                    return false;
                }
                removed.put(entry.getKey(), entry.getValue());
            }
            if (!PhysicalMaterialTransaction.commit(player, playerPlans)) {
                removed.forEach((id, amount) -> CommodityInventoryManager.addCommodity(custodyId, id, amount));
                return false;
            }
            return true;
        }

        boolean rollback(ServerPlayer player) {
            for (Map.Entry<String, Integer> entry : custody.entrySet()) {
                if (!CommodityInventoryManager.canAddCommodity(custodyId, entry.getKey(), entry.getValue())) return false;
            }
            boolean playerRestored = PhysicalMaterialTransaction.rollback(player, playerPlans);
            boolean custodyRestored = true;
            for (Map.Entry<String, Integer> entry : custody.entrySet())
                custodyRestored &= CommodityInventoryManager.addCommodity(custodyId, entry.getKey(), entry.getValue());
            return playerRestored && custodyRestored;
        }
    }
}
