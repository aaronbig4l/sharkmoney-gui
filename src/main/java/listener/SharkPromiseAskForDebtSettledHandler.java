package listener;

import currency.classes.SharkPromise;
import currency.classes.SharkPromiseSerializer;
import currency.storage.SharkCurrencyStorage;
import net.sharksystem.asap.ASAPException;
import net.sharksystem.asap.ASAPMessages;
import net.sharksystem.pki.SharkPKIComponent;
import org.web3j.abi.datatypes.primitive.Char;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

public class SharkPromiseAskForDebtSettledHandler implements SharkCurrencyMessageHandler{

    private SharkCurrencyStorage currencyStorage;

    public SharkPromiseAskForDebtSettledHandler(SharkCurrencyStorage currencyStorage) {
        this.currencyStorage=currencyStorage;
    }

    @Override
    public void handle(CharSequence uri, ASAPMessages messages, SharkPKIComponent pki, CharSequence receiver) throws IOException, ASAPException {
        System.out.println("DEBUG: received a message being asked to sign as creditor from: "
                + receiver);
        for (int i = 0; i < messages.size(); i++) {
            try {
                byte[] messageData = messages.getMessage(i, true);
                ByteArrayInputStream bais = new ByteArrayInputStream(messageData);
                DataInputStream dis = new DataInputStream(bais);
                CharSequence promiseID = dis.readUTF();



            } catch (ASAPException e) {
                System.out.println("DEBUG Handler: skipping message (not for me): " + e.getMessage());
            } catch ( IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
    }
