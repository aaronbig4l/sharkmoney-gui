package sendingPromisesTests;


import com.fasterxml.jackson.annotation.JsonTypeInfo;
import currency.classes.*;
import exepections.SharkCurrencyException;
import exepections.SharkPromiseException;
import group.SharkGroupDocument;
import net.sharksystem.SharkException;
import net.sharksystem.asap.ASAPException;
import net.sharksystem.asap.pki.CredentialMessageInMemo;
import net.sharksystem.pki.SharkPKIComponent;
import org.junit.Assert;
import org.junit.jupiter.api.*;
import testHelper.AsapCurrencyTestHelper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class PromisesTest extends AsapCurrencyTestHelper {

    public PromisesTest() {
        super(PromisesTest.class.getSimpleName());
    }

    @BeforeEach
    void setUp(TestInfo testInfo) {
        String testName = testInfo.getDisplayName()
                .replaceAll("[^a-zA-Z0-9]", "_");
        this.initSubRootFolder(
                PromisesTest.class.getSimpleName(),
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

    @Test
    public void createPromiseAndSendToBobBothInSameGroup() throws SharkException, IOException, InterruptedException {

        // Alice created a group with bob in it (he accepted). This method returns the groupID
        byte[] groupId = this.aliceCreatesEncryptedGroupWithBobSetUp();

        SharkPKIComponent alicePKI = (SharkPKIComponent) this.aliceSharkPeer.getComponent(SharkPKIComponent.class);
        SharkPKIComponent bobPKI = (SharkPKIComponent) this.bobSharkPeer.getComponent(SharkPKIComponent.class);

        // let Bob accept ALice credentials and create a certificate
        CredentialMessageInMemo aliceCredentialMessage = new CredentialMessageInMemo(ALICE_ID, ALICE_NAME, System.currentTimeMillis(), alicePKI.getPublicKey());
        bobPKI.acceptAndSignCredential(aliceCredentialMessage);

        // Alice accepts Bob Public Key
        CredentialMessageInMemo bobCredentialMessage = new CredentialMessageInMemo(BOB_ID, BOB_NAME, System.currentTimeMillis(), bobPKI.getPublicKey());
        alicePKI.acceptAndSignCredential(bobCredentialMessage);

        CharSequence promiseId = this.aliceCurrencyComponent.createPromise(2,
                this.aliceStorage.getGroupDocument(groupId).getAssignedCurrency(),
                groupId,
                ALICE_ID, //creditor
                BOB_ID, //debtor
                true);

        Thread.sleep(200);
        this.runEncounter(this.aliceSharkPeer, this.bobSharkPeer, true);
        Thread.sleep(200);
        SharkPromise alicePromise = this.aliceStorage.getSharkPendingPromiseFromStorage(promiseId);
        SharkPromise bobPromise = this.bobStorage.getSharkPendingPromiseFromStorage(promiseId);

        Assertions.assertNotNull(alicePromise);
        Assertions.assertNotNull(bobPromise);
        Assertions.assertNotNull(promiseId);
        Assertions.assertEquals(promiseId.toString(), alicePromise.getPromiseID().toString());
        Assertions.assertEquals(promiseId.toString(), bobPromise.getPromiseID().toString());
        Assertions.assertEquals(SharkPromiseState.SIGNED_BY_CREDITOR
                ,alicePromise.getStateOfPromise());
        Assertions.assertEquals(SharkPromiseState.SIGNED_BY_CREDITOR
                ,bobPromise.getStateOfPromise());
        Assertions.assertNull(alicePromise.getDebtorSignature());
        Assertions.assertNotNull(alicePromise.getCreditorSignature());
        Assertions.assertNull(bobPromise.getDebtorSignature());
        Assertions.assertNotNull(bobPromise.getCreditorSignature());
    }

    @Test
    public void createPromiseSendAndSignWithinAGroupAliceBob() throws SharkException, IOException, InterruptedException {

        // Alice created a group with bob in it (he accepted). This method returns the groupID
        byte[] groupId = this.aliceCreatesEncryptedGroupWithBobSetUp();

        SharkPKIComponent alicePKI = (SharkPKIComponent) this.aliceSharkPeer.getComponent(SharkPKIComponent.class);
        SharkPKIComponent bobPKI = (SharkPKIComponent) this.bobSharkPeer.getComponent(SharkPKIComponent.class);

        // let Bob accept ALice credentials and create a certificate
        CredentialMessageInMemo aliceCredentialMessage = new CredentialMessageInMemo(ALICE_ID, ALICE_NAME, System.currentTimeMillis(), alicePKI.getPublicKey());
        bobPKI.acceptAndSignCredential(aliceCredentialMessage);

        // Alice accepts Bob Public Key
        CredentialMessageInMemo bobCredentialMessage = new CredentialMessageInMemo(BOB_ID, BOB_NAME, System.currentTimeMillis(), bobPKI.getPublicKey());
        alicePKI.acceptAndSignCredential(bobCredentialMessage);

        SharkGroupDocument sharkGroupDocument = this.aliceStorage.getGroupDocument(groupId);
        CharSequence promiseId = this.aliceCurrencyComponent.createPromise(2,
                sharkGroupDocument.getAssignedCurrency(),
                groupId,
                ALICE_ID, //creditor
                BOB_ID, //debtor
                true);

        Thread.sleep(500);
        this.runEncounter(this.aliceSharkPeer, this.bobSharkPeer, true);
        Thread.sleep(1000);
        this.bobImpl.signPromiseAndSendBack(promiseId);
        Thread.sleep(1000);
        this.runEncounter(this.bobSharkPeer, this.aliceSharkPeer, true);
        Thread.sleep(1000);

        //Assertions
        SharkPromise signedPromiseAlice
                = this.aliceStorage.getSharkSignedPromiseFromStorage(promiseId);
        SharkPromise signedPromiseBob
                = this.bobStorage.getSharkSignedPromiseFromStorage(promiseId);

        Assertions.assertNull(this.aliceStorage.getSharkPendingPromiseFromStorage(promiseId));
        Assertions.assertNull(this.bobStorage.getSharkPendingPromiseFromStorage(promiseId));
        Assertions.assertEquals(promiseId, signedPromiseAlice.getPromiseID());
        Assertions.assertEquals(promiseId, signedPromiseBob.getPromiseID());
        Assertions.assertEquals(SharkPromiseState.FULLY_SIGNED
                ,signedPromiseAlice.getStateOfPromise());
        Assertions.assertEquals(SharkPromiseState.FULLY_SIGNED
                ,signedPromiseBob.getStateOfPromise());
        Assertions.assertNotNull(signedPromiseAlice.getCreditorSignature());
        Assertions.assertNotNull(signedPromiseAlice.getDebtorSignature());
        Assertions.assertNotNull(signedPromiseBob.getCreditorSignature());
        Assertions.assertNotNull(signedPromiseBob.getDebtorSignature());
        Assertions.assertTrue(signedPromiseAlice.getCreditorSignature().length > 0);
        Assertions.assertTrue(signedPromiseAlice.getDebtorSignature().length > 0);
        Assertions.assertTrue(signedPromiseBob.getCreditorSignature().length > 0);
        Assertions.assertTrue(signedPromiseBob.getDebtorSignature().length > 0);
        Assertions.assertEquals(2, signedPromiseAlice.getAmount());
        Assertions.assertEquals(2, signedPromiseBob.getAmount());
        Assertions.assertArrayEquals(groupId, signedPromiseAlice.getGroupIDOfPromise());
        Assertions.assertArrayEquals(groupId, signedPromiseBob.getGroupIDOfPromise());
    }

    @Test
    public void createPromiseSendAndSignWithNothingWithinAGroupAliceBob() throws SharkException, IOException, InterruptedException {

        byte[] groupId = this.aliceCreatesEncryptedGroupWithBobSetUp();

        try {
            SharkGroupDocument sharkGroupDocument = this.aliceStorage.getGroupDocument(groupId);
            CharSequence promiseId = this.aliceCurrencyComponent.createPromise(2,
                    sharkGroupDocument.getAssignedCurrency(),
                    groupId,
                    ALICE_ID, //creditor
                    BOB_ID, //debtor
                    true);
            Assertions.fail("It should not find BOBs public Key bc they not exchanged credentials ");
        }
        catch (ASAPException e ){

        }
    }

    @Test
    public void createPromiseSendAndSignWithinAGroupAliceBobClara() throws SharkException, IOException, InterruptedException {
        byte[] groupId = this.aliceCreatesEncryptedGroupWithBobAndClaraSetUp();

        SharkPKIComponent alicePKI = (SharkPKIComponent) this.aliceSharkPeer.getComponent(SharkPKIComponent.class);
        SharkPKIComponent bobPKI = (SharkPKIComponent) this.bobSharkPeer.getComponent(SharkPKIComponent.class);
        SharkPKIComponent claraPKI = (SharkPKIComponent) this.claraSharkPeer.getComponent(SharkPKIComponent.class);

        CredentialMessageInMemo aliceCredentialMessage = new CredentialMessageInMemo(ALICE_ID, ALICE_NAME, System.currentTimeMillis(), alicePKI.getPublicKey());
        CredentialMessageInMemo bobCredentialMessage = new CredentialMessageInMemo(BOB_ID, BOB_NAME, System.currentTimeMillis(), bobPKI.getPublicKey());
        CredentialMessageInMemo claraCredentialMessage = new CredentialMessageInMemo(CLARA_ID, CLARA_NAME, System.currentTimeMillis(), claraPKI.getPublicKey());

        bobPKI.acceptAndSignCredential(aliceCredentialMessage);
        alicePKI.acceptAndSignCredential(bobCredentialMessage);
        claraPKI.acceptAndSignCredential(aliceCredentialMessage);
        bobPKI.acceptAndSignCredential(claraCredentialMessage);
        claraPKI.acceptAndSignCredential(bobCredentialMessage);
        alicePKI.acceptAndSignCredential(claraCredentialMessage);

        SharkGroupDocument sharkGroupDocument = this.aliceStorage.getGroupDocument(groupId);
        CharSequence promiseId = this.aliceCurrencyComponent.createPromise(2,
                sharkGroupDocument.getAssignedCurrency(),
                groupId,
                ALICE_ID, //creditor
                BOB_ID, //debtor
                true);

        Thread.sleep(500);
        this.runEncounter(this.aliceSharkPeer, this.bobSharkPeer, true);
        this.runEncounter(this.aliceSharkPeer, this.claraSharkPeer, true);

        Thread.sleep(500);
        this.bobImpl.signPromiseAndSendBack(promiseId);

        Thread.sleep(500);
        this.runEncounter(this.bobSharkPeer, this.aliceSharkPeer, true);
        this.runEncounter(this.bobSharkPeer, this.claraSharkPeer, true);

        Thread.sleep(500);

        SharkPromise signedPromiseAlice
                = this.aliceStorage.getSharkSignedPromiseFromStorage(promiseId);
        SharkPromise signedPromiseBob
                = this.bobStorage.getSharkSignedPromiseFromStorage(promiseId);

        Assertions.assertNull(this.aliceStorage.getSharkPendingPromiseFromStorage(promiseId));
        Assertions.assertNull(this.bobStorage.getSharkPendingPromiseFromStorage(promiseId));
        Assertions.assertEquals(promiseId, signedPromiseAlice.getPromiseID());
        Assertions.assertEquals(promiseId, signedPromiseBob.getPromiseID());
        Assertions.assertEquals(SharkPromiseState.FULLY_SIGNED
                ,signedPromiseAlice.getStateOfPromise());
        Assertions.assertEquals(SharkPromiseState.FULLY_SIGNED
                ,signedPromiseBob.getStateOfPromise());
        Assertions.assertNotNull(signedPromiseAlice.getCreditorSignature());
        Assertions.assertNotNull(signedPromiseAlice.getDebtorSignature());
        Assertions.assertNotNull(signedPromiseBob.getCreditorSignature());
        Assertions.assertNotNull(signedPromiseBob.getDebtorSignature());
        Assertions.assertTrue(signedPromiseAlice.getCreditorSignature().length > 0);
        Assertions.assertTrue(signedPromiseAlice.getDebtorSignature().length > 0);
        Assertions.assertTrue(signedPromiseBob.getCreditorSignature().length > 0);
        Assertions.assertTrue(signedPromiseBob.getDebtorSignature().length > 0);
        Assertions.assertEquals(2, signedPromiseAlice.getAmount());
        Assertions.assertEquals(2, signedPromiseBob.getAmount());
        Assertions.assertArrayEquals(groupId, signedPromiseAlice.getGroupIDOfPromise());
        Assertions.assertArrayEquals(groupId, signedPromiseBob.getGroupIDOfPromise());
    }


    @Test
    public void sendPromiseAndSeeHowMuchBobAndAliceHaveAfterSendingToHim() throws SharkException, IOException, InterruptedException {
        byte []  groupId = this.aliceCreatesEncryptedGroupWithBobSetUp();

        SharkPKIComponent alicePKI
                = (SharkPKIComponent) this.aliceSharkPeer.getComponent(SharkPKIComponent.class);
        SharkPKIComponent bobPKI
                = (SharkPKIComponent) this.bobSharkPeer.getComponent(SharkPKIComponent.class);

        // let Bob accept ALice credentials and create a certificate
        CredentialMessageInMemo aliceCredentialMessage
                = new CredentialMessageInMemo(ALICE_ID,
                ALICE_NAME,
                System.currentTimeMillis(),
                alicePKI.getPublicKey());
        bobPKI.acceptAndSignCredential(aliceCredentialMessage);

        // Alice accepts Bob Public Key
        CredentialMessageInMemo bobCredentialMessage
                = new CredentialMessageInMemo(BOB_ID,
                BOB_NAME,
                System.currentTimeMillis(),
                bobPKI.getPublicKey());
        alicePKI.acceptAndSignCredential(bobCredentialMessage);

        byte[] currencyId
                = this.aliceStorage.getGroupDocument(groupId).getAssignedCurrency().getCurrencyId();

        Assertions.assertEquals(0, bobCurrencyComponent.getBalance(currencyId));


        SharkGroupDocument sharkGroupDocument = this.aliceStorage.getGroupDocument(groupId);
        CharSequence promiseId = this.aliceCurrencyComponent.createPromise(2,
                sharkGroupDocument.getAssignedCurrency(),
                groupId,
                ALICE_ID, //creditor
                BOB_ID, //debtor
                true);

        Thread.sleep(500);
        this.runEncounter(this.aliceSharkPeer, this.bobSharkPeer, true);
        Thread.sleep(1000);
        this.bobImpl.signPromiseAndSendBack(promiseId);
        Thread.sleep(1000);
        this.runEncounter(this.bobSharkPeer, this.aliceSharkPeer, true);
        Thread.sleep(1000);


        Assertions.assertEquals(-2, bobCurrencyComponent.getBalance(currencyId));
        Assertions.assertEquals(2, aliceImpl.getBalance(currencyId));
        Assertions.assertEquals(-2, bobCurrencyComponent.getExtendedBalance(currencyId, aliceSharkPeer.getPeerID()));
        Assertions.assertEquals(2, aliceImpl.getExtendedBalance(currencyId, bobSharkPeer.getPeerID()));



    }

    @Test
    public void sendingPromisesToDifferentPeers() throws SharkException, IOException, InterruptedException {

        byte[] groupId = this.aliceCreatesEncryptedGroupWithBobAndClaraSetUp();

        SharkPKIComponent alicePKI = (SharkPKIComponent) this.aliceSharkPeer.getComponent(SharkPKIComponent.class);
        SharkPKIComponent bobPKI = (SharkPKIComponent) this.bobSharkPeer.getComponent(SharkPKIComponent.class);
        SharkPKIComponent claraPKI = (SharkPKIComponent) this.claraSharkPeer.getComponent(SharkPKIComponent.class);


        bobPKI.acceptAndSignCredential(new CredentialMessageInMemo(ALICE_ID, ALICE_NAME, System.currentTimeMillis(), alicePKI.getPublicKey()));
        alicePKI.acceptAndSignCredential(new CredentialMessageInMemo(BOB_ID, BOB_NAME, System.currentTimeMillis(), bobPKI.getPublicKey()));

        claraPKI.acceptAndSignCredential(new CredentialMessageInMemo(ALICE_ID, ALICE_NAME, System.currentTimeMillis(), alicePKI.getPublicKey()));
        alicePKI.acceptAndSignCredential(new CredentialMessageInMemo(CLARA_ID, CLARA_NAME, System.currentTimeMillis(), claraPKI.getPublicKey()));

        SharkGroupDocument aliceDoc = this.aliceStorage.getGroupDocument(groupId);
        byte[] currencyId = aliceDoc.getAssignedCurrency().getCurrencyId();
        SharkCurrency currency = aliceDoc.getAssignedCurrency();

        CharSequence promiseBobId = this.aliceCurrencyComponent.createPromise(5,
                currency, groupId, ALICE_ID, BOB_ID, true);

        CharSequence promiseClaraId = this.aliceCurrencyComponent.createPromise(10,
                currency, groupId, ALICE_ID, CLARA_ID, true);

        // Network Sync 1: Sending the promises
        Thread.sleep(500);
        this.runEncounter(this.aliceSharkPeer, this.bobSharkPeer, true);
        Thread.sleep(500);
        this.runEncounter(this.aliceSharkPeer, this.claraSharkPeer, true);
        Thread.sleep(2000);

        this.bobImpl.signPromiseAndSendBack(promiseBobId);
        this.claraImpl.signPromiseAndSendBack(promiseClaraId);


        Thread.sleep(500);
        this.runEncounter(this.bobSharkPeer, this.aliceSharkPeer, true);
        Thread.sleep(500);
        this.runEncounter(this.claraSharkPeer, this.aliceSharkPeer, true);
        Thread.sleep(2000);


        Assertions.assertEquals(15, this.aliceImpl.getBalance(currencyId), "Alice should have a balance of 15 units (5 + 10).");
        Assertions.assertEquals(-5, this.bobCurrencyComponent.getBalance(currencyId), "Bob should be in debt with -5.");
        Assertions.assertEquals(-10, this.claraImpl.getBalance(currencyId), "Clara should be in debt with -10.");

        Assertions.assertEquals(5, this.aliceImpl.getExtendedBalance(currencyId, bobSharkPeer.getPeerID()));
        Assertions.assertEquals(10, this.aliceImpl.getExtendedBalance(currencyId, claraSharkPeer.getPeerID()));

        Assertions.assertEquals(-5, this.bobCurrencyComponent.getExtendedBalance(currencyId, aliceSharkPeer.getPeerID()));
        Assertions.assertEquals(-10, this.claraImpl.getExtendedBalance(currencyId, aliceSharkPeer.getPeerID()));
        Assertions.assertEquals(0, this.bobCurrencyComponent.getExtendedBalance(currencyId, claraSharkPeer.getPeerID()));

        Assertions.assertEquals(SharkPromiseState.FULLY_SIGNED, this.aliceStorage.getSharkSignedPromiseFromStorage(promiseBobId).getStateOfPromise());
        Assertions.assertEquals(SharkPromiseState.FULLY_SIGNED, this.aliceStorage.getSharkSignedPromiseFromStorage(promiseClaraId).getStateOfPromise());
    }

    @Test
    public void sendPromisesInDifferentGroupsButThePeersAreTheSame() throws SharkException, IOException, InterruptedException {
        // Arrange: Basic setup for Alice and Bob
        this.setUpScenarioEstablishCurrency_2_BobAndAlice();
        SharkPKIComponent alicePKI = (SharkPKIComponent) this.aliceSharkPeer.getComponent(SharkPKIComponent.class);
        SharkPKIComponent bobPKI = (SharkPKIComponent) this.bobSharkPeer.getComponent(SharkPKIComponent.class);
        bobPKI.acceptAndSignCredential(new CredentialMessageInMemo(ALICE_ID, ALICE_NAME, System.currentTimeMillis(), alicePKI.getPublicKey()));
        alicePKI.acceptAndSignCredential(new CredentialMessageInMemo(BOB_ID, BOB_NAME, System.currentTimeMillis(), bobPKI.getPublicKey()));

        SharkCurrency currencyA = new SharkLocalCurrency(false, "Urlaubskasse", "Spec A");
        SharkCurrency currencyB = new SharkLocalCurrency(false, "WG_Kasse", "Spec B");

        ArrayList<CharSequence> whitelist = new ArrayList<>(List.of(BOB_ID));

        byte[] groupIdA = this.aliceCurrencyComponent.establishGroup(currencyA, whitelist, false, true);
        byte[] groupIdB = this.aliceCurrencyComponent.establishGroup(currencyB, whitelist, false, true);
        Thread.sleep(1000);


        this.aliceCurrencyComponent.invitePeerToGroup(groupIdA, "Join Urlaub", BOB_ID);
        this.aliceCurrencyComponent.invitePeerToGroup(groupIdB, "Join WG", BOB_ID);
        this.runEncounter(this.aliceSharkPeer, this.bobSharkPeer, true);
        Thread.sleep(1000);


        this.bobImpl.acceptInviteAndSign("Urlaubskasse");
        this.bobImpl.acceptInviteAndSign("WG_Kasse");
        this.runEncounter(this.bobSharkPeer, this.aliceSharkPeer, true);
        Thread.sleep(1000);

        SharkGroupDocument docA = this.aliceStorage.getGroupDocument(groupIdA);
        SharkGroupDocument docB = this.aliceStorage.getGroupDocument(groupIdB);


        CharSequence promiseA = this.aliceCurrencyComponent.createPromise(20, docA.getAssignedCurrency(), groupIdA, ALICE_ID, BOB_ID, true);


        CharSequence promiseB = this.bobCurrencyComponent.createPromise(30, docB.getAssignedCurrency(), groupIdB, BOB_ID, ALICE_ID, true);


        this.runEncounter(this.aliceSharkPeer, this.bobSharkPeer, true);
        Thread.sleep(1000);


        this.bobImpl.signPromiseAndSendBack(promiseA);
        this.aliceImpl.signPromiseAndSendBack(promiseB);


        this.runEncounter(this.bobSharkPeer, this.aliceSharkPeer, true);
        Thread.sleep(1000);


        Assertions.assertEquals(20, this.aliceImpl.getBalance(currencyA.getCurrencyId()));
        Assertions.assertEquals(-20, this.bobCurrencyComponent.getBalance(currencyA.getCurrencyId()));


        Assertions.assertEquals(30, this.bobCurrencyComponent.getBalance(currencyB.getCurrencyId()));
        Assertions.assertEquals(-30, this.aliceImpl.getBalance(currencyB.getCurrencyId()));
    }

    @Test
    public void sendingPromisesToOtherPeerInAnotherGroup() throws SharkException, InterruptedException {
       byte[][] groupIds = aliceCreatesEncryptedGroupAndBobToo();
       byte[] groupIdAlice = groupIds[0];
       byte[] groupIdBob = groupIds[1];
       SharkCurrency aliceCurrency
               = this.aliceStorage.getGroupDocument(groupIdAlice).getAssignedCurrency();
       SharkCurrency bobCurrency
               = this.bobStorage.getGroupDocument(groupIdBob).getAssignedCurrency();
       byte[] currencyIdAlice = aliceCurrency.getCurrencyId();
       byte[] currencyIdBob = bobCurrency.getCurrencyId();

       //Assertions
        Exception exAliceToBob = assertThrows(SharkPromiseException.class, () -> {
            this.aliceCurrencyComponent.createPromise(5,
                    aliceCurrency, groupIdAlice, ALICE_ID, BOB_ID, true);
        });
        Exception exBobToAlice = assertThrows(SharkPromiseException.class, () -> {
            this.bobCurrencyComponent.createPromise(5,
                    bobCurrency, groupIdBob, BOB_ID, ALICE_ID, true);
        });
        Exception aliceGettingPendingException = assertThrows(SharkCurrencyException.class, () -> {
            this.aliceStorage.getGroupDocument(groupIdBob);
        });
        Exception bobGettingPendingException = assertThrows(SharkCurrencyException.class, () -> {
            this.bobStorage.getGroupDocument(groupIdAlice);
        });

        Assertions.assertEquals("Creditor and Debitor must be in the same group with given ID: "
                + Arrays.toString(groupIdAlice),
                exAliceToBob.getMessage());
        Assertions.assertEquals("Creditor and Debitor must be in the same group with given ID: "
                        + Arrays.toString(groupIdBob),
                exBobToAlice.getMessage());
        Assertions.assertEquals(0, this.aliceStorage.getPendingPromiseStorageSize());
        Assertions.assertEquals(0, this.bobStorage.getPendingPromiseStorageSize());
        Assertions.assertEquals(0, this.aliceImpl.getBalance(currencyIdAlice));
        Assertions.assertEquals(0, this.bobImpl.getBalance(currencyIdBob));
        Assertions.assertTrue(aliceGettingPendingException
                .getMessage().contains("not found in Storage"));
        Assertions.assertTrue(bobGettingPendingException
                .getMessage().contains("not found in Storage"));
        Assertions.assertNotNull(this.aliceStorage.getGroupDocument(groupIdAlice));
        Assertions.assertNotNull(this.bobStorage.getGroupDocument(groupIdBob));
    }

    @Test
    public void transferAPromiseToAnotherPeerInTheSameGroup() throws SharkException, IOException, InterruptedException {
        byte[] groupId = this.aliceCreatesEncryptedGroupWithBobAndClaraSetUp();

        SharkPKIComponent alicePKI = (SharkPKIComponent) this.aliceSharkPeer.getComponent(SharkPKIComponent.class);
        SharkPKIComponent bobPKI = (SharkPKIComponent) this.bobSharkPeer.getComponent(SharkPKIComponent.class);
        SharkPKIComponent claraPKI = (SharkPKIComponent) this.claraSharkPeer.getComponent(SharkPKIComponent.class);

        bobPKI.acceptAndSignCredential(new CredentialMessageInMemo(ALICE_ID, ALICE_NAME, System.currentTimeMillis(), alicePKI.getPublicKey()));
        alicePKI.acceptAndSignCredential(new CredentialMessageInMemo(BOB_ID, BOB_NAME, System.currentTimeMillis(), bobPKI.getPublicKey()));

        claraPKI.acceptAndSignCredential(new CredentialMessageInMemo(ALICE_ID, ALICE_NAME, System.currentTimeMillis(), alicePKI.getPublicKey()));
        alicePKI.acceptAndSignCredential(new CredentialMessageInMemo(CLARA_ID, CLARA_NAME, System.currentTimeMillis(), claraPKI.getPublicKey()));

        SharkGroupDocument aliceDoc = this.aliceStorage.getGroupDocument(groupId);
        byte[] currencyId = aliceDoc.getAssignedCurrency().getCurrencyId();
        SharkCurrency currency = aliceDoc.getAssignedCurrency();

        CharSequence promiseId = this.aliceCurrencyComponent.createPromise(
                2, currency, groupId, ALICE_ID, BOB_ID, true
        );

        Thread.sleep(500);
        this.runEncounter(this.aliceSharkPeer, this.bobSharkPeer, true);
        Thread.sleep(500);

        this.bobImpl.signPromiseAndSendBack(promiseId);

        Thread.sleep(500);
        this.runEncounter(this.bobSharkPeer, this.aliceSharkPeer, true);
        Thread.sleep(500);

        this.aliceCurrencyComponent.transferPromiseToAnotherPeer(promiseId, CLARA_ID);

        Thread.sleep(500);
        this.runEncounter(this.aliceSharkPeer, this.claraSharkPeer, true);
        Thread.sleep(500);

        Assertions.assertEquals(2, this.claraImpl.getBalance(currencyId));
        Assertions.assertEquals(-2, this.bobCurrencyComponent.getBalance(currencyId));
        Assertions.assertEquals(0, this.aliceImpl.getBalance(currencyId));}

    @Test
    public void transferAPromiseToAnotherPeerInTheAnotherGroup() throws SharkException, InterruptedException, IOException {

        byte[][] groupIds = aliceCreatesEncryptedGroupAndBobToo();
        byte[] groupIdAlice = groupIds[0];

        SharkCurrency aliceCurrency = this.aliceStorage.getGroupDocument(groupIdAlice).getAssignedCurrency();

        CharSequence promiseId = this.aliceCurrencyComponent.createPromise(
                5, aliceCurrency, groupIdAlice, ALICE_ID, ALICE_ID, true
        );

        Exception transferException = assertThrows(SharkPromiseException.class, () -> {
            this.aliceCurrencyComponent.transferPromiseToAnotherPeer(promiseId, BOB_ID);
        });

        Assertions.assertNotNull(transferException.getMessage());
    }

    @Test
    public void createPromiseWithZeroOrNegativeAmountAliceToBob() throws SharkException, IOException, InterruptedException {

        // Alice created a group with bob in it (he accepted). This method returns the groupID
        byte[] groupId = this.aliceCreatesEncryptedGroupWithBobSetUp();

        SharkPKIComponent alicePKI = (SharkPKIComponent) this.aliceSharkPeer.getComponent(SharkPKIComponent.class);
        SharkPKIComponent bobPKI = (SharkPKIComponent) this.bobSharkPeer.getComponent(SharkPKIComponent.class);

        // let Bob accept ALice credentials and create a certificate
        CredentialMessageInMemo aliceCredentialMessage = new CredentialMessageInMemo(ALICE_ID, ALICE_NAME, System.currentTimeMillis(), alicePKI.getPublicKey());
        bobPKI.acceptAndSignCredential(aliceCredentialMessage);

        // Alice accepts Bob Public Key
        CredentialMessageInMemo bobCredentialMessage = new CredentialMessageInMemo(BOB_ID, BOB_NAME, System.currentTimeMillis(), bobPKI.getPublicKey());
        alicePKI.acceptAndSignCredential(bobCredentialMessage);

        SharkCurrency currency = this.aliceStorage.getGroupDocument(groupId).getAssignedCurrency();

        // Should not work because amount is 0
        Exception cantCreateZeroAmountException = assertThrows(SharkPromiseException.class, () -> {
            this.aliceCurrencyComponent
                    .createPromise(0, currency, groupId, ALICE_ID, BOB_ID, true);
        });
        // Should not work because amount is negative
        Exception cantCreateNegativeAmountException = assertThrows(SharkPromiseException.class, () -> {
            this.aliceCurrencyComponent
                    .createPromise(-2, currency, groupId, ALICE_ID, BOB_ID, true);
        });
        Thread.sleep(500);
        this.runEncounter(this.aliceSharkPeer, this.bobSharkPeer, true);
        Thread.sleep(500);

        //TODO: assertions
        Assertions.assertEquals("Amount for promises must be positive",
                cantCreateZeroAmountException.getMessage());
        Assertions.assertEquals("Amount for promises must be positive",
                cantCreateNegativeAmountException.getMessage());
        Assertions.assertEquals(0, this.aliceStorage.getPendingPromiseStorageSize());
        Assertions.assertEquals(0, this.bobStorage.getPendingPromiseStorageSize());
    }

    @Test
    public void bobTriesToSignPromiseTwice() throws SharkException, IOException, InterruptedException {

        // Alice created a group with bob in it (he accepted). This method returns the groupID
        byte[] groupId = this.aliceCreatesEncryptedGroupWithBobSetUp();

        SharkPKIComponent alicePKI = (SharkPKIComponent) this.aliceSharkPeer.getComponent(SharkPKIComponent.class);
        SharkPKIComponent bobPKI = (SharkPKIComponent) this.bobSharkPeer.getComponent(SharkPKIComponent.class);

        // let Bob accept ALice credentials and create a certificate
        CredentialMessageInMemo aliceCredentialMessage = new CredentialMessageInMemo(ALICE_ID, ALICE_NAME, System.currentTimeMillis(), alicePKI.getPublicKey());
        bobPKI.acceptAndSignCredential(aliceCredentialMessage);

        // Alice accepts Bob Public Key
        CredentialMessageInMemo bobCredentialMessage = new CredentialMessageInMemo(BOB_ID, BOB_NAME, System.currentTimeMillis(), bobPKI.getPublicKey());
        alicePKI.acceptAndSignCredential(bobCredentialMessage);

        SharkCurrency currency = this.aliceStorage.getGroupDocument(groupId).getAssignedCurrency();
        CharSequence promiseID = this.aliceCurrencyComponent.createPromise(5,
                currency, groupId, ALICE_ID, BOB_ID, true);
        Thread.sleep(500);
        this.runEncounter(this.aliceSharkPeer, this.bobSharkPeer, true);
        Thread.sleep(500);
        this.bobCurrencyComponent.signPromiseAndSendBack(promiseID);
        Thread.sleep(100);
        Exception secondTimeSigningException = assertThrows(RuntimeException.class, () -> {
            this.bobCurrencyComponent.signPromiseAndSendBack(promiseID);
        });
        Thread.sleep(500);
        this.runEncounter(this.bobSharkPeer, this.aliceSharkPeer, true);

        Assertions.assertTrue(secondTimeSigningException.getMessage().contains("has already been sent and signed"));
        Assertions.assertEquals(0, this.bobStorage.getPendingPromiseStorageSize());
        Assertions.assertEquals(1, this.aliceStorage.getSignedPromiseStorageSize());
        Assertions.assertEquals(1, this.bobStorage.getSignedPromiseStorageSize());
        Assertions.assertEquals(promiseID,
                this.aliceStorage.getSharkSignedPromiseFromStorage(promiseID).getPromiseID());
        Assertions.assertEquals(promiseID,
                this.bobStorage.getSharkSignedPromiseFromStorage(promiseID).getPromiseID());
    }

    @Test
    public void aliceAsksBobAsCredAndHeAccepts() throws SharkException, IOException, InterruptedException {

        // Alice created a group with bob in it (he accepted). This method returns the groupID
        byte[] groupId = this.aliceCreatesEncryptedGroupWithBobSetUp();

        SharkPKIComponent alicePKI = (SharkPKIComponent) this.aliceSharkPeer.getComponent(SharkPKIComponent.class);
        SharkPKIComponent bobPKI = (SharkPKIComponent) this.bobSharkPeer.getComponent(SharkPKIComponent.class);

        // let Bob accept ALice credentials and create a certificate
        CredentialMessageInMemo aliceCredentialMessage = new CredentialMessageInMemo(ALICE_ID, ALICE_NAME, System.currentTimeMillis(), alicePKI.getPublicKey());
        bobPKI.acceptAndSignCredential(aliceCredentialMessage);

        // Alice accepts Bob Public Key
        CredentialMessageInMemo bobCredentialMessage = new CredentialMessageInMemo(BOB_ID, BOB_NAME, System.currentTimeMillis(), bobPKI.getPublicKey());
        alicePKI.acceptAndSignCredential(bobCredentialMessage);

        SharkGroupDocument sharkGroupDocument = this.aliceStorage.getGroupDocument(groupId);
        CharSequence promiseId = this.aliceCurrencyComponent.createPromise(2,
                sharkGroupDocument.getAssignedCurrency(),
                groupId,
                BOB_ID, //creditor
                ALICE_ID, //debtor
                false);

        Thread.sleep(500);
        this.runEncounter(this.aliceSharkPeer, this.bobSharkPeer, true);
        Thread.sleep(1000);
        this.bobImpl.signPromiseAndSendBack(promiseId);
        Thread.sleep(1000);
        this.runEncounter(this.bobSharkPeer, this.aliceSharkPeer, true);
        Thread.sleep(1000);

        //Assertions
        SharkPromise signedPromiseAlice
                = this.aliceStorage.getSharkSignedPromiseFromStorage(promiseId);
        SharkPromise signedPromiseBob
                = this.bobStorage.getSharkSignedPromiseFromStorage(promiseId);

        Assertions.assertNull(this.aliceStorage.getSharkPendingPromiseFromStorage(promiseId));
        Assertions.assertNull(this.bobStorage.getSharkPendingPromiseFromStorage(promiseId));
        Assertions.assertEquals(promiseId, signedPromiseAlice.getPromiseID());
        Assertions.assertEquals(promiseId, signedPromiseBob.getPromiseID());
        Assertions.assertEquals(SharkPromiseState.FULLY_SIGNED
                ,signedPromiseAlice.getStateOfPromise());
        Assertions.assertEquals(SharkPromiseState.FULLY_SIGNED
                ,signedPromiseBob.getStateOfPromise());
        Assertions.assertNotNull(signedPromiseAlice.getCreditorSignature());
        Assertions.assertNotNull(signedPromiseAlice.getDebtorSignature());
        Assertions.assertNotNull(signedPromiseBob.getCreditorSignature());
        Assertions.assertNotNull(signedPromiseBob.getDebtorSignature());
        Assertions.assertTrue(signedPromiseAlice.getCreditorSignature().length > 0);
        Assertions.assertTrue(signedPromiseAlice.getDebtorSignature().length > 0);
        Assertions.assertTrue(signedPromiseBob.getCreditorSignature().length > 0);
        Assertions.assertTrue(signedPromiseBob.getDebtorSignature().length > 0);
        Assertions.assertEquals(2, signedPromiseAlice.getAmount());
        Assertions.assertEquals(2, signedPromiseBob.getAmount());
        Assertions.assertArrayEquals(groupId, signedPromiseAlice.getGroupIDOfPromise());
        Assertions.assertArrayEquals(groupId, signedPromiseBob.getGroupIDOfPromise());
    }

}
