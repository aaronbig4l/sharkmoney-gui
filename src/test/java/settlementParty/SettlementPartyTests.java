package settlementParty;

import currency.classes.*;
import exepections.SharkCurrencyException;
import group.SharkGroupDocument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import testHelper.AsapCurrencyTestHelper;
import transactionSettelment.SettlementPartyState;
import transactionSettelment.SharkSettlementDocument;

import java.util.ArrayList;
import java.util.List;

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

        // Alice created a group with Bob and Clara in it (they accepted). This method returns the groupID
        byte[] groupId = this.aliceCreatesGroupWithBobAndClaraSetUp();

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
        Thread.sleep(500);
        this.aliceImpl.signPromiseAndSendBack(promiseAliceToBob);
        Thread.sleep(500);
        System.out.println("DEBUG: before signback alice to bob should be here");
        this.runEncounter(this.aliceSharkPeer, this.bobSharkPeer, true);
        System.out.println("DEBUG: after signback alice to bob should be here");
        Thread.sleep(500);

        // Bob owes Clara 50
        CharSequence promiseBobToClara = this.claraCurrencyComponent.createPromise(50,
                sharkGroupDocument.getAssignedCurrency(),
                groupId,
                CLARA_ID, //creditor
                BOB_ID, //debtor
                true);

        Thread.sleep(500);
        this.runEncounter(this.claraSharkPeer, this.bobSharkPeer, true);
        Thread.sleep(500);
        this.bobImpl.signPromiseAndSendBack(promiseBobToClara);
        Thread.sleep(500);
        System.out.println("DEBUG: before signback bob to clara should be here");
        this.runEncounter(this.bobSharkPeer, this.claraSharkPeer, true);
        System.out.println("DEBUG: after signback bob to clara should be here");
        Thread.sleep(500);

        // Clara owes Alice 30
        CharSequence promiseClaraToAlice = this.claraCurrencyComponent.createPromise(30,
                sharkGroupDocument.getAssignedCurrency(),
                groupId,
                ALICE_ID, //creditor
                CLARA_ID, //debtor
                false);
        Thread.sleep(500);
        this.runEncounter(this.claraSharkPeer, this.aliceSharkPeer, true);
        Thread.sleep(500);
        this.aliceImpl.signPromiseAndSendBack(promiseClaraToAlice);
        Thread.sleep(500);
        System.out.println("DEBUG: before signback clara to alice should be here");
        this.runEncounter(this.aliceSharkPeer, this.claraSharkPeer, true);
        System.out.println("DEBUG: after signback clara to alice should be here");
        Thread.sleep(500);

        int alicesigStorSize = this.aliceStorage.getSignedPromiseStorageSize();
        int bobsigStorSize = this.bobStorage.getSignedPromiseStorageSize();
        int clarasigStorSize = this.claraStorage.getSignedPromiseStorageSize();


        System.out.println("DEBUG: should be 2 each: "+alicesigStorSize+bobsigStorSize+clarasigStorSize);
        // ==========================================
        // 3. Settlement Party
        // ==========================================

        byte[] partyId = this.aliceImpl.initiateSettlementParty(groupId);

        // Gossip Loop: The document has different States GATHERING -> VERIFYING -> COMPLETED, therefore we simulate a Loop for exchanging the Doc
        for (int i = 1; i <= 5; i++) {
            syncAliceBobClaraPeers();
            Thread.sleep(100); // Pause to let them calculate the Hashes

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
            System.out.println("DEBUG: Bob signing promise and sending Alice, total amount: "+this.bobStorage.getPendingPromiseStorageSize() + " should go between Bob: " + p.getCreditorID() + " and Alice: "+ p.getDebtorID());
            this.bobImpl.signPromiseAndSendBack(p.getPromiseID());
        }

        Thread.sleep(1000);
        this.runEncounter(bobSharkPeer,aliceSharkPeer,true);
        Thread.sleep(1000);

        // Clara signs the new Promises
        for (SharkPromise p : this.claraStorage.getAllPendingPromises()) {
            System.out.println("DEBUG: Clara signing promise and sending Alice, total amount: "+this.claraStorage.getPendingPromiseStorageSize() + " should go between Clara: " + p.getCreditorID() + " and Alice: "+ p.getDebtorID());
            this.claraImpl.signPromiseAndSendBack(p.getPromiseID());
        }

        Thread.sleep(1000);
        this.runEncounter(claraSharkPeer,aliceSharkPeer,true);
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
        byte[] groupId = this.aliceCreatesGroupWithBobAndClaraSetUp();
        SharkGroupDocument groupDoc = this.aliceStorage.getGroupDocument(groupId);

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
            Thread.sleep(100);
        }

        syncAliceBobClaraPeers();
        Thread.sleep(100);

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
        byte[] groupId1 = this.aliceCreatesGroupWithBobAndClaraSetUp();
        SharkGroupDocument groupDoc1 = this.aliceStorage.getGroupDocument(groupId1);

        // Setup second Group
        CharSequence currencyName = "Bobs WG Kasse";
        SharkCurrency bobsWGCurrency = new SharkLocalCurrency(
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

        // Alice and Clara join Group 2
        Thread.sleep(100);
        this.bobCurrencyComponent.invitePeerToGroup(groupId2, "Hi Alice, join my group!", ALICE_ID);
        this.bobCurrencyComponent.invitePeerToGroup(groupId2, "Hi Clara, join my group!", CLARA_ID);
        syncAliceBobClaraPeers();
        this.aliceCurrencyComponent.acceptInviteAndSign(currencyName);
        Thread.sleep(100);
        this.claraCurrencyComponent.acceptInviteAndSign(currencyName);
        Thread.sleep(100);
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
        Thread.sleep(500);
        this.runEncounter(this.bobSharkPeer, this.aliceSharkPeer, true);
        Thread.sleep(500);
        this.aliceImpl.signPromiseAndSendBack(p1_G1);
        Thread.sleep(500);
        this.runEncounter(this.aliceSharkPeer, this.bobSharkPeer, true);
        Thread.sleep(500);

        // Group 1: Bob owes Clara 30
        CharSequence p2_G1 = this.claraCurrencyComponent.createPromise(30,
                groupDoc1.getAssignedCurrency(),
                groupId1,
                CLARA_ID,
                BOB_ID,
                true);
        Thread.sleep(500);
        this.runEncounter(this.claraSharkPeer, this.bobSharkPeer, true);
        Thread.sleep(500);
        this.bobImpl.signPromiseAndSendBack(p2_G1);
        Thread.sleep(500);
        this.runEncounter(this.bobSharkPeer, this.claraSharkPeer, true);
        Thread.sleep(500);

        // Group 1: Clara owes Alice 100
        CharSequence p3_G1 = this.aliceCurrencyComponent.createPromise(100,
                groupDoc1.getAssignedCurrency(),
                groupId1,
                ALICE_ID,
                CLARA_ID,
                true);
        Thread.sleep(500);
        this.runEncounter(this.aliceSharkPeer, this.claraSharkPeer, true);
        Thread.sleep(500);
        this.claraImpl.signPromiseAndSendBack(p3_G1);
        Thread.sleep(500);
        this.runEncounter(this.claraSharkPeer, this.aliceSharkPeer, true);
        Thread.sleep(500);

        // Group 2: Alice owes Bob 500
        CharSequence p1_G2 = this.bobCurrencyComponent.createPromise(500,
                groupDoc2.getAssignedCurrency(),
                groupId2,
                BOB_ID,
                ALICE_ID,
                true);
        Thread.sleep(500);
        this.runEncounter(this.bobSharkPeer, this.aliceSharkPeer, true);
        Thread.sleep(500);
        this.aliceImpl.signPromiseAndSendBack(p1_G2);
        Thread.sleep(500);
        this.runEncounter(this.aliceSharkPeer, this.bobSharkPeer, true);
        Thread.sleep(500);


        // Group 2: Alice owes Clara 200
        CharSequence p3_G2 = this.claraCurrencyComponent.createPromise(200,
                groupDoc2.getAssignedCurrency(),
                groupId2,
                CLARA_ID,
                ALICE_ID,
                true);
        Thread.sleep(500);
        this.runEncounter(this.claraSharkPeer, this.aliceSharkPeer, true);
        Thread.sleep(500);
        this.aliceImpl.signPromiseAndSendBack(p3_G2);
        Thread.sleep(500);
        this.runEncounter(this.aliceSharkPeer, this.claraSharkPeer, true);
        Thread.sleep(500);


        // ==========================================
        // 3. Settlement Party
        // ==========================================

        byte[] partyId = this.aliceImpl.initiateSettlementParty(groupId1);

        // Gossip Loop: The document has different States GATHERING -> VERIFYING -> COMPLETED, therefore we simulate a Loop for exchanging the Doc
        for (int i = 1; i <= 5; i++) {
            syncAliceBobClaraPeers();
            Thread.sleep(100); // Pause to let them calculate the Hashes

            // Show the SharkSettlementDoc live
            SharkSettlementDocument currentDoc = this.aliceStorage.getSettlementDocument(partyId);
            if (currentDoc != null) {
                System.out.println("Runde " + i + " | Status Alice: " + currentDoc.getState()
                        + " | Gesammelte Hashes: " + currentDoc.getComputedHashes().size());
            }
        }

        // Alice signs the new Promises
        for (SharkPromise p : this.aliceStorage.getAllPendingPromises()) {
            this.aliceImpl.signPromiseAndSendBack(p.getPromiseID());
        }

        Thread.sleep(1000);
        this.runEncounter(claraSharkPeer,aliceSharkPeer,true);
        Thread.sleep(1000);

        // Bob signs the new Promises
        for (SharkPromise p : this.bobStorage.getAllPendingPromises()) {
            this.bobImpl.signPromiseAndSendBack(p.getPromiseID());
        }

        Thread.sleep(1000);
        this.runEncounter(bobSharkPeer,claraSharkPeer,true);
        Thread.sleep(1000);

        syncAliceBobClaraPeers();
        Thread.sleep(100);

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

    @Test
    public void testCompleteSettlementCycleWithEncryptedGroup() throws Exception {
        // ==========================================
        // 1. SETUP: Start Peers and establish Group
        // ==========================================

        // Alice created a group with Bob and Clara in it (they accepted). This method returns the groupID
        byte[] groupId = this.aliceCreatesEncryptedGroupWithBobAndClaraSetUp();

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

        Thread.sleep(100);
        this.runEncounter(this.bobSharkPeer, this.aliceSharkPeer, true);
        Thread.sleep(100);
        this.aliceImpl.signPromiseAndSendBack(promiseAliceToBob);
        Thread.sleep(100);
        this.runEncounter(this.aliceSharkPeer, this.bobSharkPeer, true);
        Thread.sleep(100);

        // Bob owes Clara 50
        CharSequence promiseBobToClara = this.claraCurrencyComponent.createPromise(50,
                sharkGroupDocument.getAssignedCurrency(),
                groupId,
                CLARA_ID, //creditor
                BOB_ID, //debtor
                true);

        Thread.sleep(100);
        this.runEncounter(this.claraSharkPeer, this.bobSharkPeer, true);
        Thread.sleep(100);
        this.bobImpl.signPromiseAndSendBack(promiseBobToClara);
        Thread.sleep(100);
        this.runEncounter(this.bobSharkPeer, this.claraSharkPeer, true);
        Thread.sleep(100);

        // Clara owes Alice 30
        CharSequence promiseClaraToAlice = this.aliceCurrencyComponent.createPromise(30,
                sharkGroupDocument.getAssignedCurrency(),
                groupId,
                ALICE_ID, //creditor
                CLARA_ID, //debtor
                true);
        Thread.sleep(100);
        this.runEncounter(this.aliceSharkPeer, this.claraSharkPeer, true);
        Thread.sleep(100);
        this.claraImpl.signPromiseAndSendBack(promiseClaraToAlice);
        Thread.sleep(100);
        this.runEncounter(this.claraSharkPeer, this.aliceSharkPeer, true);
        Thread.sleep(100);

        syncAliceBobClaraPeers();

        Thread.sleep(100);

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


        Thread.sleep(200);
        // Clara signs the new Promises
        for (SharkPromise p : this.claraStorage.getAllPendingPromises()) {
            Thread.sleep(200);
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
    public void startingPartyWithOnly1PersonGroup() throws SharkCurrencyException {

        CharSequence currencyName = "AliceTalerAlone";
        SharkCurrency dummyCurrency = new SharkLocalCurrency(
            currencyName.toString(),        // Name
            "A test Currency"               // Spec
        );

        // ALice creates group only with herself
        this.aliceImpl.establishGroup(dummyCurrency, false, false, true);
    }

}
