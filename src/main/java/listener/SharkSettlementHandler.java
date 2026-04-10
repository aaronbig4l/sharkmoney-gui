package listener;

import currency.storage.SharkCurrencyStorage;
import group.SharkGroupDocument;
import implementations.SharkCurrencyComponentImpl;
import net.sharksystem.asap.ASAPException;
import net.sharksystem.asap.ASAPMessages;
import net.sharksystem.asap.crypto.ASAPCryptoAlgorithms;
import net.sharksystem.asap.crypto.ASAPKeyStore;
import net.sharksystem.asap.utils.ASAPSerialization;
import net.sharksystem.pki.SharkPKIComponent;
import transactionSettelment.SettlementPartyState;
import transactionSettelment.SharkSettlementDocument;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Handler responsible for processing incoming SharkSettlementDocuments.
 * It manages the local state transitions of a settlement party by merging
 * incoming data and triggering the appropriate actions.
 */
public class SharkSettlementHandler implements SharkCurrencyMessageHandler{

    private final SharkCurrencyComponentImpl component;
    private final SharkCurrencyStorage storage;

    public SharkSettlementHandler(SharkCurrencyStorage storage, SharkCurrencyComponentImpl component) {
        this.storage = storage;
        this.component = component;
    }

    @Override
    public void handle(CharSequence uri, ASAPMessages messages, SharkPKIComponent pki, CharSequence receiver) throws IOException, ASAPException {

        for (int i = 0; i < messages.size(); i++) {
            byte[] msgData = messages.getMessage(i, true);

            SharkSettlementDocument localDoc = null;
            try {
                ByteArrayInputStream bais = new ByteArrayInputStream(msgData);
                byte flags = ASAPSerialization.readByte(bais);
                byte[] payload = ASAPSerialization.readByteArray(bais);

                boolean isEncrypted = (flags & SharkGroupDocument.ENCRYPTED_MASK) != 0;
                if (isEncrypted) {
                    ASAPKeyStore ks = pki.getASAPKeyStore();
                    bais = new ByteArrayInputStream(payload);
                    ASAPCryptoAlgorithms.EncryptedMessagePackage encPkg =
                            ASAPCryptoAlgorithms.parseEncryptedMessagePackage(bais);

                    // Wenn die Nachricht nicht für mich ist -> überspringen
                    if (!ks.isOwner(encPkg.getReceiver())) {
                        continue;
                    }
                    // Nachricht entschlüsseln
                    payload = ASAPCryptoAlgorithms.decryptPackage(encPkg, ks);
                }

                // deserialize the incoming SharkSettlementDocument
                SharkSettlementDocument incomingDoc = SharkSettlementDocument.deserialize(payload);
                CharSequence myPeerId = component.getPeerIdOfImpl();

                if (incomingDoc == null || incomingDoc.isExpired() || !incomingDoc.getExpectedPeers().contains(myPeerId.toString())) {
                    if (incomingDoc != null && incomingDoc.isExpired()) {
                        SharkGroupDocument gd = storage.getGroupDocument(incomingDoc.getGroupId());
                        if (gd != null) gd.setPromiseCreationLock(false);
                    }
                    continue;
                }

                // Check if SettlementDoc already exists
                localDoc = component.getSharkCurrencyStorage().getSettlementDocument(incomingDoc.getPartyId());
                if (localDoc == null) {
                    localDoc = incomingDoc;
                    this.storage.getGroupDocument(localDoc.getGroupId()).setPromiseCreationLock(true);
                } else {
                    // Merge Promises
                    for (Map.Entry<CharSequence, List<byte[]>> entry : incomingDoc.getCollectedPromises().entrySet()) {
                        if (!localDoc.getSubmittedPeers().contains(entry.getKey().toString())) {
                            localDoc.addPeerPromises(entry.getKey(), entry.getValue());
                        }
                    }
                    // Merge Hashes
                    for (Map.Entry<CharSequence, String> entry : incomingDoc.getComputedHashes().entrySet()) {
                        if (!localDoc.getComputedHashes().containsKey(entry.getKey().toString())) {
                            localDoc.addPeerHash(entry.getKey(), entry.getValue());
                        }
                    }
                }

                // Save SharkSettlementDocument
                component.getSharkCurrencyStorage().saveSettlementDocument(localDoc.getPartyId(), localDoc);

                // 1. Do I have to add Promises?
                if (localDoc.getState() == SettlementPartyState.GATHERING && !localDoc.getSubmittedPeers().contains(myPeerId.toString())) {
                    System.out.println("Add my Promises to Settlement Party: " + myPeerId);
                    List<byte[]> myPromises = component.getSerializedPromisesForGroup(localDoc.getGroupId());
                    localDoc.addPeerPromises(myPeerId, myPromises);
                    component.sendSettlementDocument(localDoc);
                }

                // 2. Do I have to calculate a Hash value and add it?
                if (localDoc.getState() == SettlementPartyState.VERIFYING && !localDoc.getComputedHashes().containsKey(myPeerId.toString())) {
                    System.out.println("All Promises received. Calculating and adding my Hash...");
                    component.calculateAndSubmitHash(localDoc);
                }

                // 3. Consensus match: execute final settlement
                if (localDoc.getState() == SettlementPartyState.COMPLETED) {
                    if (!component.hasSettlementBeenExecuted(localDoc.getPartyId())) {
                        System.out.println("CONSENSUS MATCH! Executing Final Settlement...");
                        component.executeFinalSettlement(localDoc);
                        component.sendSettlementDocument(localDoc);
                    }
                } else if (localDoc.getState() == SettlementPartyState.CANCELLED) {
                    System.err.println("Settlement Party failed or Hashes mismatched.");
                    this.storage.getGroupDocument(localDoc.getGroupId()).setPromiseCreationLock(false);
                }

            } catch (Exception e) {
                System.out.println("Shark Settlement Party Error: " + e.getMessage());
                if (localDoc != null) {
                    this.storage.getGroupDocument(localDoc.getGroupId())
                            .setPromiseCreationLock(false);
                }
            }
        }
    }
}
