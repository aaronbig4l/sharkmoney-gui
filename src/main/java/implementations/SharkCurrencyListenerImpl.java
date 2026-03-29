package implementations;

import currency.api.SharkCurrencyComponent;
import currency.classes.SharkPromise;
import currency.storage.SharkCurrencyStorage;
import exepections.SharkCurrencyException;
import listener.*;
import net.sharksystem.asap.ASAPException;
import net.sharksystem.asap.ASAPMessages;
import net.sharksystem.pki.SharkPKIComponent;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class SharkCurrencyListenerImpl implements SharkCurrencyListenerNEW {

    private final SharkCurrencyComponentImpl sharkCurrencyComponent;
    private final Map<String, SharkCurrencyMessageHandler> handlers = new HashMap<>();

    public SharkCurrencyListenerImpl(SharkCurrencyComponent sharkCurrencyComponent) {

        this.sharkCurrencyComponent = (SharkCurrencyComponentImpl) sharkCurrencyComponent;
        SharkCurrencyStorage storage = sharkCurrencyComponent.getSharkCurrencyStorage();

        //Promise handler
        handlers.put(SharkPromise.SHARK_PROMISE_RECEIVE_SIGNED_SIG,
                new SharkPromiseRevSigPromHandler(storage, this.sharkCurrencyComponent));
        handlers.put(SharkPromise.SHARK_PROMISE_ASK_FOR_SIGNATURE_AS_CRED,
                new SharkPromiseAskSigCredHandler(storage));
        handlers.put(SharkPromise.SHARK_PROMISE_ASK_FOR_SIGNATURE_AS_DEB,
                new SharkPromiseAskSigDebHandler(storage));

        //Group handler
        handlers.put(SharkCurrencyComponent.INVITE_CHANNEL_URI,
                new SharkGroupInviteHandler(storage,
                        this.sharkCurrencyComponent.getPeerIdOfImpl().toString()));
        handlers.put(SharkCurrencyComponent.NEW_MEMBER_URI,
                new SharkNewMemberHandler(storage));

        //Settlement Hanlder
        handlers.put(SharkCurrencyComponent.SETTLEMENT_URI,
                new SharkSettlementHandler(this.sharkCurrencyComponent));
    }

    @Override
    public void sharkCurrencyMessageReceived(CharSequence uri, ASAPMessages messages) {
        try {
            SharkCurrencyMessageHandler handler = handlers.get(uri.toString());
            if(handler==null) {
                throw new SharkCurrencyException("Could not find uri: " + uri);
            }
            System.out.println("DEBUG Listener: uri=" + uri);
            System.out.println("DEBUG Listener: handler found=" + (handlers.get(uri.toString()) != null));
            System.out.println("DEBUG Listener: all registered keys=" + handlers.keySet());
            SharkPKIComponent pki = this.sharkCurrencyComponent.getSharkPKIComponent();
            handler.handle(uri,messages,pki, this.sharkCurrencyComponent.getPeerIdOfImpl());
        } catch (IOException | ASAPException e) {
            throw new RuntimeException(e);
        }
    }

}
