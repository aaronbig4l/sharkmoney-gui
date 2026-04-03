package testHelper;

import currency.api.SharkCurrencyComponent;
import currency.api.SharkCurrencyComponentFactory;
import currency.classes.SharkCurrency;
import currency.classes.SharkLocalCurrency;
import currency.storage.SharkCurrencyStorage;
import implementations.SharkCurrencyListenerImpl;
import listener.SharkCurrencyListener;
import net.sharksystem.SharkException;
import net.sharksystem.SharkPeer;
import net.sharksystem.SharkTestPeerFS;
import net.sharksystem.asap.pki.CredentialMessageInMemo;
import net.sharksystem.pki.SharkPKIComponent;
import net.sharksystem.testhelper.SharkPKITesthelper;
import net.sharksystem.testhelper.SharkPeerTestHelper;
import implementations.SharkCurrencyComponentImpl;

import java.io.IOException;
import java.util.ArrayList;

import static net.sharksystem.utils.testsupport.TestConstants.ROOT_DIRECTORY;

/**
 * Helper class containing methods to set up test scenarios
 */
public class AsapCurrencyTestHelper extends SharkPeerTestHelper {

    private static int testNumber = 0;
    public  String subRootFolder;

    public void initSubRootFolder(String testVariant, String testName) {
        this.subRootFolder = ROOT_DIRECTORY + testVariant + "/" + testName + "/";
    }

    //private static int portNumber = 5000;
    public static int getPortNumber() {
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (java.io.IOException e) {
            throw new RuntimeException("Kein freier Port konnte ermittelt werden.", e);
        }
    }

    public SharkTestPeerFS aliceSharkPeer;
    public SharkTestPeerFS bobSharkPeer;
    public SharkTestPeerFS claraSharkPeer;
    public SharkTestPeerFS davidSharkPeer;

    public SharkCurrencyComponent aliceCurrencyComponent;
    public SharkCurrencyComponent bobCurrencyComponent;
    public SharkCurrencyComponent claraCurrencyComponent;
    public SharkCurrencyComponent davidCurrencyComponent;

    public SharkCurrencyComponentImpl aliceImpl;
    public SharkCurrencyComponentImpl bobImpl;
    public SharkCurrencyComponentImpl claraImpl;
    public SharkCurrencyComponentImpl davidImpl;

    public SharkCurrencyStorage aliceStorage;
    public SharkCurrencyStorage bobStorage;
    public SharkCurrencyStorage claraStorage;
    public SharkCurrencyStorage davidStorage;

    public AsapCurrencyTestHelper(String testVariant) {
        this.subRootFolder = ROOT_DIRECTORY + testVariant + "/";
    }

    public void runEncounter(SharkTestPeerFS leftPeer, SharkTestPeerFS rightPeer, boolean stop)
            throws SharkException, IOException, InterruptedException {
        System.out.println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");
        System.out.println("                       start encounter: "
                + leftPeer.getASAPPeer().getPeerID() + " <--> " + rightPeer.getASAPPeer().getPeerID());
        System.out.println("<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<");

        leftPeer.getASAPTestPeerFS().startEncounter(getPortNumber(), rightPeer.getASAPTestPeerFS());
        // give them moment to exchange data
        Thread.sleep(1000);
        System.out.println("slept a moment");

        if(stop) {
            System.out.println("############################################################################");
            System.out.println("                   stop encounter: "
                    + leftPeer.getASAPPeer().getPeerID() + " <--> " + rightPeer.getASAPPeer().getPeerID());
            leftPeer.getASAPTestPeerFS().stopEncounter(rightPeer.getASAPTestPeerFS());
            System.out.println("############################################################################");
            Thread.sleep(100);
        }
    }

    /**
     * This just starts Alice not more
     * @throws SharkException Thrown when there are errors adding the component
     */
    public void setUpScenarioEstablishCurrency_1_justAlice() throws SharkException {
        this.aliceSharkPeer
                = new SharkTestPeerFS(ALICE_NAME, subRootFolder + "/" + ALICE_NAME);
        SharkPKIComponent pkiForFactory
                = SharkPKITesthelper.setupPKIComponentPeerNotStarted(this.aliceSharkPeer, ALICE_ID);
        SharkCurrencyComponentFactory currencyFactory
                = new SharkCurrencyComponentFactory(pkiForFactory);
        aliceSharkPeer.addComponent(currencyFactory, SharkCurrencyComponent.class);
        aliceSharkPeer.start(ALICE_ID);
        this.aliceCurrencyComponent
                = (SharkCurrencyComponent) this.aliceSharkPeer.getComponent(SharkCurrencyComponent.class);
        this.aliceImpl =
                (SharkCurrencyComponentImpl) aliceSharkPeer.getComponent(SharkCurrencyComponent.class);
        AsapCurrencyTestHelper.testNumber++;

        SharkCurrencyListener aliceListener = new SharkCurrencyListenerImpl(this.aliceCurrencyComponent);
        this.aliceCurrencyComponent.subscribeSharkCurrencyListener(aliceListener);
        this.aliceStorage=this.aliceCurrencyComponent.getSharkCurrencyStorage();
    }

