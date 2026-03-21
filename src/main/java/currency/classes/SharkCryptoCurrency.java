package currency.classes;

import exepections.SharkCurrencyException;
import net.sharksystem.asap.ASAPException;
import net.sharksystem.utils.SerializationHelper;

import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A Crypto based Currency for Shark Peers.
 * Extends the basic concept of a trust based currency by an exchange rate to a Crypto currency (e.g. 0.01 ETH)
 */
public class SharkCryptoCurrency implements SharkCurrency, Serializable {

    private static final long serialVersionUID = 1L;
    private static final String BACKING_CRYPTO_TYPE = "ETH"; // Fixed Crypto Type for our project

    private boolean globalLimit;
    private String currencyName;
    private String specification;
    private  byte[] id;
    private double exchangeRate;

    public SharkCryptoCurrency(boolean globalLimit, String currencyName, String specification, double exchangeRate) {
        this.globalLimit = globalLimit;
        this.currencyName = currencyName;
        this.specification = specification;
        this.id = UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8);
        this.exchangeRate = exchangeRate;
    }

    private SharkCryptoCurrency(byte[] id, boolean globalLimit, String currencyName, String specification, double exchangeRate){
        this.id = id;
        this.globalLimit = globalLimit;
        this.currencyName = currencyName;
        this.specification = specification;
        this.exchangeRate = exchangeRate;
    }

    @Override
    public byte[] toByte() throws ASAPException, IOException, SharkCurrencyException {
        List<CharSequence> currencyVariables = new ArrayList<>();

        currencyVariables.add(new String(this.id, StandardCharsets.UTF_8));
        currencyVariables.add(this.currencyName);
        currencyVariables.add(this.specification);
        currencyVariables.add(String.valueOf(this.globalLimit));

        // Wir schicken "ETH" trotzdem mit, für zukünftige Kompatibilität
        currencyVariables.add(BACKING_CRYPTO_TYPE);
        currencyVariables.add(String.valueOf(this.exchangeRate));

        if(currencyVariables.size() == 6) {
            String serializedString = SerializationHelper.collection2String(currencyVariables);
            return SerializationHelper.str2bytes(serializedString);
        } else {
            throw new SharkCurrencyException("Failure serializing currency to byte");
        }
    }

    public static SharkCryptoCurrency fromByte(byte[] data) throws IOException, SharkCurrencyException {
        if (data == null) return null;

        String dataString = SerializationHelper.bytes2str(data);
        List<CharSequence> currencyVariables = SerializationHelper.string2CharSequenceList(dataString);

        if (currencyVariables.size() < 6) {
            throw new SharkCurrencyException("Invalid currency format: expected 6 parts, got " + currencyVariables.size());
        }

        byte[] id = SerializationHelper.characterSequence2bytes(currencyVariables.get(0));
        String name = currencyVariables.get(1).toString();
        String spec = currencyVariables.get(2).toString();
        boolean limit = parseBoolean(currencyVariables.get(3));
        String cryptoType = currencyVariables.get(4).toString();
        double rate = Double.parseDouble(currencyVariables.get(5).toString());

        return new SharkCryptoCurrency(id, limit, name, spec, rate);
    }

    private static boolean parseBoolean(CharSequence cs) {
        if (cs == null || cs.length() != 4) return false;
        return cs.toString().equalsIgnoreCase("true");
    }

    @Override
    public byte[] getCurrencyId() {
        return this.id;
    }

    @Override
    public String getCurrencyName() {
        return this.currencyName;
    }

    @Override
    public String getSpecification() {
        return this.specification;
    }

    @Override
    public Boolean hasGlobalLimit() {
        return this.globalLimit;
    }

    public String getBackingCryptoType() {
        return BACKING_CRYPTO_TYPE;
    }

    public double getExchangeRate(){
        return this.exchangeRate;
    }
}
