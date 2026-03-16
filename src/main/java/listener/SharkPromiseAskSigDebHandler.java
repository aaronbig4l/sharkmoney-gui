package listener;

import currency.classes.SharkPromise;
import currency.classes.SharkPromiseSerializer;
import currency.storage.SharkCurrencyStorage;
import exepections.SharkCurrencyException;
import net.sharksystem.asap.ASAPMessages;
import net.sharksystem.asap.ASAPStorage;
import net.sharksystem.pki.SharkPKIComponent;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;

public class SharkPromiseAskSigDebHandler implements SharkCurrencyMessageHandler {

    private SharkCurrencyStorage currencyStorage;

    public SharkPromiseAskSigDebHandler(SharkCurrencyStorage currencyStorage) {
        this.currencyStorage=currencyStorage;
    }

    @Override
    public void handle(CharSequence uri, ASAPStorage storage, SharkPKIComponent pki, CharSequence sender) {
        try {
            System.out.println("DEBUG: received a message being asked to sign as debitor from: "
                            + sender);
            ASAPMessages messages = storage.getChannel(uri).getMessages(false);
            System.out.println("DEBUG: uri = " + uri);
            System.out.println("DEBUG: messages.size() = " + messages.size());
            for (int i = 0; i < messages.size(); i++) {
                byte[] messageData = messages.getMessage(i, true);
                System.out.println("DEBUG: message[" + i + "] length = " + messageData.length);
                System.out.println("DEBUG: first byte (flags) = " + messageData[0]);
                SharkPromise promise = SharkPromiseSerializer
                        .deserializePromise(messageData, pki.getASAPKeyStore());
                this.currencyStorage.addSharkPendingPromiseToStorage(promise);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
