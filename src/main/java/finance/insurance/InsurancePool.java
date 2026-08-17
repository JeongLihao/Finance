package finance.insurance;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class InsurancePool {
    public static final UUID ACCOUNT_ID=UUID.nameUUIDFromBytes("finance-system-insurance-pool".getBytes(StandardCharsets.UTF_8));
    private boolean initialized,newBusinessPaused;private long initialCapital,premiumsReceived,claimsPaid,operatingCosts,lastProcessedDay=-1;
    public boolean initialized(){return initialized;}public boolean newBusinessPaused(){return newBusinessPaused;}public long initialCapital(){return initialCapital;}public long premiumsReceived(){return premiumsReceived;}public long claimsPaid(){return claimsPaid;}public long operatingCosts(){return operatingCosts;}public long lastProcessedDay(){return lastProcessedDay;}
    void initialize(long amount){initialized=true;initialCapital=amount;}void premium(long amount){premiumsReceived=Math.addExact(premiumsReceived,amount);}void claim(long amount){claimsPaid=Math.addExact(claimsPaid,amount);}void day(long value){lastProcessedDay=value;}void pause(boolean value){newBusinessPaused=value;}public void restore(boolean initialized,boolean paused,long capital,long premiums,long claims,long costs,long day){this.initialized=initialized;newBusinessPaused=paused;initialCapital=capital;premiumsReceived=premiums;claimsPaid=claims;operatingCosts=costs;lastProcessedDay=day;}
}
