package listener;

import currency.api.SharkCurrencyComponent;
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
    private SharkCurrencyComponent sharkCurrencyComponent;

    public SharkPromiseRevSigPromHandler(SharkCurrencyStorage currencyStorage,
                                         SharkCurrencyComponent sharkCurrencyComponent) {
        this.currencyStorage=currencyStorage;
        this.sharkCurrencyComponent=sharkCurrencyComponent;
    }

    public void handle(CharSequence uri, ASAPMessages messages, SharkPKIComponent pki, CharSequence sender) throws IOException, ASAPException {
        try {
            System.out.println("DEBUG: received a fully signed promise from: " + sender);

            for (int i = 0; i < messages.size(); i++) {
                byte[] messageData = messages.getMessage(i, true);

                SharkPromise promise = SharkPromiseSerializer
                        .deserializeSignAndSendBackMessage(messageData,
                                pki.getASAPKeyStore(),
                                this.currencyStorage);

                CharSequence promiseId = promise.getPromiseID();


                // move it to the signed store now that we have the full signature back.
                if (this.currencyStorage.getSharkPendingPromiseFromStorage(promiseId) != null) {
                    System.out.println("DEBUG: moving promise " + promiseId
                            + " from pending → signed (received counter-signature)");
                    this.currencyStorage.removeSharkPendingPromiseFromStorage(promiseId);
                    this.currencyStorage.addSharkSignedPromiseToStorage(promise);
                }

                this.sharkCurrencyComponent.addBalance(promise);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
