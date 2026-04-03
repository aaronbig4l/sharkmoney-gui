package listener;

import currency.storage.SharkCurrencyStorage;
import group.SharkGroupDocument;
import net.sharksystem.asap.ASAPException;
import net.sharksystem.asap.ASAPMessages;
import net.sharksystem.asap.crypto.ASAPCryptoAlgorithms;
import net.sharksystem.asap.crypto.ASAPKeyStore;
import net.sharksystem.asap.utils.ASAPSerialization;
import net.sharksystem.pki.SharkPKIComponent;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class SharkGroupUpdateHandler implements SharkCurrencyMessageHandler {
    private final SharkCurrencyStorage sharkCurrencyStorage;

    public SharkGroupUpdateHandler(SharkCurrencyStorage sharkCurrencyStorage) {
        this.sharkCurrencyStorage = sharkCurrencyStorage;
    }

    @Override
    public void handle(CharSequence uri, ASAPMessages messages, SharkPKIComponent pki, CharSequence receiver) throws IOException {
        System.out.println("DEBUG: received a group update message");
        for (int i = 0; i < messages.size(); i++) {
            try {
                byte[] raw = messages.getMessage(i, true);

                ByteArrayInputStream bais = new ByteArrayInputStream(raw);
                byte flags = ASAPSerialization.readByte(bais);
                byte[] tmpMessage = ASAPSerialization.readByteArray(bais);
                boolean encrypted = (flags & SharkGroupDocument.ENCRYPTED_MASK) != 0;

                if (encrypted) {
                    ASAPKeyStore ks = pki.getASAPKeyStore();
                    bais = new ByteArrayInputStream(tmpMessage);
                    ASAPCryptoAlgorithms.EncryptedMessagePackage encryptedMessagePackage
                            = ASAPCryptoAlgorithms.parseEncryptedMessagePackage(bais);

                    if (!ks.isOwner(encryptedMessagePackage.getReceiver())) {
                        System.out.println("DEBUG: skipping group update, not for me: "
                                + encryptedMessagePackage.getReceiver());
                        continue;
                    }
                    tmpMessage = ASAPCryptoAlgorithms.decryptPackage(encryptedMessagePackage, ks);
                }

                DataInputStream dis = new DataInputStream(new ByteArrayInputStream(tmpMessage));
                int docLength = dis.readInt();
                byte[] docBytes = new byte[docLength];
                dis.readFully(docBytes);

                SharkGroupDocument incoming = SharkGroupDocument.fromByte(docBytes);
                SharkGroupDocument local = sharkCurrencyStorage.getGroupDocument(incoming.getGroupId());

                // Fehlende Member mergen
                for (Map.Entry<String, byte[]> entry : incoming.getCurrentMembers().entrySet()) {
                    if (!local.getCurrentMembers().containsKey(entry.getKey())) {
                        local.addMember(entry.getKey(), entry.getValue());
                        System.out.println("DEBUG: merged missing member: " + entry.getKey());
                    }
                }

                // Fehlende ETH-Adressen mergen
                for (Map.Entry<String, String> entry : incoming.getMemberEthAdresses().entrySet()) {
                    if (local.getEthAdressForPeer(entry.getKey()) == null) {
                        local.addMemberEthAdress(entry.getKey(), entry.getValue());
                    }
                }

            } catch (ASAPException e) {
                System.out.println("DEBUG: skipping group update (not for me): " + e.getMessage());
            } catch (Exception e) {
                System.err.println("DEBUG: error processing group update: " + e.getMessage());
            }
        }
    }
}
