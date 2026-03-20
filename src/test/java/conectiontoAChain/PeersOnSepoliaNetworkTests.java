package conectiontoAChain;

import blockchain.transaction.OfflineTXCreator;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import net.sharksystem.SharkException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthGetTransactionCount;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.http.HttpService;
import org.web3j.utils.Convert;
import testHelper.AsapCurrencyTestHelper;

import java.io.IOException;
import java.math.BigInteger;

public class PeersOnSepoliaNetworkTests extends AsapCurrencyTestHelper {

    private static final String SEPOLIA_RPC = "https://sepolia.drpc.org";
    private Web3j web3j;

    public PeersOnSepoliaNetworkTests() {
        super(PeersOnSepoliaNetworkTests.class.getSimpleName());
    }

    @BeforeEach
    void initNetwork() throws SharkException {
        this.setUpScenarioEstablishCurrency_4_DavidAndClaraAndBobAndAlice();
        this.web3j = Web3j.build(new HttpService(SEPOLIA_RPC));
    }

    @Test
    public void sepoliaTransactionFromAliceToBob() throws IOException, InterruptedException {
        // 1. Check connection to Sepolia Network
        String clientVersion = web3j.web3ClientVersion().send().getWeb3ClientVersion();
        Assertions.assertTrue(clientVersion.contains("Geth"), "Verbindung zu Sepolia fehlgeschlagen.");

        // 2. Check balance of Alice (must have Sepolia-Eth for the transaction to Bob)
        BigInteger aliceBalanceInWei = web3j.ethGetBalance(aliceImpl.getWalletAddress(),
                DefaultBlockParameterName.LATEST).send().getBalance();

        // Get balance of Bob
        BigInteger bobBalanceInWei = web3j.ethGetBalance(bobImpl.getWalletAddress(),
                DefaultBlockParameterName.LATEST).send().getBalance();

        System.out.println("**************************");
        System.out.println("Alice Sepolia Balance: " + Convert.fromWei(aliceBalanceInWei.toString(), Convert.Unit.ETHER) + " ETH");
        System.out.println("Bob Sepolia Balance: " + Convert.fromWei(bobBalanceInWei.toString(), Convert.Unit.ETHER) + " ETH");

        Assertions.assertTrue(aliceBalanceInWei.compareTo(BigInteger.ZERO) > 0,
                "Alice has no Sepolia ETH! Please add some ETH to " + aliceImpl.getWalletAddress());

        // 3. Prepare Transaction Parameter
        BigInteger transactionAmountInWei = Convert.toWei("0.0001", Convert.Unit.ETHER).toBigInteger(); // transaction amount

        EthGetTransactionCount ethGetTransactionCount = web3j.ethGetTransactionCount(
                aliceImpl.getWalletAddress(), DefaultBlockParameterName.LATEST).send();
        BigInteger nonce = ethGetTransactionCount.getTransactionCount();

        BigInteger gasPrice = web3j.ethGasPrice().send().getGasPrice();
        BigInteger gasLimit = BigInteger.valueOf(21000);

        // 4. Create a signed offline Transaction from Alice to Bob
        String signedTxHex = OfflineTXCreator.createSignedOfflineTX(
                aliceImpl.getWallet().getCredentials(),
                bobImpl.getWalletAddress(),
                transactionAmountInWei,
                nonce,
                gasPrice,
                gasLimit
        );

        Assertions.assertNotNull(signedTxHex, "The signed Transaction is not allowed to be null!");

        // 5. Send the Transaction on the Sepolia Network
        EthSendTransaction response = web3j.ethSendRawTransaction(signedTxHex).send();

        if (response.hasError()){
            Assertions.fail("Transaction Error: " + response.getError().getMessage());
        }

        String transactionHash = response.getTransactionHash();
        System.out.println("Successful Transaction! Hash: " + transactionHash);

        Thread.sleep(30000); // Wait 30 seconds to check if balance changed


        BigInteger aliceBalanceAfterTransaction = web3j.ethGetBalance(aliceImpl.getWalletAddress(),
                DefaultBlockParameterName.LATEST).send().getBalance();

        BigInteger bobBalanceAfterTransaction = web3j.ethGetBalance(bobImpl.getWalletAddress(),
                DefaultBlockParameterName.LATEST).send().getBalance();


        System.out.println("Alice Sepolia Balance after Transaction: " + Convert.fromWei(aliceBalanceAfterTransaction.toString(), Convert.Unit.ETHER) + " ETH");
        System.out.println("Bob Sepolia Balance after Transaction: " + Convert.fromWei(bobBalanceAfterTransaction.toString(), Convert.Unit.ETHER) + " ETH");
        System.out.println("**************************");

        Assertions.assertTrue(bobBalanceAfterTransaction.compareTo(BigInteger.ZERO) > 0);

        Assertions.assertTrue(aliceBalanceAfterTransaction.compareTo(aliceBalanceInWei) < 0); // Alice amount decreased
        Assertions.assertTrue(bobBalanceAfterTransaction.compareTo(bobBalanceInWei) > 0); // Bobs amount increased
    }

