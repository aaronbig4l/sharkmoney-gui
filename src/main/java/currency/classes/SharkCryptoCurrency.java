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

    private final int maxPromises;
    private final String currencyName;
    private final String specification;
    private final byte[] id;
    private final double exchangeRate;


    public SharkCryptoCurrency(String currencyName, String specification, double exchangeRate) {
        this(UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8),
                0, currencyName, specification, exchangeRate);
    }

    public SharkCryptoCurrency(int maxPromises, String currencyName, String specification, double exchangeRate) {
        this(UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8),
                validateMaxPromises(maxPromises), currencyName, specification, exchangeRate);
    }

    private SharkCryptoCurrency(byte[] id, int maxPromises, String currencyName, String specification, double exchangeRate){

        checkParameters(maxPromises,currencyName,specification, exchangeRate);
        this.id = id;
        this.maxPromises = maxPromises;
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
        currencyVariables.add(String.valueOf(this.maxPromises));

        
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
        int maxProm = Integer.parseInt(currencyVariables.get(3).toString());
        String cryptoType = currencyVariables.get(4).toString();
        double rate = Double.parseDouble(currencyVariables.get(5).toString());

        return new SharkCryptoCurrency(id, maxProm, name, spec, rate);
    }

    private static int validateMaxPromises(int maxPromises) {
        if(maxPromises < 1 || maxPromises > MAX_PROMISES_UPPER_BOUND) {
            throw new IllegalArgumentException(
                    "maxPromises must be between 1 and "+MAX_PROMISES_UPPER_BOUND+", got: " + maxPromises);
        }
        return maxPromises;
    }

    private static void checkParameters(int maxPromises, String currencyName, String specification, double exchangeRate) {
        if(maxPromises<0||maxPromises>MAX_PROMISES_UPPER_BOUND) {
            throw new IllegalArgumentException("maxPromises must be between 1 and " + MAX_PROMISES_UPPER_BOUND +", got: " + maxPromises);
        }
        if(currencyName.length()>30|| currencyName.isEmpty()) {
            throw new IllegalArgumentException("The name of the Currency must be between 1 and 30 characters long, got: " + currencyName.length());
        }
        if(specification.length()>100) {
            throw new IllegalArgumentException("The specification of the Currency can't be longer than 100 character, got: " + specification.length());
        }
        if(exchangeRate<0 || Double.isNaN(exchangeRate)) {
            throw new IllegalArgumentException("Exchange rate is not eligible");
        }
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
        return this.maxPromises>0;
    }

    public String getBackingCryptoType() {
        return BACKING_CRYPTO_TYPE;
    }

    public double getExchangeRate(){
        return this.exchangeRate;
    }

    @Override
    public int getMaxPromiseAmount() { return this.maxPromises; }
}
