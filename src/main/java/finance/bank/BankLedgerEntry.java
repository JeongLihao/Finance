package finance.bank;

import java.util.UUID;

public record BankLedgerEntry(UUID entryId, UUID bankId, long mcDay, BankLedgerAccount debit,
                              BankLedgerAccount credit, long amount, BankLedgerReason reason, UUID referenceId) {
    public BankLedgerEntry {
        if (entryId == null || bankId == null || mcDay < 0 || debit == null || credit == null || debit == credit
                || amount <= 0 || reason == null || referenceId == null) throw new IllegalArgumentException("invalid bank entry");
    }
}
