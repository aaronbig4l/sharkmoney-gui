package sendingPromisesTests;

import currency.classes.SharkCurrency;
import currency.classes.SharkLocalCurrency;
import exepections.SharkPromiseException;
import net.sharksystem.SharkException;
import net.sharksystem.asap.pki.CredentialMessageInMemo;
import net.sharksystem.pki.SharkPKIComponent;
import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.jupiter.api.*;
import testHelper.AsapCurrencyTestHelper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class PromiseLimitationTest extends AsapCurrencyTestHelper {

    public PromiseLimitationTest() {
        super(PromiseLimitationTest.class.getSimpleName());
    }

    @BeforeEach
    void setUp(TestInfo testInfo) {

        File rootFolder = new File("testResultsRootFolder");

        if (rootFolder.exists()) {
            try {
                FileUtils.deleteDirectory(rootFolder);
            } catch (IOException e) {
                System.out.printf("Setup fehlgeschlagen: Ordner konnte nicht gelöscht werden: %s", e);
            }
        }


        String testName = testInfo.getDisplayName()
                .replaceAll("[^a-zA-Z0-9]", "_");
        this.initSubRootFolder(
                PromiseLimitationTest.class.getSimpleName(),
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
    public void createLimitedGroupAndHitLimit() throws SharkException, InterruptedException, IOException {
        this.setUpScenarioEstablishCurrency_2_BobAndAlice();

        SharkPKIComponent alicePKI = (SharkPKIComponent) this.aliceSharkPeer.getComponent(SharkPKIComponent.class);
        SharkPKIComponent bobPKI = (SharkPKIComponent) this.bobSharkPeer.getComponent(SharkPKIComponent.class);

        bobPKI.acceptAndSignCredential(new CredentialMessageInMemo(ALICE_ID, ALICE_NAME, System.currentTimeMillis(), alicePKI.getPublicKey()));
        alicePKI.acceptAndSignCredential(new CredentialMessageInMemo(BOB_ID, BOB_NAME, System.currentTimeMillis(), bobPKI.getPublicKey()));

        CharSequence currencyName = "AlicePLTest_A";
        SharkCurrency dummyCurrency = new SharkLocalCurrency(
                3, //Maximum amount of promises you can create
                currencyName.toString(),
                "A test Currency"
        );
        ArrayList<CharSequence> whitelist = new ArrayList<>();
        whitelist.add(BOB_ID);
        byte[] groupId = this.aliceCurrencyComponent.establishGroup(dummyCurrency,
                whitelist,
                false,
                false,
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

        List<CharSequence> promiseIds = new LinkedList<>();
        String exceptionMessage= "";

        //The last creation should throw an Exception because limit is set to 3
        for(int i=1; i<=4;i++) {
            try {
                promiseIds.add(this.aliceCurrencyComponent.createPromise(2,
                        this.aliceStorage.getGroupDocument(groupId).getAssignedCurrency(),
                        groupId,
                        ALICE_ID, //creditor
                        BOB_ID, //debtor
                        true));
                Thread.sleep(400);
                this.runEncounter(this.aliceSharkPeer, this.bobSharkPeer, true);
                Thread.sleep(400);
            } catch(SharkPromiseException spe) {
                exceptionMessage=spe.getMessage();
            }
        }

        Assertions.assertEquals(3, promiseIds.size());
        Assertions.assertTrue(exceptionMessage.contains("You have reached the global Promise creation limit"));
        Assertions.assertEquals(3,this.aliceStorage.getPendingPromiseStorageSize());
        Assertions.assertEquals(3,this.bobStorage.getPendingPromiseStorageSize());
    }

}
