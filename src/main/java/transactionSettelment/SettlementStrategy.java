package transactionSettelment;

import java.util.Map;
import java.util.List;

public interface SettlementStrategy {
    List<SettlementTransaction> calculateSettlement(Map<CharSequence, Integer> netBalances);
}
