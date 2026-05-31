package finance.market;
import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;
import finance.account.AccountManager;
import java.util.UUID;
import finance.commodity.CommodityInventoryManager;


public class MarketManager {
    private static final List<Order> ORDERS =
            new ArrayList<>();

    public static void placeOrder(Order order) {

        boolean matched = matchOrder(order);

        if (!matched) {
            ORDERS.add(order);
        }

    }

    public static List<Order> getOrders() {

        return ORDERS;
    }

    private static boolean matchOrder(Order newOrder) {

        Iterator<Order> iterator = ORDERS.iterator();

        while (iterator.hasNext()) {

            Order existingOrder = iterator.next();

            if (!existingOrder.getCommodityId()
                    .equals(newOrder.getCommodityId())) {
                continue;
            }

            if (existingOrder.getType() ==
                    newOrder.getType()) {
                continue;
            }
            //禁止自交易
            if (existingOrder.getPlayerId()
                    .equals(newOrder.getPlayerId())) {
                continue;
            }

            boolean matched = false;

            if (newOrder.getType() == OrderType.BUY) {

                matched =
                        newOrder.getPrice()
                                >= existingOrder.getPrice();

            } else {

                matched =
                        existingOrder.getPrice()
                                >= newOrder.getPrice();
            }

            if (!matched) {
                continue;
            }
            long totalPrice =
                    existingOrder.getPrice()
                            * newOrder.getQuantity();

            UUID buyer;
            UUID seller;

            if (newOrder.getType() == OrderType.BUY) {

                buyer = newOrder.getPlayerId();
                seller = existingOrder.getPlayerId();

            } else {

                buyer = existingOrder.getPlayerId();
                seller = newOrder.getPlayerId();
            }

            boolean paid =
                    AccountManager.transfer(
                            buyer,
                            seller,
                            totalPrice
                    );

            if (!paid) {

                System.out.println(
                        "TRADE FAILED: insufficient funds"
                );

                return false;
            }

            boolean commodityTransferred =
                    CommodityInventoryManager
                            .removeCommodity(
                                    seller,
                                    newOrder.getCommodityId(),
                                    newOrder.getQuantity()
                            );

            if (!commodityTransferred) {

                System.out.println(
                        "TRADE FAILED: seller inventory error"
                );

                return false;
            }

            CommodityInventoryManager
                    .addCommodity(
                            buyer,
                            newOrder.getCommodityId(),
                            newOrder.getQuantity()
                    );


            System.out.println("================================");
            System.out.println("TRADE EXECUTED");
            System.out.println("Commodity: " + newOrder.getCommodityId());
            System.out.println("Price: " + existingOrder.getPrice());
            System.out.println("================================");


            iterator.remove();

            return true;
        }
        return false;
    }
}
