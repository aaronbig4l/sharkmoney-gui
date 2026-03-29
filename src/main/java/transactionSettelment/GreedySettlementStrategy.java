package transactionSettelment;
import java.util.*;

/**
 * An implementation of the SettlementStrategy interface using a Greedy Algorithm approach.
 * This Algorithms calculates the net balances of all Peers and resolves the debts by
 * continuously matching the Peer with the highest debt against the Peer with the highest credit,
 * reducing the amount of direct transactions.
 */
public class GreedySettlementStrategy implements SettlementStrategy{
    @Override
    public List<SettlementTransaction> calculateSettlement(Map<CharSequence, Integer> netBalances) {
        List<SettlementTransaction> simplifiedDebts = new ArrayList<>();

        // Priority queue for creditors: sorted by balance descending, then alphabetically
        PriorityQueue<Map.Entry<CharSequence, Integer>> creditors = new PriorityQueue<>(
                (e1, e2) -> {
                    int cmp = Integer.compare(e2.getValue(), e1.getValue());
                    if (cmp == 0) return e1.getKey().toString().compareTo(e2.getKey().toString());
                    return cmp;
                }
        );

        // Priority queue for debtors: sorted by balance ascending (biggest negative first), then alphabetically
        PriorityQueue<Map.Entry<CharSequence, Integer>> debtors = new PriorityQueue<>(
                (e1, e2) -> {
                    int cmp = Integer.compare(e1.getValue(), e2.getValue());
                    if (cmp == 0) return e1.getKey().toString().compareTo(e2.getKey().toString());
                    return cmp;
                }
        );

        // Divide in Debtors (net balance < 0) and Creditors (net balance > 0)
        for (Map.Entry<CharSequence, Integer> entry : netBalances.entrySet()) {
            if (entry.getValue() > 0) {
                creditors.add(entry);
            } else if (entry.getValue() < 0) {
                debtors.add(entry);
            }
        }

        // greedily pair the largest creditor with the largest debtor until all debts are settled
        while (!creditors.isEmpty() && !debtors.isEmpty()) {
            Map.Entry<CharSequence, Integer> maxCreditor = creditors.poll();
            Map.Entry<CharSequence, Integer> maxDebtor = debtors.poll();

            int creditAmount = maxCreditor.getValue();
            int debtAmount = Math.abs(maxDebtor.getValue());

            // the amount to settle is the smaller of the two to avoid over-payment
            int settledAmount = Math.min(creditAmount, debtAmount);

            simplifiedDebts.add(new SettlementTransaction(maxDebtor.getKey(), maxCreditor.getKey(), settledAmount));

            // if the creditor still has remaining credit, add it back and reduce his balance
            if (creditAmount > settledAmount) {
                maxCreditor.setValue(creditAmount - settledAmount);
                creditors.add(maxCreditor);
            }
            // if the debtor still has remaining debt, reduce it with the settled amount
            if (debtAmount > settledAmount) {
                maxDebtor.setValue(-(debtAmount - settledAmount));
                debtors.add(maxDebtor);
            }
        }
        return simplifiedDebts;
    }
}
