package listener;

import exepections.SharkPromiseException;
import net.sharksystem.asap.ASAPException;
import net.sharksystem.asap.ASAPMessages;

import java.io.IOException;

public interface SharkCurrencyListener {
    void sharkCurrencyMessageReceived(CharSequence uri, ASAPMessages messages)
            throws ASAPException, IOException, SharkPromiseException;

}
