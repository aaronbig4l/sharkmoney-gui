package transactionSettelment;

public enum SettlementPartyState {
    GATHERING, // Wait for answer from all peers
    VERIFYING,     // all Peers answered with their promises and start verifying their results (as Hash value)
    COMPLETED, // All Hashes are equal. Start of debt rewriting
    CANCELLED  // Timeout or Error
}
