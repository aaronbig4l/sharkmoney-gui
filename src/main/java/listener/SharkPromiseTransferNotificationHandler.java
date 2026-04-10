package listener;

import currency.api.SharkCurrencyComponent;
import currency.classes.SharkPromise;
import currency.classes.SharkPromiseSerializer;
import currency.storage.SharkCurrencyStorage;
import net.sharksystem.asap.ASAPMessages;
import net.sharksystem.pki.SharkPKIComponent;

import java.io.IOException;

public class SharkPromiseTransferNotificationHandler implements SharkCurrencyMessageHandler {

    private final SharkCurrencyStorage currencyStorage;
    private final SharkCurrencyComponent sharkCurrencyComponent;

    public SharkPromiseTransferNotificationHandler(SharkCurrencyStorage currencyStorage,
                                                   SharkCurrencyComponent sharkCurrencyComponent) {
        this.currencyStorage = currencyStorage;
        this.sharkCurrencyComponent = sharkCurrencyComponent;
    }

    @Override
    public void handle(CharSequence uri, ASAPMessages messages, SharkPKIComponent pki, CharSequence receiver) throws IOException {
        System.out.println("DEBUG: received a transfer notification. I am: " + receiver);

        for (int i = 0; i < messages.size(); i++) {
            try {
                byte[] messageData = messages.getMessage(i, true);
                SharkPromise updatedPromise = SharkPromiseSerializer
                        .deserializePromise(messageData, pki.getASAPKeyStore());

                CharSequence promiseId = updatedPromise.getPromiseID();

                if (this.currencyStorage.containsSignedPromise(promiseId)) {
                    SharkPromise oldPromise = this.currencyStorage.getSharkSignedPromiseFromStorage(promiseId);
                    this.sharkCurrencyComponent.subtractBalance(oldPromise);
                    this.currencyStorage.removeSharkSignedPromiseFromStorage(promiseId);
                    this.currencyStorage.addSharkPendingPromiseToStorage(updatedPromise);
                    System.out.println("DEBUG TransferNotification: moved promise " + promiseId
                            + " from signed to pending (transfer in progress)");
                } else {
                    System.out.println("DEBUG TransferNotification: promise " + promiseId
                            + " not found in signed storage, skipping");
                }
            } catch (Exception e) {
                System.out.println("DEBUG TransferNotification: skipping message: " + e.getMessage());
            }
        }
    }
}
