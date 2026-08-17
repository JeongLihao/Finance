package finance.data.serializer;

import finance.account.Account;
import finance.account.AccountManager;
import finance.company.CompanyManager;
import finance.governance.*;
import finance.stock.Stock;
import finance.stock.StockMarketManager;
import finance.stock.StockPortfolioManager;
import net.minecraft.nbt.*;

import java.math.BigInteger;
import java.util.*;

public final class GovernanceDataSerializer {
    private static final int MAX_ACCEPTANCES = 4096;
    private static final int MAX_ANNOUNCEMENTS = 2048;
    private static final int MAX_KEYS = 8192;
    private GovernanceDataSerializer() {}

    public static void save(CompoundTag root) {
        CompoundTag section = new CompoundTag();
        ListTag buybacks = new ListTag();
        for (BuybackPlan plan : CorporateActionManager.buybacks().values()) {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("Id", plan.id()); tag.putUUID("Company", plan.companyId());
            tag.putUUID("Escrow", plan.escrowId()); tag.putString("Symbol", plan.symbol());
            tag.putLong("Price", plan.price()); tag.putLong("Max", plan.maxShares());
            tag.putLong("Start", plan.startDay()); tag.putLong("End", plan.endDay());
            tag.putLong("Budget", plan.budget()); tag.putString("Status", plan.status().name());
            tag.put("Accepted", writeMap(plan.accepted())); tag.put("AcceptedCosts", writeMap(plan.acceptedCosts()));
            buybacks.add(tag);
        }
        section.put("Buybacks", buybacks);
        ListTag tenders = new ListTag();
        for (TenderOffer offer : CorporateActionManager.tenders().values()) {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("Id", offer.id()); tag.putUUID("Buyer", offer.buyerId());
            tag.putUUID("Company", offer.targetCompanyId()); tag.putUUID("Escrow", offer.escrowId());
            tag.putString("Symbol", offer.symbol()); tag.putLong("Price", offer.price());
            tag.putLong("Target", offer.targetShares()); tag.putLong("Minimum", offer.minShares());
            tag.putLong("Start", offer.startDay()); tag.putLong("End", offer.endDay());
            tag.putLong("Funds", offer.maxFunds()); tag.putString("Status", offer.status().name());
            tag.put("Accepted", writeMap(offer.accepted())); tag.put("AcceptedCosts", writeMap(offer.acceptedCosts()));
            tenders.add(tag);
        }
        section.put("Tenders", tenders);
        ListTag announcements = new ListTag();
        for (CorporateAnnouncement announcement : CorporateActionManager.announcements()) {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("Id", announcement.id()); tag.putUUID("Company", announcement.companyId());
            tag.putString("Type", announcement.type().name()); tag.putLong("Day", announcement.day());
            tag.putUUID("Business", announcement.businessId()); tag.putString("Title", announcement.titleKey());
            tag.putString("Details", announcement.details()); announcements.add(tag);
        }
        section.put("Announcements", announcements);
        ListTag controllers = new ListTag();
        CorporateActionManager.controllers().forEach((company, holder) -> {
            CompoundTag tag = new CompoundTag(); tag.putUUID("Company", company); tag.putUUID("Holder", holder);
            controllers.add(tag);
        });
        section.put("Controllers", controllers);
        ListTag keys = new ListTag();
        for (String key : CorporateActionManager.keys()) { CompoundTag tag = new CompoundTag(); tag.putString("Key", key); keys.add(tag); }
        section.put("Keys", keys);
        root.put("Governance", section);
    }

