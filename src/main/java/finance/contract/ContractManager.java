package finance.contract;

import finance.account.Account;
import finance.account.AccountManager;
import finance.account.TransactionRecord;
import finance.account.TransactionType;
import finance.commodity.Commodity;
import finance.commodity.CommodityInventoryManager;
import finance.commodity.CommodityRegistry;
import finance.data.EconomySavedData;
import finance.diagnostic.ModuleHealthRegistry;
import finance.market.MarketPrice;
import finance.market.NpcMarketMaker;
import finance.util.MathUtil;
import net.minecraft.resources.ResourceLocation;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import finance.company.Company;
import finance.company.CompanyManager;

public final class ContractManager {
    public static final int MAX_ACTIVE_PER_PLAYER = 3;
    public static final int MAX_HISTORY = 256;
    public static final int MAX_RECORDS = 2_048;
    private static final int LOW_STOCK_THRESHOLD = 30_000;
    private static final int MAX_DAILY_GENERATION = 3;
    private static final long MAX_REWARD = 1_000_000L;
    private static final Map<UUID, FinanceContract> CONTRACTS = new LinkedHashMap<>();
    private static final LinkedHashSet<String> DAILY_KEYS = new LinkedHashSet<>();

    private ContractManager() {}
    public static Map<UUID, FinanceContract> contracts() { return java.util.Collections.unmodifiableMap(CONTRACTS); }
    public static int lowStockThreshold() { return LOW_STOCK_THRESHOLD; }
    public static FinanceContract get(UUID id) { return id == null ? null : CONTRACTS.get(id); }
    public static Set<String> dailyKeys() { return Set.copyOf(DAILY_KEYS); }

    public static synchronized boolean restore(FinanceContract contract) {
        if (contract == null || CONTRACTS.containsKey(contract.id())) return false;
        CONTRACTS.put(contract.id(), contract); return true;
    }
    public static void restoreDailyKey(String key) { if (key != null && !key.isBlank() && key.length() <= 96) addDailyKey(key); }

    public static synchronized FinanceContract createNpcProcurement(String commodityId, int quantity,
                                                                     long reward, long day, long deadlineDay) {
        if(!finance.config.FinanceConfig.contractsEnabled()
                ||!ModuleHealthRegistry.mayWrite(ModuleHealthRegistry.Module.CONTRACT))return null;
        if (CommodityRegistry.getCommodity(commodityId) == null || quantity <= 0 || reward <= 0
                || reward > MAX_REWARD || day < 0 || deadlineDay <= day || hasLiveForCommodity(commodityId)) return null;
        pruneHistory();
        Account issuer = AccountManager.getAccounts().get(NpcMarketMaker.NPC_UUID);
        if (CONTRACTS.size() >= MAX_RECORDS || issuer == null || issuer.getBalance() < reward) return null;
        UUID escrow = UUID.randomUUID();
        Account escrowAccount = AccountManager.getOrCreateSystemAccount(escrow);
        if (escrowAccount.getBalance() != 0 || !AccountManager.moveFunds(NpcMarketMaker.NPC_UUID, escrow, reward)) {
            AccountManager.getAccounts().remove(escrow);
            return null;
        }
        FinanceContract contract;
        try {
            contract = new FinanceContract(UUID.randomUUID(), ContractType.PROCUREMENT,
                    ContractIssuerType.NPC_MARKET, NpcMarketMaker.NPC_UUID, commodityId, quantity, 0,
                    reward, escrow, null, day, deadlineDay, null, ContractStatus.OPEN, "");
        } catch (RuntimeException exception) {
            AccountManager.moveFunds(escrow, NpcMarketMaker.NPC_UUID, reward);
            AccountManager.getAccounts().remove(escrow);
            return null;
        }
        CONTRACTS.put(contract.id(), contract);
        AccountManager.addTransactionRecord(new TransactionRecord(NpcMarketMaker.NPC_UUID, escrow, reward,
                TransactionType.CONTRACT_ESCROW, NpcMarketMaker.NPC_UUID, commodityId, quantity));
        pruneHistory();
        EconomySavedData.markDirty();
        return contract;
    }

