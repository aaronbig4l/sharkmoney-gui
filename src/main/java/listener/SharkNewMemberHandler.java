package listener;

import currency.storage.SharkCurrencyStorage;
import exepections.SharkCurrencyException;
import group.SharkGroupDocument;
import net.sharksystem.asap.ASAPChannel;
import net.sharksystem.asap.ASAPException;
import net.sharksystem.asap.ASAPMessages;
import net.sharksystem.asap.ASAPStorage;
import net.sharksystem.pki.SharkPKIComponent;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

public class SharkNewMemberHandler implements SharkCurrencyMessageHandler{

    private final SharkCurrencyStorage sharkCurrencyStorage;

    public SharkNewMemberHandler(SharkCurrencyStorage sharkCurrencyStorage) {
        this.sharkCurrencyStorage = sharkCurrencyStorage;
    }

    @Override
    public void handle(CharSequence uri, ASAPMessages messages, SharkPKIComponent pki, CharSequence sender) {

        try {
            System.out.println("DEBUG: received a new member message from: " + sender + " message size is: " + messages.size());
            //--------Read all data from the message ------------------------
            for (int i = 0; i < messages.size(); i++) {
                byte[] messageData = messages.getMessage(i, true);

                ByteArrayInputStream bais = new ByteArrayInputStream(messageData);
                DataInputStream dis = new DataInputStream(bais);
                int groupIdLength = dis.readInt();
                byte[] groupId = new byte[groupIdLength];
                dis.readFully(groupId);
                try {
                    this.sharkCurrencyStorage.getGroupDocument(groupId);
                } catch (SharkCurrencyException e) {
                    System.out.println("DEBUG: received new member message for unknown group, skipping.");
                    continue;
                }
                String peerID = dis.readUTF();
                int sigLength = dis.readInt();
                byte[] signature = new byte[sigLength];
                dis.readFully(signature);

                // check if ETH-Adress was send
                boolean hasEthAdress = false;
                String ethAdress = null;
                if (dis.available() > 0) {
                    hasEthAdress = dis.readBoolean();
                    if(hasEthAdress) {
                        ethAdress = dis.readUTF();
                    }
                }

                //adding the person to my doc
                this.sharkCurrencyStorage.addMemberToGroupDocument(groupId, peerID, signature);

                if(hasEthAdress && ethAdress != null && !ethAdress.isEmpty()) {
                    SharkGroupDocument doc = this.sharkCurrencyStorage.getGroupDocument(groupId);
                    doc.addMemberEthAdress(peerID, ethAdress);
                    this.sharkCurrencyStorage.saveGroupDocument(groupId, doc);
                    System.out.println("DEBUG: Added ETH-Address " + ethAdress + " for Peer " + peerID);
                }
            }
        } catch (IOException | ASAPException e) {
            throw new RuntimeException(e);
        }
    }
}
