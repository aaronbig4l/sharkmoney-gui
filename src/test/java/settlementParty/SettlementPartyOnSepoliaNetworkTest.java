package settlementParty;

import currency.classes.SharkCryptoCurrency;

import currency.classes.SharkPromise;
import currency.classes.SharkPromiseSerializer;
import group.SharkGroupDocument;
import net.sharksystem.SharkException;
import net.sharksystem.asap.pki.CredentialMessageInMemo;
import net.sharksystem.pki.SharkPKIComponent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.http.HttpService;
import org.web3j.utils.Convert;
import testHelper.AsapCurrencyTestHelper;
import transactionSettelment.SettlementPartyState;
import transactionSettelment.SharkSettlementDocument;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

public class SettlementPartyOnSepoliaNetworkTest extends AsapCurrencyTestHelper {

    private static final String SEPOLIA_RPC = "https://sepolia.drpc.org";
    private Web3j web3j;

    public SettlementPartyOnSepoliaNetworkTest() { super(SettlementPartyOnSepoliaNetworkTest.class.getSimpleName()); }

    @BeforeEach
    void initNetwork() throws SharkException {
        this.setUpScenarioEstablishCurrency_4_DavidAndClaraAndBobAndAlice();
        this.web3j = Web3j.build(new HttpService(SEPOLIA_RPC));
    }

    @AfterEach
    void tearDown() {
        stopPeerSafely(this.aliceSharkPeer);
        stopPeerSafely(this.bobSharkPeer);
        stopPeerSafely(this.claraSharkPeer);
        stopPeerSafely(this.davidSharkPeer);
    }

