package finance.gameplay.company;

import finance.account.Account;
import finance.account.AccountManager;
import finance.account.TransactionRecord;
import finance.account.TransactionType;
import finance.company.Company;
import finance.company.CompanyManager;
import finance.contract.ContractIssuerType;
import finance.contract.ContractManager;
import finance.contract.ContractStatus;
import finance.contract.FinanceContract;
import finance.data.EconomySavedData;
import finance.diagnostic.ModuleHealthRegistry;
import finance.money.MoneyEndpoints;
import finance.money.MoneyTransferService;

import java.util.Map;
import java.util.UUID;

/** Company-to-company fulfillment for fixed-price procurement contracts. */
public final class CompanySupplyContractService {
    public static final int MAX_ACTIVE_SUPPLY_PER_COMPANY = 8;
    private static final int MAX_OPERATION_KEY_LENGTH = 48;

    private CompanySupplyContractService() {}

    public static synchronized CompanyGameplayActionResult accept(UUID actor, UUID sellerCompanyId,
                                                                   UUID contractId, long day,
                                                                   String operationKey) {
        if (!valid(actor, sellerCompanyId, contractId, day, operationKey))
            return CompanyGameplayActionResult.fail("finance.company_supply.invalid_request");
        FinanceContract contract = ContractManager.get(contractId);
        Company seller = CompanyManager.getCompany(sellerCompanyId);
        Company buyer = contract == null ? null : CompanyManager.getCompany(contract.issuerId());
        if (contract == null || contract.issuerType() != ContractIssuerType.COMPANY
                || contract.status() != ContractStatus.OPEN || seller == null || buyer == null
                || seller.isBankruptcyRisk() || buyer.isBankruptcyRisk()
                || sellerCompanyId.equals(contract.issuerId())
                || !CompanyMembershipService.hasPermission(sellerCompanyId, actor,
                CompanyPermission.MANAGE_PRODUCTION))
            return CompanyGameplayActionResult.fail("finance.company_supply.accept_denied");
        String key = scoped(actor, operationKey);
        if (contract.hasOperation(key)) return CompanyGameplayActionResult.ok("finance.company_supply.duplicate");
        if (day > contract.deadlineDay()) return CompanyGameplayActionResult.fail("finance.company_supply.expired");
        if (ContractManager.activeForCompanySupplier(sellerCompanyId) >= MAX_ACTIVE_SUPPLY_PER_COMPANY)
            return CompanyGameplayActionResult.fail("finance.company_supply.active_limit");
        Account escrow = AccountManager.getAccounts().get(contract.escrowAccountId());
        if (escrow == null || escrow.getBalance() != contract.remainingReward())
            return CompanyGameplayActionResult.fail("finance.company_supply.escrow_mismatch");
        if (!contract.acceptCompany(sellerCompanyId))
            return CompanyGameplayActionResult.fail("finance.company_supply.accept_denied");
        contract.recordOperation(key);
        EconomySavedData.markDirty();
        return CompanyGameplayActionResult.ok("finance.company_supply.accepted");
    }

    public static synchronized CompanyGameplayActionResult deliver(UUID actor, UUID sellerCompanyId,
                                                                    UUID contractId, int quantity, long day,
                                                                    String operationKey) {
        if (!valid(actor, sellerCompanyId, contractId, day, operationKey) || quantity <= 0)
            return CompanyGameplayActionResult.fail("finance.company_supply.invalid_request");
        FinanceContract contract = ContractManager.get(contractId);
        Company seller = CompanyManager.getCompany(sellerCompanyId);
        Company buyer = contract == null ? null : CompanyManager.getCompany(contract.issuerId());
        if (contract == null || contract.status() != ContractStatus.ACCEPTED
                || !sellerCompanyId.equals(contract.acceptedCompanyId()) || seller == null || buyer == null
                || seller.isBankruptcyRisk() || buyer.isBankruptcyRisk()
                || !CompanyMembershipService.hasPermission(sellerCompanyId, actor,
                CompanyPermission.MANAGE_PRODUCTION))
            return CompanyGameplayActionResult.fail("finance.company_supply.delivery_denied");
        String key = scoped(actor, operationKey);
        if (contract.hasOperation(key)) return CompanyGameplayActionResult.ok("finance.company_supply.duplicate");
        if (day > contract.deadlineDay()) return CompanyGameplayActionResult.fail("finance.company_supply.expired");
        if (quantity > contract.remainingQuantity())
            return CompanyGameplayActionResult.fail("finance.company_supply.quantity_invalid");
        long payment = contract.paymentFor(quantity);
        if (payment <= 0) return CompanyGameplayActionResult.fail("finance.company_supply.quantity_invalid");
        Account escrow = AccountManager.getAccounts().get(contract.escrowAccountId());
        if (escrow == null || escrow.getBalance() != contract.remainingReward() || escrow.getBalance() < payment)
            return CompanyGameplayActionResult.fail("finance.company_supply.escrow_mismatch");
        if (CompanyInventoryFacade.availableInput(seller, contract.commodityId()) < quantity)
            return CompanyGameplayActionResult.fail("finance.company_supply.inventory_insufficient");
        Map<String, Integer> goods = Map.of(contract.commodityId(), quantity);
        if (!CompanyInventoryFacade.canAddOutput(buyer, goods) || !seller.canDeposit(payment))
            return CompanyGameplayActionResult.fail("finance.company_supply.destination_full");
        CompanyInventoryFacade.Consumption sellerRemoval = CompanyInventoryFacade.consumeInputAtomically(seller, goods);
        if (sellerRemoval == null)
            return CompanyGameplayActionResult.fail("finance.company_supply.inventory_changed");
        if (!CompanyInventoryFacade.addOutputAtomically(buyer, goods)) {
            CompanyInventoryFacade.rollback(seller, sellerRemoval);
            return CompanyGameplayActionResult.fail("finance.company_supply.delivery_failed");
        }
        if (!MoneyTransferService.transfer(MoneyEndpoints.account(contract.escrowAccountId()),
                MoneyEndpoints.company(seller), payment).success()) {
            CompanyInventoryFacade.Consumption buyerRollback = CompanyInventoryFacade.consumeInputAtomically(buyer, goods);
            if (buyerRollback == null || !CompanyInventoryFacade.rollback(seller, sellerRemoval))
                throw new IllegalStateException("company supply payment rollback failed");
            return CompanyGameplayActionResult.fail("finance.company_supply.payment_failed");
        }
        if (!contract.recordCompanyDelivery(quantity))
            throw new IllegalStateException("validated company supply delivery rejected");
        contract.recordOperation(key);
        AccountManager.addTransactionRecord(new TransactionRecord(contract.escrowAccountId(), sellerCompanyId,
                payment, TransactionType.CONTRACT_COMPLETE, actor, contract.commodityId(), quantity));
        EconomySavedData.markDirty();
        return CompanyGameplayActionResult.ok(contract.status() == ContractStatus.COMPLETED
                ? "finance.company_supply.completed" : "finance.company_supply.partial");
    }

    private static boolean valid(UUID actor, UUID companyId, UUID contractId, long day, String key) {
        return ModuleHealthRegistry.mayWrite(ModuleHealthRegistry.Module.CONTRACT) && actor != null
                && companyId != null && contractId != null && day >= 0 && key != null && !key.isBlank()
                && key.length() <= MAX_OPERATION_KEY_LENGTH;
    }

    private static String scoped(UUID actor, String key) { return actor + ":supply:" + key; }
}
