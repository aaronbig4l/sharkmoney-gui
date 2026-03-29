package currency.storage;

import currency.classes.SharkPromise;
import exepections.SharkCurrencyException;
import group.SharkGroupDocument;
import transactionSettelment.SharkSettlementDocument;

import java.util.List;
import java.util.Map;

/**
 * This Interface provides methods for managing the storage within the
 * ASAPCurrency Application
 */
public interface SharkCurrencyStorage {

    //GROUP-DOCUMENT STORAGE METHODS
    void saveGroupDocument(byte[] groupId, SharkGroupDocument doc);
    SharkGroupDocument getGroupDocument(byte[] groupId) throws SharkCurrencyException;
    void addMemberToGroupDocument(byte[] groupId, CharSequence peerId, byte[] signature);
    boolean hasPendingInvites();
    int getPendingInviteSize();

    //INVITE STORAGE METHODS
    void savePendingInvite(String currencyName, SharkGroupDocument doc, String optionalMessage);
    SharkGroupDocument getPendingInvite(String currencyName);
    void removePendingInvite(String currencyName);

    //FULLY SIGNED PROMISE STORAGE METHODS
    void addSharkSignedPromiseToStorage(SharkPromise promise);
    void removeSharkSignedPromiseFromStorage(CharSequence promiseId);
    SharkPromise getSharkSignedPromiseFromStorage(CharSequence promiseId);
    int getSignedPromiseStorageSize();
    boolean containsSignedPromise(CharSequence promiseId);
    List<SharkPromise> getSignedPromisesForGroup(byte[] groupId);
    
    //PENDING PROMISE STORAGE METHODS
    List<SharkPromise> getAllPendingPromises();

    public List<CharSequence> getToBeSettledPromises();


    //PENDING PROMISE STORAGE METHODS
    void addSharkPendingPromiseToStorage(SharkPromise promise);
    void removeSharkPendingPromiseFromStorage(CharSequence promiseId);
    SharkPromise getSharkPendingPromiseFromStorage(CharSequence promiseId);
    int getPendingPromiseStorageSize();

    void addSharkToBeSettledPromiseToStorage(CharSequence promiseID);
    void removeSharkToBeSettledPromiseFromStorage(CharSequence promiseId);

    // SETTLEMENT STORAGE METHODS
    void saveSettlementDocument(byte[] partyId, SharkSettlementDocument doc);
    SharkSettlementDocument getSettlementDocument(byte[] partyId);
    Map<String, SharkSettlementDocument> getAllSettlementDocuments();
    void addExecutedSettlement(byte[] partyId);
    boolean hasSettlementBeenExecuted(byte[] partyId);
}
