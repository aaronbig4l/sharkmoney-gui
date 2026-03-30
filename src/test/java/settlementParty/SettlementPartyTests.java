package settlementParty;

import currency.classes.*;
import group.SharkGroupDocument;
import implementations.SharkCurrencyComponentImpl;
import net.sharksystem.SharkException;
import net.sharksystem.asap.pki.CredentialMessageInMemo;
import net.sharksystem.pki.SharkPKIComponent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import testHelper.AsapCurrencyTestHelper;
import transactionSettelment.SettlementParty;
import transactionSettelment.SettlementPartyState;
import transactionSettelment.SharkSettlementDocument;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SettlementPartyTests extends AsapCurrencyTestHelper {
    public SettlementPartyTests() {
        super("SettlementTestFolder");
    }

    @AfterEach
    void tearDown() {
        stopPeerSafely(this.aliceSharkPeer);
        stopPeerSafely(this.bobSharkPeer);
        stopPeerSafely(this.claraSharkPeer);
        stopPeerSafely(this.davidSharkPeer);
    }

    @Test
    public void testCompleteSettlementCycleWithOneGroup() throws Exception {
        // ==========================================
        // 1. SETUP: Start Peers and establish Group
        // ==========================================

        // Alice created a group with bob in it (he accepted). This method returns the groupID
        byte[] groupId = this.aliceCreatesEncryptedGroupWithBobAndClaraSetUp();

        SharkPKIComponent alicePKI = (SharkPKIComponent) this.aliceSharkPeer.getComponent(SharkPKIComponent.class);
        SharkPKIComponent bobPKI = (SharkPKIComponent) this.bobSharkPeer.getComponent(SharkPKIComponent.class);
        SharkPKIComponent claraPKI = (SharkPKIComponent) this.claraSharkPeer.getComponent(SharkPKIComponent.class);

        // let Bob and Clara accept ALice credentials and create a certificate
        CredentialMessageInMemo aliceCredentialMessage = new CredentialMessageInMemo(ALICE_ID, ALICE_NAME, System.currentTimeMillis(), alicePKI.getPublicKey());
        bobPKI.acceptAndSignCredential(aliceCredentialMessage);
        claraPKI.acceptAndSignCredential(aliceCredentialMessage);

        // Alice and Clara accepts Bob Public Key
        CredentialMessageInMemo bobCredentialMessage = new CredentialMessageInMemo(BOB_ID, BOB_NAME, System.currentTimeMillis(), bobPKI.getPublicKey());
        alicePKI.acceptAndSignCredential(bobCredentialMessage);
        claraPKI.acceptAndSignCredential(bobCredentialMessage);

        // Alice and Bob accepts Clara Public Key
        CredentialMessageInMemo claraCredentialMessage = new CredentialMessageInMemo(CLARA_ID, CLARA_NAME, System.currentTimeMillis(), claraPKI.getPublicKey());
        alicePKI.acceptAndSignCredential(claraCredentialMessage);
        bobPKI.acceptAndSignCredential(claraCredentialMessage);

        // ==========================================
        // 2. Exchange Promises
        // ==========================================

        // Alice owes Bob 100
        SharkGroupDocument sharkGroupDocument = this.aliceStorage.getGroupDocument(groupId);
        CharSequence promiseAliceToBob = this.bobCurrencyComponent.createPromise(100,
                sharkGroupDocument.getAssignedCurrency(),
                groupId,
                BOB_ID, //creditor
                ALICE_ID, //debtor
                true);

        Thread.sleep(500);
        this.runEncounter(this.bobSharkPeer, this.aliceSharkPeer, true);
        Thread.sleep(1000);
        this.aliceImpl.signPromiseAndSendBack(promiseAliceToBob);
        Thread.sleep(1000);
        this.runEncounter(this.aliceSharkPeer, this.bobSharkPeer, true);
        Thread.sleep(1000);

        // Bob owes Clara 50
        CharSequence promiseBobToClara = this.claraCurrencyComponent.createPromise(50,
                sharkGroupDocument.getAssignedCurrency(),
                groupId,
                CLARA_ID, //creditor
                BOB_ID, //debtor
                true);

        Thread.sleep(500);
        this.runEncounter(this.claraSharkPeer, this.bobSharkPeer, true);
        Thread.sleep(1000);
        this.bobImpl.signPromiseAndSendBack(promiseBobToClara);
        Thread.sleep(1000);
        this.runEncounter(this.bobSharkPeer, this.claraSharkPeer, true);
        Thread.sleep(1000);

        // Clara owes Alice 30
        CharSequence promiseClaraToAlice = this.aliceCurrencyComponent.createPromise(30,
                sharkGroupDocument.getAssignedCurrency(),
                groupId,
                ALICE_ID, //creditor
                CLARA_ID, //debtor
                true);
        Thread.sleep(500);
        this.runEncounter(this.aliceSharkPeer, this.claraSharkPeer, true);
        Thread.sleep(1000);
        this.claraImpl.signPromiseAndSendBack(promiseClaraToAlice);
        Thread.sleep(1000);
        this.runEncounter(this.claraSharkPeer, this.aliceSharkPeer, true);
        Thread.sleep(1000);

        syncAliceBobClaraPeers();

        Thread.sleep(1000);

        // ==========================================
        // 3. Settlement Party
        // ==========================================

        byte[] partyId = this.aliceImpl.initiateSettlementParty(groupId);

        // Gossip Loop: The document has different States GATHERING -> VERIFYING -> COMPLETED, therefore we simulate a Loop for exchanging the Doc
        for (int i = 1; i <= 5; i++) {
            syncAliceBobClaraPeers();
            Thread.sleep(1000); // Pause to let them calculate the Hashes

            // Show the SharkSettlementDoc live
            SharkSettlementDocument currentDoc = this.aliceStorage.getSettlementDocument(partyId);
            if (currentDoc != null) {
                System.out.println("Runde " + i + " | Status Alice: " + currentDoc.getState()
                        + " | Gesammelte Hashes: " + currentDoc.getComputedHashes().size());
            }
        }

        // Synchronize all new creates Promises and sign them
        syncAliceBobClaraPeers();
        Thread.sleep(1000);

        // Bob signs the new Promises
        for (SharkPromise p : this.bobStorage.getAllPendingPromises()) {
            this.bobImpl.signPromiseAndSendBack(p.getPromiseID());
        }

        // Clara signs the new Promises
        for (SharkPromise p : this.claraStorage.getAllPendingPromises()) {
            this.claraImpl.signPromiseAndSendBack(p.getPromiseID());
        }


        syncAliceBobClaraPeers();
        Thread.sleep(1000);

        // ==========================================
        // 4. Assertions
        // ==========================================

        // check if every group member has a Settlement Document
        SharkSettlementDocument finalDocAlice = this.aliceStorage.getSettlementDocument(partyId);
        Assertions.assertNotNull(finalDocAlice, "Settlement Document not found for Alice!");
        SharkSettlementDocument finalDocBob = this.bobStorage.getSettlementDocument(partyId);
        Assertions.assertNotNull(finalDocBob, "Settlement Document not found for Bob!");
        SharkSettlementDocument finalDocClara = this.claraStorage.getSettlementDocument(partyId);
        Assertions.assertNotNull(finalDocClara, "Settlement Document not found for Clara!");

        // check if every Peer commited a Hash
        Assertions.assertEquals(3, finalDocAlice.getComputedHashes().size());
        Assertions.assertEquals(3, finalDocBob.getComputedHashes().size());
        Assertions.assertEquals(3, finalDocClara.getComputedHashes().size());

        // check if settlement docs are completed
        Assertions.assertEquals(SettlementPartyState.COMPLETED, finalDocAlice.getState());
        Assertions.assertEquals(SettlementPartyState.COMPLETED, finalDocBob.getState());
        Assertions.assertEquals(SettlementPartyState.COMPLETED, finalDocClara.getState());

        // Alice has a net balance of -70
        // Bob has a net balance of +50
        // Clara has a net balance of +20

        // Expected Result of the Greedy Algorithm
        // 1. Alice -> Bob = 50
        // 2. Alice -> Clara = 20
        // we reduced 3 Promises to just 2

        boolean foundAliceToBob = false;
        boolean foundAliceToClara = false;

        List<byte[]> serializedPromises = this.aliceImpl.getSerializedPromisesForGroup(groupId); // get all Serialized Promises
        List<SharkPromise> allFinalPromises = new ArrayList<>();

        // deserialize Promises
        for (byte[] pBytes : serializedPromises) {
            SharkPromise promise = SharkPromiseSerializer.deserializePromise(
                    pBytes,
                    this.aliceImpl.getSharkPKIComponent().getASAPKeyStore()
            );
            allFinalPromises.add(promise);
        }

        for (SharkPromise p : allFinalPromises) {
            System.out.println("Debtor: " + p.getDebtorID() + " | Creditor: " + p.getCreditorID() + " | Amount: " + p.getAmount());

            if (p.getDebtorID().toString().equals(ALICE_ID.toString()) &&
                    p.getCreditorID().toString().equals(BOB_ID.toString()) &&
                    p.getAmount() == 50) {
                foundAliceToBob = true;
            }
            if (p.getDebtorID().toString().equals(ALICE_ID.toString()) &&
                    p.getCreditorID().toString().equals(CLARA_ID.toString()) &&
                    p.getAmount() == 20) {
                foundAliceToClara = true;
            }
        }

        Assertions.assertTrue(foundAliceToBob, "Error: New Promise (Alice pays Bob 50) not found");
        Assertions.assertTrue(foundAliceToClara, "Error: New Promise (Alice pays Clara 20) not found");
    }

    @Test
    public void testCircularDebtCancellation() throws Exception {
        // ==========================================
        // 1. SETUP: Start Peers and establish Group
        // ==========================================
        byte[] groupId = this.aliceCreatesEncryptedGroupWithBobAndClaraSetUp();
        SharkGroupDocument groupDoc = this.aliceStorage.getGroupDocument(groupId);

        // PKI Credentials
        SharkPKIComponent alicePKI = (SharkPKIComponent) this.aliceSharkPeer.getComponent(SharkPKIComponent.class);
        SharkPKIComponent bobPKI = (SharkPKIComponent) this.bobSharkPeer.getComponent(SharkPKIComponent.class);
        SharkPKIComponent claraPKI = (SharkPKIComponent) this.claraSharkPeer.getComponent(SharkPKIComponent.class);

        bobPKI.acceptAndSignCredential(new CredentialMessageInMemo(ALICE_ID, ALICE_NAME, System.currentTimeMillis(), alicePKI.getPublicKey()));
        claraPKI.acceptAndSignCredential(new CredentialMessageInMemo(ALICE_ID, ALICE_NAME, System.currentTimeMillis(), alicePKI.getPublicKey()));
        alicePKI.acceptAndSignCredential(new CredentialMessageInMemo(BOB_ID, BOB_NAME, System.currentTimeMillis(), bobPKI.getPublicKey()));
        claraPKI.acceptAndSignCredential(new CredentialMessageInMemo(BOB_ID, BOB_NAME, System.currentTimeMillis(), bobPKI.getPublicKey()));
        alicePKI.acceptAndSignCredential(new CredentialMessageInMemo(CLARA_ID, CLARA_NAME, System.currentTimeMillis(), claraPKI.getPublicKey()));
        bobPKI.acceptAndSignCredential(new CredentialMessageInMemo(CLARA_ID, CLARA_NAME, System.currentTimeMillis(), claraPKI.getPublicKey()));

        // ==========================================
        // 2. Exchange Promises
        // ==========================================

        // Create Promises (Circle: Alice -> Bob, Bob -> Clara, Clara -> Alice, always 100)
        CharSequence p1 = this.bobCurrencyComponent.createPromise(100,
                groupDoc.getAssignedCurrency(),
                groupId, BOB_ID,
                ALICE_ID,
                true);
        syncAliceBobClaraPeers();
        this.aliceImpl.signPromiseAndSendBack(p1);

        CharSequence p2 = this.claraCurrencyComponent.createPromise(100,
                groupDoc.getAssignedCurrency(),
                groupId,
                CLARA_ID,
                BOB_ID,
                true);
        syncAliceBobClaraPeers();
        this.bobImpl.signPromiseAndSendBack(p2);

        CharSequence p3 = this.aliceCurrencyComponent.createPromise(100,
                groupDoc.getAssignedCurrency(),
                groupId,
                ALICE_ID,
                CLARA_ID,
                true);
        syncAliceBobClaraPeers();
        this.claraImpl.signPromiseAndSendBack(p3);

        syncAliceBobClaraPeers();

        // ==========================================
        // 3. Settlement Party
        // ==========================================

        byte[] partyId = this.aliceImpl.initiateSettlementParty(groupId);

        for (int i = 1; i <= 5; i++) {
            syncAliceBobClaraPeers();
            Thread.sleep(1000);
        }

        syncAliceBobClaraPeers();
        Thread.sleep(1000);

        // ==========================================
        // 4. Assertions
        // ==========================================

        // We expect a COMPLETED settlement with no new Promises, because all net debts are 0

        SharkSettlementDocument finalDocAlice = this.aliceStorage.getSettlementDocument(partyId);
        Assertions.assertEquals(SettlementPartyState.COMPLETED, finalDocAlice.getState(), "Settlement did not complete!");

        // Check if there are no promises left
        List<byte[]> serializedPromises = this.aliceImpl.getSerializedPromisesForGroup(groupId);
        Assertions.assertTrue(serializedPromises.isEmpty(), "There should be 0 Promises left, as the circle cancels!");
    }

    @Test
    public void testCompleteSettlementWithTwoGroupsAndOneSettlementParty() throws Exception {
        // ==========================================
        // 1. SETUP: Start Peers and establish Group
        // ==========================================

        // Setup first Group
        byte[] groupId1 = this.aliceCreatesEncryptedGroupWithBobAndClaraSetUp();
        SharkGroupDocument groupDoc1 = this.aliceStorage.getGroupDocument(groupId1);

        // Setup second Group
        CharSequence currencyName = "Bobs WG Kasse";
        SharkCurrency bobsWGCurrency = new SharkLocalCurrency(
                false,
                currencyName.toString(),
                "A test Currency"
        );

        ArrayList<CharSequence> whitelist = new ArrayList<>();
        whitelist.add(ALICE_ID);
        whitelist.add(CLARA_ID);

        byte[] groupId2 = this.bobCurrencyComponent.establishGroup(
                bobsWGCurrency,
                whitelist,
                false,
                false,
                true
        );


        // PKI Credentials
        SharkPKIComponent alicePKI = (SharkPKIComponent) this.aliceSharkPeer.getComponent(SharkPKIComponent.class);
        SharkPKIComponent bobPKI = (SharkPKIComponent) this.bobSharkPeer.getComponent(SharkPKIComponent.class);
        SharkPKIComponent claraPKI = (SharkPKIComponent) this.claraSharkPeer.getComponent(SharkPKIComponent.class);

        bobPKI.acceptAndSignCredential(new CredentialMessageInMemo(ALICE_ID, ALICE_NAME, System.currentTimeMillis(), alicePKI.getPublicKey()));
        claraPKI.acceptAndSignCredential(new CredentialMessageInMemo(ALICE_ID, ALICE_NAME, System.currentTimeMillis(), alicePKI.getPublicKey()));
        alicePKI.acceptAndSignCredential(new CredentialMessageInMemo(BOB_ID, BOB_NAME, System.currentTimeMillis(), bobPKI.getPublicKey()));
        claraPKI.acceptAndSignCredential(new CredentialMessageInMemo(BOB_ID, BOB_NAME, System.currentTimeMillis(), bobPKI.getPublicKey()));
        alicePKI.acceptAndSignCredential(new CredentialMessageInMemo(CLARA_ID, CLARA_NAME, System.currentTimeMillis(), claraPKI.getPublicKey()));
        bobPKI.acceptAndSignCredential(new CredentialMessageInMemo(CLARA_ID, CLARA_NAME, System.currentTimeMillis(), claraPKI.getPublicKey()));

        // Alice and Clara join Group 2
        Thread.sleep(1000);
        this.bobCurrencyComponent.invitePeerToGroup(groupId2, "Hi Alice, join my group!", ALICE_ID);
        this.bobCurrencyComponent.invitePeerToGroup(groupId2, "Hi Clara, join my group!", CLARA_ID);
        syncAliceBobClaraPeers();
        this.aliceCurrencyComponent.acceptInviteAndSign(currencyName);
        Thread.sleep(500);
        this.claraCurrencyComponent.acceptInviteAndSign(currencyName);
        Thread.sleep(1000);
        syncAliceBobClaraPeers();

        SharkGroupDocument groupDoc2 = bobImpl.getSharkCurrencyStorage().getGroupDocument(groupId2);

        // ==========================================
        // 2. Exchange Promises
        // ==========================================

        // Group 1: Alice owes Bob 50
        CharSequence p1_G1 = this.bobCurrencyComponent.createPromise(50,
                groupDoc1.getAssignedCurrency(),
                groupId1,
                BOB_ID,
                ALICE_ID,
                true);
        syncAliceBobClaraPeers();
        this.aliceImpl.signPromiseAndSendBack(p1_G1);

        // Group 1: Bob owes Clara 30
        CharSequence p2_G1 = this.claraCurrencyComponent.createPromise(30,
                groupDoc1.getAssignedCurrency(),
                groupId1,
                CLARA_ID,
                BOB_ID,
                true);
        syncAliceBobClaraPeers();
        this.bobImpl.signPromiseAndSendBack(p2_G1);

        // Group 1: Clara owes Alice 100
        CharSequence p3_G1 = this.aliceCurrencyComponent.createPromise(100,
                groupDoc1.getAssignedCurrency(),
                groupId1,
                ALICE_ID,
                CLARA_ID,
                true);
        syncAliceBobClaraPeers();
        this.claraImpl.signPromiseAndSendBack(p3_G1);

        // Group 2: Alice owes Bob 500
        CharSequence p1_G2 = this.bobCurrencyComponent.createPromise(500,
                groupDoc2.getAssignedCurrency(),
                groupId2,
                BOB_ID,
                ALICE_ID,
                true);
        syncAliceBobClaraPeers();
        this.aliceCurrencyComponent.signPromiseAndSendBack(p1_G2);

        // Group 2: Bob owes Clara 50
        CharSequence p2_G2 = this.claraCurrencyComponent.createPromise(50,
                groupDoc2.getAssignedCurrency(),
                groupId2,
                CLARA_ID,
                BOB_ID,
                true);
        syncAliceBobClaraPeers();
        this.bobImpl.signPromiseAndSendBack(p2_G2);

        syncAliceBobClaraPeers();

        // ==========================================
        // 3. Settlement Party
        // ==========================================

        byte[] partyId = this.aliceImpl.initiateSettlementParty(groupId1);

        for (int i = 1; i <= 5; i++) {
            syncAliceBobClaraPeers();
            Thread.sleep(1000);
        }

        // Alice signs the new Promises
        for (SharkPromise p : this.aliceStorage.getAllPendingPromises()) {
            this.aliceImpl.signPromiseAndSendBack(p.getPromiseID());
        }

        // Bob signs the new Promises
        for (SharkPromise p : this.bobStorage.getAllPendingPromises()) {
            this.bobImpl.signPromiseAndSendBack(p.getPromiseID());
        }

        syncAliceBobClaraPeers();
        Thread.sleep(1000);

        // ==========================================
        // 4. Assertions
        // ==========================================

        // check if every group member has a Settlement Document
        SharkSettlementDocument finalDocAlice = this.aliceStorage.getSettlementDocument(partyId);
        Assertions.assertNotNull(finalDocAlice, "Settlement Document not found for Alice!");
        SharkSettlementDocument finalDocBob = this.bobStorage.getSettlementDocument(partyId);
        Assertions.assertNotNull(finalDocBob, "Settlement Document not found for Bob!");
        SharkSettlementDocument finalDocClara = this.claraStorage.getSettlementDocument(partyId);
        Assertions.assertNotNull(finalDocClara, "Settlement Document not found for Clara!");

        // check if every Peer commited a Hash
        Assertions.assertEquals(3, finalDocAlice.getComputedHashes().size());
        Assertions.assertEquals(3, finalDocBob.getComputedHashes().size());
        Assertions.assertEquals(3, finalDocClara.getComputedHashes().size());

        // check if settlement docs are completed
        Assertions.assertEquals(SettlementPartyState.COMPLETED, finalDocAlice.getState());
        Assertions.assertEquals(SettlementPartyState.COMPLETED, finalDocBob.getState());
        Assertions.assertEquals(SettlementPartyState.COMPLETED, finalDocClara.getState());

        // Alice has a net balance of +50
        // Bob has a net balance of +20
        // Clara has a net balance of -70

        // Expected Result of the Greedy Algorithm
        // 1. Clara -> Alice = 50
        // 2. Clara -> Bob = 20
        // we reduced 3 Promises to just 2

        boolean foundClaraToAlice = false;
        boolean foundClaraToBob = false;

        List<byte[]> serializedPromises = this.claraImpl.getSerializedPromisesForGroup(groupId1); // get all Serialized Promises
        List<SharkPromise> allFinalPromises = new ArrayList<>();

        // deserialize Promises
        for (byte[] pBytes : serializedPromises) {
            SharkPromise promise = SharkPromiseSerializer.deserializePromise(
                    pBytes,
                    this.aliceImpl.getSharkPKIComponent().getASAPKeyStore()
            );
            allFinalPromises.add(promise);
        }

        for (SharkPromise p : allFinalPromises) {
            System.out.println("Debtor: " + p.getDebtorID() + " | Creditor: " + p.getCreditorID() + " | Amount: " + p.getAmount());

            if (p.getDebtorID().toString().equals(CLARA_ID.toString()) &&
                    p.getCreditorID().toString().equals(ALICE_ID.toString()) &&
                    p.getAmount() == 50) {
                foundClaraToAlice = true;
            }
            if (p.getDebtorID().toString().equals(CLARA_ID.toString()) &&
                    p.getCreditorID().toString().equals(BOB_ID.toString()) &&
                    p.getAmount() == 20) {
                foundClaraToBob = true;
            }
        }

        Assertions.assertTrue(foundClaraToAlice, "Error: New Promise (Clara pays Alice 50) not found");
        Assertions.assertTrue(foundClaraToBob, "Error: New Promise (Clara pays Bob 20) not found");
    }

    /**
     * Help methode to run an encounter between Alice, Bob and Clara
     */
    private void syncAliceBobClaraPeers() throws Exception {
        // Hinweg
        runEncounter(this.aliceSharkPeer, this.bobSharkPeer, true);
        Thread.sleep(500);
        runEncounter(this.bobSharkPeer, this.claraSharkPeer, true);
        Thread.sleep(500);
        runEncounter(this.claraSharkPeer, this.aliceSharkPeer, true);
        Thread.sleep(500);
        // Rückweg (Damit Antworten auf demselben Weg direkt zurückkommen)
        runEncounter(this.bobSharkPeer, this.aliceSharkPeer, true);
        Thread.sleep(500);
        runEncounter(this.claraSharkPeer, this.bobSharkPeer, true);
        Thread.sleep(500);
        runEncounter(this.aliceSharkPeer, this.claraSharkPeer, true);
    }
}
