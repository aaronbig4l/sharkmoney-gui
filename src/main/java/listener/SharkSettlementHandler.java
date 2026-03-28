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
                SharkSettlementDocument sharkSettlementDocument = SharkSettlementDocument.deserialize(msgBytes);
                if (sharkSettlementDocument == null || sharkSettlementDocument.isExpired() ||
                    !sharkSettlementDocument.getExpectedPeers().contains(component.getPeerIdOfImpl().toString())) continue;

                CharSequence myPeerId = component.getPeerIdOfImpl();

                // 1. Do I have to add Promises?
                if (sharkSettlementDocument.getState() == SettlementPartyState.GATHERING && !sharkSettlementDocument.getSubmittedPeers().contains(myPeerId.toString())) {
                    System.out.println("Add my Promises to Settlement Party: " + myPeerId);
                    List<byte[]> myPromises = component.getSerializedPromisesForGroup(sharkSettlementDocument.getGroupId());
                    sharkSettlementDocument.addPeerPromises(myPeerId, myPromises);
                    component.sendSettlementDocument(sharkSettlementDocument);
                }

                // 2. Do I have to calculate a Hash value and add it?
                else if (sharkSettlementDocument.getState() == SettlementPartyState.VERIFYING && !sharkSettlementDocument.getComputedHashes().containsKey(myPeerId.toString())) {
                    System.out.println("All Promises received. Calculating and adding my Hash...");
                    component.calculateAndSubmitHash(sharkSettlementDocument);
                }

                // 3. Consensus match!
                else if (sharkSettlementDocument.getState() == SettlementPartyState.COMPLETED) {
                    if (!component.hasSettlementBeenExecuted(sharkSettlementDocument.getPartyId())) {
                        System.out.println("CONSENSUS MATCH! Executing Final Settlement...");
                        component.executeFinalSettlement(sharkSettlementDocument);
                    }
                }

                else if (sharkSettlementDocument.getState() == SettlementPartyState.CANCELLED) {
                    System.err.println("Settlement Party failed or Hashes mismatched.");
                }
            } catch (Exception e) {
                System.out.println("Shark Settlement Party Error: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}
