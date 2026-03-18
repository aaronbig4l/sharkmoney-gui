package listener;

import currency.classes.SharkPromise;
import currency.classes.SharkPromiseSerializer;
import currency.storage.SharkCurrencyStorage;
import net.sharksystem.asap.ASAPException;
import net.sharksystem.asap.ASAPMessages;
import net.sharksystem.asap.ASAPStorage;
import net.sharksystem.pki.SharkPKIComponent;

import java.io.IOException;

public class SharkPromiseRevSigPromHandler implements SharkCurrencyMessageHandler {

    private SharkCurrencyStorage currencyStorage;

    public SharkPromiseRevSigPromHandler(SharkCurrencyStorage currencyStorage) {
        this.currencyStorage=currencyStorage;
    }

    public void handle(CharSequence uri, ASAPStorage storage, SharkPKIComponent pki, CharSequence sender) throws IOException, ASAPException {
        try {
            System.out.println("DEBUG: received a fully signed promise from: "
                    + sender);
            ASAPMessages messages = storage.getChannel(uri).getMessages(false);
            for (int i = 0; i < messages.size(); i++) {
                byte[] messageData = messages.getMessage(i, true);
                SharkPromiseSerializer
                        .deserializeSignAndSendBackMessage(messageData,
                                pki.getASAPKeyStore(),
                                this.currencyStorage);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
