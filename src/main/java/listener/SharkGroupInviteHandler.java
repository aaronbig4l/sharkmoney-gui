package listener;

import currency.classes.SharkPromise;
import currency.storage.SharkCurrencyStorage;
import group.SharkGroupDocument;
import net.sharksystem.asap.ASAPChannel;
import net.sharksystem.asap.ASAPException;
import net.sharksystem.asap.ASAPMessages;
import net.sharksystem.asap.ASAPStorage;
import net.sharksystem.asap.crypto.ASAPCryptoAlgorithms;
import net.sharksystem.asap.crypto.ASAPKeyStore;
import net.sharksystem.asap.utils.ASAPSerialization;
import net.sharksystem.pki.SharkPKIComponent;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;


public class SharkGroupInviteHandler implements SharkCurrencyMessageHandler {

    private final SharkCurrencyStorage sharkCurrencyStorage;
    private final String thisPeersId;

    public SharkGroupInviteHandler(SharkCurrencyStorage sharkCurrencyStorage, String thisPeersId) {
        this.sharkCurrencyStorage = sharkCurrencyStorage;
        this.thisPeersId=thisPeersId;
    }

    @Override
    public void handle(CharSequence uri, ASAPMessages messages, SharkPKIComponent pki, CharSequence sender) {
        try {
            if (messages.size() == 0) {
                System.err.println("DEBUG: No messages found in channel " + uri);
                return;
            }

            for (int i = 0; i < messages.size(); i++) {
                byte[] inviteData = messages.getMessage(i, true);

                ByteArrayInputStream bais = new ByteArrayInputStream(inviteData);
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
                DataInputStream dais = new DataInputStream(bais);
                String receiver = dais.readUTF();
                if (!this.thisPeersId.equals(receiver)) {
                    System.out.println("DEBUG: rejected group invite, because Im not the receiver: "
                            + this.thisPeersId);
                    continue;
                }
                System.out.println("DEBUG: I got an invite " + this.thisPeersId);

                String optionalMessage = dais.readUTF();
                if (optionalMessage.isEmpty()) {
                    optionalMessage = null;
                }

                int docLength = dais.readInt();
                byte[] docBytes = new byte[docLength];
                dais.readFully(docBytes);
                SharkGroupDocument sharkGroupDocument = SharkGroupDocument.fromByte(docBytes);

                sharkCurrencyStorage
                        .savePendingInvite(sharkGroupDocument
                                .getAssignedCurrency()
                                .getCurrencyName(), sharkGroupDocument, optionalMessage);
            }
        } catch (IOException | ASAPException e) {
            throw new RuntimeException(e);
        }
    }
}
