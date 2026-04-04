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

import static org.junit.jupiter.api.Assertions.assertThrows;

public class CurrencyEncryptedGroupTests extends AsapCurrencyTestHelper {

    public CurrencyEncryptedGroupTests() {
        super(CurrencyEncryptedGroupTests.class.getSimpleName());
    }

    @BeforeEach
    void setUp(TestInfo testInfo) {
        String testName = testInfo.getDisplayName()
                .replaceAll("[^a-zA-Z0-9]", "_");
        this.initSubRootFolder(
                CurrencyEncryptedGroupTests.class.getSimpleName(),
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
        CharSequence currencyName = "AliceTalerEncryptedA";
        SharkCurrency dummyCurrency = new SharkLocalCurrency(
                currencyName.toString(),
                "A test Currency"
        );

        // 2. Alice creates a new Group and whitelists Bob
        ArrayList<CharSequence> whitelist = new ArrayList<>();
        whitelist.add(BOB_ID);

        byte[] groupId = this.aliceCurrencyComponent.establishGroup(
                dummyCurrency,
                whitelist,
                false,
                true,
                true);
        Thread.sleep(1000);

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

    @Test
    public void groupInviteEncryptedTo3butOnly1HasKey() throws SharkException, IOException, InterruptedException {

        // 0. Set up Alice, Bob and Clara
        this.setUpScenarioEstablishCurrency_3_ClaraAndBobAndAlice();

        // ONLY Clara and Alice exchange credentials, Bob is being left out
        SharkPKIComponent alicePKI = (SharkPKIComponent) this.aliceSharkPeer.getComponent(SharkPKIComponent.class);
        SharkPKIComponent claraPKI = (SharkPKIComponent) this.claraSharkPeer.getComponent(SharkPKIComponent.class);
        CredentialMessageInMemo aliceCredentialMessage
                = new CredentialMessageInMemo(ALICE_ID, ALICE_NAME, System.currentTimeMillis(), alicePKI.getPublicKey());
        claraPKI.acceptAndSignCredential(aliceCredentialMessage);
        CredentialMessageInMemo claraCredentialMessage
                = new CredentialMessageInMemo(CLARA_ID, CLARA_NAME, System.currentTimeMillis(), claraPKI.getPublicKey());
        alicePKI.acceptAndSignCredential(claraCredentialMessage);

        // 1. Alice arranges a new local Currency
        CharSequence currencyName = "AliceTalerEncryptedB";
        SharkCurrency dummyCurrency = new SharkLocalCurrency(
                currencyName.toString(),
                "A test Currency"
        );

        // 2. Alice creates a new Group and whitelists Bob and Clara
        ArrayList<CharSequence> whitelist = new ArrayList<>();
        whitelist.add(BOB_ID);
        whitelist.add(CLARA_ID);
        byte[] groupId = this.aliceCurrencyComponent.establishGroup(
                dummyCurrency,
                whitelist,
                false,
                true,
                true);
        Thread.sleep(1000);

        // 3. She sends to bob and clara, bobs invite should fail
        Exception bobNoKeyException = assertThrows(SharkCurrencyException.class, () -> {
            this.aliceCurrencyComponent
                    .invitePeerToGroup(groupId, "Hi Bob, join my group!", BOB_ID);
        });
        this.aliceCurrencyComponent
                .invitePeerToGroup(groupId, "Hi Clara, join my group!", CLARA_ID);

        // 4. Encounter
        this.runEncounter(this.aliceSharkPeer, this.bobSharkPeer, true);
        Thread.sleep(2000);
        this.runEncounter(this.aliceSharkPeer, this.claraSharkPeer, true);
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

        Assertions.assertEquals(1,this.claraStorage.getPendingInviteSize());
        Assertions.assertFalse(this.bobStorage.hasPendingInvites());
        Assertions.assertTrue(verifiedAliceSig);
        Assertions.assertTrue(bobNoKeyException
                .getMessage()
                .contains("no certificate issued for this peer found: " + BOB_ID));
        Assertions
                .assertArrayEquals(groupId, this.claraStorage
                        .getPendingInvite(currencyName.toString()).getGroupId());
        Assertions
                .assertEquals(1,
                        aliceDoc.getCurrentMembers().size());
    }

    @Test
    public void successfullEncryptedGroupInviteMultipleMembers() throws SharkException, IOException, InterruptedException {
        // 0. Set up Alice, Bob, Clara and David
        this.setUpScenarioEstablishCurrency_4_DavidAndClaraAndBobAndAlice();

        // Everyone exchange credentials
        SharkPKIComponent alicePKI = (SharkPKIComponent) this.aliceSharkPeer.getComponent(SharkPKIComponent.class);
        SharkPKIComponent bobPKI = (SharkPKIComponent) this.bobSharkPeer.getComponent(SharkPKIComponent.class);
        SharkPKIComponent claraPKI = (SharkPKIComponent) this.claraSharkPeer.getComponent(SharkPKIComponent.class);
        SharkPKIComponent davidPKI = (SharkPKIComponent) this.davidSharkPeer.getComponent(SharkPKIComponent.class);

        CredentialMessageInMemo aliceCredentialMessage
                = new CredentialMessageInMemo(ALICE_ID, ALICE_NAME, System.currentTimeMillis(), alicePKI.getPublicKey());
        bobPKI.acceptAndSignCredential(aliceCredentialMessage);
        claraPKI.acceptAndSignCredential(aliceCredentialMessage);
        davidPKI.acceptAndSignCredential(aliceCredentialMessage);

        CredentialMessageInMemo bobCredentialMessage
                = new CredentialMessageInMemo(BOB_ID, BOB_NAME, System.currentTimeMillis(), bobPKI.getPublicKey());
        alicePKI.acceptAndSignCredential(bobCredentialMessage);
        claraPKI.acceptAndSignCredential(bobCredentialMessage);
        davidPKI.acceptAndSignCredential(bobCredentialMessage);

        CredentialMessageInMemo claraCredentialMessage
                = new CredentialMessageInMemo(CLARA_ID, CLARA_NAME, System.currentTimeMillis(), claraPKI.getPublicKey());
        alicePKI.acceptAndSignCredential(claraCredentialMessage);
        bobPKI.acceptAndSignCredential(claraCredentialMessage);
        davidPKI.acceptAndSignCredential(claraCredentialMessage);

        CredentialMessageInMemo davidCredentialMessage
                = new CredentialMessageInMemo(DAVID_ID, DAVID_NAME, System.currentTimeMillis(), davidPKI.getPublicKey());
        alicePKI.acceptAndSignCredential(davidCredentialMessage);
        bobPKI.acceptAndSignCredential(davidCredentialMessage);
        claraPKI.acceptAndSignCredential(davidCredentialMessage);

        // 1. Alice arranges a new local Currency
        CharSequence currencyName = "AliceTalerEncryptedC";
        SharkCurrency dummyCurrency = new SharkLocalCurrency(
                currencyName.toString(),
                "A test Currency"
        );

        // 2. Alice creates a new Group and whitelists Bob and Clara
        ArrayList<CharSequence> whitelist = new ArrayList<>();
        whitelist.add(BOB_ID);
        whitelist.add(CLARA_ID);
        whitelist.add(DAVID_ID);
        byte[] groupId = this.aliceCurrencyComponent.establishGroup(
                dummyCurrency,
                whitelist,
                false,
                true, //Changed
                true);

        Thread.sleep(100);

        this.aliceCurrencyComponent
                .invitePeerToGroup(groupId, "Hi Bob, join my group!", BOB_ID);
        Thread.sleep(100);
        this.aliceCurrencyComponent
                .invitePeerToGroup(groupId, "Hi Clara, join my group!", CLARA_ID);
        Thread.sleep(100);
        this.aliceCurrencyComponent
                .invitePeerToGroup(groupId, "Hi David, join my group!", DAVID_ID);

        // 4. Encounter
        this.runEncounter(this.aliceSharkPeer, this.bobSharkPeer, true);
        Thread.sleep(200);
        this.runEncounter(this.aliceSharkPeer, this.claraSharkPeer, true);
        Thread.sleep(200);
        this.runEncounter(this.aliceSharkPeer, this.davidSharkPeer, true);
        Thread.sleep(200);

        // Aceept Invites
        this.bobImpl.acceptInviteAndSign(currencyName);
        this.claraImpl.acceptInviteAndSign(currencyName);
        this.davidImpl.acceptInviteAndSign(currencyName);

        this.runEncounter(this.bobSharkPeer, this.aliceSharkPeer, true);
        Thread.sleep(1000);
        this.runEncounter(this.claraSharkPeer, this.aliceSharkPeer, true);
        Thread.sleep(1000);
        this.runEncounter(this.aliceSharkPeer, this.claraSharkPeer, true);
        Thread.sleep(1000);
        this.runEncounter(this.davidSharkPeer, this.aliceSharkPeer, true);
        Thread.sleep(1000);
        this.runEncounter(this.aliceSharkPeer, this.davidSharkPeer, true);
        Thread.sleep(1000);
        this.runEncounter(this.bobSharkPeer, this.claraSharkPeer, true);
        Thread.sleep(1000);
        this.runEncounter(this.claraSharkPeer, this.davidSharkPeer, true);
        Thread.sleep(1000);
        this.runEncounter(this.bobSharkPeer, this.davidSharkPeer, true);
        Thread.sleep(1000);


        // 5.(Assertions)
        SharkGroupDocument aliceDoc = this.aliceStorage.getGroupDocument(groupId);
        SharkGroupDocument bobDoc = this.bobStorage.getGroupDocument(groupId);
        SharkGroupDocument claraDoc = this.claraStorage.getGroupDocument(groupId);
        SharkGroupDocument davidDoc = this.davidStorage.getGroupDocument(groupId);

        // Not null?
        Assertions.assertNotNull(aliceDoc, "Alice Group Doc should exist!");
        Assertions.assertNotNull(bobDoc, "Bobs Group Doc should exist!");
        Assertions.assertNotNull(claraDoc, "Claras Group Doc should exist!");
        Assertions.assertNotNull(davidDoc, "Davids Group Doc should exist!");

        // Group Member size equals 4 (Alice, Bob, Clara, David)?
        Assertions.assertEquals(4, aliceDoc.getCurrentMembers().size(), "The Group should include 4 Member (Alice Doc)");
        Assertions.assertEquals(4, bobDoc.getCurrentMembers().size(), "The Group should include 4 Member (Bob Doc)");
        Assertions.assertEquals(4, claraDoc.getCurrentMembers().size(), "The Group should include 4 Member (Clara Doc)");
        Assertions.assertEquals(4, davidDoc.getCurrentMembers().size(), "The Group should include 4 Member (David Doc)");

        // No pending invites?
        Assertions.assertFalse(this.bobStorage.hasPendingInvites(), "Bob should have no more pending invites...");
        Assertions.assertFalse(this.claraStorage.hasPendingInvites(), "Clara should have no more pending invites...");
        Assertions.assertFalse(this.davidStorage.hasPendingInvites(), "David should have no more pending invites...");

        // Verify Alices Signature
        byte[] aliceSignature = aliceDoc.getCurrentMembers().get(ALICE_ID);
        boolean verifiedAliceSig = ASAPCryptoAlgorithms.verify(groupId, aliceSignature, ALICE_ID, alicePKI.getASAPKeyStore());
        Assertions.assertTrue(verifiedAliceSig, "Alices Signature has to be verified");
    }

}
