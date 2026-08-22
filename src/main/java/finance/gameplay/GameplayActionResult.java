package finance.gameplay;

import java.util.List;

/** 服务端玩法操作的统一结果；客户端不得自行推测结算是否成功。 */
public record GameplayActionResult(
        boolean success,
        String messageKey,
        List<String> arguments,
        boolean refreshMenu
) {
    public GameplayActionResult {
        messageKey = messageKey == null ? "finance.access.denied" : messageKey;
        arguments = arguments == null ? List.of() : List.copyOf(arguments);
    }

    public static GameplayActionResult success(String messageKey, boolean refreshMenu) {
        return new GameplayActionResult(true, messageKey, List.of(), refreshMenu);
    }

    public static GameplayActionResult failure(String messageKey) {
        return new GameplayActionResult(false, messageKey, List.of(), false);
    }
}
