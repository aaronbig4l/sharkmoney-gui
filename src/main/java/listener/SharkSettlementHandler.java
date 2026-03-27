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
                if (sharkSettlementDocument == null) continue;

                // Timeout Check
                if (sharkSettlementDocument.isExpired()){
                    System.out.println("Settlement Party is expired. ");
                    continue;
                }

                CharSequence myPeerId = component.getPeerIdOfImpl();

                // Is the Peer in the group?
                if(!sharkSettlementDocument.getExpectedPeers().contains(myPeerId.toString())) {
                    continue;
                }

                // Do I have to add my Promises?
                if (!sharkSettlementDocument.getSubmittedPeers().contains(myPeerId.toString())) {
                    System.out.println("Add my Promises to Settlement Party: " + myPeerId);

                    List<byte[]> mySerializedPromises = component.getSerializedPromisesForGroup(sharkSettlementDocument.getGroupId());
                    sharkSettlementDocument.addPeerPromises(myPeerId, mySerializedPromises);

                    component.sendSettlementDocument(sharkSettlementDocument);
                }

                // Is the Party READY (all Peers submitted)?
                if (sharkSettlementDocument.getState() == SettlementPartyState.READY) {
                    System.out.println("All Peers added their Promises! Starting final Consensus-Calculation...");
                    component.executeFinalSettlement(sharkSettlementDocument);
                }
            } catch (Exception e) {
                System.out.println("Shark Settlement Party Error: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}
