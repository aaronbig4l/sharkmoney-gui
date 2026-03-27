package transactionSettelment;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SharkSettlementDocument {

    private final byte[] groupId;

    // Maps a PeerID (the one who is delivering the data) to his local view of balances
    private final Map<CharSequence, Map<CharSequence, Integer>> allPeerBalances;

    // Maps a PeerID to his cryptographic signature
    private final Map<CharSequence, byte[]> signatures;

    public SharkSettlementDocument(byte[] groupId) {
        this.groupId = groupId;
        this.allPeerBalances = new HashMap<>();
        this.signatures = new HashMap<>();
    }

    /**
     * Adds the local balance map of a Peer to the Document
     * @param peerId Peer who is adding the data
     * @param localBalances Balance map showing the guilt relationship to other peers from his perspective
     * @param signature cryptographic signatur of the Peer
     */
    public void addPeerData(CharSequence peerId, Map<CharSequence, Integer> localBalances, byte[]signature) {
        this.allPeerBalances.put(peerId.toString(), new HashMap<>(localBalances));
        this.signatures.put(peerId.toString(), signature);
    }

    /**
     * Check if all Group Member added their data
     * @param groupMembers List containing all Peers in a specific group
     * @return true, if all Peers of the group added their data
     */
    public boolean isFullySigned(List<CharSequence> groupMembers) {
        for (CharSequence member : groupMembers) {
            if (!signatures.containsKey(member.toString())) {
                return false;
            }
        }
        return true;
    }

    public byte[] getGroupId() {
        return groupId;
    }

    public Map<CharSequence, Map<CharSequence, Integer>> getAllPeerBalances() {
        return allPeerBalances;
    }

    public Map<CharSequence, byte[]> getSignatures() {
        return signatures;
    }

    public Set<CharSequence> getParticipatingPeers() {
        return signatures.keySet();
    }
}
