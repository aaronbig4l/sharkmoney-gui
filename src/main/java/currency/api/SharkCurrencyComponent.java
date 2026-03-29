package currency.api;


import blockchain.wallet.WalletManager;
import currency.classes.SharkCurrency;
import currency.classes.SharkPromise;
import currency.storage.SharkCurrencyStorage;
import exepections.SharkCurrencyException;
import listener.SharkCurrencyListener;
import net.sharksystem.ASAPFormats;
import net.sharksystem.SharkComponent;
import net.sharksystem.asap.ASAPException;
import net.sharksystem.asap.ASAPSecurityException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Set;


/**
 * A SharkCurrency is a local trust based currency System for SharkPeers.
 * It can be used to create open whitelisted groups between SharkPeers to exchange currency.
 * A Group is always created by one SharkPeer and includes the creation of a new local currency.
 * The currency is always bound to the group and can only be exchanged between its members.
 *
 */
@ASAPFormats(formats = {SharkCurrencyComponent.CURRENCY_FORMAT})
public interface SharkCurrencyComponent extends SharkComponent {

    /**
     * Shark-Currency URI format
     */
    public static final String CURRENCY_FORMAT = "application://x-asap-currency";
    public final String INVITE_CHANNEL_URI = "//group-document//invite";
    public final String NEW_MEMBER_URI = "//group-document//new-member";
    public final String SETTLEMENT_URI = "//group-document//settlement";
    public final String SHARK_PROMISE_ASK_DEBT_SETTLED = "//group-document//ask-debt-settled";

    public final String SHARK_PROMISE_RESPONSE_DEBT_SETTLED =  "recSignedDebtSettled//response-debt-settled";

    /**
     * Establishes a new currency group with specific configuration.
     * * Mapping to ASAP concepts:
     * - This creates an ASAP Channel with the URI based on currency.getName().
     * - If 'whitelisted' is pass, this indicates a Closed Channel scenario.
     * - If 'encrypted' is true, the channel messages should be encrypted (requires exchange of keys).
     * * @param currency       The currency object containing name and metadata.
     *
     * @param whitelisted    It stands the different peers, which are allowed to communicate with each other
     * @param encrypted      If true, the communication within this group will be encrypted.
     * @param balanceVisible If true, members are allowed to see the balances of others (application logic).
     * @throws SharkCurrencyException If the group/channel cannot be established.
     * @return the groupId
     */
    byte[] establishGroup(SharkCurrency currency, ArrayList<CharSequence> whitelisted, boolean encrypted, boolean balanceVisible)
            throws SharkCurrencyException;


    /**
     * Establishes a new currency group with specific configuration.
     * * Mapping to ASAP concepts:
     * - This creates an ASAP Channel with the URI based on currency.getName().
     * - If 'encrypted' is true, the channel messages should be encrypted (requires exchange of keys).
     * * @param currency       The currency object containing name and metadata.
     *
     * @param encrypted      If true, the communication within this group will be encrypted.
     * @param balanceVisible If true, members are allowed to see the balances of others (application logic).
     * @throws SharkCurrencyException If the group/channel cannot be established.
     * @return the groupId
     */
    byte[] establishGroup(SharkCurrency currency, boolean encrypted, boolean balanceVisible)
            throws SharkCurrencyException;

    /**
     * Establishes a new currency group with specific configuration.
     * * Mapping to ASAP concepts:
     * - This version of the method offers the possibility to invite initial members
     * - This creates an ASAP Channel with the URI based on currency.getName().
     * - If 'whitelisted' is pass, this indicates a Closed Channel scenario.
     * - If 'encrypted' is true, the channel messages should be encrypted (requires exchange of keys).
     * * @param currency       The currency object containing name and metadata.
     *
     * @param inviteMembers  List of group members who will automatically be invited
     * @param whitelisted    It stands the different peers, which are allowed to communicate with each other
     * @param encrypted      If true, the communication within this group will be encrypted.
     * @param balanceVisible If true, members are allowed to see the balances of others (application logic).
     * @throws SharkCurrencyException If the group/channel cannot be established.
     * @return the groupId
     */
    byte[] establishGroup(ArrayList<CharSequence> inviteMembers, SharkCurrency currency, ArrayList<CharSequence> whitelisted, boolean encrypted, boolean balanceVisible)
            throws SharkCurrencyException;

    /**
     * Establishes a new currency group with specific configuration.
     * * Mapping to ASAP concepts:
     * - This version of the method offers the possibility to invite initial members
     * - This creates an ASAP Channel with the URI based on currency.getName().
     * - If 'encrypted' is true, the channel messages should be encrypted (requires exchange of keys).
     * * @param currency       The currency object containing name and metadata.
     *
     * @param inviteMembers  List of group members who will automatically be invited
     * @param encrypted      If true, the communication within this group will be encrypted.
     * @param balanceVisible If true, members are allowed to see the balances of others (application logic).
     * @throws SharkCurrencyException If the group/channel cannot be established.
     * @return the groupId
     */
    byte[] establishGroup(ArrayList<CharSequence> inviteMembers, SharkCurrency currency, boolean encrypted, boolean balanceVisible)
            throws SharkCurrencyException;

