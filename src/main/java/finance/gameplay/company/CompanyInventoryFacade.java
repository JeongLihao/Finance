package finance.gameplay.company;

import finance.commodity.CommodityInventoryManager;
import finance.company.Company;
import finance.warehouse.WarehouseManager;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 公司库存的唯一入口。玩家公司的货全在托管账户（{@link #custodyId}，机器产出、收货箱、仓库都存这里）；
 * NPC 公司和系统公司没有厂房，货在公司对象自带的旧库存里。两种公司各用一边，不混用。
 */
public final class CompanyInventoryFacade {
    public record Consumption(Map<String, Integer> custody, Map<String, Integer> legacy) {}

    private CompanyInventoryFacade() {}

    public static UUID custodyId(UUID companyId) {
        if (companyId == null) throw new IllegalArgumentException("companyId");
        return UUID.nameUUIDFromBytes(("finance-company-custody:" + companyId).getBytes(StandardCharsets.UTF_8));
    }

    /** 玩家公司用托管账户，NPC 公司用旧库存。 */
    public static boolean usesCustody(Company company) {
        return company != null && company.isPlayerOwned();
    }

    /** 可动用的数量（托管账户里已抵押的不算）。 */
    public static int availableInput(Company company, String commodityId) {
        if (company == null) return 0;
        if (!usesCustody(company)) return Math.max(0, company.getInventoryAmount(commodityId));
        return Math.max(
                0,
                finance.collateral.InventoryCollateralManager.available(
                        custodyId(company.getCompanyId()), commodityId));
    }

    /** Total authority-visible inventory, including pledged custody that is not spendable. */
    public static int totalInventory(Company company, String commodityId) {
        if (company == null || commodityId == null) return 0;
        if (!usesCustody(company)) return company.getInventoryAmount(commodityId);
        return CommodityInventoryManager.getCommodityAmount(custodyId(company.getCompanyId()), commodityId);
    }

    public static int availableInsurableInventory(Company company, String commodityId) {
        if (company == null || commodityId == null) return 0;
        return availableInput(company, commodityId);
    }

    /** 这家公司看得见的全部库存（含已抵押），商品 ID → 数量；只读副本。 */
    public static Map<String, Integer> inventorySnapshot(Company company) {
        if (company == null) return Map.of();
        Map<String, Integer> source = usesCustody(company)
                ? CommodityInventoryManager.getInventory(custodyId(company.getCompanyId()))
                        .getAllCommodities()
                : company.getInventory();
        Map<String, Integer> out = new LinkedHashMap<>();
        source.forEach((id, amount) -> {
            if (amount != null && amount > 0) out.put(id, amount);
        });
        return out;
    }

    /** Removes only unpledged stock and returns a rollback token for event-level atomicity. */
    public static Consumption consumeInsurableLoss(Company company, String commodityId, int quantity) {
        if (company == null || commodityId == null || commodityId.isBlank() || quantity <= 0) return null;
        return consumeInputAtomically(company, Map.of(commodityId, quantity));
    }

    public static Consumption consumeInputAtomically(Company company, Map<String, Integer> requirements) {
        if (company == null || requirements == null) return null;
        for (Map.Entry<String, Integer> entry : requirements.entrySet())
            if (entry.getValue() == null
                    || entry.getValue() <= 0
                    || availableInput(company, entry.getKey()) < entry.getValue()) return null;
        boolean custodyMode = usesCustody(company);
        UUID custodyId = custodyId(company.getCompanyId());
        Map<String, Integer> custody = new LinkedHashMap<>(), legacy = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : requirements.entrySet()) {
            if (custodyMode) {
                if (!CommodityInventoryManager.removeCommodity(custodyId, entry.getKey(), entry.getValue())) {
                    rollback(company, new Consumption(custody, legacy));
                    return null;
                }
                custody.put(entry.getKey(), entry.getValue());
            } else {
                if (!company.removeInventory(entry.getKey(), entry.getValue())) {
                    rollback(company, new Consumption(custody, legacy));
                    return null;
                }
                legacy.put(entry.getKey(), entry.getValue());
            }
        }
        return new Consumption(Map.copyOf(custody), Map.copyOf(legacy));
    }

    public static boolean rollback(Company company, Consumption consumption) {
        if (company == null || consumption == null) return false;
        UUID custodyId = custodyId(company.getCompanyId());
        for (var e : consumption.custody().entrySet())
            if (!CommodityInventoryManager.canAddCommodity(custodyId, e.getKey(), e.getValue())) return false;
        for (var e : consumption.legacy().entrySet())
            if (!company.canAddInventory(e.getKey(), e.getValue())) return false;
        consumption.custody().forEach((id, amount) -> CommodityInventoryManager.addCommodity(custodyId, id, amount));
        consumption.legacy().forEach(company::addInventory);
        return true;
    }

    public static boolean canAddOutput(Company company, Map<String, Integer> outputs) {
        if (company == null || outputs == null) return false;
        if (!usesCustody(company))
            return outputs.entrySet().stream().allMatch(e -> company.canAddInventory(e.getKey(), e.getValue()));
        UUID custody = custodyId(company.getCompanyId());
        long total = outputs.values().stream().mapToLong(Integer::longValue).sum();
        return total > 0
                && WarehouseManager.canDepositCapacity(custody, (int) Math.min(Integer.MAX_VALUE, total))
                && outputs.entrySet().stream()
                        .allMatch(e -> CommodityInventoryManager.canAddCommodity(custody, e.getKey(), e.getValue()));
    }

    public static boolean addOutputAtomically(Company company, Map<String, Integer> outputs) {
        if (!canAddOutput(company, outputs)) return false;
        boolean custodyMode = usesCustody(company);
        UUID custody = custodyId(company.getCompanyId());
        for (var e : outputs.entrySet()) {
            boolean added = custodyMode
                    ? CommodityInventoryManager.addCommodity(custody, e.getKey(), e.getValue())
                    : company.addInventory(e.getKey(), e.getValue());
            if (!added) throw new IllegalStateException("prevalidated company output failed");
        }
        return true;
    }

    /** 公司之间转手（资产收购等）能不能收下：只看数量上限，不看仓库容量（和机器产出不同）。 */
    public static boolean canReceive(Company company, String commodityId, int quantity) {
        if (company == null || commodityId == null || quantity <= 0) return false;
        return usesCustody(company)
                ? CommodityInventoryManager.canAddCommodity(custodyId(company.getCompanyId()), commodityId, quantity)
                : company.canAddInventory(commodityId, quantity);
    }

    public static boolean receive(Company company, String commodityId, int quantity) {
        if (!canReceive(company, commodityId, quantity)) return false;
        return usesCustody(company)
                ? CommodityInventoryManager.addCommodity(custodyId(company.getCompanyId()), commodityId, quantity)
                : company.addInventory(commodityId, quantity);
    }

    /**
     * 读档后把玩家公司留在旧库存里的货并进托管账户（旧版本的“经营模式”会把货放在旧库存）。
     * 不检查仓库容量：这是已有的货换个地方记账，超出容量的照常显示“超量”。返回搬了多少件。
     */
    public static long migrateLegacyToCustody(Company company) {
        if (!usesCustody(company) || company.getInventory().isEmpty()) return 0;
        UUID custody = custodyId(company.getCompanyId());
        long moved = 0;
        for (var e : Map.copyOf(company.getInventory()).entrySet()) {
            int amount = e.getValue() == null ? 0 : e.getValue();
            if (amount <= 0) continue;
            if (!CommodityInventoryManager.canAddCommodity(custody, e.getKey(), amount)) continue;
            if (!company.removeInventory(e.getKey(), amount)) continue;
            CommodityInventoryManager.addCommodity(custody, e.getKey(), amount);
            moved += amount;
        }
        return moved;
    }
}
