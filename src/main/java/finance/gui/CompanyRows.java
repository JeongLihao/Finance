package finance.gui;

import static finance.gui.MenuCodec.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;

/** 公司页的数据行（公司、融资项目、提案）和编解码。从 {@link FinanceMenu} 拆出。 */
public final class CompanyRows {

    static final int MAX_COMPANY_ROWS = 512;
    static final int MAX_FINANCING_ROWS = 512;
    static final int MAX_PROPOSAL_ROWS = 1_024;

    private CompanyRows() {}

    public record CompanyInfo(
            UUID companyId,
            String name,
            String type,
            long cash,
            long inventoryValue,
            long totalValue,
            Map<String, Integer> inventory,
            boolean playerOwned,
            boolean isPublic,
            String strategy,
            int productionLevel,
            int storageLevel,
            int managementLevel,
            double autoSellRatio,
            long reportRevenue,
            long reportExpenses,
            long reportNetProfit,
            long reportAssets,
            long reportLiabilities,
            long reportCash,
            long reportProfitChange,
            long reportAssetChange,
            String reportSummary,
            boolean bankruptcyRisk,
            long bankruptcyRiskStartDay,
            int capitalProjectCount,
            long capitalCommitted,
            String capitalProjectSummary) {}

    public record CompanyFinancingRow(
            UUID projectId,
            UUID companyId,
            String companyName,
            String symbol,
            long issueQuantity,
            long issuePrice,
            long fundingTarget,
            long raisedAmount,
            long subscribedShares,
            long playerSubscribedShares,
            long deadlineMcDay) {}

    public record CompanyProposalRow(
            UUID proposalId,
            UUID companyId,
            String type,
            String title,
            String textValue,
            long value1,
            long value2,
            long value3,
            long startMcDay,
            long endMcDay,
            double passRatio,
            long yesVotes,
            long noVotes,
            boolean playerVoted,
            String status,
            String resultSummary,
            boolean playerCanExecute,
            String npcSummary,
            int participationBps,
            int minParticipationBps) {}

    static void writeCompanyInfo(FriendlyByteBuf buffer, CompanyInfo info) {
        buffer.writeBoolean(info != null);
        if (info != null) {
            buffer.writeUUID(info.companyId());
            buffer.writeUtf(limitString(info.name(), 64), 64);
            buffer.writeUtf(limitString(info.type(), 32), 32);
            buffer.writeLong(info.cash());
            buffer.writeLong(info.inventoryValue());
            buffer.writeLong(info.totalValue());
            writeStringIntMap(buffer, info.inventory());
            buffer.writeBoolean(info.playerOwned());
            buffer.writeBoolean(info.isPublic());
            buffer.writeUtf(limitString(info.strategy(), 32), 32);
            buffer.writeVarInt(info.productionLevel());
            buffer.writeVarInt(info.storageLevel());
            buffer.writeVarInt(info.managementLevel());
            buffer.writeDouble(info.autoSellRatio());
            buffer.writeLong(info.reportRevenue());
            buffer.writeLong(info.reportExpenses());
            buffer.writeLong(info.reportNetProfit());
            buffer.writeLong(info.reportAssets());
            buffer.writeLong(info.reportLiabilities());
            buffer.writeLong(info.reportCash());
            buffer.writeLong(info.reportProfitChange());
            buffer.writeLong(info.reportAssetChange());
            buffer.writeUtf(limitString(info.reportSummary(), 128), 128);
            buffer.writeBoolean(info.bankruptcyRisk());
            buffer.writeLong(info.bankruptcyRiskStartDay());
            buffer.writeVarInt(Math.max(0, info.capitalProjectCount()));
            buffer.writeLong(Math.max(0, info.capitalCommitted()));
            buffer.writeUtf(limitString(info.capitalProjectSummary(), 128), 128);
        }
    }

    static CompanyInfo readCompanyInfo(FriendlyByteBuf buffer) {
        if (!buffer.readBoolean()) return null;
        return new CompanyInfo(
                buffer.readUUID(),
                buffer.readUtf(64),
                buffer.readUtf(32),
                buffer.readLong(),
                buffer.readLong(),
                buffer.readLong(),
                readStringIntMap(buffer),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readUtf(32),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readDouble(),
                buffer.readLong(),
                buffer.readLong(),
                buffer.readLong(),
                buffer.readLong(),
                buffer.readLong(),
                buffer.readLong(),
                buffer.readLong(),
                buffer.readLong(),
                buffer.readUtf(128),
                buffer.readBoolean(),
                buffer.readLong(),
                buffer.readVarInt(),
                buffer.readLong(),
                buffer.readUtf(128));
    }

