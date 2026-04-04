package listener;

import currency.classes.SharkPromise;
import currency.classes.SharkPromiseSerializer;
import currency.storage.SharkCurrencyStorage;
import net.sharksystem.asap.ASAPException;
import net.sharksystem.asap.ASAPMessages;
import net.sharksystem.pki.SharkPKIComponent;

import java.io.IOException;

public class SharkPromiseAskSigCredHandler implements SharkCurrencyMessageHandler{

    private SharkCurrencyStorage currencyStorage;

    public SharkPromiseAskSigCredHandler(SharkCurrencyStorage currencyStorage) {
        this.currencyStorage=currencyStorage;
    }

    @Override
    public void handle(CharSequence uri, ASAPMessages messages, SharkPKIComponent pki, CharSequence receiver) throws IOException {
        System.out.println("DEBUG: received a message being asked to sign as creditor from: "
                + receiver);
        for (int i = 0; i < messages.size(); i++) {
            try {
                byte[] messageData = messages.getMessage(i, true);
                SharkPromise promise = SharkPromiseSerializer
                        .deserializePromise(messageData, pki.getASAPKeyStore());

                // Am I the Creditor?
                if (!pki.getOwnerID().toString().equals(promise.getCreditorID().toString())) {
                    System.out.println("DEBUG Handler: askCred skipping promise " + promise.getPromiseID() + " - I am not the creditor");
                    continue;
                }

                // in the signed store, do NOT overwrite it with the older unsigned version.
                CharSequence promiseId = promise.getPromiseID();
                if (this.currencyStorage.containsSignedPromise(promiseId)) {
                    System.out.println("DEBUG Handler: askCred skipping promise " + promiseId
                            + " - already in signed storage");
                    continue;
                }

                this.currencyStorage.addSharkPendingPromiseToStorage(promise);
                System.out.println("DEBUG Handler: askCred stored promise id=" + promise.getPromiseID());
                System.out.println("DEBUG Handler: askCred the creditor is: " + promise.getCreditorID());
            } catch (ASAPException e) {
                System.out.println("DEBUG Handler: skipping message (not for me): " + e.getMessage());
            } catch (ClassNotFoundException | IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
