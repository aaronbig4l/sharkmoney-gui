package sendingPromisesTests;

import currency.classes.*;
import group.SharkGroupDocument;
import net.sharksystem.SharkException;
import net.sharksystem.asap.ASAPException;
import net.sharksystem.asap.pki.CredentialMessageInMemo;
import net.sharksystem.pki.SharkPKIComponent;
import org.junit.Assert;
import org.junit.jupiter.api.*;
import testHelper.AsapCurrencyTestHelper;

import java.io.IOException;

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

        Thread.sleep(200);
        this.runEncounter(this.aliceSharkPeer, this.bobSharkPeer, true);
        Thread.sleep(200);
        this.bobImpl.signPromiseAndSendBack(promiseId,
                ALICE_ID,
                BOB_ID,
                true,
                sharkGroupDocument.isEncrypted(),
                false);
        Thread.sleep(200);
        this.runEncounter(this.bobSharkPeer, this.aliceSharkPeer, true);
        Thread.sleep(200);

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
    public void createPromiseSendAndSignWithinAGroupAliceBobClara(){

    }


    @Test
    public void sendPromiseWithToLessIncome(){

    }




}
