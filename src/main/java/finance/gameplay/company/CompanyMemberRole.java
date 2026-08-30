package finance.gameplay.company;

import java.util.EnumSet;
import java.util.Set;

public enum CompanyMemberRole {
    OWNER(EnumSet.allOf(CompanyPermission.class)),
    MANAGER(EnumSet.of(CompanyPermission.VIEW_COMPANY, CompanyPermission.DEPOSIT_WAREHOUSE,
            CompanyPermission.WITHDRAW_WAREHOUSE, CompanyPermission.PUBLISH_CONTRACT,
            CompanyPermission.MANAGE_PRODUCTION, CompanyPermission.SPEND_COMPANY_CASH,
            CompanyPermission.MANAGE_MEMBERS,
            CompanyPermission.VIEW_PRIVATE_FINANCIALS, CompanyPermission.OPEN_GOVERNANCE,
            CompanyPermission.MANAGE_LOGISTICS, CompanyPermission.TRANSPORT_CARGO)),
    TREASURER(EnumSet.of(CompanyPermission.VIEW_COMPANY, CompanyPermission.PUBLISH_CONTRACT,
            CompanyPermission.SPEND_COMPANY_CASH, CompanyPermission.VIEW_PRIVATE_FINANCIALS)),
    WAREHOUSE_WORKER(EnumSet.of(CompanyPermission.VIEW_COMPANY, CompanyPermission.DEPOSIT_WAREHOUSE,
            CompanyPermission.WITHDRAW_WAREHOUSE, CompanyPermission.TRANSPORT_CARGO)),
    MEMBER(EnumSet.of(CompanyPermission.VIEW_COMPANY, CompanyPermission.DEPOSIT_WAREHOUSE));

    private final Set<CompanyPermission> permissions;
    CompanyMemberRole(Set<CompanyPermission> permissions) { this.permissions = Set.copyOf(permissions); }
    public boolean allows(CompanyPermission permission) { return permission != null && permissions.contains(permission); }
    public Set<CompanyPermission> permissions() { return permissions; }
}
