package transactionSettelment;

public class SettlementTransaction {

    private final CharSequence debtorId;// The P
    private final CharSequence creditorId;
    private final int amount;

    public SettlementTransaction(CharSequence debtorId, CharSequence creditorId, int amount) {
        this.debtorId = debtorId;
        this.creditorId = creditorId;
        this.amount = amount;
    }

    public CharSequence getDebtorId() {
        return debtorId;
    }

    public CharSequence getCreditorId() {
        return creditorId;
    }

    public int getAmount() {
        return amount;
    }
}
