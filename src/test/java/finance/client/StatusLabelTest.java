package finance.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 界面上显示的状态、风险等级不能再是英文常量名。 */
class StatusLabelTest {

    private static void assertAllTranslated(Class<? extends Enum<?>> type) {
        for (Enum<?> value : type.getEnumConstants()) {
            String label = FinanceFormat.label(value);
            assertNotEquals(value.name(), label, type.getSimpleName() + "." + value.name());
            assertFalse(label.isBlank(), type.getSimpleName() + "." + value.name());
        }
    }

    @Test void everyDisplayedStatusEnumHasAChineseLabel() {
        assertAllTranslated(finance.debt.BondStatus.class);
        assertAllTranslated(finance.bank.BankStatus.class);
        assertAllTranslated(finance.regional.RegionalSupplyPressure.class);
        assertAllTranslated(finance.collateral.InventoryCollateralStatus.class);
        assertAllTranslated(finance.futures.MarginRiskStatus.class);
        assertAllTranslated(finance.insurance.ClaimStatus.class);
        assertAllTranslated(finance.gameplay.company.CompanyOperatingHealth.class);
    }

    @Test void stringsAndUnknownValuesWork() {
        assertEquals("短缺", FinanceFormat.label("SHORTAGE"));
        assertEquals("自定义", FinanceFormat.label("自定义"));
        assertEquals("", FinanceFormat.label(null));
    }
}
