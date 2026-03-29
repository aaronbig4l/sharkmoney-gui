package transactionSettelment;

/**
 * A Data Transfer Object representing a single debt relation after the settlement
 * algorithm has finished processing. It's representing a reduced transaction as result
 * of the SettlementStrategy. This transaction is later used to create new Promises.
 */
public class SettlementTransaction {

    private final CharSequence debtorId; // Person that has to pay the debt
    private final CharSequence creditorId; // Person that receives the payment
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
