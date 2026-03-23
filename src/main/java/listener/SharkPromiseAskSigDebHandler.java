package listener;

import currency.classes.SharkPromise;
import currency.classes.SharkPromiseSerializer;
import currency.storage.SharkCurrencyStorage;
import exepections.SharkCurrencyException;
import net.sharksystem.asap.ASAPException;
import net.sharksystem.asap.ASAPMessages;
import net.sharksystem.asap.ASAPStorage;
import net.sharksystem.pki.SharkPKIComponent;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

public class SharkPromiseAskSigDebHandler implements SharkCurrencyMessageHandler {

    private SharkCurrencyStorage currencyStorage;

    public SharkPromiseAskSigDebHandler(SharkCurrencyStorage currencyStorage) {
        this.currencyStorage=currencyStorage;
    }

    @Override
    public void handle(CharSequence uri, ASAPMessages messages, SharkPKIComponent pki, CharSequence receiver) throws IOException {

        System.out.println("DEBUG: received a message being asked to sign as debitor from: "
                + receiver);
        for (int i = 0; i < messages.size(); i++) {
            try {
                byte[] messageData = messages.getMessage(i, true);
                SharkPromise promise = SharkPromiseSerializer
                        .deserializePromise(messageData, pki.getASAPKeyStore());
                    this.currencyStorage.addSharkPendingPromiseToStorage(promise);
                    System.out.println("DEBUG Handler: stored promise id=" + promise.getPromiseID());
                } catch (ASAPException e) {
                    System.out.println("DEBUG Handler: skipping message (not for me): " + e.getMessage());
                } catch (ClassNotFoundException | IOException e) {
                throw new RuntimeException(e);
            }
        }

    }
}
