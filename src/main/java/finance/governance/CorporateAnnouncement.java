package finance.governance;
import java.util.UUID;
public record CorporateAnnouncement(UUID id,UUID companyId,Type type,long day,UUID businessId,String titleKey,String details){public enum Type{IPO,SHARE_ISSUE,DIVIDEND,BUYBACK,TENDER,CONTROL_CHANGE,RECAPITALIZATION,ASSET_SALE,BANKRUPTCY,FINANCIAL_REPORT,CORRECTION}public CorporateAnnouncement{if(id==null||companyId==null||type==null||day<0||businessId==null)throw new IllegalArgumentException();titleKey=titleKey==null?"":titleKey;details=details==null?"":details;}}