    /**
     * This starts Alice and Bob
     * @throws SharkException Thrown when there are errors adding the component
     */
    public void setUpScenarioEstablishCurrency_2_BobAndAlice() throws SharkException {
        setUpScenarioEstablishCurrency_1_justAlice();
        this.bobSharkPeer
                = new SharkTestPeerFS(BOB_NAME, subRootFolder + "/" + BOB_NAME);
        SharkPKIComponent pkiForFactory
                = SharkPKITesthelper.setupPKIComponentPeerNotStarted(this.bobSharkPeer, BOB_ID);
        SharkCurrencyComponentFactory currencyFactory
                = new SharkCurrencyComponentFactory(pkiForFactory);
        bobSharkPeer.addComponent(currencyFactory, SharkCurrencyComponent.class);
        bobSharkPeer.start(BOB_ID);
        this.bobCurrencyComponent
                = (SharkCurrencyComponent) this.bobSharkPeer.getComponent(SharkCurrencyComponent.class);
        this.bobImpl =
                (SharkCurrencyComponentImpl) bobSharkPeer.getComponent(SharkCurrencyComponent.class);
        AsapCurrencyTestHelper.testNumber++;

        SharkCurrencyListener bobListener = new SharkCurrencyListenerImpl(this.bobCurrencyComponent);
        this.bobCurrencyComponent.subscribeSharkCurrencyListener(bobListener);
        this.bobStorage=this.bobCurrencyComponent.getSharkCurrencyStorage();
    }

    /**
     * This starts Alice, Bob and Clara
     * @throws SharkException Thrown when there are errors adding the component
     */
    public void setUpScenarioEstablishCurrency_3_ClaraAndBobAndAlice() throws SharkException {
        setUpScenarioEstablishCurrency_2_BobAndAlice();
        this.claraSharkPeer
                = new SharkTestPeerFS(CLARA_NAME, subRootFolder + "/" + CLARA_NAME);
        SharkPKIComponent pkiForFactory
                = SharkPKITesthelper.setupPKIComponentPeerNotStarted(this.claraSharkPeer, CLARA_ID);
        SharkCurrencyComponentFactory currencyFactory
                = new SharkCurrencyComponentFactory(pkiForFactory);
        claraSharkPeer.addComponent(currencyFactory, SharkCurrencyComponent.class);
        claraSharkPeer.start(CLARA_ID);
        this.claraCurrencyComponent = (SharkCurrencyComponent) this.claraSharkPeer.getComponent(SharkCurrencyComponent.class);
        this.claraImpl =
                (SharkCurrencyComponentImpl) claraSharkPeer.getComponent(SharkCurrencyComponent.class);
        AsapCurrencyTestHelper.testNumber++;

        SharkCurrencyListener claraListener = new SharkCurrencyListenerImpl(this.claraCurrencyComponent);
        this.claraCurrencyComponent.subscribeSharkCurrencyListener(claraListener);
        this.claraStorage=this.claraCurrencyComponent.getSharkCurrencyStorage();
    }

    /**
     * This starts Alice, Bob, Clara and David
     * @throws SharkException Thrown when there are errors adding the component
     */
    public void setUpScenarioEstablishCurrency_4_DavidAndClaraAndBobAndAlice() throws SharkException {
        setUpScenarioEstablishCurrency_3_ClaraAndBobAndAlice();
        this.davidSharkPeer
                = new SharkTestPeerFS(DAVID_NAME, subRootFolder + "/" + DAVID_NAME);
        SharkPKIComponent pkiForFactory
                = SharkPKITesthelper.setupPKIComponentPeerNotStarted(this.davidSharkPeer, DAVID_ID);
        SharkCurrencyComponentFactory currencyFactory
                = new SharkCurrencyComponentFactory(pkiForFactory);
        davidSharkPeer.addComponent(currencyFactory, SharkCurrencyComponent.class);
        davidSharkPeer.start(DAVID_ID);
        this.davidCurrencyComponent = (SharkCurrencyComponent) this.davidSharkPeer.getComponent(SharkCurrencyComponent.class);
        this.davidImpl =
                (SharkCurrencyComponentImpl) davidSharkPeer.getComponent(SharkCurrencyComponent.class);
        AsapCurrencyTestHelper.testNumber++;

        SharkCurrencyListener davidListener = new SharkCurrencyListenerImpl(this.davidCurrencyComponent);
        this.davidCurrencyComponent.subscribeSharkCurrencyListener(davidListener);
        this.davidStorage=this.davidCurrencyComponent.getSharkCurrencyStorage();
    }

