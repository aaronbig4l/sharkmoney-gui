package implementations;

import currency.classes.SharkPromise;
import currency.storage.SharkCurrencyStorage;
import exepections.SharkCurrencyException;
import group.SharkGroupDocument;

import java.util.*;


public class SharkCurrencyStorageImpl implements SharkCurrencyStorage {

    private final Map<String, SharkPromise> sharkPromiseStorePending = new HashMap<>();
    private final Map<String, SharkPromise> sharkPromiseStoreSigned = new HashMap<>();
    private final Map<String, SharkGroupDocument> pendingInvites = new HashMap<>();
    private final Map<String, SharkGroupDocument> groupDocuments = new HashMap<>();

    @Override
    public void saveGroupDocument(byte[] groupId, SharkGroupDocument doc) {
        this.groupDocuments.put(toKey(groupId), doc);
    }

    @Override
    public SharkGroupDocument getGroupDocument(byte[] groupId) throws SharkCurrencyException {
        if (this.groupDocuments.containsKey(toKey(groupId))) {
            return this.groupDocuments.get(toKey(groupId));
        } else {
            throw new SharkCurrencyException("Document with ID: " + groupId + " not found in Storage");
        }
    }

    @Override
    public void addMemberToGroupDocument(byte[] groupId, CharSequence peerId, byte[] signature) {
        SharkGroupDocument doc = this.groupDocuments.get(toKey(groupId));
        if (doc == null) {
            System.err.println("DEBUG: No group document found for groupId");
            return;
        }
        doc.addMember(peerId, signature);
    }

    @Override
    public void savePendingInvite(String currencyName, SharkGroupDocument doc, String optionalMessage) {
        this.pendingInvites.put(currencyName, doc);
    }

    @Override
    public SharkGroupDocument getPendingInvite(String currencyName) {
        return this.pendingInvites.get(currencyName);
    }

    @Override
    public void removePendingInvite(String currencyName) {
        this.pendingInvites.remove(currencyName);
    }

    @Override
    public boolean hasPendingInvites() {
        return !this.pendingInvites.isEmpty();
    }

    @Override
    public void addSharkSignedPromiseToStorage(SharkPromise promise) {
        this.sharkPromiseStoreSigned.put(promise.getPromiseID().toString(), promise);
    }

    @Override
    public void removeSharkSignedPromiseFromStorage(CharSequence promiseId) {
        this.sharkPromiseStoreSigned.remove(promiseId);
    }

    @Override
    public SharkPromise getSharkSignedPromiseFromStorage(CharSequence promiseId) {
        return this.sharkPromiseStoreSigned.get(promiseId.toString());
    }

    @Override
    public boolean containsSignedPromise(CharSequence promiseId) {
        return this.sharkPromiseStoreSigned.containsKey(promiseId.toString());
    }

    //--------------------------------------------------------------------------------------------------------------------
    @Override
    public boolean transferPromiseToAnotherPeer(CharSequence promiseId, CharSequence newPeerId) {
        //TODO needed to be fixed AND TESTET !!!!!!!!!!!!!!!!!!!!!!!!!
        SharkPromise promise = this.sharkPromiseStoreSigned.get(promiseId.toString());
        if (promise == null) {
            return false;
        }
        if (newPeerId == null) {
            return false;
        }
        try {
        SharkPromise sharkPromise = this.sharkPromiseStoreSigned.get(newPeerId.toString());
        //How to see if you are creditor or debitor
        if(true){
            sharkPromise.allowedToChangeDebtor();
            sharkPromise.setAllowedToChangeDebtor(true);
           sharkPromise.setDebtor(newPeerId);
            sharkPromise.setDebtorSignature(promise.getDebtorSignature());

        }
        else {
            sharkPromise.allowedToChangeCreditor();
            sharkPromise.setAllowedToChangeCreditor(true);
            sharkPromise.setCreditor(newPeerId);
            sharkPromise.setCreditorSignature(promise.getCreditorSignature());
        }}
        catch (Exception e){
        }



        return true;
    }
    //--------------------------------------------------------------------------------------------

    public void addSharkPendingPromiseToStorage(SharkPromise promise) {
        this.sharkPromiseStorePending.put(promise.getPromiseID().toString(), promise);
    }

    public void removeSharkPendingPromiseFromStorage(CharSequence promiseId) {
        this.sharkPromiseStorePending.remove(promiseId.toString());
        System.out.println("DEBUG: remove pending promise, new size: " + this.sharkPromiseStorePending.size());
    }

    public SharkPromise getSharkPendingPromiseFromStorage(CharSequence promiseId) {
        return this.sharkPromiseStorePending.get(promiseId.toString());
    }

    //this method is needed because of hashingf purposes
    //two identical byte[] array
    private String toKey(byte[] groupId) {
        return Base64.getEncoder().encodeToString(groupId);
    }

    public int getPendingInviteSize() {
        return this.pendingInvites.size();
    }

    public String listAllMyGroupIds() {
        StringJoiner joiner = new StringJoiner(", ", "[", "]");
        this.groupDocuments.keySet().forEach(joiner::add);
        return joiner.toString();
    }

    public int getPendingPromiseStorageSize() {
        return this.sharkPromiseStorePending.size();
    }

    public int getSignedPromiseStorageSize() {
        return this.sharkPromiseStoreSigned.size();
    }

}
