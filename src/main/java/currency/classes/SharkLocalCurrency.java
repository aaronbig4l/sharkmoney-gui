package currency.classes;

import exepections.SharkCurrencyException;
import net.sharksystem.asap.ASAPException;
import net.sharksystem.utils.SerializationHelper;

import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 *
 */
public class SharkLocalCurrency implements SharkCurrency, Serializable {

    private static final long serialVersionUID = 2L;

    private final String currencyName;
    private final String specification;
    private final byte[] id;
    private final int maxPromises;


    public SharkLocalCurrency(String currencyName, String specification) {
        this(UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8),
                0, currencyName, specification);
    }

    public SharkLocalCurrency(int maxPromises, String currencyName, String specification) {
        this(UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8),
                validateMaxPromises(maxPromises), currencyName, specification);
    }

    private SharkLocalCurrency(byte[] id, int maxPromises, String currencyName, String specification) {
        checkParameters(maxPromises,currencyName,specification);
        this.id = id;
        this.maxPromises = maxPromises;
        this.currencyName = currencyName;
        this.specification = specification;
    }

    private static int validateMaxPromises(int maxPromises) {
        if(maxPromises < 1 || maxPromises > MAX_PROMISES_UPPER_BOUND) {
            throw new IllegalArgumentException(
                    "maxPromises must be between 1 and "+MAX_PROMISES_UPPER_BOUND+", got: " + maxPromises);
        }
        return maxPromises;
    }

    private static void checkParameters(int maxPromises, String currencyName, String specification) {
        if(maxPromises<0||maxPromises>MAX_PROMISES_UPPER_BOUND) {
            throw new IllegalArgumentException("maxPromises must be between 1 and " + MAX_PROMISES_UPPER_BOUND +", got: " + maxPromises);
        }
        if(currencyName.length()>30|| currencyName.isEmpty()) {
            throw new IllegalArgumentException("The name of the Currency must be between 1 and 30 characters long, got: " + currencyName.length());
        }
        if(specification.length()>100) {
            throw new IllegalArgumentException("The specification of the Currency can't be longer than 100 character, got: " + specification.length());
        }
    }

    public byte[] toByte() throws ASAPException, IOException {
        List<CharSequence> currencyVariables = new ArrayList<>(); //6

        currencyVariables.add(new String(this.id, StandardCharsets.UTF_8));
        currencyVariables.add(this.currencyName);
        currencyVariables.add(this.specification);
        currencyVariables.add(String.valueOf(this.maxPromises));

        if(currencyVariables.size()==4) {
            String serializedString = SerializationHelper.collection2String(currencyVariables);
            return SerializationHelper.str2bytes(serializedString);
        } else {
            throw new SharkCurrencyException("Failure serializing currency to byte");
        }
    }

    public static SharkLocalCurrency fromByte(byte[] data) throws IOException, SharkCurrencyException {
        if (data == null) return null;

        String dataString = SerializationHelper.bytes2str(data);
        List<CharSequence> currencyVariables = SerializationHelper.string2CharSequenceList(dataString);

        if (currencyVariables.size() < 4) {
            throw new SharkCurrencyException("Invalid currency format: expected 4 parts, got " + currencyVariables.size());
        }

        byte[] id = SerializationHelper.characterSequence2bytes(currencyVariables.get(0));
        String name = currencyVariables.get(1).toString();
        String spec = currencyVariables.get(2).toString();
        int maxProm = Integer.parseInt(currencyVariables.get(3).toString());

        return new SharkLocalCurrency(id, maxProm, name, spec);
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
        return this.maxPromises > 0;
    }

    @Override
    public int getMaxPromiseAmount() { return this.maxPromises; }
}
