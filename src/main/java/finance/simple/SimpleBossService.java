package finance.simple;

import finance.account.AccountManager;
import finance.bank.CommercialBank;
import finance.company.Company;
import finance.company.CompanyManager;
import finance.company.CompanyProposalManager;
import finance.company.CompanyProposalType;
import finance.cycle.EconomyCycleService;
import finance.governance.TakeoverService;
import finance.insurance.InsuranceManager;
import finance.insurance.InsurancePolicy;
import finance.insurance.InsuranceProduct;
import finance.insurance.PolicyStatus;
import finance.playerservice.PlayerServicesService;
import finance.stock.Stock;
import finance.stock.StockMarketManager;
import finance.stock.StockPortfolioManager;
import finance.util.MoneyFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

/**
 * “我的公司”页的“当老板”和“保险”：把股东提案、贷款、收购、开银行、工资、投保包成一键操作。
 * 真正的校验和结算仍在原来的服务里。
 */
public final class SimpleBossService {

    private SimpleBossService() {}

    /** 一种保险：险种、名称、建议保额、保费、是否已在保、到期日。 */
    public record Insurance(String product, String name, long coverage, long premium, boolean active, long expiryDay) {}

    /** 可以收购的一家上市公司；squeezable = 我已持有 90% 以上，可以买下剩余股份。 */
    public record Target(String companyId, String name, long sharePrice, int myPctX10, boolean squeezable) {}

    public record BossInfo(
            boolean publicCompany,
            int dividendPct,
            boolean wageActive,
            long wage,
            long reservationWage,
            List<Insurance> insurance,
            List<Target> targets,
            boolean ownsBank,
            long bankMinCapital,
            long bankCharterFee) {
        public static final BossInfo EMPTY = new BossInfo(false, 0, false, 0, 0, List.of(), List.of(), false, 0, 0);
    }

    static final int INSURANCE_DAYS = 7, VOTE_DAYS = 3, LOAN_DAYS = 30;

    public static BossInfo info(ServerPlayer player) {
        UUID id = player.getUUID();
        long day = EconomyCycleService.currentMcDay(player.server);
        Company company = CompanyManager.getCompanyByOwner(id);
        var macro = PlayerServicesService.macro(id);
        var bank = PlayerServicesService.bank(id);
        List<Target> targets = new ArrayList<>();
        for (Stock stock : StockMarketManager.getListedStocks()) {
            if (company != null && stock.getCompanyId().equals(company.getCompanyId())) continue;
            double pct = TakeoverService.votingPercent(id, stock.getCompanyId(), day);
            targets.add(new Target(
                    stock.getCompanyId().toString(),
                    stock.getName(),
                    stock.getFairValue(),
                    (int) Math.round(pct * 10),
                    pct + 1e-9 >= 90.0
                            && StockPortfolioManager.getHolding(id, stock.getSymbol())
                                            .getQuantity()
                                    > 0));
            if (targets.size() >= 8) break;
        }
        return new BossInfo(
                company != null && company.isPublic(),
                company == null
                        ? 0
                        : (int) Math.round(company.effectiveDividendRatio(CompanyManager.getDividendRatio()) * 100),
                macro.active() && company != null,
                macro.wage(),
                macro.reservationWage(),
                company == null ? List.of() : insurance(id, company, day),
                targets,
                bank.owned(),
                bank.minCapital(),
                bank.charterFee());
    }

    static List<Insurance> insurance(UUID player, Company company, long day) {
        List<Insurance> out = new ArrayList<>();
        out.add(offer(
                player,
                company,
                InsuranceProduct.INVENTORY_DISASTER,
                "库存险：仓库里的货被毁时赔钱",
                Math.max(100, company.inventoryValue()),
                day));
        out.add(offer(
                player,
                company,
                InsuranceProduct.BUSINESS_INTERRUPTION,
                "停工险：工厂停工时赔一部分收入",
                Math.max(100, company.getEstimatedValue() / 4),
                day));
        return out;
    }

    private static Insurance offer(
            UUID player, Company company, InsuranceProduct product, String name, long coverage, long day) {
        InsurancePolicy active = InsuranceManager.policies().values().stream()
                .filter(p -> p.product() == product
                        && p.insuredObjectId().equals(company.getCompanyId())
                        && (p.status() == PolicyStatus.ACTIVE || p.status() == PolicyStatus.PENDING)
                        && p.expiryDay() >= day)
                .findFirst()
                .orElse(null);
        if (active != null)
            return new Insurance(
                    product.name(), name, active.coverageLimit(), active.premium(), true, active.expiryDay());
        var quote = InsuranceManager.quote(player, product, company.getCompanyId(), coverage, INSURANCE_DAYS);
        return new Insurance(product.name(), name, coverage, quote == null ? -1 : quote.premium(), false, 0);
    }

