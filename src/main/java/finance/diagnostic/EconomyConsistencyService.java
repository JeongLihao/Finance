package finance.diagnostic;

import finance.account.Account;
import finance.account.AccountManager;
import finance.bank.*;
import finance.bondmarket.BondMarketManager;
import finance.chart.Candlestick;
import finance.chart.CandlestickService;
import finance.commodity.CommodityInventoryManager;
import finance.debt.*;
import finance.futures.*;
import finance.market.MarketManager;
import finance.market.Order;
import finance.stock.StockOrder;
import finance.stock.StockOrderManager;
import finance.stock.StockPortfolioManager;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Read-only cross-module invariant checker. It never repairs or mutates economic state. */
public final class EconomyConsistencyService {
    public static final int MAX_ISSUES = 1_000;

    private EconomyConsistencyService() {}

    public static DiagnosticReport run(long mcDay) {
        long started = System.nanoTime();
        List<DiagnosticIssue> issues = new ArrayList<>();
        checkAccounts(issues);
        checkInventories(issues);
        checkOrders(issues);
        checkBondsAndLoans(issues);
        checkWarehouses(issues);
        checkLogistics(issues);
        checkSettlements(issues);
        checkExploration(issues);
        checkContracts(issues);
        checkCompanyGameplay(issues, mcDay);
        checkBanking(issues);
        checkFutures(issues);
        checkFunds(issues, mcDay);
        checkInsurance(issues);
        checkGovernance(issues);
        checkHistory(issues);
        checkCycle(issues, mcDay);
        if (issues.isEmpty()) add(issues, DiagnosticSeverity.INFO, "GLOBAL", "CONSISTENT", "economy", "全部检查项都通过");
        return new DiagnosticReport(
                UUID.randomUUID(),
                Instant.now(),
                Math.max(-1, mcDay),
                Math.max(0, System.nanoTime() - started),
                issues);
    }

    public static DiagnosticReport runModule(ModuleHealthRegistry.Module module, long mcDay) {
        long started = System.nanoTime();
        List<DiagnosticIssue> issues = new ArrayList<>();
        if (module != null)
            switch (module) {
                case ACCOUNT -> checkAccounts(issues);
                case MARKET -> {
                    checkInventories(issues);
                    checkOrders(issues);
                }
                case WAREHOUSE -> checkWarehouses(issues);
                case LOGISTICS -> checkLogistics(issues);
                case SETTLEMENT -> checkSettlements(issues);
                case EXPLORATION -> checkExploration(issues);
                case CONTRACT -> checkContracts(issues);
                case COMPANY_GAMEPLAY -> checkCompanyGameplay(issues, mcDay);
                case STOCK -> {
                    checkInventories(issues);
                    checkOrders(issues);
                    checkGovernance(issues);
                }
                case DEBT -> checkBondsAndLoans(issues);
                case BANKING -> checkBanking(issues);
                case CARAVAN -> checkCaravan(issues);
                case NPC_ECONOMY -> checkNpcEconomy(issues);
                case RIVALS -> checkRivals(issues);
                case MACRO_ECONOMY -> {
                    for (DiagnosticIssue i : finance.macro.MacroConsistency.check())
                        add(issues, i.severity(), i.module(), i.code(), i.subject(), i.message());
                }
                case FUTURES -> checkFutures(issues);
                case FUND -> checkFunds(issues, mcDay);
                case INSURANCE -> checkInsurance(issues);
                case HISTORY -> checkHistory(issues);
                case CYCLE -> checkCycle(issues, mcDay);
            }
        if (issues.isEmpty())
            add(
                    issues,
                    DiagnosticSeverity.INFO,
                    module == null ? "GLOBAL" : module.name(),
                    "CONSISTENT",
                    module == null ? "economy" : module.name().toLowerCase(),
                    "该模块检查项都通过");
        return new DiagnosticReport(
                UUID.randomUUID(),
                Instant.now(),
                Math.max(-1, mcDay),
                Math.max(0, System.nanoTime() - started),
                issues);
    }

