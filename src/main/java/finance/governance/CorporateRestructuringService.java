package finance.governance;

import finance.account.AccountManager;
import finance.account.TransactionRecord;
import finance.account.TransactionType;
import finance.company.Company;
import finance.company.CompanyManager;
import finance.company.CompanyProposal;
import finance.company.CompanyProposalManager;
import finance.company.CompanyProposalType;
import finance.data.EconomySavedData;

import java.util.UUID;

/** Transactional execution layer used after governance approval. */
public final class CorporateRestructuringService {
    private CorporateRestructuringService() {}

    public static synchronized CorporateActionManager.Result emergencyContribution(
            UUID investor, UUID proposalId, long day, String operationKey) {
        CompanyProposal proposal = CompanyProposalManager.getProposal(proposalId);
        UUID companyId = proposal == null ? null : proposal.getCompanyId();
        long amount = proposal == null ? 0 : proposal.getValue1();
        Company company = CompanyManager.getCompany(companyId);
        String key = "recap:" + operationKey;
        if (investor == null || proposal == null
                || !proposal.isExecutableAuthorization(CompanyProposalType.EMERGENCY_RECAPITALIZATION, companyId)
                || company == null || !company.isBankruptcyRisk() || amount <= 0
                || operationKey == null || operationKey.isBlank() || operationKey.length() > 96
                || CorporateActionManager.keys().contains(key)) {
            return CorporateActionManager.Result.fail("Invalid emergency recapitalization");
        }
        String riskKey = "recap-risk:" + companyId + ":" + company.getBankruptcyRiskStartDay();
        if (CorporateActionManager.keys().contains(riskKey) || !company.canDeposit(amount)
                || !AccountManager.withdraw(investor, amount)) {
            return CorporateActionManager.Result.fail("Recapitalization unavailable or funds insufficient");
        }
        if (!company.deposit(amount)) {
            AccountManager.deposit(investor, amount);
            return CorporateActionManager.Result.fail("Recapitalization settlement failed");
        }
        CorporateActionManager.putKeyDirect(key);
        CorporateActionManager.putKeyDirect(riskKey);
        CorporateActionManager.putAnnouncementDirect(new CorporateAnnouncement(UUID.randomUUID(), companyId,
                CorporateAnnouncement.Type.RECAPITALIZATION, day, UUID.nameUUIDFromBytes(key.getBytes()),
                "recapitalization.completed", "investor=" + investor + ",amount=" + amount));
        AccountManager.addTransactionRecord(new TransactionRecord(investor, companyId, amount,
                TransactionType.COMPANY_RECAPITALIZATION, investor, company.getName(), 0));
        if (!CompanyProposalManager.markExecuted(proposalId, "紧急再融资已完成")) {
            throw new IllegalStateException("validated recapitalization authorization could not be consumed");
        }
        EconomySavedData.markDirty();
        return CorporateActionManager.Result.ok(companyId, "Emergency recapitalization completed");
    }

    public static synchronized CorporateActionManager.Result purchaseInventoryAsset(
            UUID sellerApprover, UUID proposalId, long day, String operationKey) {
        CompanyProposal proposal = CompanyProposalManager.getProposal(proposalId);
        UUID buyerCompanyId = proposal == null ? null : proposal.getCompanyId();
        String[] asset = proposal == null || proposal.getTextValue() == null
                ? new String[0] : proposal.getTextValue().split("\\|", 2);
        UUID sellerCompanyId;
        try { sellerCompanyId = asset.length == 2 ? UUID.fromString(asset[0]) : null; }
        catch (IllegalArgumentException ignored) { sellerCompanyId = null; }
        String commodityId = asset.length == 2 ? asset[1] : "";
        long quantityLong = proposal == null ? 0 : proposal.getValue2();
        int quantity = quantityLong > Integer.MAX_VALUE ? -1 : (int) quantityLong;
        long price = proposal == null ? 0 : proposal.getValue1();
        Company buyer = CompanyManager.getCompany(buyerCompanyId);
        Company seller = CompanyManager.getCompany(sellerCompanyId);
        String key = "asset-purchase:" + operationKey;
        if (proposal == null
                || !proposal.isExecutableAuthorization(CompanyProposalType.MAJOR_ASSET_PURCHASE, buyerCompanyId)
                || buyer == null || seller == null || buyer == seller || commodityId == null || commodityId.isBlank()
                || commodityId.length() > 64 || quantity <= 0 || price <= 0 || operationKey == null
                || operationKey.isBlank() || operationKey.length() > 96 || CorporateActionManager.keys().contains(key)
                || !GovernanceAuthorizationService.mayManage(sellerApprover, sellerCompanyId)
                || buyer.getCash() < price || !seller.canDeposit(price)
                || seller.getInventoryAmount(commodityId) < quantity || !buyer.canAddInventory(commodityId, quantity)) {
            return CorporateActionManager.Result.fail("Invalid or unauthorized asset purchase");
        }
        if (!buyer.withdraw(price) || !seller.removeInventory(commodityId, quantity)) {
            if (buyer.canDeposit(price)) buyer.deposit(price);
            return CorporateActionManager.Result.fail("Asset reservation failed");
        }
        if (!buyer.addInventory(commodityId, quantity) || !seller.deposit(price)) {
            seller.addInventory(commodityId, quantity);
            buyer.deposit(price);
            return CorporateActionManager.Result.fail("Asset settlement failed");
        }
        CorporateActionManager.putKeyDirect(key);
        CorporateActionManager.putAnnouncementDirect(new CorporateAnnouncement(UUID.randomUUID(), buyerCompanyId,
                CorporateAnnouncement.Type.ASSET_SALE, day, UUID.nameUUIDFromBytes(key.getBytes()),
                "asset.purchase.completed", "seller=" + sellerCompanyId + ",item=" + commodityId
                + ",quantity=" + quantity + ",price=" + price));
        AccountManager.addTransactionRecord(new TransactionRecord(buyerCompanyId, sellerCompanyId, price,
                TransactionType.COMPANY_ASSET_TRANSFER, proposal.getCreatorId(), commodityId, quantity));
        if (!CompanyProposalManager.markExecuted(proposalId, "重大资产交易已完成")) {
            throw new IllegalStateException("validated asset-purchase authorization could not be consumed");
        }
        EconomySavedData.markDirty();
        return CorporateActionManager.Result.ok(buyerCompanyId, "Asset purchase completed");
    }
}