    @Test
    public void sepoliaTransactionBetweenAllPeers() throws IOException, InterruptedException {
        // 1. Check connection to Sepolia Network
        String clientVersion = web3j.web3ClientVersion().send().getWeb3ClientVersion();
        Assertions.assertTrue(clientVersion.contains("Geth"), "Verbindung zu Sepolia fehlgeschlagen.");

        // 2. Check balance of Alice (must have Sepolia-Eth for the transactions)
        BigInteger aliceBalanceInWei = web3j.ethGetBalance(aliceImpl.getWalletAddress(),
                DefaultBlockParameterName.LATEST).send().getBalance();

        // Get balance of Bob
        BigInteger bobBalanceInWei = web3j.ethGetBalance(bobImpl.getWalletAddress(),
                DefaultBlockParameterName.LATEST).send().getBalance();

        // Get balance of Clara
        BigInteger claraBalanceInWei = web3j.ethGetBalance(claraImpl.getWalletAddress(),
                DefaultBlockParameterName.LATEST).send().getBalance();

        // Get balance of David
        BigInteger davidBalanceInWei = web3j.ethGetBalance(davidImpl.getWalletAddress(),
                DefaultBlockParameterName.LATEST).send().getBalance();

        System.out.println("**************************");
        System.out.println("Preparing first Transaction...");
        System.out.println();
        System.out.println("Alice Sepolia Balance: " + Convert.fromWei(aliceBalanceInWei.toString(), Convert.Unit.ETHER) + " ETH");
        System.out.println("Bob Sepolia Balance: " + Convert.fromWei(bobBalanceInWei.toString(), Convert.Unit.ETHER) + " ETH");
        System.out.println("Clara Sepolia Balance: " + Convert.fromWei(claraBalanceInWei.toString(), Convert.Unit.ETHER) + " ETH");
        System.out.println("David Sepolia Balance: " + Convert.fromWei(davidBalanceInWei.toString(), Convert.Unit.ETHER) + " ETH");
        System.out.println();

        Assertions.assertTrue(aliceBalanceInWei.compareTo(BigInteger.ZERO) > 0,
                "Alice has no Sepolia ETH! Please add some ETH to " + aliceImpl.getWalletAddress());

        // 3. Prepare Transaction Parameter
        BigInteger transactionAmountInWei = Convert.toWei("0.0001", Convert.Unit.ETHER).toBigInteger(); // transaction amount

        EthGetTransactionCount ethGetTransactionCountAlice = web3j.ethGetTransactionCount(
                aliceImpl.getWalletAddress(), DefaultBlockParameterName.LATEST).send();
        EthGetTransactionCount ethGetTransactionCountBob = web3j.ethGetTransactionCount(
                bobImpl.getWalletAddress(), DefaultBlockParameterName.LATEST).send();
        EthGetTransactionCount ethGetTransactionCountClara = web3j.ethGetTransactionCount(
                claraImpl.getWalletAddress(), DefaultBlockParameterName.LATEST).send();

        BigInteger nonceAlice = ethGetTransactionCountAlice.getTransactionCount();
        BigInteger nonceBob = ethGetTransactionCountBob.getTransactionCount();
        BigInteger nonceClara = ethGetTransactionCountClara.getTransactionCount();

        BigInteger gasPrice = web3j.ethGasPrice().send().getGasPrice();
        BigInteger gasLimit = BigInteger.valueOf(21000);

        // 4. Create a signed offline Transaction from Alice to Bob
        String signedTxHexAliceToBob = OfflineTXCreator.createSignedOfflineTX(
                aliceImpl.getWallet().getCredentials(),
                bobImpl.getWalletAddress(),
                transactionAmountInWei,
                nonceAlice,
                gasPrice,
                gasLimit
        );

        // Create a signed offline Transaction from Alice to Clara
        String signedTxHexAliceToClara = OfflineTXCreator.createSignedOfflineTX(
                aliceImpl.getWallet().getCredentials(),
                claraImpl.getWalletAddress(),
                transactionAmountInWei,
                nonceAlice.add(BigInteger.ONE), // We have to increase the nonce value of the second Transaction by 1
                gasPrice,
                gasLimit
        );

        Assertions.assertNotNull(signedTxHexAliceToBob, "The signed Transaction is not allowed to be null!");
        Assertions.assertNotNull(signedTxHexAliceToClara, "The signed Transaction is not allowed to be null!");

        // 5. Send the Transaction on the Sepolia Network
        EthSendTransaction responseAliceToBob = web3j.ethSendRawTransaction(signedTxHexAliceToBob).send();
        Thread.sleep(2000); // Wait 2 seconds before next Transaction
        EthSendTransaction responseAliceToClara = web3j.ethSendRawTransaction(signedTxHexAliceToClara).send();

        if (responseAliceToBob.hasError()){
            Assertions.fail("Transaction Error: " + responseAliceToBob.getError().getMessage());
        }
        if (responseAliceToClara.hasError()){
            Assertions.fail("Transaction Error: " + responseAliceToClara.getError().getMessage());
        }

        String transactionHashAliceToBob = responseAliceToBob.getTransactionHash();
        System.out.println("Successful Transaction from Alice to Bob! Hash: " + transactionHashAliceToBob);

        String transactionHashAliceToClara = responseAliceToBob.getTransactionHash();
        System.out.println("Successful Transaction from Alice to Clara! Hash: " + transactionHashAliceToClara);

        Thread.sleep(30000); // Wait 30 seconds to check if balance changed


        BigInteger aliceBalanceAfterTransaction = web3j.ethGetBalance(aliceImpl.getWalletAddress(),
                DefaultBlockParameterName.LATEST).send().getBalance();

        BigInteger bobBalanceAfterTransaction = web3j.ethGetBalance(bobImpl.getWalletAddress(),
                DefaultBlockParameterName.LATEST).send().getBalance();

        BigInteger claraBalanceAfterTransaction = web3j.ethGetBalance(claraImpl.getWalletAddress(),
                DefaultBlockParameterName.LATEST).send().getBalance();


        System.out.println();
        System.out.println("Alice Sepolia Balance after Transaction: " + Convert.fromWei(aliceBalanceAfterTransaction.toString(), Convert.Unit.ETHER) + " ETH");
        System.out.println("Bob Sepolia Balance after Transaction: " + Convert.fromWei(bobBalanceAfterTransaction.toString(), Convert.Unit.ETHER) + " ETH");
        System.out.println("Clara Sepolia Balance after Transaction: " + Convert.fromWei(claraBalanceAfterTransaction.toString(), Convert.Unit.ETHER) + " ETH");
        System.out.println();
        System.out.println("Preparing second Transaction...");
        System.out.println();

        BigInteger transactionAmountInWei2 = Convert.toWei("0.00005", Convert.Unit.ETHER).toBigInteger(); // half the first transaction amount

        // Create a signed offline Transaction from Bob to Clara
        String signedTxHexBobToDavid = OfflineTXCreator.createSignedOfflineTX(
                bobImpl.getWallet().getCredentials(),
                davidImpl.getWalletAddress(),
                transactionAmountInWei2,
                nonceBob,
                gasPrice,
                gasLimit
        );

        String signedTxHexClaraToDavid = OfflineTXCreator.createSignedOfflineTX(
                claraImpl.getWallet().getCredentials(),
                davidImpl.getWalletAddress(),
                transactionAmountInWei2,
                nonceClara,
                gasPrice,
                gasLimit
        );

        Assertions.assertNotNull(signedTxHexBobToDavid, "The signed Transaction is not allowed to be null!");
        Assertions.assertNotNull(signedTxHexClaraToDavid, "The signed Transaction is not allowed to be null!");

        // 5. Send the Transaction on the Sepolia Network
        EthSendTransaction responseBobToDavid = web3j.ethSendRawTransaction(signedTxHexBobToDavid).send();
        EthSendTransaction responseClaraToDavid = web3j.ethSendRawTransaction(signedTxHexClaraToDavid).send();

        if (responseBobToDavid.hasError()){
            Assertions.fail("Transaction Error: " + responseAliceToBob.getError().getMessage());
        }
        if (responseClaraToDavid.hasError()){
            Assertions.fail("Transaction Error: " + responseAliceToClara.getError().getMessage());
        }

        String transactionHashBobToDavid = responseAliceToBob.getTransactionHash();
        System.out.println("Successful Transaction from Bob to David! Hash: " + transactionHashBobToDavid);

        String transactionHashClaraToDavid = responseAliceToBob.getTransactionHash();
        System.out.println("Successful Transaction from Clara to David! Hash: " + transactionHashClaraToDavid);

        Thread.sleep(30000); // Wait 30 seconds to check if balance changed

        BigInteger bobBalanceAfterTransaction2 = web3j.ethGetBalance(bobImpl.getWalletAddress(),
                DefaultBlockParameterName.LATEST).send().getBalance();

        BigInteger claraBalanceAfterTransaction2 = web3j.ethGetBalance(claraImpl.getWalletAddress(),
                DefaultBlockParameterName.LATEST).send().getBalance();

        BigInteger davidBalanceAfterTransaction = web3j.ethGetBalance(davidImpl.getWalletAddress(),
                DefaultBlockParameterName.LATEST).send().getBalance();

        System.out.println();
        System.out.println("Bob Sepolia Balance after Transaction: " + Convert.fromWei(bobBalanceAfterTransaction2.toString(), Convert.Unit.ETHER) + " ETH");
        System.out.println("Clara Sepolia Balance after Transaction: " + Convert.fromWei(claraBalanceAfterTransaction2.toString(), Convert.Unit.ETHER) + " ETH");
        System.out.println("David Sepolia Balance after Transaction: " + Convert.fromWei(davidBalanceAfterTransaction.toString(), Convert.Unit.ETHER) + " ETH");
        System.out.println("**************************");

        Assertions.assertTrue(bobBalanceAfterTransaction2.compareTo(BigInteger.ZERO) > 0);
        Assertions.assertTrue(claraBalanceAfterTransaction2.compareTo(BigInteger.ZERO) > 0);
        Assertions.assertTrue(davidBalanceAfterTransaction.compareTo(BigInteger.ZERO) > 0);

        Assertions.assertTrue(aliceBalanceAfterTransaction.compareTo(aliceBalanceInWei) < 0); // Alice amount decreased
        Assertions.assertTrue(bobBalanceAfterTransaction.compareTo(bobBalanceInWei) > 0); // Bobs amount increased
        Assertions.assertTrue(claraBalanceAfterTransaction.compareTo(claraBalanceInWei) > 0); // Claras amount increased
        Assertions.assertTrue(davidBalanceAfterTransaction.compareTo(davidBalanceInWei) > 0); // Davids amount increased
    }

}
