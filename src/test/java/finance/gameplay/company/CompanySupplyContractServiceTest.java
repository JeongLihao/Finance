package finance.gameplay.company;

import finance.account.AccountManager;
import finance.commodity.Commodity;
import finance.commodity.CommodityCategory;
import finance.commodity.CommodityRegistry;
import finance.company.Company;
import finance.company.CompanyManager;
import finance.company.CompanyType;
import finance.contract.ContractManager;
import finance.contract.ContractStatus;
import finance.contract.FinanceContract;
import finance.data.EconomySavedData;
import finance.data.serializer.AccountDataSerializer;
import finance.data.serializer.ContractDataSerializer;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CompanySupplyContractServiceTest {
    private UUID buyerOwner, sellerOwner;
    private Company buyer, seller;

    @BeforeEach void setup() {
        EconomySavedData.resetRuntimeState();
        CommodityRegistry.register(new Commodity("company_supply_iron", "minecraft:iron_ingot", "Supply Iron",
                CommodityCategory.RAW_MATERIALS, 10));
        buyerOwner = UUID.randomUUID(); sellerOwner = UUID.randomUUID();
        buyer = company("Buyer", buyerOwner, 1_000);
        seller = company("Seller", sellerOwner, 0);
        assertTrue(seller.addInventory("company_supply_iron", 10));
    }

    @AfterEach void cleanup() { EconomySavedData.resetRuntimeState(); }

    @Test void sellerCompanyCanAcceptAndSettlePartialDeliveriesFromEscrow() {
        FinanceContract contract = CompanyContractService.publishProcurement(buyerOwner, buyer.getCompanyId(),
                "company_supply_iron", 10, 100, 1, 5, "publish");
        assertNotNull(contract);
        assertTrue(CompanySupplyContractService.accept(sellerOwner, seller.getCompanyId(), contract.id(),
                1, "accept").success());

        assertTrue(CompanySupplyContractService.deliver(sellerOwner, seller.getCompanyId(), contract.id(),
                4, 2, "partial").success());
        assertEquals(ContractStatus.ACCEPTED, contract.status());
        assertEquals(4, contract.deliveredQuantity());
        assertEquals(60, contract.remainingReward());
        assertEquals(60, AccountManager.getBalance(contract.escrowAccountId()));
        assertEquals(40, seller.getCash());
        assertEquals(4, buyer.getInventoryAmount("company_supply_iron"));
        assertEquals(6, seller.getInventoryAmount("company_supply_iron"));

        assertTrue(CompanySupplyContractService.deliver(sellerOwner, seller.getCompanyId(), contract.id(),
                6, 3, "finish").success());
        assertEquals(ContractStatus.COMPLETED, contract.status());
        assertEquals(0, AccountManager.getBalance(contract.escrowAccountId()));
        assertEquals(100, seller.getCash());
        assertEquals(10, buyer.getInventoryAmount("company_supply_iron"));
        assertEquals(0, seller.getInventoryAmount("company_supply_iron"));
        assertEquals(1_000, buyer.getCash() + seller.getCash());
    }

    @Test void partialContractRoundTripsAndExpiryRefundsOnlyRemainingEscrow() {
        FinanceContract contract = CompanyContractService.publishProcurement(buyerOwner, buyer.getCompanyId(),
                "company_supply_iron", 10, 100, 1, 2, "publish-expiry");
        assertNotNull(contract);
        assertTrue(CompanySupplyContractService.accept(sellerOwner, seller.getCompanyId(), contract.id(),
                1, "accept-expiry").success());
        assertTrue(CompanySupplyContractService.deliver(sellerOwner, seller.getCompanyId(), contract.id(),
                4, 1, "partial-expiry").success());

        CompoundTag root = new CompoundTag();
        AccountDataSerializer.save(root); ContractDataSerializer.save(root);
        ContractManager.clearDirect(); AccountManager.clearAccountsDirect();
        AccountDataSerializer.load(root); ContractDataSerializer.load(root);
        FinanceContract loaded = ContractManager.get(contract.id());
        assertNotNull(loaded);
        assertEquals(seller.getCompanyId(), loaded.acceptedCompanyId());
        assertEquals(4, loaded.deliveredQuantity());
        assertEquals(60, loaded.remainingReward());

        ContractManager.processDay(4);
        assertEquals(ContractStatus.EXPIRED, loaded.status());
        assertEquals(960, buyer.getCash());
        assertEquals(40, seller.getCash());
        assertEquals(0, AccountManager.getBalance(loaded.escrowAccountId()));
    }

    @Test void buyerCannotAcceptItsOwnContractAndDeliveryIsIdempotent() {
        FinanceContract contract = CompanyContractService.publishProcurement(buyerOwner, buyer.getCompanyId(),
                "company_supply_iron", 10, 100, 1, 5, "publish-guard");
        assertNotNull(contract);
        assertFalse(CompanySupplyContractService.accept(buyerOwner, buyer.getCompanyId(), contract.id(),
                1, "self").success());
        assertTrue(CompanySupplyContractService.accept(sellerOwner, seller.getCompanyId(), contract.id(),
                1, "accept-guard").success());
        assertTrue(CompanySupplyContractService.deliver(sellerOwner, seller.getCompanyId(), contract.id(),
                5, 2, "same-delivery").success());
        assertTrue(CompanySupplyContractService.deliver(sellerOwner, seller.getCompanyId(), contract.id(),
                5, 2, "same-delivery").success());
        assertEquals(5, contract.deliveredQuantity());
        assertEquals(50, seller.getCash());
    }

    private static Company company(String name, UUID owner, long cash) {
        Company company = new Company(UUID.randomUUID(), name, CompanyType.RAW_MATERIALS, cash, owner);
        CompanyManager.registerDirect(company);
        assertTrue(CompanyGameplayManager.restore(new CompanyGameplayProfile(company.getCompanyId(),
                CompanyOperatingMode.LEGACY_AUTOMATIC)));
        return company;
    }
}