    protected byte[] aliceCreatesGroupWithBobAndClaraSetUp() throws SharkException, InterruptedException, IOException {
        setUpScenarioEstablishCurrency_3_ClaraAndBobAndAlice();
        Thread.sleep(500);
        CharSequence currencyName = "Alice Lira";
        SharkCurrency dummyCurrency = new SharkLocalCurrency(
                currencyName.toString(),
                "A test Currency"
        );

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

        ArrayList<CharSequence> whitelist = new ArrayList<>();
        whitelist.add(BOB_ID);
        whitelist.add(CLARA_ID);

        byte[] groupId = this.aliceCurrencyComponent.establishGroup(
                dummyCurrency,
                whitelist,
                false,
                false,
                true);

        Thread.sleep(100);
        this.aliceCurrencyComponent
                .invitePeerToGroup(groupId, "Hi Bob, join my group!", BOB_ID);
        this.aliceCurrencyComponent
                .invitePeerToGroup(groupId, "Hi Clara, join my group!", CLARA_ID);
        this.runEncounter(this.aliceSharkPeer, this.bobSharkPeer, true);
        this.runEncounter(this.aliceSharkPeer, this.claraSharkPeer, true);
        // 5.1 Accept Invitation
        this.bobImpl.acceptInviteAndSign(currencyName);
        Thread.sleep(100);
        this.runEncounter(this.bobSharkPeer, this.aliceSharkPeer, true);
        Thread.sleep(100);
        this.claraImpl.acceptInviteAndSign(currencyName);
        Thread.sleep(100);
        this.runEncounter(this.claraSharkPeer, this.aliceSharkPeer, true);
        Thread.sleep(100);
        //5.2 more encounters (we need better solution for this xd)
        this.runEncounter(this.bobSharkPeer, this.claraSharkPeer, true);
        this.runEncounter(this.claraSharkPeer, this.bobSharkPeer, true);
        Thread.sleep(100);

        return groupId;
    }

    protected byte[] aliceCreatesEncryptedGroupWithBobSetUp() throws SharkException, InterruptedException, IOException {

        setUpScenarioEstablishCurrency_2_BobAndAlice();
        Thread.sleep(500);
        SharkPKIComponent alicePKI = (SharkPKIComponent) this.aliceSharkPeer.getComponent(SharkPKIComponent.class);
        SharkPKIComponent bobPKI = (SharkPKIComponent) this.bobSharkPeer.getComponent(SharkPKIComponent.class);

        // let Bob accept ALice credentials and create a certificate
        CredentialMessageInMemo aliceCredentialMessage = new CredentialMessageInMemo(ALICE_ID, ALICE_NAME, System.currentTimeMillis(), alicePKI.getPublicKey());
        bobPKI.acceptAndSignCredential(aliceCredentialMessage);

        // Alice accepts Bob Public Key
        CredentialMessageInMemo bobCredentialMessage = new CredentialMessageInMemo(BOB_ID, BOB_NAME, System.currentTimeMillis(), bobPKI.getPublicKey());
        alicePKI.acceptAndSignCredential(bobCredentialMessage);

        CharSequence currencyName = "AliceTalerForPromiseTest_A";
        SharkCurrency dummyCurrency = new SharkLocalCurrency(
                currencyName.toString(),
                "A test Currency"
        );

        ArrayList<CharSequence> whitelist = new ArrayList<>();
        whitelist.add(BOB_ID);

        byte[] groupId = this.aliceCurrencyComponent.establishGroup(
                dummyCurrency,
                whitelist,
                false,
                true,
                true);

        Thread.sleep(1000);
        this.aliceCurrencyComponent
                .invitePeerToGroup(groupId, "Hi Bob, join my group!", BOB_ID);
        this.runEncounter(this.aliceSharkPeer, this.bobSharkPeer, true);
        Thread.sleep(1000);
        this.bobImpl.acceptInviteAndSign(currencyName);
        Thread.sleep(1000);
        this.runEncounter(this.bobSharkPeer,this.aliceSharkPeer,true);
        Thread.sleep(1000);

        return groupId;
    }