    private static void checkAccounts(List<DiagnosticIssue> out) {
        for (Map.Entry<UUID, Account> entry : AccountManager.getAccounts().entrySet()) {
            Account account = entry.getValue();
            if (entry.getKey() == null
                    || account == null
                    || account.getPlayerId() == null
                    || !entry.getKey().equals(account.getPlayerId()))
                add(
                        out,
                        DiagnosticSeverity.FATAL,
                        "ACCOUNT",
                        "IDENTITY_MISMATCH",
                        String.valueOf(entry.getKey()),
                        "账户表的键和账户所有者不一致");
            else if (account.getBalance() < 0 || account.getFrozenBalance() < 0)
                add(
                        out,
                        DiagnosticSeverity.FATAL,
                        "ACCOUNT",
                        "NEGATIVE_BALANCE",
                        entry.getKey().toString(),
                        "可用余额或冻结余额为负数");
            else if (BigInteger.valueOf(account.getBalance())
                            .add(BigInteger.valueOf(account.getFrozenBalance()))
                            .compareTo(BigInteger.valueOf(Long.MAX_VALUE))
                    > 0)
                add(
                        out,
                        DiagnosticSeverity.ERROR,
                        "ACCOUNT",
                        "TOTAL_OVERFLOW",
                        entry.getKey().toString(),
                        "可用余额加冻结余额超出 long 范围");
        }
    }

    private static void checkInventories(List<DiagnosticIssue> out) {
        CommodityInventoryManager.getInventories().forEach((owner, inventory) -> {
            if (owner == null || inventory == null)
                add(
                        out,
                        DiagnosticSeverity.ERROR,
                        "MARKET",
                        "BAD_INVENTORY_OWNER",
                        String.valueOf(owner),
                        "库存的所有者或内容为空");
            else
                inventory.getAllCommodities().forEach((id, amount) -> {
                    if (id == null || id.isBlank() || amount == null || amount < 0)
                        add(
                                out,
                                DiagnosticSeverity.ERROR,
                                "MARKET",
                                "INVALID_COMMODITY_HOLDING",
                                owner.toString(),
                                "商品 ID 为空或数量为负数");
                });
        });
        StockPortfolioManager.getPortfolios()
                .forEach((owner, portfolio) -> portfolio.forEach((symbol, holding) -> {
                    if (owner == null
                            || symbol == null
                            || symbol.isBlank()
                            || holding == null
                            || holding.getQuantity() < 0
                            || holding.getAverageCost() < 0)
                        add(
                                out,
                                DiagnosticSeverity.ERROR,
                                "STOCK",
                                "INVALID_STOCK_HOLDING",
                                String.valueOf(owner),
                                "股票持仓的身份、数量或成本无效");
                }));
    }

    private static void checkOrders(List<DiagnosticIssue> out) {
        Set<UUID> ids = new HashSet<>();
        for (Order order : MarketManager.getOrders()) {
            if (order == null
                    || order.getOrderId() == null
                    || !ids.add(order.getOrderId())
                    || order.getPlayerId() == null
                    || order.getCommodityId() == null
                    || order.getCommodityId().isBlank()
                    || order.getType() == null
                    || order.getPrice() <= 0
                    || order.getQuantity() <= 0)
                add(
                        out,
                        DiagnosticSeverity.ERROR,
                        "MARKET",
                        "INVALID_ORDER",
                        order == null ? "null" : String.valueOf(order.getOrderId()),
                        "商品订单重复，或剩余数量状态无效");
        }
        ids.clear();
        for (StockOrder order : StockOrderManager.getOrders()) {
            if (order == null
                    || order.getOrderId() == null
                    || !ids.add(order.getOrderId())
                    || order.getPlayerId() == null
                    || order.getSymbol() == null
                    || order.getSymbol().isBlank()
                    || order.getType() == null
                    || order.getPrice() <= 0
                    || order.getQuantity() <= 0)
                add(
                        out,
                        DiagnosticSeverity.ERROR,
                        "STOCK",
                        "INVALID_ORDER",
                        order == null ? "null" : String.valueOf(order.getOrderId()),
                        "股票订单重复，或剩余数量状态无效");
        }
        ids.clear();
        BondMarketManager.orders().forEach(order -> {
            if (order.orderId() == null
                    || !ids.add(order.orderId())
                    || order.playerId() == null
                    || order.bondId() == null
                    || order.side() == null
                    || order.limitPricePerUnit() <= 0
                    || order.remainingQuantity() <= 0
                    || order.createdSequence() <= 0)
                add(
                        out,
                        DiagnosticSeverity.ERROR,
                        "DEBT",
                        "INVALID_BOND_ORDER",
                        String.valueOf(order.orderId()),
                        "债券订单重复或无效");
        });
    }