    // ------------------------------------------------------------ 操作

    public static SimpleInvestService.Result insure(ServerPlayer player, String productName) {
        Company company = CompanyManager.getCompanyByOwner(player.getUUID());
        if (company == null) return fail("先开一家公司。");
        InsuranceProduct product;
        try {
            product = InsuranceProduct.valueOf(productName);
        } catch (RuntimeException e) {
            return fail("没有这种保险。");
        }
        long day = EconomyCycleService.currentMcDay(player.server);
        Insurance offer = insurance(player.getUUID(), company, day).stream()
                .filter(i -> i.product().equals(product.name()))
                .findFirst()
                .orElse(null);
        if (offer == null || offer.active()) return fail("已经在保了。");
        if (offer.premium() < 0) return fail("公司还没有足够的经营记录，暂时不能投保。");
        long before = AccountManager.getBalance(player.getUUID());
        var r = InsuranceManager.purchase(
                player.getUUID(),
                product,
                company.getCompanyId(),
                offer.coverage(),
                INSURANCE_DAYS,
                day,
                UUID.randomUUID().toString());
        if (!r.success()) return fail(r.message());
        return new SimpleInvestService.Result(
                true,
                "投保成功：保 " + INSURANCE_DAYS + " 天，最多赔 " + MoneyFormat.pounds(offer.coverage()) + "，保费 "
                        + MoneyFormat.pounds(offer.premium()) + "。",
                AccountManager.getBalance(player.getUUID()) - before);
    }

    /** 股东提案模板：提高分红、增发 10%、回购 5%。投票期 3 天，过半通过。 */
    public static SimpleInvestService.Result propose(ServerPlayer player, String template) {
        UUID id = player.getUUID();
        Company company = CompanyManager.getCompanyByOwner(id);
        if (company == null) return fail("先开一家公司。");
        if (!company.isPublic()) return fail("公司上市后才能开股东大会。");
        Stock stock = StockMarketManager.getStockByCompanyId(company.getCompanyId());
        if (stock == null) return fail("找不到公司的股票。");
        long day = EconomyCycleService.currentMcDay(player.server);
        long price = Math.max(1, stock.getFairValue());
        long total = Math.max(1, stock.getTotalShares());
        CompanyProposalManager.Result r;
        String what;
        switch (template) {
            case "dividend" -> {
                long pct = Math.min(100, Math.round(company.getDividendRatio() * 100) + 10);
                r = CompanyProposalManager.createProposal(
                        id,
                        company.getCompanyId(),
                        CompanyProposalType.DIVIDEND,
                        "",
                        pct,
                        0,
                        0,
                        day,
                        day + VOTE_DAYS,
                        0.5);
                what = "把分红比例提到 " + pct + "%";
            }
            case "issue" -> {
                long qty = Math.max(1, total / 10);
                r = CompanyProposalManager.createProposal(
                        id,
                        company.getCompanyId(),
                        CompanyProposalType.SHARE_ISSUE,
                        "",
                        qty,
                        price,
                        qty * price,
                        day,
                        day + VOTE_DAYS,
                        0.5);
                what = "增发 " + qty + " 股，每股 " + MoneyFormat.pounds(price) + "，最多募到 " + MoneyFormat.pounds(qty * price);
            }
            case "buyback" -> {
                long qty = Math.max(1, total / 20);
                r = CompanyProposalManager.createProposal(
                        id,
                        company.getCompanyId(),
                        CompanyProposalType.SHARE_BUYBACK,
                        "",
                        price,
                        qty,
                        7,
                        day,
                        day + VOTE_DAYS,
                        0.5);
                what = "公司花钱回购 " + qty + " 股（每股不超过 " + MoneyFormat.pounds(price) + "）";
            }
            default -> {
                return fail("没有这种提案。");
            }
        }
        return r.success()
                ? new SimpleInvestService.Result(true, "已提交股东大会：" + what + "。投票 " + VOTE_DAYS + " 天，过半赞成就执行。", 0)
                : fail(r.message());
    }

    public static SimpleInvestService.Result vote(ServerPlayer player, UUID proposalId, boolean support) {
        if (proposalId == null) return fail("请求无效。");
        long day = EconomyCycleService.currentMcDay(player.server);
        var r = CompanyProposalManager.vote(player.getUUID(), proposalId, support, day);
        return r.success() ? new SimpleInvestService.Result(true, support ? "已投赞成票。" : "已投反对票。", 0) : fail(r.message());
    }

