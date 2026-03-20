package implementations;

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

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
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
    public void signPromiseAndSendBack(CharSequence promiseId,
                                       CharSequence creditorId,
                                       CharSequence debtorId,
                                       Boolean sign,
                                       Boolean encrypt,
                                       Boolean asCreditor) {
        try {
            SharkPromise promise = this.sharkCurrencyStorage
                    .getSharkPendingPromiseFromStorage(promiseId);
            if(promise==null) {
                throw new SharkPromiseException("Promise with PromiseId: "
                        + promiseId + " not found in Storage");
            }
            ASAPKeyStore ks = this.sharkPKIComponent.getASAPKeyStore();
            CharSequence sender;
            Set<CharSequence> receiver = new HashSet<>();
            if(asCreditor) {
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
            byte[] serializedContent = null;

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
    public int getBalance(CharSequence currencyName) throws SharkCurrencyException {
        return 0;
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


        } catch (IOException | InvalidAlgorithmParameterException | CipherException | NoSuchAlgorithmException |
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
            this.notifySharkCurrencyListener(uri);
        } catch (NullPointerException e) {
            e.printStackTrace();
        }
    }

    @Override
    public ASAPStorage getASAPStorage() throws IOException, ASAPException {
        return this.asapPeer.getASAPStorage(SharkCurrencyComponent.CURRENCY_FORMAT);
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
}
