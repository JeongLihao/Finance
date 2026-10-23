package finance.diagnostic;

import static finance.diagnostic.EconomyConsistencyService.add;

import finance.bank.*;
import finance.debt.*;
import finance.futures.*;
import java.math.BigInteger;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 债券和贷款、银行、期货、基金、保险的一致性检查。只读，从 {@link EconomyConsistencyService} 拆出。 */
final class CreditChecks {

    private CreditChecks() {}

    static void checkBondsAndLoans(List<DiagnosticIssue> out) {
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

    static void checkBanking(List<DiagnosticIssue> out) {
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

    static void checkFutures(List<DiagnosticIssue> out) {
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

    static void checkFunds(List<DiagnosticIssue> out, long day) {
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

    static void checkInsurance(List<DiagnosticIssue> out) {
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
}