    /** 公司向银行借钱，30 天内分期还。钱进公司账上。 */
    public static SimpleInvestService.Result loan(ServerPlayer player, long amount) {
        UUID id = player.getUUID();
        Company company = CompanyManager.getCompanyByOwner(id);
        if (company == null) return fail("先开一家公司。");
        CommercialBank bank = SimpleCenterService.mainBank();
        if (bank == null) return fail("现在没有银行营业。");
        long day = EconomyCycleService.currentMcDay(player.server);
        var r = finance.debt.CompanyLoanManager.applyCommercial(
                id, company.getCompanyId(), bank.id(), amount, day, LOAN_DAYS, 7);
        return r.success()
                ? new SimpleInvestService.Result(
                        true, "公司借到 " + MoneyFormat.pounds(amount) + "，" + LOAN_DAYS + " 天内每 7 天还一次，利息按央行利率算。", 0)
                : fail(r.message());
    }

    /** 公开收购：按现价高 10% 出价，想买到 51% 的股份，期限 7 天。 */
    public static SimpleInvestService.Result takeover(ServerPlayer player, UUID companyId) {
        Company target = companyId == null ? null : CompanyManager.getCompany(companyId);
        Stock stock = target == null ? null : StockMarketManager.getStockByCompanyId(companyId);
        if (stock == null) return fail("这家公司没有上市。");
        UUID id = player.getUUID();
        long day = EconomyCycleService.currentMcDay(player.server);
        long price = Math.max(1, stock.getFairValue() * 11 / 10);
        long held = StockPortfolioManager.getHolding(id, stock.getSymbol()).getQuantity();
        long want = Math.max(1, stock.getTotalShares() * 51 / 100 - held);
        long before = AccountManager.getBalance(id);
        var r = TakeoverService.offerTender(
                id, companyId, price, want, want, 7, day, UUID.randomUUID().toString());
        if (!r.success()) return fail(r.message());
        return new SimpleInvestService.Result(
                true,
                "已发出收购：每股出 " + MoneyFormat.pounds(price) + "，想收 " + want + " 股，7 天内股东可以把股份卖给你。钱先托管着，收不满会退回。",
                AccountManager.getBalance(id) - before);
    }

    public static SimpleInvestService.Result squeeze(ServerPlayer player, UUID companyId) {
        long day = EconomyCycleService.currentMcDay(player.server);
        var r = companyId == null ? null : TakeoverService.squeezeOut(player.getUUID(), companyId, day, null);
        return r != null && r.success()
                ? new SimpleInvestService.Result(true, "已买下剩余股份，这家公司归你了。", 0)
                : fail(r == null ? "请求无效。" : r.message());
    }

    /** 开一家自己的银行：代码和名字自动起。 */
    public static SimpleInvestService.Result charterBank(ServerPlayer player, long capital) {
        String name = player.getGameProfile().getName();
        String code = ("P" + name.replaceAll("[^A-Za-z0-9]", "")).toUpperCase(java.util.Locale.ROOT);
        code = code.substring(0, Math.min(4, code.length()));
        if (code.length() < 3)
            code = "PB" + player.getUUID().toString().substring(0, 2).toUpperCase(java.util.Locale.ROOT);
        long day = EconomyCycleService.currentMcDay(player.server);
        long before = AccountManager.getBalance(player.getUUID());
        var r = finance.bank.PlayerBankService.charter(player.getUUID(), code, name + "银行", capital, day);
        return r.success()
                ? new SimpleInvestService.Result(
                        true,
                        "开了一家银行「" + name + "银行」。别人存进来的钱你可以放贷赚利差。",
                        AccountManager.getBalance(player.getUUID()) - before)
                : fail(r.message());
    }

    /** 老板直接定分红比例（拿出可分配利润的百分之几分给股东），发放周期不变。 */
    public static SimpleInvestService.Result setDividend(ServerPlayer player, long pct) {
        Company company = CompanyManager.getCompanyByOwner(player.getUUID());
        if (company == null) return fail("先开一家公司。");
        if (!company.isPublic()) return fail("公司上市后才有股东分红。");
        long clamped = Math.max(0, Math.min(100, pct));
        company.setDividendPolicy(
                clamped / 100.0, company.effectiveDividendCycleDays(CompanyManager.getDividendCycleDays()));
        finance.data.EconomySavedData.markDirty();
        return new SimpleInvestService.Result(true, "分红比例改为 " + clamped + "%：每期利润的 " + clamped + "% 分给股东。", 0);
    }

    public static SimpleInvestService.Result setWage(ServerPlayer player, long wage) {
        var r = PlayerServicesService.act(
                player,
                player.getUUID(),
                PlayerServicesService.Action.MACRO_WAGE,
                "",
                wage,
                0,
                0,
                0,
                EconomyCycleService.currentMcDay(player.server));
        return r.success()
                ? new SimpleInvestService.Result(true, "工人日工资改为 " + MoneyFormat.pounds(wage) + "。", 0)
                : fail(r.message());
    }

    private static SimpleInvestService.Result fail(String message) {
        return new SimpleInvestService.Result(false, message, 0);
    }
}