    protected byte[][] aliceCreatesEncryptedGroupAndBobToo() throws SharkException, InterruptedException {
        setUpScenarioEstablishCurrency_2_BobAndAlice();
        Thread.sleep(500);
        CharSequence aliceCurrencyName = "AliceTalerForPromiseTest_B";
        CharSequence bobCurrencycName = "BobTalerForPromiseTest_B";
        SharkCurrency aliceCurrency = new SharkLocalCurrency(
                aliceCurrencyName.toString(),
                "A test Currency" );
        SharkCurrency bobCurrency = new SharkLocalCurrency(
                bobCurrencycName.toString(),
                "A test Currency");

        ArrayList<CharSequence> whitelist = new ArrayList<>();
        whitelist.add(ALICE_ID);
        whitelist.add(BOB_ID);

        byte[] groupIdAlice = this.aliceCurrencyComponent.establishGroup(
                aliceCurrency,
                whitelist,
                false,
                true,
                true);
        Thread.sleep(1000);
        byte[] groupIdBoB = this.bobCurrencyComponent.establishGroup(
                bobCurrency,
                whitelist,
                false,
                true,
                true
        );

        return new byte[][]{groupIdAlice,groupIdBoB};


    }



    protected byte[] aliceCreatesEncryptedGroupWithBobAndClaraSetUp() throws SharkException, InterruptedException, IOException {
        setUpScenarioEstablishCurrency_3_ClaraAndBobAndAlice();
        Thread.sleep(100);
        CharSequence currencyName = "AliceTalerForPromiseTest_C";
        SharkCurrency dummyCurrency = new SharkLocalCurrency(
                currencyName.toString(),
                "A test Currency"
        );

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

        ArrayList<CharSequence> whitelist = new ArrayList<>();
        whitelist.add(BOB_ID);
        whitelist.add(CLARA_ID);

        byte[] groupId = this.aliceCurrencyComponent.establishGroup(
                dummyCurrency,
                whitelist,
                false,
                true,
                true);

        Thread.sleep(100);
        this.aliceCurrencyComponent
                .invitePeerToGroup(groupId, "Hi Bob, join my group!", BOB_ID);
        this.aliceCurrencyComponent
                .invitePeerToGroup(groupId, "Hi Clara, join my group!", CLARA_ID);
        this.runEncounter(this.aliceSharkPeer, this.bobSharkPeer, true);
        this.runEncounter(this.aliceSharkPeer, this.claraSharkPeer, true);
        Thread.sleep(100);
        // 5.1 Accept Invitation
        this.bobImpl.acceptInviteAndSign(currencyName);
        Thread.sleep(100);
        this.runEncounter(this.bobSharkPeer, this.aliceSharkPeer, true);
        Thread.sleep(100);
        this.claraImpl.acceptInviteAndSign(currencyName);
        Thread.sleep(100);
        this.runEncounter(this.claraSharkPeer, this.aliceSharkPeer, true);
        Thread.sleep(100);
        //5.2 more encounters (we need better solution for this xd)
        this.runEncounter(this.bobSharkPeer, this.claraSharkPeer, true);
        this.runEncounter(this.claraSharkPeer, this.bobSharkPeer, true);
        Thread.sleep(200);

        return groupId;
    }