    static void writeCompanyInfoList(FriendlyByteBuf buffer, List<CompanyInfo> companies) {
        List<CompanyInfo> safe = bounded(companies, MAX_COMPANY_ROWS);
        buffer.writeVarInt(safe.size());
        for (CompanyInfo company : safe) {
            writeCompanyInfo(buffer, company);
        }
    }

    static List<CompanyInfo> readCompanyInfoList(FriendlyByteBuf buffer) {
        int size = readBoundedSize(buffer, "companies", MAX_COMPANY_ROWS);
        List<CompanyInfo> companies = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            CompanyInfo company = readCompanyInfo(buffer);
            if (company != null) {
                companies.add(company);
            }
        }
        return companies;
    }

    static void writeCompanyFinancingRows(FriendlyByteBuf buffer, List<CompanyFinancingRow> rows) {
        List<CompanyFinancingRow> safe = bounded(rows, MAX_FINANCING_ROWS);
        buffer.writeVarInt(safe.size());
        for (CompanyFinancingRow row : safe) {
            buffer.writeUUID(row.projectId());
            buffer.writeUUID(row.companyId());
            buffer.writeUtf(limitString(row.companyName(), 64), 64);
            buffer.writeUtf(limitString(row.symbol(), 16), 16);
            buffer.writeLong(row.issueQuantity());
            buffer.writeLong(row.issuePrice());
            buffer.writeLong(row.fundingTarget());
            buffer.writeLong(row.raisedAmount());
            buffer.writeLong(row.subscribedShares());
            buffer.writeLong(row.playerSubscribedShares());
            buffer.writeLong(row.deadlineMcDay());
        }
    }

    static List<CompanyFinancingRow> readCompanyFinancingRows(FriendlyByteBuf buffer) {
        int size = readBoundedSize(buffer, "company financing rows", MAX_FINANCING_ROWS);
        List<CompanyFinancingRow> rows = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            rows.add(new CompanyFinancingRow(
                    buffer.readUUID(),
                    buffer.readUUID(),
                    buffer.readUtf(64),
                    buffer.readUtf(16),
                    buffer.readLong(),
                    buffer.readLong(),
                    buffer.readLong(),
                    buffer.readLong(),
                    buffer.readLong(),
                    buffer.readLong(),
                    buffer.readLong()));
        }
        return rows;
    }

    static void writeCompanyProposalRows(FriendlyByteBuf buffer, List<CompanyProposalRow> rows) {
        List<CompanyProposalRow> safe = bounded(rows, MAX_PROPOSAL_ROWS);
        buffer.writeVarInt(safe.size());
        for (CompanyProposalRow row : safe) {
            buffer.writeUUID(row.proposalId());
            buffer.writeUUID(row.companyId());
            buffer.writeUtf(limitString(row.type(), 24), 24);
            buffer.writeUtf(limitString(row.title(), 32), 32);
            buffer.writeUtf(limitString(row.textValue(), 64), 64);
            buffer.writeLong(row.value1());
            buffer.writeLong(row.value2());
            buffer.writeLong(row.value3());
            buffer.writeLong(row.startMcDay());
            buffer.writeLong(row.endMcDay());
            buffer.writeDouble(row.passRatio());
            buffer.writeLong(row.yesVotes());
            buffer.writeLong(row.noVotes());
            buffer.writeBoolean(row.playerVoted());
            buffer.writeUtf(limitString(row.status(), 16), 16);
            buffer.writeUtf(limitString(row.resultSummary(), 96), 96);
            buffer.writeBoolean(row.playerCanExecute());
            buffer.writeUtf(limitString(row.npcSummary(), 96), 96);
            buffer.writeVarInt(Math.max(0, Math.min(10_000, row.participationBps())));
            buffer.writeVarInt(Math.max(0, Math.min(10_000, row.minParticipationBps())));
        }
    }

    static List<CompanyProposalRow> readCompanyProposalRows(FriendlyByteBuf buffer) {
        int size = readBoundedSize(buffer, "company proposals", MAX_PROPOSAL_ROWS);
        List<CompanyProposalRow> rows = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            rows.add(new CompanyProposalRow(
                    buffer.readUUID(),
                    buffer.readUUID(),
                    buffer.readUtf(24),
                    buffer.readUtf(32),
                    buffer.readUtf(64),
                    buffer.readLong(),
                    buffer.readLong(),
                    buffer.readLong(),
                    buffer.readLong(),
                    buffer.readLong(),
                    buffer.readDouble(),
                    buffer.readLong(),
                    buffer.readLong(),
                    buffer.readBoolean(),
                    buffer.readUtf(16),
                    buffer.readUtf(96),
                    buffer.readBoolean(),
                    buffer.readUtf(96),
                    buffer.readVarInt(),
                    buffer.readVarInt()));
        }
        return rows;
    }
}
