package transactionSettelment;

import org.web3j.abi.datatypes.primitive.Char;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SettlementParty {
    private final SettlementStrategy strategy;

    public SettlementParty(SettlementStrategy strategy) {
        this.strategy = strategy;
    }

    public List<SettlementTransaction> executeSettlement(Map<CharSequence, Integer> globalNetBalances) {
        // 2. Consensus-Check: The Sum of all Balances in a closed group has to be equal to 0
        int totalNetworkBalance = 0;
        for (Integer balance : globalNetBalances.values()) {
            totalNetworkBalance += balance;
        }

        if (totalNetworkBalance != 0) {
            throw new IllegalStateException("Consensus Error: The Sum of all Net Balances in the Network is not 0! Settlement Party failed!");
        }

        // 3. Use Strategy Pattern to minimize the Transactions
        return strategy.calculateSettlement(globalNetBalances);
    }

}