    public static synchronized FinanceContract createCompanyProcurement(UUID companyId, String commodityId,
                                                                         int quantity, long reward, long day,
                                                                         long deadlineDay) {
        if(!finance.config.FinanceConfig.contractsEnabled()
                ||!ModuleHealthRegistry.mayWrite(ModuleHealthRegistry.Module.CONTRACT))return null;
        Company company = CompanyManager.getCompany(companyId);
        if (company == null || company.isBankruptcyRisk() || CommodityRegistry.getCommodity(commodityId) == null
                || quantity <= 0 || reward <= 0 || reward > MAX_REWARD || day < 0 || deadlineDay <= day
                || hasLiveForIssuerCommodity(companyId, commodityId)) return null;
        pruneHistory();
        if (CONTRACTS.size() >= MAX_RECORDS || company.getCash() < reward) return null;
        UUID escrowId = UUID.randomUUID(); Account escrow = AccountManager.getOrCreateSystemAccount(escrowId);
        if (!company.withdraw(reward) || !escrow.deposit(reward)) {
            if (escrow.getBalance() > 0) { escrow.withdraw(reward); company.deposit(reward); }
            AccountManager.getAccounts().remove(escrowId); return null;
        }
        FinanceContract contract;
        try {
            contract = new FinanceContract(UUID.randomUUID(), ContractType.PROCUREMENT, ContractIssuerType.COMPANY,
                    companyId, commodityId, quantity, 0, reward, escrowId, null, day, deadlineDay,
                    null, ContractStatus.OPEN, "");
        } catch (RuntimeException exception) {
            escrow.withdraw(reward); company.deposit(reward); AccountManager.getAccounts().remove(escrowId); return null;
        }
        CONTRACTS.put(contract.id(), contract);
        AccountManager.addTransactionRecord(new TransactionRecord(companyId, escrowId, reward,
                TransactionType.CONTRACT_ESCROW, company.getOwnerId(), company.getName() + "/" + commodityId, quantity));
        EconomySavedData.markDirty(); return contract;
    }

    public static synchronized int generateForShortages(long day) {
        if (day < 0 || !finance.config.FinanceConfig.contractsEnabled()
                || !ModuleHealthRegistry.mayWrite(ModuleHealthRegistry.Module.MARKET)
                ||!ModuleHealthRegistry.mayWrite(ModuleHealthRegistry.Module.CONTRACT)) return 0;
        List<Commodity> commodities = new ArrayList<>(CommodityRegistry.getAllCommodities());
        commodities.sort(Comparator.comparing(Commodity::getId));
        int generated = 0;
        for (Commodity commodity : commodities) {
            if (generated >= MAX_DAILY_GENERATION) break;
            String dailyKey = day + ":" + commodity.getId();
            if (DAILY_KEYS.contains(dailyKey)) continue;
            if (!hasPhysicalItemMapping(commodity)) { addDailyKey(dailyKey); continue; }
            int stock = CommodityInventoryManager.getCommodityAmount(NpcMarketMaker.NPC_UUID, commodity.getId());
            if (stock >= LOW_STOCK_THRESHOLD || hasLiveForCommodity(commodity.getId())) { addDailyKey(dailyKey); continue; }
            MarketPrice price = NpcMarketMaker.getMarketPrice(commodity.getId());
            if (price == null || price.getBidPrice() <= 0) { addDailyKey(dailyKey); continue; }
            int quantity = Math.min(256, LOW_STOCK_THRESHOLD - stock);
            Account npc = AccountManager.getAccounts().get(NpcMarketMaker.NPC_UUID);
            if (npc == null) { addDailyKey(dailyKey); continue; }
            long npcBalance = npc.getBalance();
            long budget = Math.min(MAX_REWARD, npcBalance / 10);
            long base = MathUtil.multiplyExactOrNegative1(price.getBidPrice(), quantity);
            long reward = base <= 0 ? -1 : BigInteger.valueOf(base).multiply(BigInteger.valueOf(110))
                    .divide(BigInteger.valueOf(100)).min(BigInteger.valueOf(budget)).longValue();
            if (reward > 0 && createNpcProcurement(commodity.getId(), quantity, reward, day, day + 3) != null) generated++;
            addDailyKey(dailyKey);
        }
        return generated;
    }

