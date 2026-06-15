package consoleTest;

import currency.api.SharkCurrencyComponent;
import currency.classes.SharkCurrency;
import currency.classes.SharkLocalCurrency;
import currency.classes.SharkPromise;
import currency.classes.SharkPromiseSerializer;
import currency.storage.SharkCurrencyStorage;
import net.sharksystem.asap.pki.CredentialMessageInMemo;
import net.sharksystem.pki.SharkPKIComponent;
import testHelper.AsapCurrencyTestHelper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class SimpleConsoleTest extends AsapCurrencyTestHelper {

    public SimpleConsoleTest() {
        super("consoleTest");
    }

    public static void main(String[] args) throws Exception {
        SimpleConsoleTest test = new SimpleConsoleTest();

        // Storage aus vorherigen Runs löschen
        java.io.File testFolder = new java.io.File("testResultsRootFolder/consoleTest");
        if (testFolder.exists()) {
            org.apache.commons.io.FileUtils.deleteDirectory(testFolder);
        }

        // Setup
        test.setUpScenarioEstablishCurrency_2_BobAndAlice();
        SharkPKIComponent alicePKI = (SharkPKIComponent) test.aliceSharkPeer.getComponent(SharkPKIComponent.class);
        SharkPKIComponent bobPKI = (SharkPKIComponent) test.bobSharkPeer.getComponent(SharkPKIComponent.class);
        bobPKI.acceptAndSignCredential(new CredentialMessageInMemo(ALICE_ID, ALICE_NAME, System.currentTimeMillis(), alicePKI.getPublicKey()));
        alicePKI.acceptAndSignCredential(new CredentialMessageInMemo(BOB_ID, BOB_NAME, System.currentTimeMillis(), bobPKI.getPublicKey()));

        // Gruppe erstellen
        SharkCurrency currency = new SharkLocalCurrency("TestCoin", "Test");
        ArrayList<CharSequence> whitelist = new ArrayList<>();
        whitelist.add(BOB_ID);
        byte[] groupId = test.aliceCurrencyComponent.establishGroup(currency, whitelist, false, true, true);

        // Bob einladen + beitreten
        test.aliceCurrencyComponent.invitePeerToGroup(groupId, "Hey Bob!", BOB_ID);
        test.runEncounter(test.aliceSharkPeer, test.bobSharkPeer, true);
        Thread.sleep(1000);
        test.bobImpl.acceptInviteAndSign("TestCoin");
        test.runEncounter(test.bobSharkPeer, test.aliceSharkPeer, true);
        Thread.sleep(1000);

        // Promise erstellen und senden
        CharSequence promiseId = test.aliceCurrencyComponent.createPromise(10, currency, groupId, ALICE_ID, BOB_ID, true);
        Set<CharSequence> receivers = new HashSet<>();
        receivers.add(BOB_ID);
        test.aliceCurrencyComponent.sendPromise(promiseId, true, ALICE_ID, receivers, true, false, SharkPromise.SHARK_PROMISE_ASK_FOR_SIGNATURE_AS_DEB);
        test.runEncounter(test.aliceSharkPeer, test.bobSharkPeer, true);
        Thread.sleep(1000);

        // Bob signiert und schickt zurück
        SharkCurrencyStorage bobStorage = test.bobCurrencyComponent.getSharkCurrencyStorage();
        SharkPromise bobPromise = bobStorage.getSharkPendingPromiseFromStorage(promiseId);
        Set<CharSequence> aliceReceivers = new HashSet<>();
        aliceReceivers.add(ALICE_ID);
        byte[] msg = SharkPromiseSerializer.serializeSignAndSendBackMessage(
                promiseId,
                bobPromise.getDebtorSignature(),
                BOB_ID,
                aliceReceivers,
                false,
                bobPKI.getASAPKeyStore()
        );
        test.bobSharkPeer.getASAPPeer().sendASAPMessage(
                SharkCurrencyComponent.CURRENCY_FORMAT,
                SharkPromise.SHARK_PROMISE_RECEIVE_SIGNED_SIG,
                msg
        );
        test.runEncounter(test.bobSharkPeer, test.aliceSharkPeer, true);
        Thread.sleep(1000);

        // Bob Balance manuell aktualisieren
        SharkCurrencyStorage aliceStorage = test.aliceCurrencyComponent.getSharkCurrencyStorage();
        SharkPromise signed = aliceStorage.getSharkSignedPromiseFromStorage(promiseId);
        test.bobCurrencyComponent.addBalance(signed);

        // Ergebnis
        System.out.println("Alice Balance: " + test.aliceCurrencyComponent.getBalance(currency.getCurrencyId()));
        System.out.println("Bob Balance: " + test.bobCurrencyComponent.getBalance(currency.getCurrencyId()));
    }
}