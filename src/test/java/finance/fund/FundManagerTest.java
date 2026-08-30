package finance.fund;

import finance.account.AccountManager;
import finance.data.EconomySavedData;
import finance.diagnostic.EconomyConsistencyService;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.*;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FundManagerTest {
    private final UUID player=UUID.fromString("00000000-0000-0000-0000-000000008001");
    @BeforeEach void setup(){EconomySavedData.resetRuntimeState();FundManager.seedDefaultsIfNeeded();FundManager.acknowledgeRisk(player,FundType.MONEY_MARKET);AccountManager.deposit(player,100_000);}
    @AfterEach void cleanup(){EconomySavedData.resetRuntimeState();}
    @Test void subscriptionCreatesRealCustodyCashAndConservesShares(){long before=AccountManager.getBalance(player);var result=FundManager.subscribe(player,"money-short",10_000,0,"once");assertTrue(result.success(),result.message());assertEquals(before-10_000,AccountManager.getBalance(player));FundDefinition d=FundManager.definitions().get("money-short");assertTrue(AccountManager.getBalance(d.custodyAccountId())>0);assertEquals(result.shareUnits(),FundManager.position(player,"money-short").shareUnits());assertEquals(result.shareUnits(),FundManager.states().get("money-short").totalShareUnits());assertTrue(EconomyConsistencyService.run(0).healthy());}
    @Test void duplicateOperationKeyCannotChargeTwice(){assertTrue(FundManager.subscribe(player,"money-short",10_000,0,"same").success());long balance=AccountManager.getBalance(player);assertFalse(FundManager.subscribe(player,"money-short",10_000,0,"same").success());assertEquals(balance,AccountManager.getBalance(player));}
    @Test void redemptionIsAtomicAndReducesFundTotalShares(){var buy=FundManager.subscribe(player,"money-short",10_000,0,"buy");long redeem=buy.shareUnits()/100*100;long before=FundManager.states().get("money-short").totalShareUnits();var result=FundManager.requestRedemption(player,"money-short",redeem,0,"sell");assertTrue(result.success(),result.message());assertEquals(before-redeem,FundManager.states().get("money-short").totalShareUnits());assertEquals(0,FundManager.position(player,"money-short").frozenShareUnits());}
    @Test void insufficientLiquidityKeepsFrozenClaimAndRestartRestoresIt(){var buy=FundManager.subscribe(player,"money-short",10_000,0,"buy");FundDefinition d=FundManager.definitions().get("money-short");long cash=AccountManager.getBalance(d.custodyAccountId());assertTrue(AccountManager.withdraw(d.custodyAccountId(),cash));long shares=buy.shareUnits()/100*100;var result=FundManager.requestRedemption(player,"money-short",shares,0,"pending");assertTrue(result.success());assertEquals(shares,FundManager.position(player,"money-short").frozenShareUnits());CompoundTag saved=new EconomySavedData().save(new CompoundTag());EconomySavedData.resetRuntimeState();EconomySavedData.load(saved);assertEquals(shares,FundManager.position(player,"money-short").frozenShareUnits());assertTrue(FundManager.requests().values().stream().anyMatch(r->r.status()==FundRedemptionRequest.Status.PENDING));}
    @Test void managementFeeIsIdempotentForSameDay(){FundManager.subscribe(player,"money-short",50_000,0,"buy");FundManager.processDay(1);long fee=FundManager.states().get("money-short").accruedFees();FundManager.processDay(1);assertEquals(fee,FundManager.states().get("money-short").accruedFees());}
    @Test void planDoesNotExecuteTwiceOnSameDay(){FundManager.createPlan(player,"money-short",1_000,7,1);FundManager.processDay(1);long balance=AccountManager.getBalance(player);FundManager.processDay(1);assertEquals(balance,AccountManager.getBalance(player));}
    @Test void feeCounterOverflowRejectsBeforeMovingCashOrShares(){FundState state=FundManager.states().get("money-short");state.restore(state.status(),0,state.currentNav(),state.previousNav(),state.lastNavDay(),state.lastFeeDay(),Long.MAX_VALUE,state.realizedIncome(),state.constituentVersion(),state.constituentEffectiveDay(),state.lastRebalanceDay(),state.suspensionReason(),state.navHistory());long cash=AccountManager.getBalance(player);var result=FundManager.subscribe(player,"money-short",10_000,0,"overflow-fee");assertFalse(result.success());assertEquals(cash,AccountManager.getBalance(player));assertNull(FundManager.position(player,"money-short"));assertEquals(0,state.totalShareUnits());}
    @Test void fullRedemptionHistoryPrunesTerminalRowBeforeFreezingNewClaim(){var buy=FundManager.subscribe(player,"money-short",10_000,0,"capacity-buy");for(int i=0;i<FundManager.MAX_REQUESTS;i++)FundManager.putRequestDirect(new FundRedemptionRequest(UUID.randomUUID(),player,"money-short",1,i,FundRedemptionRequest.Status.PAID));var result=FundManager.requestRedemption(player,"money-short",buy.shareUnits()/100*100,1,"capacity-redeem");assertTrue(result.success(),result.message());assertEquals(FundManager.MAX_REQUESTS,FundManager.requests().size());assertEquals(0,FundManager.position(player,"money-short").frozenShareUnits());}
}
