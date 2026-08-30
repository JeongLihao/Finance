package finance.compat.ponder;

import finance.FinanceMod;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

/** Registers Finance subjects without depending on Create itself. */
public final class FinancePonderPlugin implements PonderPlugin {
    @Override
    public String getModId() {
        return FinanceMod.MOD_ID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        ResourceLocation template = id("ponder", "debug/scene_1");
        helper.addStoryBoard(finance("portable_ledger"), template, FinancePonderScenes::gettingStarted);
        helper.addStoryBoard(finance("warehouse_controller"), template, FinancePonderScenes::warehouseBasics);
        helper.addStoryBoard(finance("market_terminal"), template, FinancePonderScenes::marketTrading);
        helper.addStoryBoard(finance("warehouse_controller"), template, FinancePonderScenes::contractDelivery);
        helper.addStoryBoard(finance("company_factory_controller"), template, FinancePonderScenes::companyProduction);
        helper.addStoryBoard(finance("sealed_cargo_crate"), template, FinancePonderScenes::logisticsDelivery);
        helper.addStoryBoard(finance("settlement_trade_station"), template, FinancePonderScenes::settlementHelp);
        helper.addStoryBoard(finance("survey_board"), template, FinancePonderScenes::fieldSurvey);
        helper.addStoryBoard(finance("securities_terminal"), template, FinancePonderScenes::advancedFinance);
        helper.addStoryBoard(finance("survey_board"),template,FinancePonderScenes::regionalTradeFlow);
        helper.addStoryBoard(finance("bank_counter"),template,FinancePonderScenes::inventoryCollateral);
        helper.addStoryBoard(finance("securities_terminal"),template,FinancePonderScenes::companyHedge);
        helper.addStoryBoard(finance("warehouse_controller"),template,FinancePonderScenes::insuranceEvidence);
    }

    private static ResourceLocation finance(String path) {
        return id(FinanceMod.MOD_ID, path);
    }

    private static ResourceLocation id(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }
}
