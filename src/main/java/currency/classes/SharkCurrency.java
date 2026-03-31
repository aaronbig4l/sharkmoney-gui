package currency.classes;

import exepections.SharkCurrencyException;
import net.sharksystem.asap.ASAPException;

import java.io.IOException;

/**
 * The Currency interface represents a currency.
 * It can be implemented by classes that represent a specific currency.
 */
public interface SharkCurrency {

    int MAX_PROMISES_UPPER_BOUND = 1_000_000;

    /**
     * Returns the ID of the currency.
     * @return the ID of the currency
     */
    byte[] getCurrencyId();

    /**
     * Returns the name of the currency.
     * @return the name of the currency
     */
    String getCurrencyName();

    /**
     * Returns the specification of the currency.
     * @return the specification of the currency
     */
    String getSpecification();

    /**
     * Returns whether the currency has a global limit.
     * @return true if the currency has a global limit, false otherwise
     */
    Boolean hasGlobalLimit();

    /**
     * Turns the Currency object into a byte[] for serialization purposes
     * @return the byte series of this currency object
     */
    byte[] toByte() throws SharkCurrencyException, IOException, ASAPException, Exception;

    /**
     * Returns the maximum amount of promises everyone in this group can create,
     * if 0 -> no global limit
     * if below 0 -> error
     * @return int amount
     */
    int getMaxPromiseAmount();

}
