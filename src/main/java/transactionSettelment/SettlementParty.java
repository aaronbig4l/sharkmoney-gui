package transactionSettelment;

import org.web3j.abi.datatypes.primitive.Char;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Orchestrates the debt settlement process for a group.
 * Before executing the SettlementStrategy, it performs a consensus check to ensure
 * that the sum of all net balances in the group is exactly zero - a requirement for a group to
 * start a settlement party. It uses the Strategy Design for the SettlementStrategy allowing
 * new settlement Algorithms in the future.
 */
public class SettlementParty {
    private final SettlementStrategy strategy; // Algorithm used to calculate the reduced Transactions

    public SettlementParty(SettlementStrategy strategy) {
        this.strategy = strategy;
    }

    /**
     * Validates and executes the settlement for the given net balances.
     * @param globalNetBalances a Map of peerIDs to their net balances within the group;
     *                          positive values indicates credit; negative values indicates debt.
     * @return a List of SettlementTransactions: objects representing the reduced set of transfers.
     */
    public List<SettlementTransaction> executeSettlement(Map<CharSequence, Integer> globalNetBalances) {
        // Consensus-Check: The Sum of all Balances in a closed group has to be equal to 0
        int totalNetworkBalance = 0;
        for (Integer balance : globalNetBalances.values()) {
            totalNetworkBalance += balance;
        }

        if (totalNetworkBalance != 0) {
            throw new IllegalStateException("Consensus Error: The Sum of all Net Balances in the Network is not 0! Settlement Party failed!");
        }

        // Use Strategy Pattern to reduce the Transactions
        return strategy.calculateSettlement(globalNetBalances);
    }

}
