package finance.gameplay.company;

import finance.account.AccountManager;
import finance.company.*;
import finance.contract.*;
import finance.commodity.Commodity;
import finance.commodity.CommodityCategory;
import finance.commodity.CommodityRegistry;
import org.junit.jupiter.api.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class CompanyContractIntegrationTest {
    private Company company; private UUID owner;
    @BeforeEach void setup(){CompanyManager.clearCompaniesDirect();ContractManager.clearDirect();AccountManager.clearAccountsDirect();CommodityRegistry.register(new Commodity("company_contract_iron","minecraft:iron_ingot","Contract Iron",CommodityCategory.RAW_MATERIALS,10));owner=UUID.randomUUID();company=new Company(UUID.randomUUID(),"Buyer",CompanyType.RAW_MATERIALS,20_000,owner);CompanyManager.registerDirect(company);CompanyGameplayManager.createForNewCompany(company);}
    @AfterEach void cleanup(){CompanyManager.clearCompaniesDirect();ContractManager.clearDirect();AccountManager.clearAccountsDirect();CommodityRegistry.removeCommodity("company_contract_iron");}
    @Test void companyProcurementEscrowsExistingCashAndBankruptcyCancellationRefundsExactly(){long cash=company.getCash();FinanceContract contract=CompanyContractService.publishProcurement(owner,company.getCompanyId(),"company_contract_iron",20,500,1,3,"publish");assertNotNull(contract);assertEquals(cash-500,company.getCash());assertEquals(500,AccountManager.getAccounts().get(contract.escrowAccountId()).getBalance());assertTrue(ContractManager.cancelCompanyContracts(company.getCompanyId()));assertEquals(ContractStatus.CANCELLED,contract.status());assertEquals(cash,company.getCash());assertEquals(0,AccountManager.getAccounts().get(contract.escrowAccountId()).getBalance());assertTrue(ContractManager.cancelCompanyContracts(company.getCompanyId()));assertEquals(cash,company.getCash());}
    @Test void riskCompanyAndDuplicateCommodityCannotPublish(){FinanceContract first=CompanyContractService.publishProcurement(owner,company.getCompanyId(),"company_contract_iron",10,100,1,3,"one");assertNotNull(first);assertNull(CompanyContractService.publishProcurement(owner,company.getCompanyId(),"company_contract_iron",10,100,1,3,"two"));company.setBankruptcyRisk(true,1);assertNull(CompanyContractService.publishProcurement(owner,company.getCompanyId(),"company_contract_iron",10,100,1,3,"three"));}
}
