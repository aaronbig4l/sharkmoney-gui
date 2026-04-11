package listener;

import currency.api.SharkCurrencyComponent;
import currency.classes.SharkPromise;
import currency.classes.SharkPromiseManagement;
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
                    System.out.println("DEBUG TransferNotification: Old promise deleted and balance adjusted.");


                    boolean encrypt = this.currencyStorage.getGroupDocument(updatedPromise.getGroupIDOfPromise()).isEncrypted();
                    boolean amICreditor = pki.getOwnerID().toString().equals(updatedPromise.getCreditorID().toString());

                    if (amICreditor) {
                        SharkPromiseManagement.signAsCreditor(pki.getASAPKeyStore(), updatedPromise, encrypt);
                    } else {
                        SharkPromiseManagement.signAsDebtor(pki.getASAPKeyStore(), updatedPromise, encrypt);
                    }

                    updatedPromise.updateState();
                    this.currencyStorage.addSharkPendingPromiseToStorage(updatedPromise);
                    System.out.println("DEBUG TransferNotification: Auto-signed the new promise without contradiction and moved to pending.");

                }

                else {

                    this.currencyStorage.addSharkPendingPromiseToStorage(updatedPromise);
                    System.out.println("DEBUG TransferNotification: New promise stored in pending storage. Waiting for manual sign.");
                }
            } catch (Exception e) {
                System.out.println("DEBUG TransferNotification: skipping message: " + e.getMessage());
            }
        }
    }
}