    private static void checkWarehouses(List<DiagnosticIssue> out) {
        Set<String> positions = new HashSet<>();
        Set<UUID> owners = new HashSet<>();
        for (finance.warehouse.WarehouseRecord record : finance.warehouse.WarehouseManager.all()) {
            String position = record.dimensionId() + ":" + record.blockPos().asLong();
            boolean locationMustBeUnique = record.status() != finance.warehouse.WarehouseStatus.DISABLED
                    && record.status() != finance.warehouse.WarehouseStatus.ORPHANED;
            if (record.capacityUnits() <= 0 || (locationMustBeUnique && !positions.add(position))) {
                add(
                        out,
                        DiagnosticSeverity.ERROR,
                        "WAREHOUSE",
                        "INVALID_WAREHOUSE",
                        record.warehouseId().toString(),
                        "仓库容量无效，或有效位置重复");
            }
            owners.add(record.ownerId());
        }
        for (UUID owner : owners) {
            long used = finance.warehouse.WarehouseManager.usedCapacity(owner);
            long capacity = finance.warehouse.WarehouseManager.totalCapacity(owner);
            if (used > capacity)
                add(
                        out,
                        DiagnosticSeverity.WARN,
                        "WAREHOUSE",
                        "WAREHOUSE_OVER_CAPACITY",
                        owner.toString(),
                        "托管量超出仓库有效容量，已禁止新存入");
        }
    }

    private static void checkLogistics(List<DiagnosticIssue> out) {
        for (finance.logistics.Shipment shipment :
                finance.logistics.ShipmentManager.all().values()) {
            finance.logistics.TransportCargo cargo = finance.logistics.TransportCustodyManager.get(shipment.id());
            boolean exact = cargo != null
                    && shipment.commodityId().equals(cargo.commodityId())
                    && shipment.quantity() == cargo.quantity();
            if (shipment.status().carriesCargo() != exact)
                add(
                        out,
                        DiagnosticSeverity.FATAL,
                        "LOGISTICS",
                        "SHIPMENT_CUSTODY_MISMATCH",
                        shipment.id().toString(),
                        "运单状态和运输托管不一致");
            if (finance.warehouse.WarehouseManager.get(shipment.sourceWarehouseId()) == null
                    || finance.warehouse.WarehouseManager.get(shipment.destinationWarehouseId()) == null)
                add(
                        out,
                        DiagnosticSeverity.FATAL,
                        "LOGISTICS",
                        "SHIPMENT_WAREHOUSE_MISSING",
                        shipment.id().toString(),
                        "运单指向不存在的仓库");
        }
        for (UUID shipmentId : finance.logistics.TransportCustodyManager.all().keySet())
            if (finance.logistics.ShipmentManager.get(shipmentId) == null)
                add(
                        out,
                        DiagnosticSeverity.FATAL,
                        "LOGISTICS",
                        "ORPHAN_TRANSPORT_CUSTODY",
                        shipmentId.toString(),
                        "运输托管没有对应的运单记录");
    }

    private static void checkSettlements(List<DiagnosticIssue> out) {
        for (finance.settlement.LocalDemand demand :
                finance.settlement.SettlementManager.demands().values()) {
            if (finance.settlement.SettlementManager.get(demand.settlementId()) == null) {
                add(
                        out,
                        DiagnosticSeverity.FATAL,
                        "SETTLEMENT",
                        "MISSING_SETTLEMENT",
                        demand.id().toString(),
                        "本地需求指向不存在的聚落");
                continue;
            }
            Account escrow = AccountManager.getAccounts().get(demand.escrowAccountId());
            boolean live = demand.status() == finance.settlement.DemandStatus.OPEN
                    || demand.status() == finance.settlement.DemandStatus.ACCEPTED;
            long balance = escrow == null ? -1 : escrow.getBalance();
            if ((live && balance != demand.reward()) || (!live && balance != 0))
                add(
                        out,
                        DiagnosticSeverity.FATAL,
                        "SETTLEMENT",
                        "ESCROW_MISMATCH",
                        demand.id().toString(),
                        "本地需求状态和预存资金不一致");
        }
    }

