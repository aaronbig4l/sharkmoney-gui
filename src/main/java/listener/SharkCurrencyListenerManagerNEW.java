package listener;

import exepections.SharkPromiseException;
import net.sharksystem.asap.ASAPException;
import net.sharksystem.asap.ASAPMessages;
import net.sharksystem.asap.listenermanager.GenericNotifier;
import net.sharksystem.asap.listenermanager.GenericListenerImplementation;

import java.io.IOException;

public class SharkCurrencyListenerManagerNEW
        extends GenericListenerImplementation<SharkCurrencyListenerNEW> {

    public void addSharkCurrencyListener(SharkCurrencyListenerNEW listener) {
        this.addListener(listener);
    }

    public void removeSharkCurrencyListener(SharkCurrencyListenerNEW listener) {
        this.removeListener(listener);
    }

    protected void notifySharkCurrencyListener(
            CharSequence uri, ASAPMessages messages) {
        System.out.println("DEBUG: notifySharkCurrencyListener called for uri: " + uri);
        SharkCurrencyNotifier sharkCurrencyNotifier =
                new SharkCurrencyNotifier(uri, messages);

        this.notifyAll(sharkCurrencyNotifier, false);
    }

    private class SharkCurrencyNotifier implements GenericNotifier<SharkCurrencyListenerNEW> {
        private final CharSequence uri;
        private ASAPMessages messages;

        public SharkCurrencyNotifier(CharSequence uri, ASAPMessages messages) {
            this.uri = uri;
            this.messages=messages;
        }

        @Override
        public void doNotify(SharkCurrencyListenerNEW sharkMessagesReceivedListener) {
            try {
                sharkMessagesReceivedListener.sharkCurrencyMessageReceived(this.uri, this.messages);
            } catch (ASAPException | IOException e) {
                e.printStackTrace();
            }
        }
    }
}
