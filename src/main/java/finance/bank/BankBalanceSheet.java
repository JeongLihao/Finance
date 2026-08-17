package finance.bank;

public record BankBalanceSheet(long totalAssets, long reserves, long companyLoans, long interbankAssets,
                               long bondAssets, long demandDeposits, long timeDeposits, long interbankLiabilities,
                               long centralBankBorrowing, long equity, long loanLossReserve, boolean balanced) { }
