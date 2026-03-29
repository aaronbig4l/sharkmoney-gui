package listener;

import net.sharksystem.asap.ASAPException;
import net.sharksystem.asap.ASAPMessages;
import net.sharksystem.pki.SharkPKIComponent;

import java.io.IOException;

public class SharkPromiseResponseForDebtSattledHandler implements SharkCurrencyMessageHandler{
    @Override
    public void handle(CharSequence uri, ASAPMessages messages, SharkPKIComponent pki, CharSequence receiver) throws IOException, ASAPException {

    }
}
