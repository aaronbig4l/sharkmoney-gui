package listener;

import currency.api.SharkCurrencyComponent;
import currency.classes.SharkPromise;
import currency.storage.SharkCurrencyStorage;
import exepections.SharkCurrencyException;
import group.SharkGroupDocument;
import implementations.SharkCurrencyComponentImpl;
import net.sharksystem.asap.*;
import net.sharksystem.asap.crypto.ASAPCryptoAlgorithms;
import net.sharksystem.asap.crypto.ASAPKeyStore;
import net.sharksystem.asap.utils.ASAPSerialization;
import net.sharksystem.pki.SharkPKIComponent;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

public class SharkNewMemberHandler implements SharkCurrencyMessageHandler{

    private final SharkCurrencyStorage sharkCurrencyStorage;
    private final SharkCurrencyComponent sharkCurrencyComponent;
    private String senderId;
    private SharkPKIComponent pkiComponent;

    public SharkNewMemberHandler(SharkCurrencyStorage sharkCurrencyStorage, SharkCurrencyComponent sharkCurrencyComponent) {
        this.sharkCurrencyStorage = sharkCurrencyStorage;
        this.sharkCurrencyComponent = sharkCurrencyComponent;
    }

    @Override
    public void handle(CharSequence uri, ASAPMessages messages, SharkPKIComponent pki, CharSequence receiver) {

        try {
            this.pkiComponent=pki;
            System.out.println("DEBUG: I "+pki.getOwnerID()+" received a new member message, message size is: " + messages.size());
            //--------Read all data from the message ------------------------
            for (int i = 0; i < messages.size(); i++) {
                byte[] messageData = messages.getMessage(i, true);

                ByteArrayInputStream bais = new ByteArrayInputStream(messageData);
                byte flags = ASAPSerialization.readByte(bais);
                byte[] tmpMessage = ASAPSerialization.readByteArray(bais);
                boolean encrypted = (flags & SharkPromise.ENCRYPTED_MASK) != 0;
                if (encrypted) {
                    // decrypt
                    ASAPKeyStore ks = pki.getASAPKeyStore();
                    bais = new ByteArrayInputStream(tmpMessage);
                    ASAPCryptoAlgorithms.EncryptedMessagePackage
                            encryptedMessagePackage = ASAPCryptoAlgorithms.parseEncryptedMessagePackage(bais);

                    // for me?
                    if (!ks.isOwner(encryptedMessagePackage.getReceiver())) {
                        System.out.println("SharkPromise Message: message not for me. Current user: "
                                + ks.getOwner()
                                + ", recipient: "
                                + encryptedMessagePackage.getReceiver());
                        continue;
                    }
                    // replace message with decrypted message
                    tmpMessage = ASAPCryptoAlgorithms.decryptPackage(
                            encryptedMessagePackage, ks);
                }
                bais = new ByteArrayInputStream(tmpMessage);
                DataInputStream dis = new DataInputStream(bais);

                this.senderId = dis.readUTF(); //1
                int groupIdLength = dis.readInt(); //2
                byte[] groupId = new byte[groupIdLength];
                dis.readFully(groupId); //3
                int size = dis.readInt();
                byte[] senderSig = null;
                for (int k = 0; k < size; k++) {
                    String peerID = dis.readUTF();
                    int sigLength = dis.readInt();
                    byte[] sig = new byte[sigLength];
                    dis.readFully(sig);
                    if(peerID.equals(this.senderId)) {
                        senderSig = sig;
                    }
                }

                boolean hasEthAddress = dis.available() > 0 && dis.readBoolean();
                String ethAddress = hasEthAddress ? dis.readUTF() : null;

                this.sharkCurrencyStorage
                        .addMemberToGroupDocument(groupId, this.senderId, senderSig);

                SharkGroupDocument updatedDoc = this.sharkCurrencyStorage.getGroupDocument(groupId);
                CharSequence myId = pki.getOwnerID();

                if(myId.toString().equals(updatedDoc.getGroupCreator().toString())) {
                    for(String member : updatedDoc.getCurrentMembers().keySet()) {
                        if(member.equals(myId.toString())) continue;
                        try {
                            this.sharkCurrencyComponent.sendGroupDocumentUpdate(groupId, member);
                        } catch(Exception e) {
                            System.err.println("Failed to send group update to " + member);
                        }
                    }
                }

                if(hasEthAddress && !ethAddress.isEmpty()) {
                    SharkGroupDocument doc = this.sharkCurrencyStorage.getGroupDocument(groupId);
                    doc.addMemberEthAdress(this.senderId, ethAddress);
                    this.sharkCurrencyStorage.saveGroupDocument(groupId, doc);
                    System.out.println("DEBUG: Added ETH-Address " + ethAddress + " for Peer " + this.senderId);
                }
            }
        } catch (IOException | ASAPException e) {
            throw new RuntimeException(e);
        }
    }
}