    private static void checkExploration(List<DiagnosticIssue> out) {
        for (finance.exploration.ExplorationAssignment assignment :
                finance.exploration.ExplorationManager.assignments().values()) {
            Account escrow = AccountManager.getAccounts().get(assignment.escrowId());
            long balance = escrow == null ? -1 : escrow.getBalance();
            boolean live = assignment.status() == finance.exploration.ExplorationStatus.ACTIVE;
            if ((live && balance != assignment.reward()) || (!live && balance != 0))
                add(
                        out,
                        DiagnosticSeverity.FATAL,
                        "EXPLORATION",
                        "EXPLORATION_ESCROW_MISMATCH",
                        assignment.id().toString(),
                        "调查任务状态和预存资金不一致");
        }
    }

    private static void checkContracts(List<DiagnosticIssue> out) {
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

    private static void checkCompanyGameplay(List<DiagnosticIssue> out, long mcDay) {
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
            if (profile.operatingMode() == finance.gameplay.company.CompanyOperatingMode.PLAYER_DRIVEN
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
            if (facility.boundWarehouseId() != null) {
                finance.warehouse.WarehouseRecord warehouse =
                        finance.warehouse.WarehouseManager.get(facility.boundWarehouseId());
                if (warehouse == null || !facility.companyId().equals(warehouse.companyId()))
                    add(
                            out,
                            DiagnosticSeverity.ERROR,
                            "COMPANY_GAMEPLAY",
                            "FACILITY_WAREHOUSE_MISMATCH",
                            facility.facilityId().toString(),
                            "设施的仓库不存在或属于其他公司");
            }
        }
    }

    private static void checkBondsAndLoans(List<DiagnosticIssue> out) {
        for (CorporateBond bond : CorporateBondManager.bonds().values()) {
            BigInteger holdings = BigInteger.ZERO;
            for (Map.Entry<UUID, Long> holding : bond.holdings().entrySet()) {
                if (holding.getKey() == null || holding.getValue() == null || holding.getValue() <= 0)
                    add(
                            out,
                            DiagnosticSeverity.ERROR,
                            "DEBT",
                            "INVALID_BOND_HOLDING",
                            bond.id().toString(),
                            "债券持有人或数量无效");
                else holdings = holdings.add(BigInteger.valueOf(holding.getValue()));
            }
            if (holdings.compareTo(BigInteger.valueOf(bond.totalQuantity())) > 0)
                add(
                        out,
                        DiagnosticSeverity.FATAL,
                        "DEBT",
                        "BOND_OVERISSUED",
                        bond.id().toString(),
                        "债券持有量超过发行量");
            if (bond.faceValue() <= 0
                    || bond.totalQuantity() <= 0
                    || bond.couponBasisPoints() < 0
                    || bond.issueDay() < 0
                    || bond.subscriptionEndDay() < bond.issueDay()
                    || bond.maturityDay() <= bond.subscriptionEndDay()
                    || bond.couponIntervalDays() <= 0
                    || bond.escrowCash() < 0)
                add(
                        out,
                        DiagnosticSeverity.ERROR,
                        "DEBT",
                        "INVALID_BOND_CONTRACT",
                        bond.id().toString(),
                        "债券的经济参数检查不通过");
        }
        for (CompanyLoan loan : CompanyLoanManager.loans().values()) {
            if (loan.originalPrincipal() <= 0
                    || loan.outstandingPrincipal() < 0
                    || loan.outstandingPrincipal() > loan.originalPrincipal()
                    || loan.accruedInterest() < 0
                    || loan.annualRateBasisPoints() < 0
                    || loan.issueDay() < 0
                    || loan.maturityDay() <= loan.issueDay()
                    || loan.paymentIntervalDays() <= 0
                    || loan.lastAccrualDay() < loan.issueDay()
                    || loan.lenderId() == null
                    || loan.lenderType() == null)
                add(
                        out,
                        DiagnosticSeverity.ERROR,
                        "DEBT",
                        "INVALID_LOAN",
                        loan.id().toString(),
                        "贷款的本金、日期、利率或放款人无效");
            if (loan.lenderType() == LoanLenderType.COMMERCIAL_BANK && BankingManager.bank(loan.lenderId()) == null)
                add(
                        out,
                        DiagnosticSeverity.FATAL,
                        "BANKING",
                        "MISSING_LOAN_LENDER",
                        loan.id().toString(),
                        "商业贷款指向不存在的银行");
        }
    }

    private static void checkBanking(List<DiagnosticIssue> out) {
        for (CommercialBank bank : BankingManager.banks().values()) {
            BankBalanceSheet sheet = bank.ledger().balanceSheet();
            if (!sheet.balanced())
                add(
                        out,
                        DiagnosticSeverity.FATAL,
                        "BANKING",
                        "UNBALANCED_LEDGER",
                        bank.id().toString(),
                        "银行资产不等于负债加权益");
            if (sheet.reserves() < 0
                    || sheet.companyLoans() < 0
                    || sheet.demandDeposits() < 0
                    || sheet.timeDeposits() < 0
                    || sheet.loanLossReserve() < 0)
                add(
                        out,
                        DiagnosticSeverity.FATAL,
                        "BANKING",
                        "NEGATIVE_LEDGER_BALANCE",
                        bank.id().toString(),
                        "银行总账里有不应为负的余额变成了负数");
            BigInteger demand = BigInteger.ZERO, time = BigInteger.ZERO;
            for (BankCustomerAccount account : BankingManager.accounts().values())
                if (account.bankId().equals(bank.id()) && account.status() != BankAccountStatus.CLOSED) {
                    if (account.balance() < 0 || account.frozen() < 0 || account.frozen() > account.balance())
                        add(
                                out,
                                DiagnosticSeverity.ERROR,
                                "BANKING",
                                "INVALID_CUSTOMER_ACCOUNT",
                                account.id().toString(),
                                "银行账户余额或冻结金额检查不通过");
                    if (account.type() == BankAccountType.DEMAND_DEPOSIT)
                        demand = demand.add(BigInteger.valueOf(account.balance()));
                    else time = time.add(BigInteger.valueOf(account.balance()));
                }
            if (!demand.equals(BigInteger.valueOf(sheet.demandDeposits()))
                    || !time.equals(BigInteger.valueOf(sheet.timeDeposits())))
                add(
                        out,
                        DiagnosticSeverity.FATAL,
                        "BANKING",
                        "CUSTOMER_LEDGER_MISMATCH",
                        bank.id().toString(),
                        "客户明细账和活期/定期存款负债不一致");
        }
        if (DepositInsuranceService.fund() < 0 || FuturesClearingService.guaranteeFundForAudit() < 0)
            add(out, DiagnosticSeverity.FATAL, "BANKING", "NEGATIVE_SYSTEM_POOL", "system-pools", "保险基金或清算基金为负数");
    }

    private static void checkFutures(List<DiagnosticIssue> out) {
        Map<UUID, BigInteger> net = new java.util.LinkedHashMap<>();
        for (MarginAccount account : MarginManager.accounts().values())
            if (account.cashBalance() < 0
                    || account.frozenForOrders() < 0
                    || account.frozenForOrders() > account.cashBalance())
                add(
                        out,
                        DiagnosticSeverity.FATAL,
                        "FUTURES",
                        "INVALID_MARGIN_ACCOUNT",
                        account.ownerId().toString(),
                        "保证金现金或冻结金额检查不通过");
        Set<UUID> orderIds = new HashSet<>();
        Map<UUID, BigInteger> reserved = new java.util.LinkedHashMap<>();
        for (FuturesOrder order : FuturesMarketManager.orders()) {
            if (!orderIds.add(order.orderId())
                    || order.remainingQuantity() <= 0
                    || order.limitPrice() <= 0
                    || order.sequence() <= 0
                    || order.reservedMargin() < 0
                    || FuturesMarketManager.contract(order.contractId()) == null)
                add(
                        out,
                        DiagnosticSeverity.ERROR,
                        "FUTURES",
                        "INVALID_FUTURES_ORDER",
                        order.orderId().toString(),
                        "期货订单重复或无效");
            reserved.merge(order.playerId(), BigInteger.valueOf(order.reservedMargin()), BigInteger::add);
        }
        for (MarginAccount account : MarginManager.accounts().values())
            if (!reserved.getOrDefault(account.ownerId(), BigInteger.ZERO)
                    .equals(BigInteger.valueOf(account.frozenForOrders())))
                add(
                        out,
                        DiagnosticSeverity.ERROR,
                        "FUTURES",
                        "MARGIN_ORDER_MISMATCH",
                        account.ownerId().toString(),
                        "冻结的订单保证金和有效订单占用不一致");
        for (FuturesPosition position : MarginManager.positions().values()) {
            if (position.signedQuantity() == 0
                    || position.averageEntryPrice() <= 0
                    || FuturesMarketManager.contract(position.contractId()) == null)
                add(
                        out,
                        DiagnosticSeverity.ERROR,
                        "FUTURES",
                        "INVALID_POSITION",
                        position.ownerId().toString(),
                        "期货持仓的数量、价格或合约为零或无效");
            net.merge(position.contractId(), BigInteger.valueOf(position.signedQuantity()), BigInteger::add);
        }
        net.forEach((contract, quantity) -> {
            if (quantity.signum() != 0)
                add(
                        out,
                        DiagnosticSeverity.FATAL,
                        "FUTURES",
                        "OPEN_INTEREST_MISMATCH",
                        contract.toString(),
                        "Long and short open interest do not net to zero: " + quantity);
        });
    }

    private static void checkHistory(List<DiagnosticIssue> out) {
        CandlestickService.getSeriesDirect().forEach((key, series) -> {
            long previous = -1;
            for (Candlestick bar : series.getBars(Integer.MAX_VALUE)) {
                if (bar.mcDay() <= previous
                        || bar.high() < Math.max(bar.open(), bar.close())
                        || bar.low() > Math.min(bar.open(), bar.close())
                        || bar.high() < bar.low()
                        || bar.volume() < 0)
                    add(out, DiagnosticSeverity.WARN, "HISTORY", "INVALID_CANDLE", key.toString(), "K 线日期或开高低收量关系无效");
                previous = bar.mcDay();
            }
        });
    }

    private static void checkFunds(List<DiagnosticIssue> out, long day) {
        Map<String, BigInteger> shares = new java.util.LinkedHashMap<>(), frozen = new java.util.LinkedHashMap<>();
        finance.fund.FundManager.positions()
                .forEach((player, map) -> map.forEach((id, p) -> {
                    if (player == null
                            || p == null
                            || p.shareUnits() <= 0
                            || p.frozenShareUnits() < 0
                            || p.frozenShareUnits() > p.shareUnits()
                            || p.totalCost() < 0)
                        add(
                                out,
                                DiagnosticSeverity.ERROR,
                                "FUND",
                                "INVALID_POSITION",
                                String.valueOf(player),
                                "基金持仓的数量、冻结份额或成本无效");
                    if (p != null) {
                        shares.merge(id, BigInteger.valueOf(Math.max(0, p.shareUnits())), BigInteger::add);
                        frozen.merge(id, BigInteger.valueOf(Math.max(0, p.frozenShareUnits())), BigInteger::add);
                    }
                }));
        finance.fund.FundManager.definitions().forEach((id, d) -> {
            finance.fund.FundState s = finance.fund.FundManager.states().get(id);
            if (s == null) {
                add(out, DiagnosticSeverity.FATAL, "FUND", "MISSING_STATE", id, "基金定义没有对应的运行状态");
                return;
            }
            if (!shares.getOrDefault(id, BigInteger.ZERO).equals(BigInteger.valueOf(s.totalShareUnits())))
                add(out, DiagnosticSeverity.FATAL, "FUND", "SHARE_MISMATCH", id, "玩家份额合计不等于基金总份额");
            var v = finance.fund.FundValuationService.value(d, s, Math.max(0, day));
            if (s.totalShareUnits() > 0 && (v.nav() <= 0 || v.netAssets() <= 0))
                add(out, DiagnosticSeverity.FATAL, "FUND", "INVALID_NAV", id, "基金有流通份额，但净值不为正");
        });
        Map<String, BigInteger> pending = new java.util.LinkedHashMap<>();
        for (finance.fund.FundRedemptionRequest r :
                finance.fund.FundManager.requests().values())
            if (r.status() == finance.fund.FundRedemptionRequest.Status.PENDING
                    || r.status() == finance.fund.FundRedemptionRequest.Status.EXECUTING)
                pending.merge(r.fundId(), BigInteger.valueOf(r.shareUnits()), BigInteger::add);
        pending.forEach((id, total) -> {
            if (total.compareTo(frozen.getOrDefault(id, BigInteger.ZERO)) > 0)
                add(out, DiagnosticSeverity.FATAL, "FUND", "REDEMPTION_LOCK_MISMATCH", id, "待赎回份额超过冻结份额");
        });
    }

    private static void checkInsurance(List<DiagnosticIssue> out) {
        var pool = finance.insurance.InsuranceManager.pool();
        if (pool.initialized()
                && finance.account.AccountManager.getBalance(finance.insurance.InsurancePool.ACCOUNT_ID) < 0)
            add(out, DiagnosticSeverity.FATAL, "INSURANCE", "NEGATIVE_POOL", "insurance-pool", "保险资金池现金为负数");
        BigInteger exposure = BigInteger.ZERO, unpaid = BigInteger.ZERO;
        for (var p : finance.insurance.InsuranceManager.policies().values()) {
            if (p.remainingLimit() < 0
                    || p.remainingLimit() > p.coverageLimit()
                    || p.premium() <= 0
                    || p.expiryDay() <= p.effectiveDay())
                add(out, DiagnosticSeverity.ERROR, "INSURANCE", "INVALID_POLICY", p.id().toString(), "保单的保额、保费或日期无效");
            if (p.status() == finance.insurance.PolicyStatus.ACTIVE
                    || p.status() == finance.insurance.PolicyStatus.PENDING)
                exposure = exposure.add(BigInteger.valueOf(p.remainingLimit()));
        }
        Set<String> pairs = new HashSet<>();
        for (var c : finance.insurance.InsuranceManager.claims().values()) {
            if (!finance.insurance.InsuranceManager.policies().containsKey(c.policyId())
                    || !finance.insurance.InsuranceManager.events().containsKey(c.eventId())
                    || c.paidAmount() > c.approvedAmount()
                    || !pairs.add(c.policyId() + ":" + c.eventId()))
                add(
                        out,
                        DiagnosticSeverity.FATAL,
                        "INSURANCE",
                        "INVALID_CLAIM",
                        c.id().toString(),
                        "理赔的关联、赔付或事件唯一性检查不通过");
            if (c.status() == finance.insurance.ClaimStatus.APPROVED
                    || c.status() == finance.insurance.ClaimStatus.PARTIALLY_PAID)
                unpaid = unpaid.add(BigInteger.valueOf(c.unpaidAmount()));
        }
        if (!exposure.equals(BigInteger.valueOf(finance.insurance.InsuranceManager.activeExposure())))
            add(out, DiagnosticSeverity.FATAL, "INSURANCE", "EXPOSURE_MISMATCH", "insurance-pool", "有效风险敞口合计不一致");
        if (!unpaid.equals(BigInteger.valueOf(finance.insurance.InsuranceManager.approvedUnpaid())))
            add(out, DiagnosticSeverity.FATAL, "INSURANCE", "UNPAID_MISMATCH", "insurance-pool", "已批准未赔付的理赔合计不一致");
    }

    private static void checkGovernance(List<DiagnosticIssue> out) {
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

    private static long safeProduct(long a, long b) {
        try {
            return Math.multiplyExact(a, b);
        } catch (ArithmeticException ex) {
            return -1;
        }
    }

    private static long sumShares(Map<UUID, Long> values) {
        BigInteger total = BigInteger.ZERO;
        for (var entry : values.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || entry.getValue() <= 0) return -1;
            total = total.add(BigInteger.valueOf(entry.getValue()));
            if (total.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) return -1;
        }
        return total.longValue();
    }

    private static void checkCycle(List<DiagnosticIssue> out, long mcDay) {
        long processed = finance.cycle.FinancialCycleService.lastProcessedDay();
        long closed = finance.cycle.FinancialCycleService.lastClosedMarketDay();
        long observed = finance.cycle.FinancialCycleService.observedMarketDay();
        if (closed > observed && observed >= 0)
            add(
                    out,
                    DiagnosticSeverity.ERROR,
                    "CYCLE",
                    "CLOSED_AFTER_OBSERVED",
                    "financial-cycle",
                    "已收盘的市场日期晚于观测到的市场日期");
        if (mcDay >= 0 && (processed > mcDay || observed > mcDay))
            add(out, DiagnosticSeverity.WARN, "CYCLE", "CLOCK_ROLLBACK", "financial-cycle", "记录的周期日期超前于当前世界日期");
    }

    private static void checkRivals(List<DiagnosticIssue> out) {
        // 竞争公司档案只能指向无主公司（破产公司的档案在日结时清理）
        for (finance.rival.RivalProfile p : finance.rival.RivalManager.all()) {
            finance.company.Company c = finance.company.CompanyManager.getCompany(p.companyId());
            if (c != null && c.isPlayerOwned()) {
                add(
                        out,
                        DiagnosticSeverity.ERROR,
                        "RIVALS",
                        "PROFILE_ON_PLAYER_COMPANY",
                        p.companyId().toString(),
                        "竞争对手档案指向玩家公司");
            }
        }
    }

    private static void checkNpcEconomy(List<DiagnosticIssue> out) {
        // NPC 投资者：账户不能为负，持股不能让单股合计超过有表决权股份（股本核对由 STOCK 模块负责）。
        for (finance.npcinvestor.NpcInvestorArchetype a : finance.npcinvestor.NpcInvestorArchetype.values()) {
            finance.account.Account account = AccountManager.getAccounts().get(a.accountId());
            if (account != null && (account.getBalance() < 0 || account.getFrozenBalance() < 0)) {
                add(out, DiagnosticSeverity.ERROR, "NPC_ECONOMY", "NEGATIVE_BALANCE", a.name(), "NPC 投资者余额为负数");
            }
            var portfolio = StockPortfolioManager.getPortfolios().get(a.accountId());
            if (portfolio == null) continue;
            for (var entry : portfolio.entrySet()) {
                finance.stock.Stock stock = finance.stock.StockMarketManager.getStock(entry.getKey());
                if (stock == null || entry.getValue().getQuantity() > stock.getVotingShares()) {
                    add(
                            out,
                            DiagnosticSeverity.ERROR,
                            "NPC_ECONOMY",
                            "HOLDING_INVALID",
                            a.name() + "/" + entry.getKey(),
                            "NPC 投资者持有不存在的股票，或持股超过有表决权股总数");
                }
            }
        }
    }

    private static void checkCaravan(List<DiagnosticIssue> out) {
        // P1-6：商队模块熔断后的恢复路径——校验 routes/shipments 基本完整性，
        // 仿其他模块（损坏条目在 load 期已跳过＋计数，这里只做一致性核查）。
        Set<UUID> routeIds = new HashSet<>();
        for (finance.caravan.CaravanRoute r : finance.caravan.CaravanManager.routes()) {
            String id = r.id() == null ? "null" : r.id().toString();
            if (r.id() == null
                    || !routeIds.add(r.id())
                    || r.ownerId() == null
                    || r.sourceSettlementId() == null
                    || r.destSettlementId() == null
                    || r.sourceSettlementId().equals(r.destSettlementId())
                    || r.commodityId() == null
                    || r.commodityId().isBlank()
                    || r.qtyPerTrip() <= 0
                    || r.guardLevel() < finance.caravan.CaravanRoute.MIN_GUARD_LEVEL
                    || r.guardLevel() > finance.caravan.CaravanRoute.MAX_GUARD_LEVEL
                    || r.status() == null
                    || r.saturationBps() < 0
                    || r.createdDay() < 0)
                add(out, DiagnosticSeverity.ERROR, "CARAVAN", "INVALID_ROUTE", id, "商队路线的身份、起止点、数量、护卫等级或状态无效");
        }
        Set<UUID> shipIds = new HashSet<>();
        for (finance.caravan.CaravanShipment s : finance.caravan.CaravanManager.allInTransit()) {
            String id = s.id() == null ? "null" : s.id().toString();
            if (s.id() == null
                    || !shipIds.add(s.id())
                    || s.routeId() == null
                    || s.ownerId() == null
                    || s.commodityId() == null
                    || s.commodityId().isBlank()
                    || s.qty() <= 0
                    || s.buyUnitPrice() <= 0
                    || s.arriveDay() <= s.departDay()
                    || s.departDay() < 0
                    || s.guardLevel() < finance.caravan.CaravanRoute.MIN_GUARD_LEVEL
                    || s.guardLevel() > finance.caravan.CaravanRoute.MAX_GUARD_LEVEL
                    || s.raidBpsSnapshot() < 0
                    || s.insuredValuePerUnit() < 0
                    || s.dispatchCost() < 0
                    || s.status() == null)
                add(out, DiagnosticSeverity.ERROR, "CARAVAN", "INVALID_SHIPMENT", id, "在途货物的身份、定价、劫掠快照、日期或状态无效");
            else if (!routeIds.contains(s.routeId()))
                add(out, DiagnosticSeverity.ERROR, "CARAVAN", "ORPHAN_SHIPMENT", id, "在途货物指向不存在的路线");
        }
    }

    private static void add(
            List<DiagnosticIssue> out,
            DiagnosticSeverity severity,
            String module,
            String code,
            String subject,
            String message) {
        if (out.size() < MAX_ISSUES) out.add(new DiagnosticIssue(severity, module, code, subject, message));
    }
}
