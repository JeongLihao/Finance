package finance.fund;

import java.util.List;

public final class FundRiskService {
    private FundRiskService() { }
    public static FundRiskMetrics calculate(FundDefinition definition, FundState state, long day) {
        List<FundNavPoint> points=state.navHistory(); FundValuationService.Valuation value=FundValuationService.value(definition,state,day);
        double assets=Math.max(1,value.netAssets()); double stock=100.0*value.stockValue()/assets, bond=100.0*(value.bondValue()+value.billValue())/assets, cash=100.0*value.cash()/assets;
        if(points.size()<2)return new FundRiskMetrics(0,0,0,0,stock,bond,cash,cash>=20?"A":"B",false);
        double total=100.0*(points.get(points.size()-1).nav()/(double)points.get(0).nav()-1); double peak=points.get(0).nav(),draw=0,sum=0,sumSq=0;int n=0;
        for(int i=1;i<points.size();i++){peak=Math.max(peak,points.get(i).nav());draw=Math.max(draw,100.0*(peak-points.get(i).nav())/peak);double r=points.get(i).nav()/(double)points.get(i-1).nav()-1;sum+=r;sumSq+=r*r;n++;}
        double variance=n>1?Math.max(0,(sumSq-sum*sum/n)/(n-1)):0; double vol=Math.sqrt(variance)*100;
        double benchmarkReturn=points.get(0).benchmarkLevel()>0&&points.get(points.size()-1).benchmarkLevel()>0?100.0*(points.get(points.size()-1).benchmarkLevel()/(double)points.get(0).benchmarkLevel()-1):0;
        return new FundRiskMetrics(total,vol,draw,total-benchmarkReturn,stock,bond,cash,cash>=20?"A":cash>=10?"B":"C",points.size()>=7);
    }
}
