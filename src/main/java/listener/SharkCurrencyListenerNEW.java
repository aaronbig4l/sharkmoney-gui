package listener;

import exepections.SharkPromiseException;
import net.sharksystem.asap.ASAPException;
import net.sharksystem.asap.ASAPMessages;

import java.io.IOException;

public interface SharkCurrencyListenerNEW {
    void sharkCurrencyMessageReceived(CharSequence uri, ASAPMessages messages)
            throws ASAPException, IOException, SharkPromiseException;

}
