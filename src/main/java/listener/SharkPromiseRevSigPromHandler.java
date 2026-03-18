package listener;

import currency.storage.SharkCurrencyStorage;
import net.sharksystem.asap.ASAPStorage;
import net.sharksystem.pki.SharkPKIComponent;

public class SharkPromiseRevSigPromHandler implements SharkCurrencyMessageHandler {

    private SharkCurrencyStorage currencyStorage;

    public SharkPromiseRevSigPromHandler(SharkCurrencyStorage currencyStorage) {
        this.currencyStorage=currencyStorage;
    }

    public void handle(CharSequence uri, ASAPStorage storage, SharkPKIComponent pki, CharSequence sender) {
        try {

        }
    }
}
