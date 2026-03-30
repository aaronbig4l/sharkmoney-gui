package currencyGroupTests;

import currency.classes.SharkCurrency;
import currency.classes.SharkLocalCurrency;
import exepections.SharkCurrencyException;
import group.SharkGroupDocument;
import net.sharksystem.SharkException;
import net.sharksystem.asap.crypto.ASAPCryptoAlgorithms;
import net.sharksystem.asap.pki.CredentialMessageInMemo;
import net.sharksystem.pki.SharkPKIComponent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import testHelper.*;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.util.*;

public class CurrencyGroupTestsEncrypted extends AsapCurrencyTestHelper {

    public CurrencyGroupTestsEncrypted() {
        super(CurrencyGroupTestsEncrypted.class.getSimpleName());
    }

    @BeforeEach
    void setUp(TestInfo testInfo) {
        String testName = testInfo.getDisplayName()
                .replaceAll("[^a-zA-Z0-9]", "_");
        this.initSubRootFolder(
                CurrencyGroupTestsEncrypted.class.getSimpleName(),
                testName
        );
    }

    @AfterEach
    void tearDown() {
        stopPeerSafely(this.aliceSharkPeer);
        stopPeerSafely(this.bobSharkPeer);
        stopPeerSafely(this.claraSharkPeer);
        stopPeerSafely(this.davidSharkPeer);
    }

    /// ENCRYPTED GROUPS ///
    @Test
    public void successfullEncryptedGroupInviteReceived() throws SharkException, InterruptedException, IOException {

        // 0. Set up Alice and Bob
        this.setUpScenarioEstablishCurrency_2_BobAndAlice();

        SharkPKIComponent alicePKI = (SharkPKIComponent) this.aliceSharkPeer.getComponent(SharkPKIComponent.class);
        SharkPKIComponent bobPKI = (SharkPKIComponent) this.bobSharkPeer.getComponent(SharkPKIComponent.class);

        // let Bob accept ALice credentials and create a certificate
        CredentialMessageInMemo aliceCredentialMessage = new CredentialMessageInMemo(ALICE_ID, ALICE_NAME, System.currentTimeMillis(), alicePKI.getPublicKey());
        bobPKI.acceptAndSignCredential(aliceCredentialMessage);

        // Alice accepts Bob Public Key
        CredentialMessageInMemo bobCredentialMessage = new CredentialMessageInMemo(BOB_ID, BOB_NAME, System.currentTimeMillis(), bobPKI.getPublicKey());
        alicePKI.acceptAndSignCredential(bobCredentialMessage);

        // 1. Alice arranges a new local Currency
        CharSequence currencyName = "AliceTalerC";
        SharkCurrency dummyCurrency = new SharkLocalCurrency(
                false,
                currencyName.toString(),
                "A test Currency"
        );

        // 2. Alice creates a new Group and whitelists Bob
        ArrayList<CharSequence> whitelist = new ArrayList<>();
        whitelist.add(BOB_ID);

        byte[] groupId = this.aliceCurrencyComponent.establishGroup(
                dummyCurrency,
                whitelist,
                true,
                true);

        Thread.sleep(2000);

        // 3. Encounter including message exchange starts, Alice will send a group invite to Bob the builder
        this.aliceCurrencyComponent
                .invitePeerToGroup(groupId, "Hi Bob, join my group!", BOB_ID);

        // 4. Encounter
        this.runEncounter(this.aliceSharkPeer, this.bobSharkPeer, true);
        Thread.sleep(2000);

        // 5.(Assertions)
        SharkGroupDocument aliceDoc = this.aliceStorage.getGroupDocument(groupId);
        byte[] aliceSignature = aliceDoc.getCurrentMembers().get(ALICE_ID);
        boolean verifiedAliceSig = ASAPCryptoAlgorithms.verify(
                groupId,
                aliceSignature,
                ALICE_ID,
                ((SharkPKIComponent) aliceSharkPeer
                        .getComponent(SharkPKIComponent.class))
                        .getASAPKeyStore());

        Assertions
                .assertNotNull(aliceDoc, "Alice document is null.");
        //bob does not have the document stored -> Exception when asking for it
        Assertions
                .assertThrows(SharkCurrencyException.class, () -> {
                    this.bobStorage.getGroupDocument(groupId);
                });

        Assertions
                .assertArrayEquals(groupId, this.aliceStorage.getGroupDocument(groupId).getGroupId());

        Assertions
                .assertEquals(1,this.bobStorage.getPendingInviteSize());

        //bob should have the invite pending
        Assertions
                .assertArrayEquals(groupId, this.bobStorage
                        .getPendingInvite(currencyName.toString()).getGroupId());
        //we expect 1 member. Just alice because bob didn't do anything yet
        Assertions
                .assertEquals(1,
                        aliceDoc.getCurrentMembers().size());
        //Alice signature has to be verified
        Assertions
                .assertTrue(verifiedAliceSig, "Alice signature is not verified");
    }
}
