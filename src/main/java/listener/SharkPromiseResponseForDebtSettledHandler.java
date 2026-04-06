package listener;

import currency.api.SharkCurrencyComponent;
import currency.storage.SharkCurrencyStorage;
import currency.classes.SharkPromise;
import net.sharksystem.asap.ASAPException;
import net.sharksystem.asap.ASAPMessages;
import net.sharksystem.asap.ASAPSecurityException;
import net.sharksystem.asap.crypto.ASAPCryptoAlgorithms;

import net.sharksystem.asap.utils.ASAPSerialization;
import net.sharksystem.pki.SharkPKIComponent;

import java.io.ByteArrayInputStream;
import java.io.IOException;

public class SharkPromiseResponseForDebtSettledHandler implements SharkCurrencyMessageHandler {

    private SharkCurrencyStorage currencyStorage;
    private SharkCurrencyComponent currencyComponent;

    public SharkPromiseResponseForDebtSettledHandler(SharkCurrencyStorage currencyStorage, SharkCurrencyComponent currencyComponent) {
        this.currencyStorage = currencyStorage;
        this.currencyComponent = currencyComponent;
    }

    @Override
    public void handle(CharSequence uri, ASAPMessages messages, SharkPKIComponent pki, CharSequence receiver) throws IOException, ASAPException {
        for (int i = 0; i < messages.size(); i++) {

            ByteArrayInputStream bais = new ByteArrayInputStream(messages.getMessage(i, true));
            byte flags = ASAPSerialization.readByteParameter(bais);
            byte[] tmpMessage = ASAPSerialization.readByteArray(bais);

            boolean encrypted = (flags & SharkPromise.ENCRYPTED_MASK) != 0;

            if (encrypted) {
                ASAPCryptoAlgorithms.EncryptedMessagePackage encryptedMessagePackage =
                        ASAPCryptoAlgorithms.parseEncryptedMessagePackage(tmpMessage);

                if (!pki.getASAPKeyStore().isOwner(encryptedMessagePackage.getReceiver())) {
                    throw new ASAPException("SharkPromise Message: message not for me. Current user: "
                            + pki.getASAPKeyStore().getOwner()
                            + ", recipient: "
                            + encryptedMessagePackage.getReceiver());
                }

                try {
                    tmpMessage = ASAPCryptoAlgorithms.decryptPackage(encryptedMessagePackage, pki.getASAPKeyStore());
                } catch (ASAPSecurityException e) {
                    throw new ASAPException("Decryption of ResponseForDebtSettled message failed", e);
                }
            }

            ByteArrayInputStream payloadStream = new ByteArrayInputStream(tmpMessage);
            boolean isAccepted = ASAPSerialization.readBooleanParameter(payloadStream);
            CharSequence promiseID = ASAPSerialization.readCharSequenceParameter(payloadStream);

            if (isAccepted) {
                if (this.currencyStorage.containsSignedPromise(promiseID)) {
                    SharkPromise settledPromise = this.currencyStorage.getSharkSignedPromiseFromStorage(promiseID);
                    this.currencyComponent.subtractBalance(settledPromise);
                    this.currencyStorage.removeSharkSignedPromiseFromStorage(promiseID);
                }
            }
        }
    }
}