    /**
     * It is used for to Invite Members to a Group which has no invited Members as a list.
     * You can send Members one by one or multiple Members at once.
     * @param groupId Id of the group
     * @param optionalMessage An optional message can be added to an invitation
     * @param peerId ID of the peer you want to invite
     *
     */
    void invitePeerToGroup(byte[] groupId, String optionalMessage, CharSequence peerId)
            throws SharkCurrencyException;

    /**
     * Sends a specific amount of Currency to another peer.
     * Creates a transaction, serializes it, signs it and adds it to the ASAP channel.
     * @param promiseId Id of the promise.
     * @param fromPendingStorage true when the promise is send and not fully signed
     * @param sender ID of sender
     * @param receiver The ASAP Peer IDs of the recipients.
     * @param sign true if this promise should be signed by sender
     * @param encrypt true if this prmoise should be encrypted
     * @param uri the uri of this promise
     * @throws SharkCurrencyException If the Promise cannot be sent due to error.
     */
    void sendPromise(CharSequence promiseId, Boolean fromPendingStorage, CharSequence sender, Set<CharSequence> receiver, boolean sign, boolean encrypt, CharSequence uri)
            throws ASAPException, IOException;

    /**
     * creates a SharkPromise object
     *
     * @param amount the amount of the currency
     * @param referenceValue a Shark Currency which this promise references
     * @param creditorId id of creditor
     * @param debtorId id of debitor
     * @param asCreditor is this bond created with you as the creditor true/false
     * @return promiseId id of created promise
     */
    CharSequence createPromise(int amount,
                               SharkCurrency referenceValue,
                               byte[] groupId,
                               CharSequence creditorId,
                               CharSequence debtorId,
                               boolean asCreditor) throws ASAPException, IOException;


    void signPromiseAndSendBack(CharSequence promiseId);


    /**
     * This method is if you want to settle a Debt from a Promise
     * @param promiseID the promise what you want to settle
     * @throws ASAPException
     * @throws IOException
     */
    CharSequence askFoDebtSettled(CharSequence promiseID) throws ASAPException, IOException;

    /**
     * Calculates the current balance for the local user in the specified currency.
     *
     * @param currencyId The id of the currency.
     * @return The current balance.
     * @throws SharkCurrencyException If the history cannot be read.
     */
    int getBalance(byte[] currencyId) throws SharkCurrencyException;


    int getExtendedBalance(byte[] currencyId, CharSequence peerId) throws SharkCurrencyException;


    /**
     * this will execute the acceptInvite() method from the listener but first
     * it signs the document which currency name is given in the parameter :D
     *
     * @param currencyName name of the currency for the group
     */
    void acceptInviteAndSign(CharSequence currencyName) throws SharkCurrencyException, ASAPSecurityException, ASAPException, IOException;


    /**
     * Add Balance to your Account (also negative Promise)
     * @param promise
     */
    void addBalance(SharkPromise promise);


    /**
     * Transfers an existing promise to a new peer. Evaluates the role of the 
     * executing peer (Creditor/Debtor) and checks the respective modification permissions.
     * If permitted, modifies the promise, signs it newly, and updates the storage.
     *
     * @param promiseId The ID of the promise to transfer.
     * @param newPeerId The ID of the new peer taking over the role.
     * @throws SharkCurrencyException if permissions are missing, the promise doesn't exist, or cryptographic operations fail.
     */
    void transferPromiseToAnotherPeer(CharSequence promiseId, CharSequence newPeerId) throws SharkCurrencyException;

    /**
     * Starts a Settlement Party for a specific group to rewrite debts.
     * Collects the peer's own promises and start a GATHERING phase by
     * broadcasting a SharkSettlementDocument.
     * @param groupId Id of an existing Group to settle.
     * @return The unique Party ID of the created settlement party
     */
    byte[] initiateSettlementParty(byte[] groupId);


    /**
     * declines an invitation to a group,
     * it will remove the invite form pending invites in storage
     *
     * @param currencyName name of the group currency you were invited from
     */
    void declineInvite(CharSequence currencyName);

    /**
     * returns the shark currency storage
     *
     * @return SharkCurrencyStorage of the Peer
     */
    SharkCurrencyStorage getSharkCurrencyStorage();

    /**
     * subscribes a listener to this component
     * @param listener the listener that will be subscribed
     */
    void subscribeSharkCurrencyListener(SharkCurrencyListener listener);

    /**
     * Get the Ethereum Wallet Address of the Peer
     * @return ethereum Address as String
     */
    String getWalletAddress();

    /**
     * Get the Ethereum Wallet of the Peer
     * @return ethereum wallet
     */
    WalletManager getWallet();
}