    @Test
    public void testCompleteSettlementCycleOnSepolia() throws Exception {
        // ==========================================
        // 1. SETUP: Start Peers and establish Crypto Currency Group
        // ==========================================
        this.setUpScenarioEstablishCurrency_3_ClaraAndBobAndAlice();

        SharkPKIComponent alicePKI = (SharkPKIComponent) this.aliceSharkPeer.getComponent(SharkPKIComponent.class);
        SharkPKIComponent bobPKI = (SharkPKIComponent) this.bobSharkPeer.getComponent(SharkPKIComponent.class);
        SharkPKIComponent claraPKI = (SharkPKIComponent) this.claraSharkPeer.getComponent(SharkPKIComponent.class);

        // Exchange PKI Credentials
        bobPKI.acceptAndSignCredential(new CredentialMessageInMemo(ALICE_ID, ALICE_NAME, System.currentTimeMillis(), alicePKI.getPublicKey()));
        claraPKI.acceptAndSignCredential(new CredentialMessageInMemo(ALICE_ID, ALICE_NAME, System.currentTimeMillis(), alicePKI.getPublicKey()));
        alicePKI.acceptAndSignCredential(new CredentialMessageInMemo(BOB_ID, BOB_NAME, System.currentTimeMillis(), bobPKI.getPublicKey()));
        claraPKI.acceptAndSignCredential(new CredentialMessageInMemo(BOB_ID, BOB_NAME, System.currentTimeMillis(), bobPKI.getPublicKey()));
        alicePKI.acceptAndSignCredential(new CredentialMessageInMemo(CLARA_ID, CLARA_NAME, System.currentTimeMillis(), claraPKI.getPublicKey()));
        bobPKI.acceptAndSignCredential(new CredentialMessageInMemo(CLARA_ID, CLARA_NAME, System.currentTimeMillis(), claraPKI.getPublicKey()));

        // Crypto Currency Group
        CharSequence cryptoCurrencyName = "ASAP Sepolia";
        SharkCryptoCurrency asapSepoliaCurrency = new SharkCryptoCurrency(
                cryptoCurrencyName.toString(),
                "A Layer-2 offline Sepolia Currency",
                0.00002);

        ArrayList<CharSequence> whitelist = new ArrayList<>();
        whitelist.add(BOB_ID);
        whitelist.add(CLARA_ID);

        // Alice etabliert die Crypto-Gruppe
        byte[] groupId = this.aliceCurrencyComponent.establishGroup(
                asapSepoliaCurrency,
                whitelist,
                false,
                false,
                true);

        // Bob und Clara einladen
        this.aliceCurrencyComponent.invitePeerToGroup(groupId, "Join my Sepolia Group!", BOB_ID);
        this.aliceCurrencyComponent.invitePeerToGroup(groupId, "Join my Sepolia Group!", CLARA_ID);
        syncAliceBobClaraPeers();

        // Bob und Clara akzeptieren die Einladung
        this.bobImpl.acceptInviteAndSign(cryptoCurrencyName);
        this.claraImpl.acceptInviteAndSign(cryptoCurrencyName);
        syncAliceBobClaraPeers();

        SharkGroupDocument sharkGroupDocument = this.aliceStorage.getGroupDocument(groupId);

        // ==========================================
        // 2. Exchange Promises (Crypto Promises)
        // ==========================================

        // Alice owes Bob 10
        CharSequence p1 = this.bobCurrencyComponent.createPromise(10,
                sharkGroupDocument.getAssignedCurrency(),
                groupId,
                BOB_ID,
                ALICE_ID,
                true);
        syncAliceBobClaraPeers();
        this.aliceImpl.signPromiseAndSendBack(p1);

        // Bob owes Clara 20
        CharSequence p2 = this.claraCurrencyComponent.createPromise(20,
                sharkGroupDocument.getAssignedCurrency(),
                groupId,
                CLARA_ID,
                BOB_ID,
                true);
        syncAliceBobClaraPeers();
        this.bobImpl.signPromiseAndSendBack(p2);

        // Clara owes Alice 30
        CharSequence p3 = this.aliceCurrencyComponent.createPromise(30,
                sharkGroupDocument.getAssignedCurrency(),
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

        // Gossip Loop
        for (int i = 1; i <= 5; i++) {
            syncAliceBobClaraPeers();
            Thread.sleep(1000);
        }

        // Sign pending optimized Crypto-Promises
        for (SharkPromise p : this.aliceStorage.getAllPendingPromises()) {
            this.aliceImpl.signPromiseAndSendBack(p.getPromiseID());
            Thread.sleep(500);
            syncAliceBobClaraPeers();
            Thread.sleep(500);
        }

        for (int i = 0; i < 3; i++) {
            syncAliceBobClaraPeers();
            Thread.sleep(1000);
        }

        // ==========================================
        // 4. Settlement Party Assertions
        // ==========================================

        SharkSettlementDocument finalDocAlice = this.aliceStorage.getSettlementDocument(partyId);
        Assertions.assertEquals(SettlementPartyState.COMPLETED, finalDocAlice.getState(), "Settlement should be COMPLETED!");

        boolean foundBobToAlice = false;
        boolean foundClaraToAlice = false;

        // Prüfe Alices lokalen Speicher auf die neuen Promises
        List<byte[]> serializedPromisesAlice = this.aliceImpl.getSerializedPromisesForGroup(groupId);
        for (byte[] pBytes : serializedPromisesAlice) {
            SharkPromise promise = SharkPromiseSerializer.deserializePromise(
                    pBytes, this.aliceImpl.getSharkPKIComponent().getASAPKeyStore()
            );

            // Check Bob -> Alice (10)
            if (promise.getDebtorID().toString().equals(BOB_ID.toString()) &&
                    promise.getCreditorID().toString().equals(ALICE_ID.toString()) &&
                    promise.getAmount() == 10) {
                foundBobToAlice = true;
                // Currency Check: Is the Currency a SharkCryptoCurrency?
                Assertions.assertTrue(promise.getReferenceValue() instanceof SharkCryptoCurrency, "The optimized Promise has to use a SharkCryptoCurrency!");
            }

            // Clara -> Alice (10)
            if (promise.getDebtorID().toString().equals(CLARA_ID.toString()) &&
                    promise.getCreditorID().toString().equals(ALICE_ID.toString()) &&
                    promise.getAmount() == 10) {
                foundClaraToAlice = true;
                Assertions.assertTrue(promise.getReferenceValue() instanceof SharkCryptoCurrency, "The optimized Promise has to use a SharkCryptoCurrency!");
            }
        }

        Assertions.assertTrue(foundBobToAlice, "Promise (Bob pays Alice 10) not found");
        Assertions.assertTrue(foundClaraToAlice, "Promise (Clara pays Alice 10) not found");

        // ==========================================
        // 5. Blockchain TX + Assertions
        // ==========================================

        // Check connection to Sepolia Network
        String clientVersion = web3j.web3ClientVersion().send().getWeb3ClientVersion();
        Assertions.assertTrue(clientVersion.contains("Geth"), "Verbindung zu Sepolia fehlgeschlagen.");

        // Get balance from Alice
        BigInteger aliceBalanceInWei = web3j.ethGetBalance(aliceImpl.getWalletAddress(),
                DefaultBlockParameterName.LATEST).send().getBalance();

        // Get balance from Bob
        BigInteger bobBalanceInWei = web3j.ethGetBalance(bobImpl.getWalletAddress(),
                DefaultBlockParameterName.LATEST).send().getBalance();

        // Get balance from Bob
        BigInteger claraBalanceInWei = web3j.ethGetBalance(claraImpl.getWalletAddress(),
                DefaultBlockParameterName.LATEST).send().getBalance();

        // Check Crypto Balances
        System.out.println("**************************");
        System.out.println("Alice Sepolia Balance: " + Convert.fromWei(aliceBalanceInWei.toString(), Convert.Unit.ETHER) + " ETH - Address: " + aliceImpl.getWalletAddress());
        System.out.println("Bob Sepolia Balance: " + Convert.fromWei(bobBalanceInWei.toString(), Convert.Unit.ETHER) + " ETH- Address: " + bobImpl.getWalletAddress());
        System.out.println("Clara Sepolia Balance: " + Convert.fromWei(claraBalanceInWei.toString(), Convert.Unit.ETHER) + " ETH - Address: " + claraImpl.getWalletAddress());

        // Bob and Clara has to make a Crypto TX to Alice

        // Bob -> Alice = 10 * 0.00002 ETH = 0.0002 ETH
        // Clara -> Alice = 10 * 0.00002 ETH = 0.0002 ETH

        this.bobImpl.executeCryptoPayments(groupId, this.web3j);
        this.claraImpl.executeCryptoPayments(groupId, this.web3j);

        // Wait 30s before TX
        Thread.sleep(30000);

        BigInteger aliceBalanceAfterTransaction = web3j.ethGetBalance(aliceImpl.getWalletAddress(),
                DefaultBlockParameterName.LATEST).send().getBalance();

        BigInteger bobBalanceAfterTransaction = web3j.ethGetBalance(bobImpl.getWalletAddress(),
                DefaultBlockParameterName.LATEST).send().getBalance();

        BigInteger claraBalanceAfterTransaction = web3j.ethGetBalance(claraImpl.getWalletAddress(),
                DefaultBlockParameterName.LATEST).send().getBalance();

        System.out.println("Alice Sepolia Balance after Transaction: " + Convert.fromWei(aliceBalanceAfterTransaction.toString(), Convert.Unit.ETHER) + " ETH");
        System.out.println("Bob Sepolia Balance after Transaction: " + Convert.fromWei(bobBalanceAfterTransaction.toString(), Convert.Unit.ETHER) + " ETH");
        System.out.println("Clara Sepolia Balance after Transaction: " + Convert.fromWei(claraBalanceAfterTransaction.toString(), Convert.Unit.ETHER) + " ETH");
        System.out.println("**************************");


        Assertions.assertTrue(aliceBalanceAfterTransaction.compareTo(aliceBalanceInWei) > 0); // Alice amount increased
        Assertions.assertTrue(bobBalanceAfterTransaction.compareTo(bobBalanceInWei) < 0); // Bobs amount decreased
        Assertions.assertTrue(claraBalanceAfterTransaction.compareTo(claraBalanceInWei) < 0); // Claras amount decreased
    }

    /**
     * Help methode to run an encounter between Alice, Bob and Clara
     */
    private void syncAliceBobClaraPeers() throws Exception {
        runEncounter(this.aliceSharkPeer, this.bobSharkPeer, true);
        Thread.sleep(500);
        runEncounter(this.bobSharkPeer, this.claraSharkPeer, true);
        Thread.sleep(500);
        runEncounter(this.claraSharkPeer, this.aliceSharkPeer, true);
        Thread.sleep(500);
        runEncounter(this.bobSharkPeer, this.aliceSharkPeer, true);
        Thread.sleep(500);
        runEncounter(this.claraSharkPeer, this.bobSharkPeer, true);
        Thread.sleep(500);
        runEncounter(this.aliceSharkPeer, this.claraSharkPeer, true);
    }
}
