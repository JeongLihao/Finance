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
import finance.stock.StockHolding;
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
    private EconomyConsistencyService() { }

    public static DiagnosticReport run(long mcDay) {
        long started = System.nanoTime();
        List<DiagnosticIssue> issues = new ArrayList<>();
        checkAccounts(issues); checkInventories(issues); checkOrders(issues); checkBondsAndLoans(issues);
        checkWarehouses(issues); checkContracts(issues); checkCompanyGameplay(issues, mcDay); checkBanking(issues); checkFutures(issues); checkFunds(issues, mcDay); checkInsurance(issues); checkGovernance(issues); checkHistory(issues); checkCycle(issues, mcDay);
        if (issues.isEmpty()) add(issues, DiagnosticSeverity.INFO, "GLOBAL", "CONSISTENT", "economy", "All checked invariants passed");
        return new DiagnosticReport(UUID.randomUUID(), Instant.now(), Math.max(-1, mcDay),
                Math.max(0, System.nanoTime() - started), issues);
    }

    public static DiagnosticReport runModule(ModuleHealthRegistry.Module module, long mcDay) {
        long started = System.nanoTime(); List<DiagnosticIssue> issues = new ArrayList<>();
        if (module != null) switch (module) {
            case ACCOUNT -> checkAccounts(issues);
            case MARKET -> { checkInventories(issues); checkOrders(issues); }
            case WAREHOUSE -> checkWarehouses(issues);
            case CONTRACT -> checkContracts(issues);
            case COMPANY_GAMEPLAY -> checkCompanyGameplay(issues,mcDay);
            case STOCK -> { checkInventories(issues); checkOrders(issues); checkGovernance(issues); }
            case DEBT -> checkBondsAndLoans(issues);
            case BANKING -> checkBanking(issues);
            case FUTURES -> checkFutures(issues);
            case FUND -> checkFunds(issues, mcDay);
            case INSURANCE -> checkInsurance(issues);
            case HISTORY -> checkHistory(issues);
            case CYCLE -> checkCycle(issues, mcDay);
        }
        if (issues.isEmpty()) add(issues, DiagnosticSeverity.INFO, module == null ? "GLOBAL" : module.name(),
                "CONSISTENT", module == null ? "economy" : module.name().toLowerCase(), "Module invariants passed");
        return new DiagnosticReport(UUID.randomUUID(), Instant.now(), Math.max(-1, mcDay),
                Math.max(0, System.nanoTime() - started), issues);
    }

    private static void checkAccounts(List<DiagnosticIssue> out) {
        for (Map.Entry<UUID, Account> entry : AccountManager.getAccounts().entrySet()) {
            Account account = entry.getValue();
            if (entry.getKey() == null || account == null || account.getPlayerId() == null
                    || !entry.getKey().equals(account.getPlayerId()))
                add(out, DiagnosticSeverity.FATAL, "ACCOUNT", "IDENTITY_MISMATCH", String.valueOf(entry.getKey()), "Account map key does not match account owner");
            else if (account.getBalance() < 0 || account.getFrozenBalance() < 0)
                add(out, DiagnosticSeverity.FATAL, "ACCOUNT", "NEGATIVE_BALANCE", entry.getKey().toString(), "Available or frozen balance is negative");
            else if (BigInteger.valueOf(account.getBalance()).add(BigInteger.valueOf(account.getFrozenBalance()))
                    .compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0)
                add(out, DiagnosticSeverity.ERROR, "ACCOUNT", "TOTAL_OVERFLOW", entry.getKey().toString(), "Available plus frozen balance exceeds long range");
        }
    }

    private static void checkInventories(List<DiagnosticIssue> out) {
        CommodityInventoryManager.getInventories().forEach((owner, inventory) -> {
            if (owner == null || inventory == null) add(out, DiagnosticSeverity.ERROR, "MARKET", "BAD_INVENTORY_OWNER", String.valueOf(owner), "Inventory owner or value is null");
            else inventory.getAllCommodities().forEach((id, amount) -> {
                if (id == null || id.isBlank() || amount == null || amount < 0)
                    add(out, DiagnosticSeverity.ERROR, "MARKET", "INVALID_COMMODITY_HOLDING", owner.toString(), "Commodity id is blank or quantity is negative");
            });
        });
        StockPortfolioManager.getPortfolios().forEach((owner, portfolio) -> portfolio.forEach((symbol, holding) -> {
            if (owner == null || symbol == null || symbol.isBlank() || holding == null
                    || holding.getQuantity() < 0 || holding.getAverageCost() < 0)
                add(out, DiagnosticSeverity.ERROR, "STOCK", "INVALID_STOCK_HOLDING", String.valueOf(owner), "Stock holding has invalid identity, quantity, or cost");
        }));
    }

    private static void checkOrders(List<DiagnosticIssue> out) {
        Set<UUID> ids = new HashSet<>();
        for (Order order : MarketManager.getOrders()) {
            if (order == null || order.getOrderId() == null || !ids.add(order.getOrderId())
                    || order.getPlayerId() == null || order.getCommodityId() == null || order.getCommodityId().isBlank()
                    || order.getType() == null || order.getPrice() <= 0 || order.getQuantity() <= 0)
                add(out, DiagnosticSeverity.ERROR, "MARKET", "INVALID_ORDER", order == null ? "null" : String.valueOf(order.getOrderId()), "Commodity order is duplicated or has invalid remaining state");
        }
        ids.clear();
        for (StockOrder order : StockOrderManager.getOrders()) {
            if (order == null || order.getOrderId() == null || !ids.add(order.getOrderId())
                    || order.getPlayerId() == null || order.getSymbol() == null || order.getSymbol().isBlank()
                    || order.getType() == null || order.getPrice() <= 0 || order.getQuantity() <= 0)
                add(out, DiagnosticSeverity.ERROR, "STOCK", "INVALID_ORDER", order == null ? "null" : String.valueOf(order.getOrderId()), "Stock order is duplicated or has invalid remaining state");
        }
        ids.clear();
        BondMarketManager.orders().forEach(order -> {
            if (order.orderId() == null || !ids.add(order.orderId()) || order.playerId() == null
                    || order.bondId() == null || order.side() == null || order.limitPricePerUnit() <= 0
                    || order.remainingQuantity() <= 0 || order.createdSequence() <= 0)
                add(out, DiagnosticSeverity.ERROR, "DEBT", "INVALID_BOND_ORDER", String.valueOf(order.orderId()), "Bond order is duplicated or invalid");
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
                add(out, DiagnosticSeverity.ERROR, "WAREHOUSE", "INVALID_WAREHOUSE", record.warehouseId().toString(),
                        "Warehouse capacity is invalid or an active position is duplicated");
            }
            owners.add(record.ownerId());
        }
        for (UUID owner : owners) {
            long used = finance.warehouse.WarehouseManager.usedCapacity(owner);
            long capacity = finance.warehouse.WarehouseManager.totalCapacity(owner);
            if (used > capacity) add(out, DiagnosticSeverity.WARN, "WAREHOUSE", "WAREHOUSE_OVER_CAPACITY",
                    owner.toString(), "Custody exceeds active warehouse capacity; new deposits are blocked");
        }
    }

    private static void checkContracts(List<DiagnosticIssue> out) {
        for (finance.contract.FinanceContract contract : finance.contract.ContractManager.contracts().values()) {
            Account escrow = AccountManager.getAccounts().get(contract.escrowAccountId());
            boolean live = contract.status() == finance.contract.ContractStatus.OPEN
                    || contract.status() == finance.contract.ContractStatus.ACCEPTED;
            if (contract.requiredQuantity() <= 0 || contract.deliveredQuantity() < 0
                    || contract.deliveredQuantity() > contract.requiredQuantity()
                    || contract.rewardAmount() <= 0 || contract.deadlineDay() <= contract.createdDay()) {
                add(out, DiagnosticSeverity.ERROR, "CONTRACT", "INVALID_CONTRACT", contract.id().toString(),
                        "Contract quantity, reward, or date invariant failed");
            }
            if (contract.status() == finance.contract.ContractStatus.ACCEPTED
                    && (contract.acceptedPlayerId() == null || contract.destinationWarehouseId() == null)) {
                add(out, DiagnosticSeverity.ERROR, "CONTRACT", "CONTRACT_ACCEPTOR_MISSING", contract.id().toString(),
                        "Accepted contract has no player or destination warehouse");
            }
            if (contract.status() == finance.contract.ContractStatus.ACCEPTED
                    && contract.destinationWarehouseId() != null) {
                finance.warehouse.WarehouseRecord destination = finance.warehouse.WarehouseManager.get(
                        contract.destinationWarehouseId());
                if (destination == null || !destination.ownerId().equals(contract.acceptedPlayerId())) {
                    add(out, DiagnosticSeverity.ERROR, "CONTRACT", "CONTRACT_DESTINATION_INVALID",
                            contract.id().toString(), "Accepted contract destination is missing or owned by another player");
                }
            }
            if (escrow == null || (live && escrow.getBalance() != contract.rewardAmount())
                    || (!live && escrow.getBalance() != 0)) {
                add(out, DiagnosticSeverity.FATAL, "CONTRACT", "CONTRACT_ESCROW_MISMATCH", contract.id().toString(),
                        "Contract escrow does not match its settlement state");
            }
        }
    }

    private static void checkCompanyGameplay(List<DiagnosticIssue> out, long mcDay) {
        for (finance.gameplay.company.CompanyGameplayProfile profile
                : finance.gameplay.company.CompanyGameplayManager.profiles().values()) {
            finance.company.Company company = finance.company.CompanyManager.getCompany(profile.companyId());
            if (company == null) {
                add(out, DiagnosticSeverity.ERROR, "COMPANY_GAMEPLAY", "GAMEPLAY_COMPANY_MISSING",
                        profile.companyId().toString(), "Company gameplay profile references a missing company");
                continue;
            }
            if (company.getOwnerId() != null && profile.members().containsKey(company.getOwnerId()))
                add(out, DiagnosticSeverity.ERROR, "COMPANY_GAMEPLAY", "DUPLICATE_COMPANY_OWNER",
                        profile.companyId().toString(), "Company owner must not be duplicated in the member map");
            for (UUID warehouseId : profile.warehouseIds()) {
                finance.warehouse.WarehouseRecord warehouse = finance.warehouse.WarehouseManager.get(warehouseId);
                if (warehouse == null || !profile.companyId().equals(warehouse.companyId()))
                    add(out, DiagnosticSeverity.ERROR, "COMPANY_GAMEPLAY", "COMPANY_WAREHOUSE_MISMATCH",
                            warehouseId.toString(), "Company warehouse binding is missing or points to another company");
            }
            if (profile.operatingMode() == finance.gameplay.company.CompanyOperatingMode.PLAYER_DRIVEN
                    && finance.gameplay.company.CompanyFacilityManager.forCompany(profile.companyId()).stream()
                    .noneMatch(f -> f.status() != finance.gameplay.company.CompanyFacilityStatus.DISABLED
                            && f.status() != finance.gameplay.company.CompanyFacilityStatus.ORPHANED))
                add(out, DiagnosticSeverity.WARN, "COMPANY_GAMEPLAY", "PLAYER_COMPANY_NO_FACILITY",
                        profile.companyId().toString(), "Player-driven company has no usable production facility");
        }
        Set<String> positions = new HashSet<>();
        for (finance.gameplay.company.CompanyFacilityRecord facility : finance.gameplay.company.CompanyFacilityManager.all()) {
            boolean orphan = facility.status() == finance.gameplay.company.CompanyFacilityStatus.ORPHANED;
            if (!orphan && finance.company.CompanyManager.getCompany(facility.companyId()) == null)
                add(out, DiagnosticSeverity.ERROR, "COMPANY_GAMEPLAY", "FACILITY_COMPANY_MISSING", facility.facilityId().toString(),
                        "Facility references a missing company without being orphaned");
            if (!orphan && !positions.add(facility.dimensionId() + ":" + facility.blockPos().asLong()))
                add(out, DiagnosticSeverity.ERROR, "COMPANY_GAMEPLAY", "DUPLICATE_FACILITY_POSITION", facility.facilityId().toString(),
                        "Two active facility records occupy the same position");
            if (mcDay >= 0 && facility.lastProcessedDay() > mcDay)
                add(out, DiagnosticSeverity.ERROR, "COMPANY_GAMEPLAY", "FACILITY_DAY_IN_FUTURE", facility.facilityId().toString(),
                        "Facility last-processed day is later than the current world day");
            if (facility.boundWarehouseId() != null) {
                finance.warehouse.WarehouseRecord warehouse = finance.warehouse.WarehouseManager.get(facility.boundWarehouseId());
                if (warehouse == null || !facility.companyId().equals(warehouse.companyId()))
                    add(out, DiagnosticSeverity.ERROR, "COMPANY_GAMEPLAY", "FACILITY_WAREHOUSE_MISMATCH", facility.facilityId().toString(),
                            "Facility warehouse is missing or belongs to another company");
            }
        }
    }

    private static void checkBondsAndLoans(List<DiagnosticIssue> out) {
        for (CorporateBond bond : CorporateBondManager.bonds().values()) {
            BigInteger holdings = BigInteger.ZERO;
            for (Map.Entry<UUID, Long> holding : bond.holdings().entrySet()) {
                if (holding.getKey() == null || holding.getValue() == null || holding.getValue() <= 0)
                    add(out, DiagnosticSeverity.ERROR, "DEBT", "INVALID_BOND_HOLDING", bond.id().toString(), "Bond holder or quantity is invalid");
                else holdings = holdings.add(BigInteger.valueOf(holding.getValue()));
            }
            if (holdings.compareTo(BigInteger.valueOf(bond.totalQuantity())) > 0)
                add(out, DiagnosticSeverity.FATAL, "DEBT", "BOND_OVERISSUED", bond.id().toString(), "Bond holdings exceed issued quantity");
            if (bond.faceValue() <= 0 || bond.totalQuantity() <= 0 || bond.couponBasisPoints() < 0
                    || bond.issueDay() < 0 || bond.subscriptionEndDay() < bond.issueDay()
                    || bond.maturityDay() <= bond.subscriptionEndDay() || bond.couponIntervalDays() <= 0
                    || bond.escrowCash() < 0)
                add(out, DiagnosticSeverity.ERROR, "DEBT", "INVALID_BOND_CONTRACT", bond.id().toString(), "Bond economic invariants are invalid");
        }
        for (CompanyLoan loan : CompanyLoanManager.loans().values()) {
            if (loan.originalPrincipal() <= 0 || loan.outstandingPrincipal() < 0
                    || loan.outstandingPrincipal() > loan.originalPrincipal() || loan.accruedInterest() < 0
                    || loan.annualRateBasisPoints() < 0 || loan.issueDay() < 0 || loan.maturityDay() <= loan.issueDay()
                    || loan.paymentIntervalDays() <= 0 || loan.lastAccrualDay() < loan.issueDay()
                    || loan.lenderId() == null || loan.lenderType() == null)
                add(out, DiagnosticSeverity.ERROR, "DEBT", "INVALID_LOAN", loan.id().toString(), "Loan principal, dates, rate, or lender is invalid");
            if (loan.lenderType() == LoanLenderType.COMMERCIAL_BANK && BankingManager.bank(loan.lenderId()) == null)
                add(out, DiagnosticSeverity.FATAL, "BANKING", "MISSING_LOAN_LENDER", loan.id().toString(), "Commercial loan references a missing bank");
        }
    }

    private static void checkBanking(List<DiagnosticIssue> out) {
        for (CommercialBank bank : BankingManager.banks().values()) {
            BankBalanceSheet sheet = bank.ledger().balanceSheet();
            if (!sheet.balanced()) add(out, DiagnosticSeverity.FATAL, "BANKING", "UNBALANCED_LEDGER", bank.id().toString(), "Bank assets do not equal liabilities plus equity");
            if (sheet.reserves() < 0 || sheet.companyLoans() < 0 || sheet.demandDeposits() < 0
                    || sheet.timeDeposits() < 0 || sheet.loanLossReserve() < 0)
                add(out, DiagnosticSeverity.FATAL, "BANKING", "NEGATIVE_LEDGER_BALANCE", bank.id().toString(), "Bank ledger contains a negative unsigned balance");
            BigInteger demand = BigInteger.ZERO, time = BigInteger.ZERO;
            for (BankCustomerAccount account : BankingManager.accounts().values()) if (account.bankId().equals(bank.id()) && account.status() != BankAccountStatus.CLOSED) {
                if (account.balance() < 0 || account.frozen() < 0 || account.frozen() > account.balance())
                    add(out, DiagnosticSeverity.ERROR, "BANKING", "INVALID_CUSTOMER_ACCOUNT", account.id().toString(), "Bank account balance/frozen invariant failed");
                if (account.type() == BankAccountType.DEMAND_DEPOSIT) demand = demand.add(BigInteger.valueOf(account.balance()));
                else time = time.add(BigInteger.valueOf(account.balance()));
            }
            if (!demand.equals(BigInteger.valueOf(sheet.demandDeposits())) || !time.equals(BigInteger.valueOf(sheet.timeDeposits())))
                add(out, DiagnosticSeverity.FATAL, "BANKING", "CUSTOMER_LEDGER_MISMATCH", bank.id().toString(), "Customer subledger does not match demand/time deposit liabilities");
        }
        if (DepositInsuranceService.fund() < 0 || FuturesClearingService.guaranteeFundForAudit() < 0)
            add(out, DiagnosticSeverity.FATAL, "BANKING", "NEGATIVE_SYSTEM_POOL", "system-pools", "Insurance or clearing fund is negative");
    }

    private static void checkFutures(List<DiagnosticIssue> out) {
        Map<UUID, BigInteger> net = new java.util.LinkedHashMap<>();
        for (MarginAccount account : MarginManager.accounts().values()) if (account.cashBalance() < 0
                || account.frozenForOrders() < 0 || account.frozenForOrders() > account.cashBalance())
            add(out, DiagnosticSeverity.FATAL, "FUTURES", "INVALID_MARGIN_ACCOUNT", account.ownerId().toString(), "Margin cash/frozen invariant failed");
        Set<UUID> orderIds = new HashSet<>(); Map<UUID, BigInteger> reserved = new java.util.LinkedHashMap<>();
        for (FuturesOrder order : FuturesMarketManager.orders()) {
            if (!orderIds.add(order.orderId()) || order.remainingQuantity() <= 0 || order.limitPrice() <= 0
                    || order.sequence() <= 0 || order.reservedMargin() < 0 || FuturesMarketManager.contract(order.contractId()) == null)
                add(out, DiagnosticSeverity.ERROR, "FUTURES", "INVALID_FUTURES_ORDER", order.orderId().toString(), "Futures order is duplicated or invalid");
            reserved.merge(order.playerId(), BigInteger.valueOf(order.reservedMargin()), BigInteger::add);
        }
        for (MarginAccount account : MarginManager.accounts().values()) if (!reserved.getOrDefault(account.ownerId(), BigInteger.ZERO)
                .equals(BigInteger.valueOf(account.frozenForOrders())))
            add(out, DiagnosticSeverity.ERROR, "FUTURES", "MARGIN_ORDER_MISMATCH", account.ownerId().toString(), "Frozen order margin differs from active order reservations");
        for (FuturesPosition position : MarginManager.positions().values()) {
            if (position.signedQuantity() == 0 || position.averageEntryPrice() <= 0 || FuturesMarketManager.contract(position.contractId()) == null)
                add(out, DiagnosticSeverity.ERROR, "FUTURES", "INVALID_POSITION", position.ownerId().toString(), "Futures position has zero/invalid quantity, price, or contract");
            net.merge(position.contractId(), BigInteger.valueOf(position.signedQuantity()), BigInteger::add);
        }
        net.forEach((contract, quantity) -> { if (quantity.signum() != 0) add(out, DiagnosticSeverity.FATAL, "FUTURES", "OPEN_INTEREST_MISMATCH", contract.toString(), "Long and short open interest do not net to zero: " + quantity); });
    }

    private static void checkHistory(List<DiagnosticIssue> out) {
        CandlestickService.getSeriesDirect().forEach((key, series) -> {
            long previous = -1;
            for (Candlestick bar : series.getBars(Integer.MAX_VALUE)) {
                if (bar.mcDay() <= previous || bar.high() < Math.max(bar.open(), bar.close())
                        || bar.low() > Math.min(bar.open(), bar.close()) || bar.high() < bar.low() || bar.volume() < 0)
                    add(out, DiagnosticSeverity.WARN, "HISTORY", "INVALID_CANDLE", key.toString(), "K-line date or OHLCV relation is invalid");
                previous = bar.mcDay();
            }
        });
    }

    private static void checkFunds(List<DiagnosticIssue> out,long day){
        Map<String,BigInteger> shares=new java.util.LinkedHashMap<>(),frozen=new java.util.LinkedHashMap<>();
        finance.fund.FundManager.positions().forEach((player,map)->map.forEach((id,p)->{if(player==null||p==null||p.shareUnits()<=0||p.frozenShareUnits()<0||p.frozenShareUnits()>p.shareUnits()||p.totalCost()<0)add(out,DiagnosticSeverity.ERROR,"FUND","INVALID_POSITION",String.valueOf(player),"Fund position quantity, frozen shares, or cost is invalid");if(p!=null){shares.merge(id,BigInteger.valueOf(Math.max(0,p.shareUnits())),BigInteger::add);frozen.merge(id,BigInteger.valueOf(Math.max(0,p.frozenShareUnits())),BigInteger::add);}}));
        finance.fund.FundManager.definitions().forEach((id,d)->{finance.fund.FundState s=finance.fund.FundManager.states().get(id);if(s==null){add(out,DiagnosticSeverity.FATAL,"FUND","MISSING_STATE",id,"Fund definition has no runtime state");return;}if(!shares.getOrDefault(id,BigInteger.ZERO).equals(BigInteger.valueOf(s.totalShareUnits())))add(out,DiagnosticSeverity.FATAL,"FUND","SHARE_MISMATCH",id,"Player shares do not equal fund total shares");var v=finance.fund.FundValuationService.value(d,s,Math.max(0,day));if(s.totalShareUnits()>0&&(v.nav()<=0||v.netAssets()<=0))add(out,DiagnosticSeverity.FATAL,"FUND","INVALID_NAV",id,"Fund with outstanding shares has no positive NAV");});
        Map<String,BigInteger> pending=new java.util.LinkedHashMap<>();for(finance.fund.FundRedemptionRequest r:finance.fund.FundManager.requests().values())if(r.status()==finance.fund.FundRedemptionRequest.Status.PENDING||r.status()==finance.fund.FundRedemptionRequest.Status.EXECUTING)pending.merge(r.fundId(),BigInteger.valueOf(r.shareUnits()),BigInteger::add);pending.forEach((id,total)->{if(total.compareTo(frozen.getOrDefault(id,BigInteger.ZERO))>0)add(out,DiagnosticSeverity.FATAL,"FUND","REDEMPTION_LOCK_MISMATCH",id,"Pending redemptions exceed frozen fund shares");});
    }

    private static void checkInsurance(List<DiagnosticIssue> out){var pool=finance.insurance.InsuranceManager.pool();if(pool.initialized()&&finance.account.AccountManager.getBalance(finance.insurance.InsurancePool.ACCOUNT_ID)<0)add(out,DiagnosticSeverity.FATAL,"INSURANCE","NEGATIVE_POOL","insurance-pool","Insurance pool cash is negative");BigInteger exposure=BigInteger.ZERO,unpaid=BigInteger.ZERO;for(var p:finance.insurance.InsuranceManager.policies().values()){if(p.remainingLimit()<0||p.remainingLimit()>p.coverageLimit()||p.premium()<=0||p.expiryDay()<=p.effectiveDay())add(out,DiagnosticSeverity.ERROR,"INSURANCE","INVALID_POLICY",p.id().toString(),"Policy limits, premium, or dates are invalid");if(p.status()==finance.insurance.PolicyStatus.ACTIVE||p.status()==finance.insurance.PolicyStatus.PENDING)exposure=exposure.add(BigInteger.valueOf(p.remainingLimit()));}Set<String> pairs=new HashSet<>();for(var c:finance.insurance.InsuranceManager.claims().values()){if(!finance.insurance.InsuranceManager.policies().containsKey(c.policyId())||!finance.insurance.InsuranceManager.events().containsKey(c.eventId())||c.paidAmount()>c.approvedAmount()||!pairs.add(c.policyId()+":"+c.eventId()))add(out,DiagnosticSeverity.FATAL,"INSURANCE","INVALID_CLAIM",c.id().toString(),"Claim references, payment, or event uniqueness failed");if(c.status()==finance.insurance.ClaimStatus.APPROVED||c.status()==finance.insurance.ClaimStatus.PARTIALLY_PAID)unpaid=unpaid.add(BigInteger.valueOf(c.unpaidAmount()));}if(!exposure.equals(BigInteger.valueOf(finance.insurance.InsuranceManager.activeExposure())))add(out,DiagnosticSeverity.FATAL,"INSURANCE","EXPOSURE_MISMATCH","insurance-pool","Active exposure total is inconsistent");if(!unpaid.equals(BigInteger.valueOf(finance.insurance.InsuranceManager.approvedUnpaid())))add(out,DiagnosticSeverity.FATAL,"INSURANCE","UNPAID_MISMATCH","insurance-pool","Approved unpaid claims are inconsistent");}

    private static void checkGovernance(List<DiagnosticIssue> out){for(var stock:finance.stock.StockMarketManager.getStocks()){var snapshot=finance.governance.ShareholderRegistryService.snapshot(stock.getCompanyId(),0);if(stock.getTreasuryShares()<0||stock.getTreasuryShares()>stock.getTotalShares()||stock.getVotingShares()+stock.getTreasuryShares()!=stock.getTotalShares())add(out,DiagnosticSeverity.FATAL,"STOCK","CAP_TABLE_MISMATCH",stock.getSymbol(),"Total shares do not equal voting plus treasury shares");if(!snapshot.consistent())add(out,DiagnosticSeverity.FATAL,"STOCK","HOLDER_MISMATCH",stock.getSymbol(),snapshot.issue());}for(var p:finance.governance.CorporateActionManager.buybacks().values()){if(p.price()<=0||p.maxShares()<=0||p.endDay()<=p.startDay()||safeProduct(p.price(),p.maxShares())!=p.budget()||sumShares(p.accepted())<0)add(out,DiagnosticSeverity.FATAL,"STOCK","BUYBACK_INVARIANT",p.id().toString(),"Buyback economic terms or accepted shares are invalid");if(p.status()==finance.governance.CapitalActionStatus.OPEN){var a=finance.account.AccountManager.getAccounts().get(p.escrowId());if(a==null||a.getBalance()!=p.budget())add(out,DiagnosticSeverity.FATAL,"STOCK","BUYBACK_ESCROW",p.id().toString(),"Open buyback escrow does not match approved budget");}}for(var o:finance.governance.CorporateActionManager.tenders().values()){if(o.price()<=0||o.targetShares()<=0||o.minShares()<=0||o.minShares()>o.targetShares()||o.endDay()<=o.startDay()||safeProduct(o.price(),o.targetShares())!=o.maxFunds()||sumShares(o.accepted())<0)add(out,DiagnosticSeverity.FATAL,"STOCK","TENDER_INVARIANT",o.id().toString(),"Tender quantities or economic terms are invalid");if(o.status()==finance.governance.CapitalActionStatus.OPEN){var a=finance.account.AccountManager.getAccounts().get(o.escrowId());if(a==null||a.getBalance()!=o.maxFunds())add(out,DiagnosticSeverity.FATAL,"STOCK","TENDER_ESCROW",o.id().toString(),"Open tender escrow does not cover maximum funds");}}for(var e:finance.governance.CorporateActionManager.controllers().entrySet()){var snapshot=finance.governance.ShareholderRegistryService.snapshot(e.getKey(),0);var top=snapshot.largest();if(top==null||!top.id().equals(e.getValue())||top.votingPercent()<=50)add(out,DiagnosticSeverity.ERROR,"STOCK","STALE_CONTROLLER",e.getKey().toString(),"Stored controller no longer owns a majority of voting shares");}}
    private static long safeProduct(long a,long b){try{return Math.multiplyExact(a,b);}catch(ArithmeticException ex){return -1;}}
    private static long sumShares(Map<UUID,Long> values){BigInteger total=BigInteger.ZERO;for(var entry:values.entrySet()){if(entry.getKey()==null||entry.getValue()==null||entry.getValue()<=0)return -1;total=total.add(BigInteger.valueOf(entry.getValue()));if(total.compareTo(BigInteger.valueOf(Long.MAX_VALUE))>0)return -1;}return total.longValue();}

    private static void checkCycle(List<DiagnosticIssue> out, long mcDay) {
        long processed = finance.cycle.FinancialCycleService.lastProcessedDay();
        long closed = finance.cycle.FinancialCycleService.lastClosedMarketDay();
        long observed = finance.cycle.FinancialCycleService.observedMarketDay();
        if (closed > observed && observed >= 0) add(out, DiagnosticSeverity.ERROR, "CYCLE", "CLOSED_AFTER_OBSERVED", "financial-cycle", "Closed market day is later than observed market day");
        if (mcDay >= 0 && (processed > mcDay || observed > mcDay)) add(out, DiagnosticSeverity.WARN, "CYCLE", "CLOCK_ROLLBACK", "financial-cycle", "Stored cycle day is ahead of current world day");
    }

    private static void add(List<DiagnosticIssue> out, DiagnosticSeverity severity, String module,
                            String code, String subject, String message) {
        if (out.size() < MAX_ISSUES) out.add(new DiagnosticIssue(severity, module, code, subject, message));
    }
}
