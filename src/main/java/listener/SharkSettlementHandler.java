package listener;

import group.SharkGroupDocument;
import implementations.SharkCurrencyComponentImpl;
import net.sharksystem.asap.ASAPException;
import net.sharksystem.asap.ASAPMessages;
import net.sharksystem.pki.SharkPKIComponent;
import transactionSettelment.SettlementPartyState;
import transactionSettelment.SharkSettlementDocument;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class SharkSettlementHandler implements SharkCurrencyMessageHandler{

    private final SharkCurrencyComponentImpl component;

    public SharkSettlementHandler(SharkCurrencyComponentImpl component) {
        this.component = component;
    }

    @Override
    public void handle(CharSequence uri, ASAPMessages messages, SharkPKIComponent pki, CharSequence receiver) throws IOException, ASAPException {
        Iterator<byte[]> msgIterator = messages.getMessages();

        while(msgIterator.hasNext()){
            byte[] msgBytes = msgIterator.next();

            try {
                // deserialize SharkSettlementDocument
                SharkSettlementDocument incomingDoc = SharkSettlementDocument.deserialize(msgBytes);
                CharSequence myPeerId = component.getPeerIdOfImpl();

                if (incomingDoc == null || incomingDoc.isExpired() ||
                    !incomingDoc.getExpectedPeers().contains(component.getPeerIdOfImpl().toString())) continue;

                // Check if SettlementDoc already exists
                SharkSettlementDocument localDoc = component.getSharkCurrencyStorage().getSettlementDocument(incomingDoc.getPartyId());

                if (localDoc == null) {
                    localDoc = incomingDoc;
                } else {
                    // MERGE PROMISES: Fehlen uns Promises, die der andere schon gesammelt hat?
                    for (Map.Entry<CharSequence, List<byte[]>> entry : incomingDoc.getCollectedPromises().entrySet()) {
                        if (!localDoc.getSubmittedPeers().contains(entry.getKey().toString())) {
                            localDoc.addPeerPromises(entry.getKey(), entry.getValue());
                        }
                    }
                    // MERGE HASHES: Fehlen uns Hashes, die der andere schon gesammelt hat?
                    for (Map.Entry<CharSequence, String> entry : incomingDoc.getComputedHashes().entrySet()) {
                        if (!localDoc.getComputedHashes().containsKey(entry.getKey().toString())) {
                            localDoc.addPeerHash(entry.getKey(), entry.getValue());
                        }
                    }
                }

                // save sharksettlement Doc
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
                    component.sendSettlementDocument(localDoc);
                }

                // 3. Consensus match!
                if (localDoc.getState() == SettlementPartyState.COMPLETED) {
                    if (!component.hasSettlementBeenExecuted(localDoc.getPartyId())) {
                        System.out.println("CONSENSUS MATCH! Executing Final Settlement...");
                        component.executeFinalSettlement(localDoc);
                        component.sendSettlementDocument(localDoc);
                    }
                }

                else if (localDoc.getState() == SettlementPartyState.CANCELLED) {
                    System.err.println("Settlement Party failed or Hashes mismatched.");
                }
            } catch (Exception e) {
                System.out.println("Shark Settlement Party Error: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}
