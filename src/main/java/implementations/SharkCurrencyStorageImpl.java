package implementations;

import currency.classes.SharkPromise;
import currency.classes.SharkPromiseState;
import currency.storage.SharkCurrencyStorage;
import exepections.SharkCurrencyException;
import group.SharkGroupDocument;
import transactionSettelment.SharkSettlementDocument;

import java.util.*;


public class SharkCurrencyStorageImpl implements SharkCurrencyStorage {

    private final Map<String, SharkPromise> sharkPromiseStorePending = new HashMap<>();
    private final Map<String, SharkPromise> sharkPromiseStoreSigned = new HashMap<>();
    private final Map<String, SharkGroupDocument> pendingInvites = new HashMap<>();
    private final Map<String, SharkGroupDocument> groupDocuments = new HashMap<>();
    private final Map<String, SharkSettlementDocument> settlementDocuments = new HashMap<>();
    private Set<String> executedSettlements = new HashSet<>();

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

    public List<SharkPromise> getSignedPromisesForGroup(byte[] groupId){
        List<SharkPromise> groupPromises = new ArrayList<>();
        long currentTime = System.currentTimeMillis();

        // iterate all fully signed and not expired Promises
        for (SharkPromise promise : this.sharkPromiseStoreSigned.values()) {

            if (Arrays.equals(promise.getGroupIDOfPromise(), groupId)) {
                boolean fullySinged = promise.getStateOfPromise() == SharkPromiseState.FULLY_SIGNED;
                boolean notExpired = promise.getExpirationDate() > currentTime;
                boolean hasSignature = promise.getCreditorSignature() != null && promise.getDebtorSignature() != null;

                if(fullySinged && notExpired && hasSignature) {
                    groupPromises.add(promise);
                }
            }
        }

        return groupPromises;
    }

    @Override
    public List<SharkPromise> getAllPendingPromises() {
        return new ArrayList<>(this.sharkPromiseStorePending.values());
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

    @Override
    public void saveSettlementDocument(byte[] partyId, SharkSettlementDocument doc) {
        this.settlementDocuments.put(Base64.getEncoder().encodeToString(partyId), doc);
    }

    @Override
    public SharkSettlementDocument getSettlementDocument(byte[] partyId) {
        return this.settlementDocuments.get(Base64.getEncoder().encodeToString(partyId));
    }

    @Override
    public Map<String, SharkSettlementDocument> getAllSettlementDocuments() {
        return this.settlementDocuments;
    }

    @Override
    public void addExecutedSettlement(byte[] partyId) {
        if (partyId != null) {
            this.executedSettlements.add(Base64.getEncoder().encodeToString(partyId));
        }
    }

    @Override
    public boolean hasSettlementBeenExecuted(byte[] partyId) {
        if (partyId == null) return false;
        return this.executedSettlements.contains(Base64.getEncoder().encodeToString(partyId));
    }

    public int getSignedPromiseStorageSize() {
        return this.sharkPromiseStoreSigned.size();
    }

}
