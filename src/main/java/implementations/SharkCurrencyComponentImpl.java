package implementations;

import blockchain.wallet.WalletManager;
import currency.classes.*;
import currency.storage.SharkCurrencyStorage;
import exepections.SharkPromiseException;
import group.SharkGroupDocument;
import currency.api.SharkCurrencyComponent;
import group.GroupSignings;
import exepections.SharkCurrencyException;
import listener.SharkCurrencyListenerManagerNEW;
import listener.SharkCurrencyListenerNEW;
import net.sharksystem.*;
import net.sharksystem.asap.*;
import net.sharksystem.asap.crypto.ASAPCryptoAlgorithms;
import net.sharksystem.asap.crypto.ASAPKeyStore;
import net.sharksystem.pki.SharkPKIComponent;
import org.web3j.crypto.CipherException;
import transactionSettelment.*;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.*;

/**
 * An implementation to test our Currency-Component which we need for testing
 * WORK IN PROGRESS!!!!!
 */
public class SharkCurrencyComponentImpl
        extends SharkCurrencyListenerManagerNEW
        implements SharkCurrencyComponent, ASAPMessageReceivedListener {

    private final SharkPKIComponent sharkPKIComponent;
    private ASAPPeer asapPeer;
    private SharkCurrencyListenerNEW sharkCurrencyListenerNEW;
    private SharkCurrencyStorage sharkCurrencyStorage;
    private WalletManager wallet;

    private Map<String, Integer> promiseBalanceSimple = new HashMap<>();
    private Map<String, Map<CharSequence, Integer>> promiseBalanceExtended = new HashMap<>();

    public SharkCurrencyComponentImpl(SharkPKIComponent pki) throws SharkException {
        this.sharkPKIComponent = pki;
    }

    @Override
    public byte[] establishGroup(SharkCurrency currency, ArrayList<CharSequence> whitelistMember, boolean encrypted, boolean balanceVisible) throws SharkCurrencyException {
        this.checkComponentRunning();
        SharkGroupDocument sharkGroupDocument = new SharkGroupDocument(this.asapPeer.getPeerID(), currency, whitelistMember , encrypted, balanceVisible, GroupSignings.SIGNED_BY_NONE);
        try{
            // 1. Get Name of the Currency URI
            byte[] groupId = sharkGroupDocument.getGroupId();
            ASAPKeyStore ks = this.sharkPKIComponent.getASAPKeyStore();

            // 2. sign document and add yourself to the group
            byte[] signature = ASAPCryptoAlgorithms
                    .sign(sharkGroupDocument.getGroupId(), ks);
            if(signature == null || signature.length == 0) {
                System.err.println("CRITICAL: Signature could not be created! Check KeyStore for ID: "
                        + this.asapPeer.getPeerID());
            } else {
                System.out.println("SUCCESS: Created signature with length: " + signature.length);
            }
            boolean successAddMember = sharkGroupDocument
                    .addMember(this.asapPeer.getPeerID(),signature);
            System.out.println("DEBUG: added: " + this.asapPeer.getPeerID());
            if(!successAddMember) {
                throw new SharkCurrencyException("Error in adding member to group");
            }

            if(currency instanceof SharkCryptoCurrency) {
                sharkGroupDocument.addMemberEthAdress(this.asapPeer.getPeerID(), this.getWalletAddress());
            }

            // 3. save the newly created document
            this.sharkCurrencyStorage.saveGroupDocument(groupId, sharkGroupDocument);
            return groupId;

        } catch (ASAPException e){
            throw new SharkCurrencyException(e.getMessage());
        }
    }

    @Override
    public byte[] establishGroup(SharkCurrency currency, boolean encrypted, boolean balanceVisible) throws SharkCurrencyException {
        // pass the method to the other establishGroup methode with null for whitelisted
        byte[] groupId = this.establishGroup(currency, null, encrypted, balanceVisible);
        return groupId;
    }

    @Override
    public byte[] establishGroup(ArrayList<CharSequence> inviteMembers, SharkCurrency currency, ArrayList<CharSequence> whitelisted, boolean encrypted, boolean balanceVisible) throws SharkCurrencyException {

        // 0. Check method call validity
        this.checkComponentRunning();
        if (whitelisted != null && inviteMembers != null) {
            boolean allWhitelisted = inviteMembers.stream()
                    .allMatch(invited -> whitelisted.stream()
                            .anyMatch(white -> white.toString().equals(invited.toString())));

            if (!allWhitelisted) {
                throw new SharkCurrencyException("Can not invite peers that are not on the whitelist.");
            }
        }
        return null; //TODO...
    }

    @Override
    public byte[] establishGroup(ArrayList<CharSequence> inviteMembers, SharkCurrency currency, boolean encrypted, boolean balanceVisible) throws SharkCurrencyException {
        return null;
    }

    @Override
    public void sendPromise(CharSequence promiseId,
                            Boolean fromPendingStorage,
                            CharSequence sender,
                            Set<CharSequence> receiver,
                            boolean sign,
                            boolean encrypt,
                            CharSequence uri) throws ASAPException, IOException {
        SharkPromise promise;
            if(fromPendingStorage) {
                promise = this.sharkCurrencyStorage
                        .getSharkPendingPromiseFromStorage(promiseId);
            } else {
                promise = this.sharkCurrencyStorage.getSharkSignedPromiseFromStorage(promiseId);
            }

            byte[] serializedPromise = SharkPromiseSerializer
                    .serializePromise(promise,
                            sender,
                            receiver,
                            sign,
                            encrypt,
                            this.sharkPKIComponent.getASAPKeyStore(),
                            false,
                            0);

            this.asapPeer
                    .sendASAPMessage(SharkCurrencyComponent.CURRENCY_FORMAT, uri, serializedPromise);

    }

    @Override
    public CharSequence createPromise(int amount,
                              SharkCurrency referenceValue,
                              byte[] groupId,
                              CharSequence creditorId,
                              CharSequence debtorId,
                              boolean asCreditor) throws ASAPException, IOException {
        if(amount<=0) {
            throw new SharkPromiseException("Amount for promises must be positive");
        }

        if(this.sharkCurrencyStorage.getGroupDocument(groupId)==null) {
            throw new SharkPromiseException("You are not in a group with given ID: "
                    + Arrays.toString(groupId));
        }
        boolean containsCred = this.sharkCurrencyStorage
                .getGroupDocument(groupId).getCurrentMembers().containsKey(creditorId.toString());
        boolean containsDeb = this.sharkCurrencyStorage
                .getGroupDocument(groupId).getCurrentMembers().containsKey(debtorId.toString());
        if(!containsCred || !containsDeb) {
            throw new SharkPromiseException("Creditor and Debitor must be in the same group with given ID: "
                    + Arrays.toString(groupId));
        }
        SharkPromise promise =
                new SharkInMemoPromise(amount, referenceValue, groupId, creditorId, debtorId);
        Set<CharSequence> receiver = new HashSet<>();
        ASAPKeyStore keystore = this.sharkPKIComponent.getASAPKeyStore();
        CharSequence promiseId = promise.getPromiseID();
        if(asCreditor) {
            SharkPromiseManagement
                    .signAsCreditor(keystore, promise);
            promise.updateState();
            receiver.add(promise.getDebtorID());
            this.sharkCurrencyStorage.addSharkPendingPromiseToStorage(promise);
            this.sendPromise(promiseId,
                    true,
                    promise.getCreditorID(),
                    receiver,
                    true,
                    true,
                    SharkPromise.SHARK_PROMISE_ASK_FOR_SIGNATURE_AS_DEB);
            return promiseId;
        } else {
            SharkPromiseManagement
                    .signAsDebtor(keystore, promise);
            promise.updateState();
            receiver.add(promise.getCreditorID());
            this.sharkCurrencyStorage.addSharkPendingPromiseToStorage(promise);
            this.sendPromise(promiseId,
                    true,
                    promise.getDebtorID(),
                    receiver,
                    true,
                    true,
                    SharkPromise.SHARK_PROMISE_ASK_FOR_SIGNATURE_AS_CRED);
            return promise.getPromiseID();
        }
    }

    @Override
    public void signPromiseAndSendBack(CharSequence promiseId) {
        try {
            if(this.sharkCurrencyStorage.containsSignedPromise(promiseId)) {
                throw new SharkPromiseException("Promise with id: " + promiseId + " has already been sent and signed");
            }

            SharkPromise promise = this.sharkCurrencyStorage
                    .getSharkPendingPromiseFromStorage(promiseId);
            if(promise==null) {
                throw new SharkPromiseException("Promise with PromiseId: "
                        + promiseId + " not found in pending Storage");
            }
            CharSequence creditorId = promise.getCreditorID();
            CharSequence debtorId = promise.getDebtorID();
            boolean asCreditor = this.asapPeer.getPeerID().toString().equals(creditorId.toString());
            ASAPKeyStore ks = this.sharkPKIComponent.getASAPKeyStore();
            CharSequence sender;
            Set<CharSequence> receiver = new HashSet<>();
            if(asCreditor) {
                System.out.println("AS CREDITOR HERE");
                SharkPromiseManagement.signAsCreditor(ks, promise);
                sender=creditorId;
                receiver.add(debtorId);
            } else {
                SharkPromiseManagement.signAsDebtor(ks, promise);
                sender=debtorId;
                receiver.add(creditorId);
            }
            promise.updateState();
            this.sharkCurrencyStorage.removeSharkPendingPromiseFromStorage(promiseId);
            this.sharkCurrencyStorage.addSharkSignedPromiseToStorage(promise);

            byte[] signature;
            if(asCreditor) {
                signature = this.sharkCurrencyStorage.getSharkSignedPromiseFromStorage(promiseId).getCreditorSignature();
            } else {
                signature = this.sharkCurrencyStorage.getSharkSignedPromiseFromStorage(promiseId).getDebtorSignature();
            }
            this.addBalance(promise);
            byte[] serializedContent = null;
            boolean encrypt = this.sharkCurrencyStorage
                    .getGroupDocument(promise.getGroupIDOfPromise()).isEncrypted();
            serializedContent = SharkPromiseSerializer
                    .serializeSignAndSendBackMessage(promiseId,
                            signature,
                            sender,
                            receiver,
                            encrypt,
                            this.sharkPKIComponent.getASAPKeyStore());
            this.asapPeer.sendASAPMessage(SharkCurrencyComponent.CURRENCY_FORMAT,
                    SharkPromise.SHARK_PROMISE_RECEIVE_SIGNED_SIG,
                    serializedContent);

        } catch (IOException | ASAPException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public int getBalance(byte[] currencyId) throws SharkCurrencyException {
        if (currencyId == null) return 0;

        // Konsistente Umwandlung in einen stabilen Key
        String key = encodeKey(currencyId);
        return this.promiseBalanceSimple.getOrDefault(key, 0);
    }

    public int getExtendedBalance(byte[] currencyId, CharSequence peerId) throws SharkCurrencyException {
        if (currencyId == null) return 0;

        if (peerId == null) return 0;

        String key = encodeKey(currencyId);

        Map<CharSequence, Integer> relationMap = this.promiseBalanceExtended.get(key);

        if (relationMap == null) return 0;

        return relationMap.getOrDefault(peerId, 0);

    }

    @Override
    public void addBalance(SharkPromise promise) {
        int transactionAmount;
        CharSequence relation;
        if(this.asapPeer.getPeerID()==promise.getCreditorID()){
            transactionAmount = promise.getAmount();
        relation = promise.getDebtorID();}
        else {
            transactionAmount = -promise.getAmount();
            relation = promise.getCreditorID();
        }
        byte[] currencyId = promise.getReferenceValue().getCurrencyId();
        String key = java.util.Base64.getEncoder().encodeToString(currencyId);
        this.promiseBalanceSimple.merge(key, transactionAmount, Integer::sum);

        Map<CharSequence, Integer> relationMap = this.promiseBalanceExtended
                .computeIfAbsent(key, k -> new HashMap<>());

        relationMap.merge(relation, transactionAmount, Integer::sum);

    }

    @Override
    public void transferPromiseToAnotherPeer(CharSequence promiseId, CharSequence newPeerId) throws SharkCurrencyException {
        SharkPromise promise = this.sharkCurrencyStorage.getSharkSignedPromiseFromStorage(promiseId);

        if (promise == null) {
            throw new SharkCurrencyException("Transfer failed: Promise with ID " + promiseId + " does not exist in Signed Storage.");
        }

        CharSequence myPeerId = this.getPeerIdOfImpl();
        boolean isCreditor = myPeerId.equals(promise.getCreditorID());
        boolean isDebtor = myPeerId.equals(promise.getDebtorID());

       
        if (isCreditor) {
            if (!promise.allowedToChangeCreditor()) {
                throw new SharkCurrencyException("Transfer failed: Permission to change the creditor is missing.");
            }
            promise.setCreditor(newPeerId);
        } else if (isDebtor) {
            if (!promise.allowedToChangeDebtor()) {
                throw new SharkCurrencyException("Transfer failed: Permission to change the debtor is missing.");
            }
            promise.setDebtor(newPeerId);
        } else {
            throw new SharkCurrencyException("Transfer failed: The executing peer is neither creditor nor debtor of this promise.");
        }

       
        promise.updateState();

        
        this.sharkCurrencyStorage.removeSharkSignedPromiseFromStorage(promiseId);
        this.sharkCurrencyStorage.addSharkPendingPromiseToStorage(promise);

      
        this.signPromiseAndSendBack(promiseId);
    }

    @Override
    public byte[] initiateSettlementParty(byte[] groupId) {
        try {
            SharkGroupDocument groupDoc = this.sharkCurrencyStorage.getGroupDocument(groupId);

            if (groupDoc == null) throw new SharkCurrencyException("Group not found for Settlement!");

            // Add all current Members of the Group to the Settlement Party
            Set<CharSequence> expectedPeers = new HashSet<>(groupDoc.getCurrentMembers().keySet());

            // Create random PartyID & Timeout
            byte[] partyId = UUID.randomUUID().toString().getBytes();
            long timeMillis = 60000L; // 60 seconds

            // create new SharkSettlementDocument
            SharkSettlementDocument sharkSettlementDocument = new SharkSettlementDocument(
                    partyId, groupId, this.asapPeer.getPeerID(), expectedPeers, timeMillis
            );

            // Add own Promises
            List<byte[]> serializedPromises = this.getSerializedPromisesForGroup(groupId);
            sharkSettlementDocument.addPeerPromises(this.asapPeer.getPeerID(), serializedPromises);

            // Save and broadcast document to all peers
            this.getSharkCurrencyStorage().saveSettlementDocument(partyId, sharkSettlementDocument);
            this.sendSettlementDocument(sharkSettlementDocument);
            System.out.println("Settlement Party initiated successfully!");

            return partyId;

        } catch (IOException | ASAPException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Broadcasts a Settlement Document over the ASAP Network
    */
    public void sendSettlementDocument(SharkSettlementDocument sharkSettlementDocument) throws IOException, ASAPException {
        byte[] serializedDoc = sharkSettlementDocument.serialize();
        this.asapPeer.sendASAPMessage(CURRENCY_FORMAT, SETTLEMENT_URI, serializedDoc);
    }

    /**
     * Collects all fully signed promises belonging to a group.
     * @param groupId ID of the group whose promises should be collected
     * @return a list of serialized promise byte arrays
     */
    public List<byte[]> getSerializedPromisesForGroup(byte[] groupId) throws ASAPSecurityException, IOException {
        List<byte[]> serializedPromises = new ArrayList<>();

        List<SharkPromise> promises = this.sharkCurrencyStorage.getSignedPromisesForGroup(groupId);
        for (SharkPromise p : promises) {
            byte[] promisAsByte = SharkPromiseSerializer.serializePromise(
                    p, p.getCreditorID(), new HashSet<>(), false, false, this.sharkPKIComponent.getASAPKeyStore(), false, 0
            );
            serializedPromises.add(promisAsByte);
        }
        return serializedPromises;
    }

    /**
     * The final step of the settlement. Executes only after the consensus of a party (matching hashes) is reached.
     * It clears old debts from the storage and creates new, optimize promises.
     *
     * @param settlementDocument SharkSettlementDocument of the Settlement Party
     */
    public void executeFinalSettlement(SharkSettlementDocument settlementDocument) throws IOException, ASAPException, ClassNotFoundException {
        Map<CharSequence, Integer> globalNetBalances = new HashMap<>();
        Set<String> processedPromises = new HashSet<>();

        // 1. extract all collected Promises from the Peers
        for (List<byte[]> promiseList : settlementDocument.getCollectedPromises().values()) {
            for (byte[] promisBytes : promiseList) {
                // remove old Promises
                SharkPromise promise = SharkPromiseSerializer.deserializePromise(promisBytes, this.sharkPKIComponent.getASAPKeyStore());

                String pId = promise.getPromiseID().toString();

                // Only process if we don't already have this PromiseID
                if (!processedPromises.contains(pId)) {
                    processedPromises.add(pId);

                    globalNetBalances.put(promise.getCreditorID(), globalNetBalances.getOrDefault(promise.getCreditorID(), 0) + promise.getAmount());
                    globalNetBalances.put(promise.getDebtorID(), globalNetBalances.getOrDefault(promise.getDebtorID(), 0) - promise.getAmount());
                }
            }
        }


        // 2. Start Settlement Algorithm
        SettlementParty settlementParty = new SettlementParty(new GreedySettlementStrategy());
        List<SettlementTransaction> optimizedTx = settlementParty.executeSettlement(globalNetBalances);

        SharkCurrency currency = this.sharkCurrencyStorage.getGroupDocument(settlementDocument.getGroupId()).getAssignedCurrency();

        // Delete old Promises
        List<SharkPromise> oldPromises = this.getSharkCurrencyStorage().getSignedPromisesForGroup(settlementDocument.getGroupId());
        for (SharkPromise old : oldPromises) {
            this.getSharkCurrencyStorage().removeSharkSignedPromiseFromStorage(old.getPromiseID());
        }

        // 3. Create new Promises
        for (SettlementTransaction tx : optimizedTx) {
            if (this.asapPeer.getPeerID().toString().equals(tx.getDebtorId().toString())) {
                this.createPromise(
                        tx.getAmount(),
                        currency,
                        settlementDocument.getGroupId(),
                        tx.getCreditorId(),
                        tx.getDebtorId(),
                        false);
            }
        }

        // 4. Mark in storage as finished
        this.sharkCurrencyStorage.addExecutedSettlement(settlementDocument.getPartyId());
    }

    /**
     * Computes the local peer's settlement result hash and submits it to the
     * SharkSettlementDocument. This methode is called during the VERIFYING phase
     * after all peers have submited their promises.
     * @param settlementDocument the SharkSettlementDocument in VERIFYING state
     * @throws Exception if deserialization, the settlement algorithm or hashing fails
     */
    public void calculateAndSubmitHash(SharkSettlementDocument settlementDocument) throws Exception {
        Map<CharSequence, Integer> globalNetBalances = new HashMap<>();
        Set<String> processedPromises = new HashSet<>();

        // 1. Deserialize all collected promises and compute the net balances
        for (List<byte[]> promiseList : settlementDocument.getCollectedPromises().values()) {
            for (byte[] promisBytes : promiseList) {
                SharkPromise promise = SharkPromiseSerializer.deserializePromise(promisBytes, this.sharkPKIComponent.getASAPKeyStore());

                String pId = promise.getPromiseID().toString();
                // Deduplicate by ID since multiple peers may carry the same promise
                if (!processedPromises.contains(pId)) {
                    processedPromises.add(pId);

                    globalNetBalances.put(promise.getCreditorID(), globalNetBalances.getOrDefault(promise.getCreditorID(), 0) + promise.getAmount());
                    globalNetBalances.put(promise.getDebtorID(), globalNetBalances.getOrDefault(promise.getDebtorID(), 0) - promise.getAmount());
                }
            }
        }

        // 2. Optimize TX with Settlement Algorithm
        SettlementParty settlementParty = new SettlementParty(new GreedySettlementStrategy());
        List<SettlementTransaction> optimizedTx = settlementParty.executeSettlement(globalNetBalances);

        // 3. Sort deterministically so all peers can produce the same hashes
        optimizedTx.sort(Comparator.comparing((SettlementTransaction tx) -> tx.getDebtorId().toString())
                .thenComparing(tx -> tx.getCreditorId().toString()));

        // 4. Compute the SHA-256 Hash Value
        StringBuilder sb = new StringBuilder();
        for (SettlementTransaction tx : optimizedTx) {
            sb.append(tx.getDebtorId()).append(">").append(tx.getCreditorId()).append(":").append(tx.getAmount()).append(";");
        }

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hashBytes = digest.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
        String finalHash = Base64.getEncoder().encodeToString(hashBytes);

        // 5. Submit the Hash to the Settlement Document, persist and send
        settlementDocument.addPeerHash(this.getPeerIdOfImpl(), finalHash);
        this.sharkCurrencyStorage.saveSettlementDocument(settlementDocument.getPartyId(), settlementDocument); // <--- DAS MUSS HIER REIN
        this.sendSettlementDocument(settlementDocument);

        // Check if Party state is completed and execute the final settlement
        if (settlementDocument.getState() == SettlementPartyState.COMPLETED) {
            if (!this.hasSettlementBeenExecuted(settlementDocument.getPartyId())) {
                System.out.println("CONSENSUS MATCH! Executing Final Settlement...");
                this.executeFinalSettlement(settlementDocument);
            }
        }
    }

    public boolean hasSettlementBeenExecuted(byte[] partyId) {
        return this.sharkCurrencyStorage.hasSettlementBeenExecuted(partyId);
    }

    @Override
    public void acceptInviteAndSign(CharSequence currencyName) throws ASAPException, IOException {

        //get doc from pending invites
        SharkGroupDocument sharkGroupDocument
                = this.sharkCurrencyStorage.getPendingInvite(currencyName.toString());
        byte[] groupId = sharkGroupDocument.getGroupId();

        if (sharkGroupDocument == null){
            throw new SharkCurrencyException("Fehler beim Akzeptieren: Es liegt keine Einladung für die Währung " + currencyName + " vor.");
        }
        if (!sharkGroupDocument.getWhitelistMember().isEmpty() && !sharkGroupDocument.getWhitelistMember().contains(this.asapPeer.getPeerID().toString())){
            this.sharkCurrencyStorage.removePendingInvite(currencyName.toString());
            throw new SharkCurrencyException("Fehler beim Akzeptieren: Der Peer " + asapPeer.getPeerID().toString() + " befindet sich nicht in der Whitelist.");
        }

        //sign doc and add yourself
        ASAPKeyStore ks = this.sharkPKIComponent.getASAPKeyStore();
        byte[] signature = ASAPCryptoAlgorithms
                .sign(sharkGroupDocument.getGroupId(), ks);
        sharkGroupDocument.addMember(this.asapPeer.getPeerID(), signature);

        if(sharkGroupDocument.getAssignedCurrency() instanceof SharkCryptoCurrency) {
            sharkGroupDocument.addMemberEthAdress(this.asapPeer.getPeerID(), this.getWalletAddress());
        }

        //safe doc to your storage
        this.sharkCurrencyStorage.saveGroupDocument(groupId,sharkGroupDocument);

        //remove the pending invite since you accepted
        this.sharkCurrencyStorage.removePendingInvite(currencyName.toString());

        //package your member data for notifying the members
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        String peerID = this.asapPeer.getPeerID().toString();
        dos.writeInt(groupId.length);
        dos.write(groupId);
        dos.writeUTF(peerID);
        dos.writeInt(signature.length);
        dos.write(signature);
        if(sharkGroupDocument.getAssignedCurrency() instanceof SharkCryptoCurrency) {
            dos.writeBoolean(true); // Flag: there is a ETH-Adress
            dos.writeUTF(this.getWalletAddress());
        } else {
            dos.writeBoolean(false); // Flag: there is no ETH-Adress
        }
        dos.flush();

        byte[] signatureAndIDAsContent = baos.toByteArray();
        this.asapPeer.sendASAPMessage(CURRENCY_FORMAT, NEW_MEMBER_URI, signatureAndIDAsContent);
    }

    @Override
    public void declineInvite(CharSequence currencyName) {
        this.sharkCurrencyStorage.removePendingInvite(currencyName.toString());
    }

    //Sets-Up the PKI for our peer
    @Override
    public void onStart(ASAPPeer asapPeer) throws SharkException {
        this.asapPeer = asapPeer;
        try {
            // Initialize storage for peer to listen to the ASAPCurrency application
            this.asapPeer.getASAPStorage(SharkCurrencyComponent.CURRENCY_FORMAT);
            this.sharkCurrencyStorage = new SharkCurrencyStorageImpl();
            this.asapPeer.addASAPMessageReceivedListener(SharkCurrencyComponent.CURRENCY_FORMAT, this);

            // Initialize Ethereum Wallet for the Peer in Storage (using the Peer ID as password for now)
            String peerStoragePath = "storage/" + asapPeer.getPeerID().toString() + "/wallet/";
            File walletDir = new File(peerStoragePath);

            if (!walletDir.exists()){
                walletDir.mkdirs();
            }
            this.wallet = new WalletManager();
            this.wallet.initializeWallet(asapPeer.getPeerID().toString(), walletDir);


        } catch (IOException | CipherException | InvalidAlgorithmParameterException | NoSuchAlgorithmException |
                 NoSuchProviderException e) {
            throw new SharkException("Could not initialize ASAP storage for currency", e);
        }
    }

    private void checkComponentRunning() throws SharkCurrencyException {
        if(this.asapPeer == null || this.sharkPKIComponent == null)
            throw new SharkCurrencyException("peer not started and/or pki not initialized");
    }

    @Override
    public void invitePeerToGroup(byte[] groupId, String optionalMessage, CharSequence peerId)
            throws SharkCurrencyException {

        this.checkComponentRunning();
        SharkGroupDocument sharkGroupDocument = this.sharkCurrencyStorage.getGroupDocument(groupId);

        if(!sharkGroupDocument.getWhitelistMember().contains(peerId)) {
            throw new SharkCurrencyException("Peer with id: " + peerId + " can not be invited because this peer is not whitelisted.");
        }

        try {
            System.out.println("DEBUG: groupId right before sending it: " + sharkGroupDocument.getGroupId());
             byte[] docBytes =  sharkGroupDocument.sharkDocumentToByte();

             ByteArrayOutputStream baos = new ByteArrayOutputStream();
             DataOutputStream daos = new DataOutputStream(baos);

             daos.writeUTF(peerId.toString());
             daos.writeUTF(optionalMessage != null ? optionalMessage : "");
             daos.writeInt(docBytes.length);
             daos.write(docBytes);

             byte[] fullContentOfInvite = baos.toByteArray();
             CharSequence inviteURI = INVITE_CHANNEL_URI;
            System.out.println("DEBUG: Sending invite to: ");
             this.asapPeer.sendASAPMessage(CURRENCY_FORMAT, inviteURI, fullContentOfInvite);

        } catch(ASAPException | IOException e) {
            throw new SharkCurrencyException(e.getLocalizedMessage());
        }
    }

    @Override
    public void asapMessagesReceived(ASAPMessages asapMessages, String sender, List<ASAPHop> list) throws IOException {
        try {
            CharSequence uri = asapMessages.getURI();
            System.out.println("DEBUG asapMessagesReceived: uri=" + uri + ", size=" + asapMessages.size());
            this.notifySharkCurrencyListener(uri, asapMessages);
        } catch (NullPointerException e) {
            e.printStackTrace();
        }
    }

    public SharkPKIComponent getSharkPKIComponent() {
        return this.sharkPKIComponent;
    }

    public CharSequence getPeerIdOfImpl() {
        return this.asapPeer.getPeerID();
    }

    @Override
    public SharkCurrencyStorage getSharkCurrencyStorage() {
        return this.sharkCurrencyStorage;
    }

    @Override
    public void subscribeSharkCurrencyListener(SharkCurrencyListenerNEW listener) {
        this.sharkCurrencyListenerNEW = listener;
        this.addSharkCurrencyListener(listener);
    }

    @Override
    public String getWalletAddress() {
        if (this.wallet != null){
            return this.wallet.getMyAdress();
        }
        return "";
    }

    @Override
    public WalletManager getWallet() {
        return this.wallet;
    }

    private String encodeKey(byte[] id){
        return Base64.getEncoder().encodeToString(id);
    }
}
