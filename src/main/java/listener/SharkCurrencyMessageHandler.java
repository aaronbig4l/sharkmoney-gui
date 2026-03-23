package listener;

import net.sharksystem.asap.ASAPException;
import net.sharksystem.asap.ASAPMessages;
import net.sharksystem.asap.ASAPStorage;
import net.sharksystem.pki.SharkPKIComponent;

import java.io.IOException;

/**
 * This Interface handles individual message types within the SharkCurrency Application.
 * For example.
 * You want to handle a group invite differently than being asked to sign a promise
 */
public interface SharkCurrencyMessageHandler {

    /**
     * logic for handling different kinds of messages will be implemented here
     *
     * @param uri uri of the message which tells us the type of message
     * @param messages
     * @param pki
     * @param receiver
     */
    void handle(CharSequence uri, ASAPMessages messages, SharkPKIComponent pki, CharSequence receiver) throws IOException, ASAPException;
}