    public static void load(CompoundTag root) {
        CorporateActionManager.clearDirect();
        if (!root.contains("Governance", Tag.TAG_COMPOUND)) return;
        CompoundTag section = root.getCompound("Governance");
        for (Tag raw : section.getList("Buybacks", Tag.TAG_COMPOUND)) {
            try {
                CompoundTag tag = (CompoundTag) raw;
                UUID id=tag.getUUID("Id"), company=tag.getUUID("Company"), escrow=tag.getUUID("Escrow");
                String symbol=tag.getString("Symbol"); long price=tag.getLong("Price"), max=tag.getLong("Max");
                long start=tag.getLong("Start"), end=tag.getLong("End"), budget=tag.getLong("Budget");
                CapitalActionStatus status=CapitalActionStatus.valueOf(tag.getString("Status"));
                Map<UUID,Long> accepted=readMap(tag.getList("Accepted",Tag.TAG_COMPOUND));
                Map<UUID,Long> costs=readMap(tag.getList("AcceptedCosts",Tag.TAG_COMPOUND));
                Stock stock=StockMarketManager.getStockByCompanyId(company);
                boolean valid=id!=null&&company!=null&&escrow!=null&&stock!=null&&stock.getSymbol().equals(symbol)
                        &&price>0&&max>0&&start>=0&&end>start&&budget==exactMultiply(price,max)
                        &&sum(accepted)<=stock.getVotingShares()&&validEscrow(status,escrow,budget);
                if(!valid){if(status==CapitalActionStatus.OPEN)recoverBuyback(company,escrow,symbol,accepted,costs);continue;}
                BuybackPlan plan=new BuybackPlan(id,company,escrow,symbol,price,max,start,end,budget,status);
                accepted.forEach((holder,shares)->plan.accept(holder,shares,costs.getOrDefault(holder,0L)));
                if(!CorporateActionManager.putBuybackDirect(plan)&&status==CapitalActionStatus.OPEN)
                    recoverBuyback(company,escrow,symbol,accepted,costs);
            } catch (Exception ignored) {}
        }
        for (Tag raw : section.getList("Tenders",Tag.TAG_COMPOUND)) {
            try {
                CompoundTag tag=(CompoundTag)raw;
                UUID id=tag.getUUID("Id"),buyer=tag.getUUID("Buyer"),company=tag.getUUID("Company"),escrow=tag.getUUID("Escrow");
                String symbol=tag.getString("Symbol");long price=tag.getLong("Price"),target=tag.getLong("Target");
                long minimum=tag.getLong("Minimum"),start=tag.getLong("Start"),end=tag.getLong("End"),funds=tag.getLong("Funds");
                CapitalActionStatus status=CapitalActionStatus.valueOf(tag.getString("Status"));
                Map<UUID,Long>accepted=readMap(tag.getList("Accepted",Tag.TAG_COMPOUND));
                Map<UUID,Long>costs=readMap(tag.getList("AcceptedCosts",Tag.TAG_COMPOUND));
                Stock stock=StockMarketManager.getStockByCompanyId(company);
                boolean valid=id!=null&&buyer!=null&&company!=null&&escrow!=null&&stock!=null&&stock.getSymbol().equals(symbol)
                        &&price>0&&target>0&&minimum>0&&minimum<=target&&start>=0&&end>start
                        &&funds==exactMultiply(price,target)&&sum(accepted)<=stock.getVotingShares()
                        &&validEscrow(status,escrow,funds);
                if(!valid){if(status==CapitalActionStatus.OPEN)recoverTender(buyer,escrow,symbol,accepted,costs);continue;}
                TenderOffer offer=new TenderOffer(id,buyer,company,escrow,symbol,price,target,minimum,start,end,funds,status);
                accepted.forEach((holder,shares)->offer.accept(holder,shares,costs.getOrDefault(holder,0L)));
                if(!CorporateActionManager.putTenderDirect(offer)&&status==CapitalActionStatus.OPEN)
                    recoverTender(buyer,escrow,symbol,accepted,costs);
            }catch(Exception ignored){}
        }
        int count=0;
        for(Tag raw:section.getList("Announcements",Tag.TAG_COMPOUND)){
            if(++count>MAX_ANNOUNCEMENTS)break;
            try{CompoundTag tag=(CompoundTag)raw;String title=tag.getString("Title"),details=tag.getString("Details");
                if(title.length()>128||details.length()>512||tag.getLong("Day")<0)continue;
                CorporateActionManager.putAnnouncementDirect(new CorporateAnnouncement(tag.getUUID("Id"),tag.getUUID("Company"),
                        CorporateAnnouncement.Type.valueOf(tag.getString("Type")),tag.getLong("Day"),tag.getUUID("Business"),title,details));
            }catch(Exception ignored){}
        }
        for(Tag raw:section.getList("Controllers",Tag.TAG_COMPOUND))try{CompoundTag tag=(CompoundTag)raw;UUID company=tag.getUUID("Company"),holder=tag.getUUID("Holder");if(CompanyManager.getCompany(company)!=null)CorporateActionManager.putControllerDirect(company,holder);}catch(Exception ignored){}
        count=0;for(Tag raw:section.getList("Keys",Tag.TAG_COMPOUND)){if(++count>MAX_KEYS)break;String key=((CompoundTag)raw).getString("Key");if(key.length()<=160)CorporateActionManager.putKeyDirect(key);}
    }

