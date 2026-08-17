package finance.data.serializer;

import finance.commodity.CommodityRegistry;
import finance.company.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Persists company state, financing subscriptions and shareholder proposals. */
public final class CompanyDataSerializer {

    private CompanyDataSerializer() {
    }

    public static void save(CompoundTag tag) {
        ListTag financingProjectsTag = new ListTag();
        for (CompanyFinancingProject project : CompanyFinancingManager.getProjects()) {
            CompoundTag projectTag = new CompoundTag();
            projectTag.putUUID("ProjectId", project.getProjectId());
            projectTag.putUUID("CompanyUUID", project.getCompanyId());
            projectTag.putString("Symbol", project.getSymbol());
            projectTag.putLong("IssueQuantity", project.getIssueQuantity());
            projectTag.putLong("IssuePrice", project.getIssuePrice());
            projectTag.putLong("FundingTarget", project.getFundingTarget());
            projectTag.putLong("DeadlineMcDay", project.getDeadlineMcDay());
            projectTag.putLong("CreatedAt", project.getCreatedAt().toEpochSecond(ZoneOffset.UTC));
            ListTag subscriptionsTag = new ListTag();
            for (Map.Entry<UUID, Long> entry : project.getSubscriptions().entrySet()) {
                CompoundTag subscriptionTag = new CompoundTag();
                subscriptionTag.putUUID("PlayerUUID", entry.getKey());
                subscriptionTag.putLong("Quantity", entry.getValue());
                subscriptionsTag.add(subscriptionTag);
            }
            projectTag.put("Subscriptions", subscriptionsTag);
            financingProjectsTag.add(projectTag);
        }
        tag.put("CompanyFinancingProjects", financingProjectsTag);

        ListTag proposalsTag = new ListTag();
        for (CompanyProposal proposal : CompanyProposalManager.getProposals()) {
            CompoundTag proposalTag = new CompoundTag();
            proposalTag.putUUID("ProposalId", proposal.getProposalId());
            proposalTag.putUUID("CompanyUUID", proposal.getCompanyId());
            proposalTag.putUUID("CreatorUUID", proposal.getCreatorId());
            proposalTag.putString("Type", proposal.getType().name());
            proposalTag.putString("Title", proposal.getTitle());
            proposalTag.putString("TextValue", proposal.getTextValue());
            proposalTag.putLong("Value1", proposal.getValue1());
            proposalTag.putLong("Value2", proposal.getValue2());
            proposalTag.putLong("Value3", proposal.getValue3());
            proposalTag.putLong("StartMcDay", proposal.getStartMcDay());
            proposalTag.putLong("EndMcDay", proposal.getEndMcDay());
            proposalTag.putDouble("PassRatio", proposal.getPassRatio());
            proposalTag.putDouble("MinParticipationRatio", proposal.getMinParticipationRatio());
            proposalTag.putLong("VotingSharesSnapshot", proposal.getVotingSharesSnapshot());
            proposalTag.putLong("CreatedAt", proposal.getCreatedAt().toEpochSecond(ZoneOffset.UTC));
            proposalTag.putString("Status", proposal.getStatus().name());
            proposalTag.putString("ResultSummary", proposal.getResultSummary());
            ListTag votesTag = new ListTag();
            for (Map.Entry<UUID, CompanyProposal.VoteRecord> entry : proposal.getVotes().entrySet()) {
                CompoundTag voteTag = new CompoundTag();
                voteTag.putUUID("PlayerUUID", entry.getKey());
                voteTag.putBoolean("Support", entry.getValue().support());
                voteTag.putLong("Power", entry.getValue().power());
                votesTag.add(voteTag);
            }
            proposalTag.put("Votes", votesTag);
            ListTag snapshotTag=new ListTag();
            for(Map.Entry<UUID,Long> entry:proposal.getVotingPowerSnapshot().entrySet()){CompoundTag row=new CompoundTag();row.putUUID("HolderUUID",entry.getKey());row.putLong("Power",entry.getValue());snapshotTag.add(row);}
            proposalTag.put("VotingPowerSnapshot",snapshotTag);
            proposalsTag.add(proposalTag);
        }
        tag.put("CompanyProposals", proposalsTag);

        ListTag companiesTag = new ListTag();

        for (Company company : CompanyManager.getCompanies()) {
            CompoundTag companyTag = new CompoundTag();
            companyTag.putUUID("CompanyUUID", company.getCompanyId());
            companyTag.putString("Name", company.getName());
            companyTag.putString("Type", company.getType().name());
            companyTag.putLong("Cash", company.getCash());
            if (company.getOwnerId() != null) {
                companyTag.putUUID("OwnerUUID", company.getOwnerId());
            }

            CompoundTag inventoryTag = new CompoundTag();
            for (Map.Entry<String, Integer> entry : company.getInventory().entrySet()) {
                inventoryTag.putInt(entry.getKey(), entry.getValue());
            }
            companyTag.put("Inventory", inventoryTag);

            // P3：保存盈利和分红字段
            companyTag.putLong("DailyRevenue", company.getDailyRevenue());
            companyTag.putLong("DailyCost", company.getDailyCost());
            companyTag.putLong("RetainedEarnings", company.getRetainedEarnings());
            companyTag.putLong("DistributableProfit", company.getDistributableProfit());
            companyTag.putLong("LastDividendDay", company.getLastDividendDay());
            companyTag.putDouble("CompanyDividendRatio", company.getDividendRatio());
            companyTag.putInt("CompanyDividendCycleDays", company.getDividendCycleDays());
            companyTag.putString("Strategy", company.getStrategy().name());
            companyTag.putInt("ProductionLevel", company.getProductionLevel());
            companyTag.putInt("StorageLevel", company.getStorageLevel());
            companyTag.putInt("ManagementLevel", company.getManagementLevel());
            companyTag.putDouble("AutoSellRatio", company.getAutoSellRatio());
            companyTag.putBoolean("BankruptcyRisk", company.isBankruptcyRisk());
            companyTag.putLong("BankruptcyRiskStartDay", company.getBankruptcyRiskStartDay());
            ListTag recentProfitsTag = new ListTag();
            for (Long profit : company.getRecentProfits()) {
                CompoundTag profitTag = new CompoundTag();
                profitTag.putLong("Profit", profit);
                recentProfitsTag.add(profitTag);
            }
            companyTag.put("RecentProfits", recentProfitsTag);
            ListTag dividendHistoryTag = new ListTag();
            for (Company.DividendRecord record : company.getDividendHistory()) {
                CompoundTag recordTag = new CompoundTag();
                recordTag.putLong("McDay", record.mcDay());
                recordTag.putLong("TotalAmount", record.totalAmount());
                recordTag.putLong("PerShare", record.perShare());
                dividendHistoryTag.add(recordTag);
            }
            companyTag.put("DividendHistory", dividendHistoryTag);
            ListTag financialReportsTag = new ListTag();
            for (CompanyFinancialReport report : company.getFinancialReports()) {
                CompoundTag reportTag = new CompoundTag();
                reportTag.putLong("McDay", report.mcDay());
                reportTag.putLong("Revenue", report.revenue());
                reportTag.putLong("Expenses", report.expenses());
                reportTag.putLong("NetProfit", report.netProfit());
                reportTag.putLong("Assets", report.assets());
                reportTag.putLong("Liabilities", report.liabilities());
                reportTag.putLong("CashBalance", report.cashBalance());
                reportTag.putLong("AssetChange", report.assetChange());
                reportTag.putLong("ProfitChange", report.profitChange());
                reportTag.putString("Summary", report.summary());
                reportTag.putLong("CreatedAt", report.createdAt().toEpochSecond(ZoneOffset.UTC));
                financialReportsTag.add(reportTag);
            }
            companyTag.put("FinancialReports", financialReportsTag);

            // P4：保存上市状态
            companyTag.putBoolean("IsPublic", company.isPublic());

            companiesTag.add(companyTag);
        }

        tag.put("Companies", companiesTag);

    }

