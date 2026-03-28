package transactionSettelment;
import java.util.*;

public class GreedySettlementStrategy implements SettlementStrategy{
    @Override
    public List<SettlementTransaction> calculateSettlement(Map<CharSequence, Integer> netBalances) {
        List<SettlementTransaction> simplifiedDebts = new ArrayList<>();

        // PriorityQueues sorted by biggest amount (biggest first) and alphabetically (when same amount)
        PriorityQueue<Map.Entry<CharSequence, Integer>> creditors = new PriorityQueue<>(
                (e1, e2) -> {
                    int cmp = Integer.compare(e2.getValue(), e1.getValue());
                    if (cmp == 0) return e1.getKey().toString().compareTo(e2.getKey().toString());
                    return cmp;
                }
        );
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

        // work greedily
        while (!creditors.isEmpty() && !debtors.isEmpty()) {
            Map.Entry<CharSequence, Integer> maxCreditor = creditors.poll();
            Map.Entry<CharSequence, Integer> maxDebtor = debtors.poll();

            int creditAmount = maxCreditor.getValue();
            int debtAmount = maxDebtor.getValue();

            // amount, that has to be equalized
            int settledAmount = Math.min(creditAmount, debtAmount);

            simplifiedDebts.add(new SettlementTransaction(maxDebtor.getKey(), maxCreditor.getKey(), settledAmount));

            // Add rest amounts back to Queues
            if (creditAmount > settledAmount) {
                maxCreditor.setValue(creditAmount - settledAmount);
                creditors.add(maxCreditor);
            }
            if (debtAmount > settledAmount) {
                maxDebtor.setValue(-(debtAmount - settledAmount));
                debtors.add(maxDebtor);
            }
        }
        return simplifiedDebts;
    }
}
