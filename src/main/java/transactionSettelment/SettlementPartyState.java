package transactionSettelment;

public enum SettlementPartyState {
    GATHERING, // Wait for answer from all peers
    READY,     // all Peers answered with their promises
    CANCELLED  // Timeout or Error
}
