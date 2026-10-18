package finance.simple;

import finance.account.TransactionRecord;
import finance.account.TransactionType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SimpleSummaryTest {

    @Test void summarizesIncomeAndSpendingSinceLastOpen() {
        UUID me = UUID.randomUUID(), market = UUID.randomUUID();
        LocalDateTime since = LocalDateTime.now().minusSeconds(5);
        List<TransactionRecord> records = List.of(
                new TransactionRecord(market, me, 999, TransactionType.COMMODITY_SELL),
                new TransactionRecord(market, me, 1_200, TransactionType.COMMODITY_SELL),
                new TransactionRecord(market, me, 300, TransactionType.DIVIDEND),
                new TransactionRecord(me, market, 450, TransactionType.COMMODITY_BUY));
        List<String> lines = SimpleCenterService.summarize(me, records, since);
        assertEquals("收入 +24 磅 99 便士，支出 4 磅 50 便士。", lines.get(0));
        assertTrue(lines.contains("分红 +3 磅"));
    }

    @Test void nothingNewMeansNoSummary() {
        UUID me = UUID.randomUUID();
        List<TransactionRecord> records = List.of(new TransactionRecord(UUID.randomUUID(), me, 100, TransactionType.DIVIDEND));
        assertTrue(SimpleCenterService.summarize(me, records, LocalDateTime.now().plusSeconds(60)).isEmpty());
    }
}
