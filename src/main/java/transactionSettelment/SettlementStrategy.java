package transactionSettelment;

import java.util.Map;
import java.util.List;

/**
 * An Interface for the Settlement Algorithms used to minimize debts.
 */
public interface SettlementStrategy {

    /**
     * Executes the Settlement Algorithm
     * @param netBalances A Map containing the net balances of each Peer (PeerID -> Balances)
     *  -> Positive values indicate a net creditor; negative values indicate a net debtor
     * @return A List of optimized SettlementTransactions that resolve all balances
     */
    List<SettlementTransaction> calculateSettlement(Map<CharSequence, Integer> netBalances);
}
