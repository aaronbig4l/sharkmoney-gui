package currency.classes;

import exepections.SharkPromiseException;

import java.io.Serializable;
import java.util.Calendar;
import java.util.UUID;

public class SharkInMemoPromise implements SharkPromise, Serializable {

    private static final long serialVersionUID = 1L;

    private final int amount;
    private final SharkCurrency referenceValue;
    private final byte[] groupId;
    private boolean allowedToChangeDebtor, allowedToChangeCreditor;
    private long expirationDate;
    private CharSequence promiseID;
    private CharSequence debtorID, creditorID;
    private byte[] debtorSignature, creditorSignature;
    private SharkPromiseState promiseState;

    public SharkInMemoPromise(SharkPromise promise) {
        this(promise.getPromiseID(), promise.getAmount(),
                promise.getReferenceValue(), promise.getGroupIDOfPromise(),
                promise.allowedToChangeCreditor(), promise.allowedToChangeDebtor(),
                promise.getExpirationDate(), promise.getCreditorID(), promise.getDebtorID(),
                promise.getCreditorSignature(), promise.getDebtorSignature());
    }

    public SharkInMemoPromise(int amount,
                              SharkCurrency referenceValue,
                              byte[] groupId,
                              CharSequence creditorId,
                              CharSequence debtorId) {
        this(generatePromiseID(), amount,
                referenceValue, groupId,
                true, true,
                getDefaultExpirationDate(), creditorId, debtorId,
                null, null);
    }

    public SharkInMemoPromise(CharSequence promiseID,
                              int amount,
                              SharkCurrency referenceValue,
                              byte[] groupId,
                              boolean allowedToChangeCreditor,
                              boolean allowedToChangeDebtor,
                              long expirationDate,
                              CharSequence creditorID,
                              CharSequence debtorID,
                              byte[] creditorSignature,
                              byte[] debtorSignature) {
        this.promiseID=promiseID;
        this.amount=amount;
        this.referenceValue=referenceValue;
        this.groupId=groupId;
        this.allowedToChangeCreditor=allowedToChangeCreditor;
        this.allowedToChangeDebtor=allowedToChangeDebtor;
        this.expirationDate=expirationDate;
        this.creditorID=creditorID;
        this.debtorID=debtorID;
        this.creditorSignature=creditorSignature;
        this.debtorSignature=debtorSignature;
        this.promiseState= SharkPromiseState.UNSIGNED;
    }

    @Override
    public CharSequence getPromiseID() {
        return this.promiseID;
    }

    @Override
    public byte[] getGroupIDOfPromise() {
        return this.groupId;
    }

    @Override
    public CharSequence getCreditorID() {
        return this.creditorID;
    }

    @Override
    public CharSequence getDebtorID() {
        return this.debtorID;
    }

    @Override
    public int getAmount() {
        return this.amount;
    }

    @Override
    public SharkCurrency getReferenceValue() {
        return this.referenceValue;
    }

    @Override
    public boolean allowedToChangeCreditor() {
        return this.allowedToChangeCreditor;
    }

    @Override
    public boolean allowedToChangeDebtor() {
        return this.allowedToChangeDebtor;
    }

    @Override
    public long getExpirationDate() {
        return this.expirationDate;
    }

    @Override
    public byte[] getCreditorSignature() {
        return this.creditorSignature;
    }

    @Override
    public byte[] getDebtorSignature() {
        return this.debtorSignature;
    }

    @Override
    public void setAllowedToChangeCreditor(boolean allowed) throws SharkPromiseException {
        this.allowedToChangeCreditor=allowed;
    }

    @Override
    public void setAllowedToChangeDebtor(boolean allowed) throws SharkPromiseException {
        this.allowedToChangeDebtor=allowed;
    }

    @Override
    public void setCreditorSignature(byte[] signature) {
        this.creditorSignature=signature;
    }

    @Override
    public void setDebtorSignature(byte[] signature) {
        this.debtorSignature=signature;
    }

    @Override
    public SharkPromiseState getStateOfPromise() {
        return this.promiseState;
    }

    @Override
    public void setStateOfPromise(SharkPromiseState newState) {
        this.promiseState=newState;
    }

    @Override
    public void updateState() {

        if (this.promiseState == SharkPromiseState.ANULLED) { return; }

        boolean hasDebtor = this.debtorSignature != null && this.debtorSignature.length > 0;
        boolean hasCreditor = this.creditorSignature != null && this.creditorSignature.length > 0;

        if (!hasDebtor && !hasCreditor) {
            setStateOfPromise(SharkPromiseState.UNSIGNED);
        } else if (!hasDebtor && hasCreditor) {
            setStateOfPromise(SharkPromiseState.SIGNED_BY_CREDITOR);
        } else if (hasDebtor && !hasCreditor) {
            setStateOfPromise(SharkPromiseState.SIGNED_BY_DEBITOR);
        } else {
            setStateOfPromise(SharkPromiseState.FULLY_SIGNED);
        }
    }

    @Override
    public void setCreditor(CharSequence peerId) {
        if (allowedToChangeCreditor) {
            this.creditorID=peerId;
        }

    }

    @Override
    public void setDebtor(CharSequence peerId) {
        if (allowedToChangeDebtor) {
            this.debtorID=peerId;
        }
    }

    /**
     * generates a new unique ID for this Promise
     * @return the unique UUID converted to a String
     */
    private static CharSequence generatePromiseID() {
        UUID uuid = UUID.randomUUID();
        return uuid.toString();
    }

    private static long getDefaultExpirationDate() {
        Calendar until = Calendar.getInstance();
        until.add(Calendar.YEAR, 1); // expires by default in 1 Year
        return until.getTimeInMillis();
    }
}
