package finance.diagnostic;

import static finance.diagnostic.EconomyConsistencyService.add;

import finance.account.Account;
import finance.account.AccountManager;
import finance.bank.*;
import finance.debt.*;
import finance.futures.*;
import java.math.BigInteger;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 供货合同、公司经营、股本和公司行动（回购、要约）的一致性检查。只读，从 {@link EconomyConsistencyService} 拆出。 */
final class CompanyChecks {

    private CompanyChecks() {}

    static void checkContracts(List<DiagnosticIssue> out) {
        for (finance.contract.FinanceContract contract :
                finance.contract.ContractManager.contracts().values()) {
            Account escrow = AccountManager.getAccounts().get(contract.escrowAccountId());
            boolean live = contract.status() == finance.contract.ContractStatus.OPEN
                    || contract.status() == finance.contract.ContractStatus.ACCEPTED;
            if (contract.requiredQuantity() <= 0
                    || contract.deliveredQuantity() < 0
                    || contract.deliveredQuantity() > contract.requiredQuantity()
                    || contract.rewardAmount() <= 0
                    || contract.deadlineDay() <= contract.createdDay()) {
                add(
                        out,
                        DiagnosticSeverity.ERROR,
                        "CONTRACT",
                        "INVALID_CONTRACT",
                        contract.id().toString(),
                        "合同的数量、奖励或日期检查不通过");
            }
            if (contract.status() == finance.contract.ContractStatus.ACCEPTED
                    && (contract.acceptedPlayerId() == null || contract.destinationWarehouseId() == null)) {
                add(
                        out,
                        DiagnosticSeverity.ERROR,
                        "CONTRACT",
                        "CONTRACT_ACCEPTOR_MISSING",
                        contract.id().toString(),
                        "已接受的合同缺少玩家或目的仓库");
            }
            if (contract.status() == finance.contract.ContractStatus.ACCEPTED
                    && contract.destinationWarehouseId() != null) {
                finance.warehouse.WarehouseRecord destination =
                        finance.warehouse.WarehouseManager.get(contract.destinationWarehouseId());
                if (destination == null || !destination.ownerId().equals(contract.acceptedPlayerId())) {
                    add(
                            out,
                            DiagnosticSeverity.ERROR,
                            "CONTRACT",
                            "CONTRACT_DESTINATION_INVALID",
                            contract.id().toString(),
                            "已接受合同的目的仓库不存在或属于其他玩家");
                }
            }
            if (escrow == null
                    || (live && escrow.getBalance() != contract.rewardAmount())
                    || (!live && escrow.getBalance() != 0)) {
                add(
                        out,
                        DiagnosticSeverity.FATAL,
                        "CONTRACT",
                        "CONTRACT_ESCROW_MISMATCH",
                        contract.id().toString(),
                        "合同预存资金和结算状态不一致");
            }
        }
    }

    static void checkCompanyGameplay(List<DiagnosticIssue> out, long mcDay) {
        for (finance.gameplay.company.CompanyGameplayProfile profile :
                finance.gameplay.company.CompanyGameplayManager.profiles().values()) {
            finance.company.Company company = finance.company.CompanyManager.getCompany(profile.companyId());
            if (company == null) {
                add(
                        out,
                        DiagnosticSeverity.ERROR,
                        "COMPANY_GAMEPLAY",
                        "GAMEPLAY_COMPANY_MISSING",
                        profile.companyId().toString(),
                        "公司经营档案指向不存在的公司");
                continue;
            }
            if (company.getOwnerId() != null && profile.members().containsKey(company.getOwnerId()))
                add(
                        out,
                        DiagnosticSeverity.ERROR,
                        "COMPANY_GAMEPLAY",
                        "DUPLICATE_COMPANY_OWNER",
                        profile.companyId().toString(),
                        "公司所有者不应重复出现在成员表里");
            for (UUID warehouseId : profile.warehouseIds()) {
                finance.warehouse.WarehouseRecord warehouse = finance.warehouse.WarehouseManager.get(warehouseId);
                if (warehouse == null || !profile.companyId().equals(warehouse.companyId()))
                    add(
                            out,
                            DiagnosticSeverity.ERROR,
                            "COMPANY_GAMEPLAY",
                            "COMPANY_WAREHOUSE_MISMATCH",
                            warehouseId.toString(),
                            "公司仓库绑定缺失或指向其他公司");
            }
            finance.company.Company owner = finance.company.CompanyManager.getCompany(profile.companyId());
            if (owner != null
                    && owner.isPlayerOwned()
                    && finance.gameplay.company.CompanyFacilityManager.forCompany(profile.companyId()).stream()
                            .noneMatch(f -> f.status() != finance.gameplay.company.CompanyFacilityStatus.DISABLED
                                    && f.status() != finance.gameplay.company.CompanyFacilityStatus.ORPHANED))
                add(
                        out,
                        DiagnosticSeverity.WARN,
                        "COMPANY_GAMEPLAY",
                        "PLAYER_COMPANY_NO_FACILITY",
                        profile.companyId().toString(),
                        "玩家公司没有可用的生产设施");
        }
        Set<String> positions = new HashSet<>();
        for (finance.gameplay.company.CompanyFacilityRecord facility :
                finance.gameplay.company.CompanyFacilityManager.all()) {
            boolean orphan = facility.status() == finance.gameplay.company.CompanyFacilityStatus.ORPHANED;
            if (!orphan && finance.company.CompanyManager.getCompany(facility.companyId()) == null)
                add(
                        out,
                        DiagnosticSeverity.ERROR,
                        "COMPANY_GAMEPLAY",
                        "FACILITY_COMPANY_MISSING",
                        facility.facilityId().toString(),
                        "设施指向不存在的公司，却没有标记为无主");
            if (!orphan
                    && !positions.add(
                            facility.dimensionId() + ":" + facility.blockPos().asLong()))
                add(
                        out,
                        DiagnosticSeverity.ERROR,
                        "COMPANY_GAMEPLAY",
                        "DUPLICATE_FACILITY_POSITION",
                        facility.facilityId().toString(),
                        "两个有效设施记录占用同一位置");
            if (mcDay >= 0 && facility.lastProcessedDay() > mcDay)
                add(
                        out,
                        DiagnosticSeverity.ERROR,
                        "COMPANY_GAMEPLAY",
                        "FACILITY_DAY_IN_FUTURE",
                        facility.facilityId().toString(),
                        "设施上次结算日期晚于当前世界日期");
        }
    }