    private static boolean validEscrow(CapitalActionStatus status,UUID escrow,long expected){Account account=AccountManager.getAccounts().get(escrow);long balance=account==null?0:account.getBalance();return status!=CapitalActionStatus.OPEN||balance==expected;}
    private static void recoverBuyback(UUID companyId,UUID escrow,String symbol,Map<UUID,Long>accepted,Map<UUID,Long>costs){finance.company.Company company=CompanyManager.getCompany(companyId);long cash=balance(escrow);if(company==null||!canRestoreShares(symbol,accepted)||(cash>0&&!company.canDeposit(cash)))return;restoreShares(symbol,accepted,costs);if(cash>0&&AccountManager.withdraw(escrow,cash))company.deposit(cash);}
    private static void recoverTender(UUID buyer,UUID escrow,String symbol,Map<UUID,Long>accepted,Map<UUID,Long>costs){long cash=balance(escrow);if(buyer==null||!canRestoreShares(symbol,accepted)||(cash>0&&!AccountManager.canDeposit(buyer,cash)))return;restoreShares(symbol,accepted,costs);if(cash>0)AccountManager.moveFunds(escrow,buyer,cash);}
    private static long balance(UUID id){Account account=id==null?null:AccountManager.getAccounts().get(id);return account==null?0:account.getBalance();}
    private static boolean canRestoreShares(String symbol,Map<UUID,Long>accepted){if(symbol==null||symbol.isBlank())return accepted.isEmpty();for(var entry:accepted.entrySet())if(!StockPortfolioManager.canAddHolding(entry.getKey(),symbol,entry.getValue()))return false;return true;}
    private static void restoreShares(String symbol,Map<UUID,Long>accepted,Map<UUID,Long>costs){for(var entry:accepted.entrySet())if(!StockPortfolioManager.addHolding(entry.getKey(),symbol,entry.getValue(),costs.getOrDefault(entry.getKey(),0L)))throw new IllegalStateException("validated governance quarantine restore failed");}
    private static long exactMultiply(long a,long b){try{return Math.multiplyExact(a,b);}catch(ArithmeticException e){return -1;}}
    private static long sum(Map<UUID,Long> map){BigInteger total=BigInteger.ZERO;for(long value:map.values())total=total.add(BigInteger.valueOf(value));return total.min(BigInteger.valueOf(Long.MAX_VALUE)).longValue();}
    private static ListTag writeMap(Map<UUID,Long>map){ListTag list=new ListTag();map.forEach((id,value)->{CompoundTag tag=new CompoundTag();tag.putUUID("Holder",id);tag.putLong("Shares",value);list.add(tag);});return list;}
    private static Map<UUID,Long>readMap(ListTag list){Map<UUID,Long>map=new LinkedHashMap<>();int count=0;for(Tag raw:list){if(++count>MAX_ACCEPTANCES)break;try{CompoundTag tag=(CompoundTag)raw;UUID id=tag.getUUID("Holder");long value=tag.getLong("Shares");if(value>0&&!map.containsKey(id))map.put(id,value);}catch(Exception ignored){}}return map;}
}
