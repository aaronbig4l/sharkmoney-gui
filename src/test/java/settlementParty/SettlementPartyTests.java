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

    /**
     * Help methode to run an encounter between Alice, Bob and Clara
     */
    private void syncAliceBobClaraPeers() throws Exception {
        // Hinweg
        runEncounter(this.aliceSharkPeer, this.bobSharkPeer, true);
        Thread.sleep(1000);
        runEncounter(this.bobSharkPeer, this.claraSharkPeer, true);
        Thread.sleep(1000);
        runEncounter(this.claraSharkPeer, this.aliceSharkPeer, true);
        Thread.sleep(1000);
        // Rückweg (Damit Antworten auf demselben Weg direkt zurückkommen)
        runEncounter(this.bobSharkPeer, this.aliceSharkPeer, true);
        Thread.sleep(1000);
        runEncounter(this.claraSharkPeer, this.bobSharkPeer, true);
        Thread.sleep(1000);
        runEncounter(this.aliceSharkPeer, this.claraSharkPeer, true);
    }
}
