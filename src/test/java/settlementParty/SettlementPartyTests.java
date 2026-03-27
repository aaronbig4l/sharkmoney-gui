package settlementParty;

import currency.classes.SharkInMemoPromise;
import currency.classes.SharkLocalCurrency;
import implementations.SharkCurrencyComponentImpl;
import net.sharksystem.SharkException;
import org.junit.jupiter.api.Test;
import testHelper.AsapCurrencyTestHelper;
import transactionSettelment.SettlementParty;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class SettlementPartyTests extends AsapCurrencyTestHelper {
    public SettlementPartyTests() {
        super("SettlementTestFolder");
    }

    @Test
    public void testSettlementPartyWithTwoIsolatedGroups() throws SharkException, IOException, InterruptedException {
        // 1. Setup for Peers and Groups
        this.setUpScenarioEstablishCurrency_4_DavidAndClaraAndBobAndAlice();

        // Create first Group A
        byte[] groupIdA = this.aliceCreatesEncryptedGroupWithBobAndClaraSetUp();
        SharkLocalCurrency currencyGroupA = new SharkLocalCurrency(
                false,
                "Alice Settlement Taler 1",
                "A test currency");

        // Create second Group B
        SharkLocalCurrency currencyGroupB = new SharkLocalCurrency(
                false,
                "Alice Settlement Taler 2",
                "A test currency"
        );
        ArrayList<CharSequence> whitelistB = new ArrayList<>();
        whitelistB.add(DAVID_ID);
        byte[] groupIdB = this.aliceCurrencyComponent.establishGroup(currencyGroupB, whitelistB, true, true);

        // 2. Execute Promises

    }
}