    protected byte[] aliceCreatesCentralizedEncryptedGroupWithBobAndClaraSetUp() throws SharkException, InterruptedException, IOException {
        setUpScenarioEstablishCurrency_3_ClaraAndBobAndAlice();
        Thread.sleep(500);

        CharSequence currencyName = "AliceTalerForPromiseTest_C";
        SharkCurrency dummyCurrency = new SharkLocalCurrency(
                currencyName.toString(),
                "A test Currency"
        );

        SharkPKIComponent alicePKI = (SharkPKIComponent) this.aliceSharkPeer.getComponent(SharkPKIComponent.class);
        SharkPKIComponent bobPKI = (SharkPKIComponent) this.bobSharkPeer.getComponent(SharkPKIComponent.class);
        SharkPKIComponent claraPKI = (SharkPKIComponent) this.claraSharkPeer.getComponent(SharkPKIComponent.class);

        CredentialMessageInMemo aliceCredentialMessage = new CredentialMessageInMemo(ALICE_ID, ALICE_NAME, System.currentTimeMillis(), alicePKI.getPublicKey());
        bobPKI.acceptAndSignCredential(aliceCredentialMessage);
        claraPKI.acceptAndSignCredential(aliceCredentialMessage);

        CredentialMessageInMemo bobCredentialMessage = new CredentialMessageInMemo(BOB_ID, BOB_NAME, System.currentTimeMillis(), bobPKI.getPublicKey());
        alicePKI.acceptAndSignCredential(bobCredentialMessage);
        claraPKI.acceptAndSignCredential(bobCredentialMessage);

        CredentialMessageInMemo claraCredentialMessage = new CredentialMessageInMemo(CLARA_ID, CLARA_NAME, System.currentTimeMillis(), claraPKI.getPublicKey());
        alicePKI.acceptAndSignCredential(claraCredentialMessage);
        bobPKI.acceptAndSignCredential(claraCredentialMessage);

        ArrayList<CharSequence> whitelist = new ArrayList<>();
        whitelist.add(BOB_ID);
        whitelist.add(CLARA_ID);

        byte[] groupId = this.aliceCurrencyComponent.establishGroup(
                dummyCurrency,
                whitelist,
                true, // centralized
                true, // encrypted
                true);

        Thread.sleep(500);


        this.aliceCurrencyComponent.invitePeerToGroup(groupId, "Hi Bob, join my group!", BOB_ID);
        this.runEncounter(this.aliceSharkPeer, this.bobSharkPeer, true);

        Thread.sleep(1000);
        this.bobImpl.acceptInviteAndSign(currencyName);
        this.runEncounter(this.bobSharkPeer, this.aliceSharkPeer, true);
        Thread.sleep(500);


        this.aliceCurrencyComponent.invitePeerToGroup(groupId, "Hi Clara, join my group!", CLARA_ID);
        this.runEncounter(this.aliceSharkPeer, this.claraSharkPeer, true);
        Thread.sleep(1000);
        this.claraImpl.acceptInviteAndSign(currencyName);
        this.runEncounter(this.claraSharkPeer, this.aliceSharkPeer, true);
        Thread.sleep(500);


        this.runEncounter(this.aliceSharkPeer, this.bobSharkPeer, true);
        this.runEncounter(this.aliceSharkPeer, this.claraSharkPeer, true);


        this.runEncounter(this.bobSharkPeer, this.claraSharkPeer, true);
        Thread.sleep(500);

        return groupId;
    }


    protected byte[] aliceCreatesCentralizedEncryptedGroupWithBobSetUp() throws SharkException, InterruptedException, IOException {

        setUpScenarioEstablishCurrency_2_BobAndAlice();
        Thread.sleep(500);
        SharkPKIComponent alicePKI = (SharkPKIComponent) this.aliceSharkPeer.getComponent(SharkPKIComponent.class);
        SharkPKIComponent bobPKI = (SharkPKIComponent) this.bobSharkPeer.getComponent(SharkPKIComponent.class);

        // let Bob accept ALice credentials and create a certificate
        CredentialMessageInMemo aliceCredentialMessage = new CredentialMessageInMemo(ALICE_ID, ALICE_NAME, System.currentTimeMillis(), alicePKI.getPublicKey());
        bobPKI.acceptAndSignCredential(aliceCredentialMessage);

        // Alice accepts Bob Public Key
        CredentialMessageInMemo bobCredentialMessage = new CredentialMessageInMemo(BOB_ID, BOB_NAME, System.currentTimeMillis(), bobPKI.getPublicKey());
        alicePKI.acceptAndSignCredential(bobCredentialMessage);

        CharSequence currencyName = "AliceTalerForPromiseTest_D";
        SharkCurrency dummyCurrency = new SharkLocalCurrency(
                currencyName.toString(),
                "A test Currency"
        );

        ArrayList<CharSequence> whitelist = new ArrayList<>();
        whitelist.add(BOB_ID);

        byte[] groupId = this.aliceCurrencyComponent.establishGroup(
                dummyCurrency,
                whitelist,
                true,
                true,
                true);

        Thread.sleep(1000);
        this.aliceCurrencyComponent
                .invitePeerToGroup(groupId, "Hi Bob, join my group!", BOB_ID);
        this.runEncounter(this.aliceSharkPeer, this.bobSharkPeer, true);
        Thread.sleep(1000);
        this.bobImpl.acceptInviteAndSign(currencyName);
        Thread.sleep(1000);
        this.runEncounter(this.bobSharkPeer,this.aliceSharkPeer,true);
        Thread.sleep(1000);

        return groupId;
    }

    protected static void stopPeerSafely(SharkPeer peer) {
        if (peer != null) {
            try {
                peer.stop();
            } catch (Exception e) {
                System.err.println("Error stopping peers: " + e.getMessage());
            }
        }
    }
}
