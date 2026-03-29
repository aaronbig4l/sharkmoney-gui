package transactionSettelment;

/**
 * Represents te different states of a Settlement Party.
 * The state dictates which action a peer needs to take next for the party to settle.
 */
public enum SettlementPartyState {
    GATHERING, // Wait for answer (prmomises) from all peers
    VERIFYING,     // All Peers answered with their Promises. Calculate the reducing result and exchange as Hash value.
    COMPLETED, // All Result Hashes are equal. Send the new settled Promises and let them sign.
    CANCELLED  // Settlement Party failed: Timeout or Error
}