    public static void load(CompoundTag tag) {
        if (tag.contains("CompanyFinancingProjects")) {
            ListTag projectsTag = tag.getList("CompanyFinancingProjects", Tag.TAG_COMPOUND);
            for (Tag rawTag : projectsTag) {
                CompoundTag projectTag = (CompoundTag) rawTag;
                UUID projectId = NbtDataSupport.readUuidOrNull(projectTag, "ProjectId");
                UUID companyUUID = NbtDataSupport.readUuidOrNull(projectTag, "CompanyUUID");
                if (projectId == null || companyUUID == null) {
                    continue;
                }
                CompanyFinancingProject project = new CompanyFinancingProject(
                        projectId,
                        companyUUID,
                        projectTag.getString("Symbol"),
                        projectTag.getLong("IssueQuantity"),
                        projectTag.getLong("IssuePrice"),
                        projectTag.getLong("FundingTarget"),
                        projectTag.getLong("DeadlineMcDay"),
                        LocalDateTime.ofEpochSecond(projectTag.getLong("CreatedAt"), 0, ZoneOffset.UTC));
                ListTag subscriptionsTag = projectTag.getList("Subscriptions", Tag.TAG_COMPOUND);
                for (Tag subscriptionRaw : subscriptionsTag) {
                    CompoundTag subscriptionTag = (CompoundTag) subscriptionRaw;
                    UUID playerUUID = NbtDataSupport.readUuidOrNull(subscriptionTag, "PlayerUUID");
                    long quantity = subscriptionTag.getLong("Quantity");
                    if (playerUUID != null && quantity > 0) {
                        project.addSubscription(playerUUID, quantity);
                    }
                }
                CompanyFinancingManager.addProjectDirect(project);
            }
        }

        // ---- 加载公司股东提案 ----

        if (tag.contains("CompanyProposals")) {
            ListTag proposalsTag = tag.getList("CompanyProposals", Tag.TAG_COMPOUND);
            for (Tag rawTag : proposalsTag) {
                CompoundTag proposalTag = (CompoundTag) rawTag;
                UUID proposalId = NbtDataSupport.readUuidOrNull(proposalTag, "ProposalId");
                UUID companyUUID = NbtDataSupport.readUuidOrNull(proposalTag, "CompanyUUID");
                UUID creatorUUID = NbtDataSupport.readUuidOrNull(proposalTag, "CreatorUUID");
                CompanyProposalType type = NbtDataSupport.safeEnum(CompanyProposalType.class, proposalTag.getString("Type"), null);
                CompanyProposalStatus status = NbtDataSupport.safeEnum(CompanyProposalStatus.class,
                        proposalTag.getString("Status"), CompanyProposalStatus.ACTIVE);
                if (proposalId == null || companyUUID == null || creatorUUID == null || type == null) {
                    continue;
                }
                CompanyProposal proposal = new CompanyProposal(
                        proposalId,
                        companyUUID,
                        creatorUUID,
                        type,
                        proposalTag.getString("Title"),
                        proposalTag.getString("TextValue"),
                        proposalTag.getLong("Value1"),
                        proposalTag.getLong("Value2"),
                        proposalTag.getLong("Value3"),
                        proposalTag.getLong("StartMcDay"),
                        proposalTag.getLong("EndMcDay"),
                        proposalTag.getDouble("PassRatio"),
                        proposalTag.contains("MinParticipationRatio")
                                ? proposalTag.getDouble("MinParticipationRatio")
                                : 0.0,
                        proposalTag.contains("VotingSharesSnapshot")
                                ? proposalTag.getLong("VotingSharesSnapshot")
                                : 0,
                        LocalDateTime.ofEpochSecond(proposalTag.getLong("CreatedAt"), 0, ZoneOffset.UTC),
                        status,
                        proposalTag.getString("ResultSummary"));
                ListTag votesTag = proposalTag.getList("Votes", Tag.TAG_COMPOUND);
                for (Tag voteRaw : votesTag) {
                    CompoundTag voteTag = (CompoundTag) voteRaw;
                    UUID playerUUID = NbtDataSupport.readUuidOrNull(voteTag, "PlayerUUID");
                    if (playerUUID != null && voteTag.getLong("Power") > 0) {
                        proposal.addVote(playerUUID, voteTag.getBoolean("Support"), voteTag.getLong("Power"));
                    }
                }
                for(Tag snapshotRaw:proposalTag.getList("VotingPowerSnapshot",Tag.TAG_COMPOUND)){CompoundTag row=(CompoundTag)snapshotRaw;UUID holder=NbtDataSupport.readUuidOrNull(row,"HolderUUID");if(holder!=null&&row.getLong("Power")>0)proposal.restoreVotingPower(holder,row.getLong("Power"));}
                CompanyProposalManager.addProposalDirect(proposal);
            }
        }

        // ---- 加载交易记录 ----
        // ---- 加载成交历史 ----

        if (tag.contains("Companies")) {
            CompanyManager.clearCompaniesDirect();

            ListTag companiesTag = tag.getList(
                    "Companies",
                    Tag.TAG_COMPOUND
            );

            for (Tag rawTag : companiesTag) {
                CompoundTag companyTag = (CompoundTag) rawTag;

                UUID companyUUID = NbtDataSupport.readUuidOrNull(companyTag, "CompanyUUID");
                if (companyUUID == null) {
                    continue;
                }
                String name = companyTag.getString("Name");
                CompanyType type = NbtDataSupport.safeEnum(CompanyType.class, companyTag.getString("Type"), null);
                if (type == null) {
                    continue;
                }
                long cash = companyTag.getLong("Cash");
                UUID ownerUUID = NbtDataSupport.readUuidOrNull(companyTag, "OwnerUUID");

                Company company = new Company(companyUUID, name, type, cash, ownerUUID);

                CompoundTag inventoryTag = companyTag.getCompound("Inventory");
                for (String key : inventoryTag.getAllKeys()) {
                    if (CommodityRegistry.getCommodity(key) != null) {
                        company.addInventory(key, inventoryTag.getInt(key));
                    }
                }

                List<Long> recentProfits = new java.util.ArrayList<>();
                if (companyTag.contains("RecentProfits")) {
                    ListTag recentProfitsTag = companyTag.getList("RecentProfits", Tag.TAG_COMPOUND);
                    for (Tag profitRaw : recentProfitsTag) {
                        recentProfits.add(((CompoundTag) profitRaw).getLong("Profit"));
                    }
                }
                List<Company.DividendRecord> dividendHistory = new java.util.ArrayList<>();
                if (companyTag.contains("DividendHistory")) {
                    ListTag dividendHistoryTag = companyTag.getList("DividendHistory", Tag.TAG_COMPOUND);
                    for (Tag recordRaw : dividendHistoryTag) {
                        CompoundTag recordTag = (CompoundTag) recordRaw;
                        dividendHistory.add(new Company.DividendRecord(
                                recordTag.getLong("McDay"),
                                recordTag.getLong("TotalAmount"),
                                recordTag.getLong("PerShare")));
                    }
                }
                company.restoreFinancials(
                        companyTag.getLong("DailyRevenue"),
                        companyTag.getLong("DailyCost"),
                        companyTag.getLong("RetainedEarnings"),
                        companyTag.contains("DistributableProfit")
                                ? companyTag.getLong("DistributableProfit")
                                : Math.max(0, companyTag.getLong("RetainedEarnings")),
                        companyTag.getLong("LastDividendDay"),
                        recentProfits,
                        dividendHistory);
                company.restoreDividendPolicy(
                        companyTag.contains("CompanyDividendRatio")
                                ? companyTag.getDouble("CompanyDividendRatio")
                                : -1.0,
                        companyTag.contains("CompanyDividendCycleDays")
                                ? companyTag.getInt("CompanyDividendCycleDays")
                                : -1);
                if (companyTag.contains("FinancialReports")) {
                    ListTag reportsTag = companyTag.getList("FinancialReports", Tag.TAG_COMPOUND);
                    for (Tag reportRaw : reportsTag) {
                        CompoundTag reportTag = (CompoundTag) reportRaw;
                        company.addFinancialReportDirect(new CompanyFinancialReport(
                                reportTag.getLong("McDay"),
                                reportTag.getLong("Revenue"),
                                reportTag.getLong("Expenses"),
                                reportTag.getLong("NetProfit"),
                                reportTag.getLong("Assets"),
                                reportTag.getLong("Liabilities"),
                                reportTag.getLong("CashBalance"),
                                reportTag.getLong("AssetChange"),
                                reportTag.getLong("ProfitChange"),
                                reportTag.getString("Summary"),
                                LocalDateTime.ofEpochSecond(reportTag.getLong("CreatedAt"), 0, ZoneOffset.UTC)));
                    }
                }
                company.restoreManagement(
                        companyTag.contains("Strategy")
                                ? NbtDataSupport.safeEnum(finance.company.CompanyStrategy.class,
                                companyTag.getString("Strategy"),
                                finance.company.CompanyStrategy.STABLE)
                                : finance.company.CompanyStrategy.STABLE,
                        companyTag.getInt("ProductionLevel"),
                        companyTag.getInt("StorageLevel"),
                        companyTag.getInt("ManagementLevel"),
                        companyTag.contains("AutoSellRatio") ? companyTag.getDouble("AutoSellRatio") : 0.5);
                if (companyTag.contains("BankruptcyRisk")) {
                    company.setBankruptcyRisk(
                            companyTag.getBoolean("BankruptcyRisk"),
                            companyTag.getLong("BankruptcyRiskStartDay"));
                }

                // P4：恢复上市状态
                if (companyTag.contains("IsPublic")) {
                    company.setPublic(companyTag.getBoolean("IsPublic"));
                }

                CompanyManager.registerDirect(company);
            }
        }

        // ---- 加载商品定义（管理员添加的自定义商品，必须在市场价格之前） ----

    }
}