    /** Event-specific procurement with a separate daily key and the same escrow/budget boundary. */
    public static synchronized FinanceContract generateEventProcurement(String eventKey,String commodityId,long day){
        if(!finance.config.FinanceConfig.contractsEnabled()
                ||!ModuleHealthRegistry.mayWrite(ModuleHealthRegistry.Module.CONTRACT))return null;
        if(eventKey==null||eventKey.isBlank()||eventKey.length()>64||day<0||CommodityRegistry.getCommodity(commodityId)==null)return null;
        String key="event:"+day+":"+Integer.toUnsignedString(eventKey.hashCode(),36)+":"+commodityId;
        if(DAILY_KEYS.contains(key)){return null;}addDailyKey(key);
        Commodity commodity=CommodityRegistry.getCommodity(commodityId);if(!hasPhysicalItemMapping(commodity)||hasLiveForCommodity(commodityId))return null;
        MarketPrice price=NpcMarketMaker.getMarketPrice(commodityId);Account npc=AccountManager.getAccounts().get(NpcMarketMaker.NPC_UUID);
        if(price==null||price.getBidPrice()<=0||npc==null)return null;
        int quantity=128;long base=MathUtil.multiplyExactOrNegative1(price.getBidPrice(),quantity);if(base<=0)return null;
        long reward=BigInteger.valueOf(base).multiply(BigInteger.valueOf(125)).divide(BigInteger.valueOf(100))
                .min(BigInteger.valueOf(Math.min(MAX_REWARD,npc.getBalance()/10))).longValue();
        return reward>0?createNpcProcurement(commodityId,quantity,reward,day,day+4):null;
    }

    public static synchronized void processDay(long day) {
        settleExpired(day);
        generateForShortages(day);
        pruneHistory();
    }

    static synchronized void settleExpired(long day) {
        for (FinanceContract contract : new ArrayList<>(CONTRACTS.values())) {
            if ((contract.status() == ContractStatus.OPEN || contract.status() == ContractStatus.ACCEPTED)
                    && day > contract.deadlineDay()) refundExpired(contract);
        }
        pruneHistory();
    }

    private static void refundExpired(FinanceContract contract) {
        Account escrow = AccountManager.getAccounts().get(contract.escrowAccountId());
        if (escrow == null) { contract.setFailureReason("missing escrow account"); return; }
        long balance = escrow.getBalance();
        if (balance != contract.rewardAmount() || !refundToIssuer(contract, escrow, balance)) {
            contract.setFailureReason("escrow refund blocked"); return;
        }
        contract.expire();
        AccountManager.addTransactionRecord(new TransactionRecord(contract.escrowAccountId(), contract.issuerId(),
                balance, TransactionType.CONTRACT_REFUND, contract.acceptedPlayerId(),
                contract.commodityId(), contract.requiredQuantity()));
        EconomySavedData.markDirty();
    }

    public static int activeFor(UUID playerId) {
        int count = 0;
        for (FinanceContract contract : CONTRACTS.values()) if (contract.status() == ContractStatus.ACCEPTED
                && playerId.equals(contract.acceptedPlayerId())) count++;
        return count;
    }
    public static boolean hasLiveForCommodity(String commodityId) {
        return CONTRACTS.values().stream().anyMatch(contract -> contract.commodityId().equals(commodityId)
                && (contract.status() == ContractStatus.OPEN || contract.status() == ContractStatus.ACCEPTED));
    }
    public static boolean hasLiveForIssuerCommodity(UUID issuerId, String commodityId) {
        return issuerId != null && CONTRACTS.values().stream().anyMatch(contract -> issuerId.equals(contract.issuerId())
                && contract.commodityId().equals(commodityId)
                && (contract.status() == ContractStatus.OPEN || contract.status() == ContractStatus.ACCEPTED));
    }