    static void checkGovernance(List<DiagnosticIssue> out) {
        for (var stock : finance.stock.StockMarketManager.getStocks()) {
            var snapshot = finance.governance.ShareholderRegistryService.snapshot(stock.getCompanyId(), 0);
            if (stock.getTreasuryShares() < 0
                    || stock.getTreasuryShares() > stock.getTotalShares()
                    || stock.getVotingShares() + stock.getTreasuryShares() != stock.getTotalShares())
                add(out, DiagnosticSeverity.FATAL, "STOCK", "CAP_TABLE_MISMATCH", stock.getSymbol(), "总股本不等于有表决权股加库存股");
            if (!snapshot.consistent())
                add(out, DiagnosticSeverity.FATAL, "STOCK", "HOLDER_MISMATCH", stock.getSymbol(), snapshot.issue());
        }
        for (var p : finance.governance.CorporateActionManager.buybacks().values()) {
            if (p.price() <= 0
                    || p.maxShares() <= 0
                    || p.endDay() <= p.startDay()
                    || safeProduct(p.price(), p.maxShares()) != p.budget()
                    || sumShares(p.accepted()) < 0)
                add(out, DiagnosticSeverity.FATAL, "STOCK", "BUYBACK_INVARIANT", p.id().toString(), "回购条款或已接受股数无效");
            if (p.status() == finance.governance.CapitalActionStatus.OPEN) {
                var a = finance.account.AccountManager.getAccounts().get(p.escrowId());
                if (a == null || a.getBalance() != p.budget())
                    add(
                            out,
                            DiagnosticSeverity.FATAL,
                            "STOCK",
                            "BUYBACK_ESCROW",
                            p.id().toString(),
                            "进行中回购的预存资金和批准预算不一致");
            }
        }
        for (var o : finance.governance.CorporateActionManager.tenders().values()) {
            if (o.price() <= 0
                    || o.targetShares() <= 0
                    || o.minShares() <= 0
                    || o.minShares() > o.targetShares()
                    || o.endDay() <= o.startDay()
                    || safeProduct(o.price(), o.targetShares()) != o.maxFunds()
                    || sumShares(o.accepted()) < 0)
                add(out, DiagnosticSeverity.FATAL, "STOCK", "TENDER_INVARIANT", o.id().toString(), "要约收购的数量或条款无效");
            if (o.status() == finance.governance.CapitalActionStatus.OPEN) {
                var a = finance.account.AccountManager.getAccounts().get(o.escrowId());
                if (a == null || a.getBalance() != o.maxFunds())
                    add(
                            out,
                            DiagnosticSeverity.FATAL,
                            "STOCK",
                            "TENDER_ESCROW",
                            o.id().toString(),
                            "进行中要约的预存资金不够最大收购金额");
            }
        }
        for (var e : finance.governance.CorporateActionManager.controllers().entrySet()) {
            var snapshot = finance.governance.ShareholderRegistryService.snapshot(e.getKey(), 0);
            var top = snapshot.largest();
            if (top == null || !top.id().equals(e.getValue()) || top.votingPercent() <= 50)
                add(
                        out,
                        DiagnosticSeverity.ERROR,
                        "STOCK",
                        "STALE_CONTROLLER",
                        e.getKey().toString(),
                        "记录中的控股人已不再持有过半表决权股");
        }
    }

    static long safeProduct(long a, long b) {
        try {
            return Math.multiplyExact(a, b);
        } catch (ArithmeticException ex) {
            return -1;
        }
    }

    static long sumShares(Map<UUID, Long> values) {
        BigInteger total = BigInteger.ZERO;
        for (var entry : values.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || entry.getValue() <= 0) return -1;
            total = total.add(BigInteger.valueOf(entry.getValue()));
            if (total.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) return -1;
        }
        return total.longValue();
    }
}