    public static synchronized boolean cancelCompanyContracts(UUID companyId) {
        Company company = CompanyManager.getCompany(companyId);
        if (company == null) return false;
        BigInteger total = BigInteger.ZERO;
        for (FinanceContract contract : CONTRACTS.values()) {
            if (!companyId.equals(contract.issuerId()) || contract.issuerType() != ContractIssuerType.COMPANY
                    || contract.status().terminal()) continue;
            Account escrow = AccountManager.getAccounts().get(contract.escrowAccountId());
            if (escrow == null || escrow.getBalance() != contract.rewardAmount()) return false;
            total = total.add(BigInteger.valueOf(contract.rewardAmount()));
        }
        if (total.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0 || total.signum() > 0
                && !company.canDeposit(total.longValue())) return false;
        for (FinanceContract contract : CONTRACTS.values()) {
            if (!companyId.equals(contract.issuerId()) || contract.issuerType() != ContractIssuerType.COMPANY
                    || contract.status().terminal()) continue;
            Account escrow = AccountManager.getAccounts().get(contract.escrowAccountId());
            if (escrow != null && escrow.getBalance() == contract.rewardAmount()
                    && refundToIssuer(contract, escrow, contract.rewardAmount())) contract.cancel();
            else contract.setFailureReason("bankruptcy cancellation refund blocked");
        }
        EconomySavedData.markDirty();
        return true;
    }

    private static boolean refundToIssuer(FinanceContract contract, Account escrow, long amount) {
        if (contract.issuerType() == ContractIssuerType.COMPANY) {
            Company company = CompanyManager.getCompany(contract.issuerId());
            if (company == null || !company.canDeposit(amount) || !escrow.withdraw(amount)) return false;
            if (!company.deposit(amount)) { escrow.deposit(amount); return false; }
            return true;
        }
        Account issuer = AccountManager.getAccounts().get(contract.issuerId());
        return issuer != null && issuer.canDeposit(amount)
                && AccountManager.moveFunds(contract.escrowAccountId(), contract.issuerId(), amount);
    }

    /**
     * Daily economic simulation must remain independent from the live Minecraft item registry. The
     * warehouse boundary performs the authoritative registry lookup when a player actually delivers.
     */
    private static boolean hasPhysicalItemMapping(Commodity commodity) {
        if (commodity == null || commodity.getItemId() == null) return false;
        ResourceLocation id = ResourceLocation.tryParse(commodity.getItemId());
        return id != null && !"minecraft:air".equals(id.toString());
    }

    private static void addDailyKey(String key) {
        DAILY_KEYS.add(key);
        while (DAILY_KEYS.size() > 512) DAILY_KEYS.remove(DAILY_KEYS.iterator().next());
    }
    private static void pruneHistory() {
        List<FinanceContract> terminal = CONTRACTS.values().stream().filter(c -> {
            Account account = AccountManager.getAccounts().get(c.escrowAccountId());
            return c.status().terminal() && account != null && account.getBalance() == 0;
        }).sorted(Comparator.comparingLong(FinanceContract::createdDay)).toList();
        int remove = terminal.size() - MAX_HISTORY;
        for (int i = 0; i < remove; i++) {
            FinanceContract removed = CONTRACTS.remove(terminal.get(i).id());
            if (removed != null) AccountManager.getAccounts().remove(removed.escrowAccountId());
        }
    }
    public static void clearDirect() { CONTRACTS.clear(); DAILY_KEYS.clear(); }
}
