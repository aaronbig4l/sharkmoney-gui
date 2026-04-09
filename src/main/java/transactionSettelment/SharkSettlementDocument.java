package transactionSettelment;

import net.sharksystem.utils.SerializationHelper;
import org.web3j.protocol.core.methods.response.EthLog;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.*;

/**
 * Represents the shared state document of a Settlement Party in the Shark Network.
 * A SharkSettlementDocument is created by the initializing peer and then gossiped
 * across the group. Each peer contributes their promises of the group.
 * Once all promises are collected, a hash of their locally computed settlement result
 * will be added and compared with all other participating peers.
 * When the hashes match, consensus is reached.
 */
public class SharkSettlementDocument {

    // Serialization constants
    private static final String EMPTY_PLACEHOLDER = "NULL";
    private static final String EMPTY = "EMPTY";
    private static final String LIST_DELIMITER = ":::";
    private static final String SET_DELIMITER = ",";

    private final byte[] partyId; // ID to identify the settlement party instance
    private final byte[] groupId; // GroupID this settlement belongs
    private final CharSequence initiatorId; // The peer who initialized this settlement party
    private final Set<CharSequence> expectedPeers; // Set of Peers that are expected to participate (usually all group members)
    private final Set<CharSequence> submittedPeers; // Set of Peers that habe already submitted their Promises
    private SettlementPartyState state; // current lifecycle of this settlement party
    private final long createdAt; // timestamp when the party was created
    private final long expiresAt; // timestamp at which the Settlement Party expires
    private Map<CharSequence, List<byte[]>> collectedPromises; // Maps each PeerID to a List of serialized SharkPromises
    private Map<CharSequence, String> computedHashes; // Maps each PeerID to the SHA Hash they computed over the settlement algorithms result

    public SharkSettlementDocument(byte[] partyId, byte[] groupId, CharSequence initiatorId,
                                   Set<CharSequence> expectedPeers, long timeoutMillis) {
        this.partyId = partyId;
        this.groupId = groupId;
        this.initiatorId = initiatorId;
        this.expectedPeers = new HashSet<>(expectedPeers);
        this.submittedPeers = new HashSet<>();
        this.collectedPromises = new HashMap<>();
        this.state = SettlementPartyState.GATHERING;
        this.createdAt = System.currentTimeMillis();
        this.expiresAt = this.createdAt + timeoutMillis;
        this.computedHashes = new HashMap<>();
    }

    // private Constucutor used to deserialize
    private SharkSettlementDocument(byte[] partyId, byte[] groupId, CharSequence initiatorId,
                                    long createdAt, long expiresAt) {
        this.partyId = partyId;
        this.groupId = groupId;
        this.initiatorId = initiatorId;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.expectedPeers = new HashSet<>();
        this.submittedPeers = new HashSet<>();
        this.collectedPromises = new HashMap<>();
        this.computedHashes = new HashMap<>();
    }

    /**
     * Adds the Promises from a Peer during the GATHERING phase to the Document.
     * Marks the peer as having submitted their data and triggers a state update.
     * @param peerId Peer who is adding the data
     * @param serializedPromises serialized Promises
     */
    public void addPeerPromises(CharSequence peerId, List<byte[]> serializedPromises) {
        this.collectedPromises.put(peerId.toString(), new ArrayList<>(serializedPromises));
        this.submittedPeers.add(peerId.toString());

        this.updateState();
    }

    /**
     * Adds the settlement result hash of a peer during the VERIFYING phase.
     * Triggers a state update which may transition to the party COMPLETED or
     * CANCELLED state, depending on the hash consensus.
     * @param peerId ID of the Peer submitting their hash
     * @param hash Hash Value of the peer's computed settlement result
     */
    public void addPeerHash(CharSequence peerId, String hash) {
        this.computedHashes.put(peerId.toString(), hash);

        this.updateState();
    }

    /**
     * Returns whether this settlement party has exceeded its timeout.
     * @return true if the party has expired, otherwise false
     */
    public boolean isExpired() {
        return System.currentTimeMillis() > expiresAt;
    }


    /**
     * Serializes this document into a byte array for transport over the ASAP Protocol.
     * @return the serialized byte array
     * @throws IOException if serialization fails
     */
    public byte[] serialize() throws IOException {
        List<CharSequence> documentVariables = new ArrayList<>();

        documentVariables.add(Base64.getEncoder().encodeToString(this.partyId));
        documentVariables.add(Base64.getEncoder().encodeToString(this.groupId));
        documentVariables.add(this.initiatorId.toString());
        documentVariables.add(String.join(SET_DELIMITER, this.expectedPeers));
        documentVariables.add(this.submittedPeers.isEmpty() ? EMPTY_PLACEHOLDER : String.join(SET_DELIMITER, this.submittedPeers));

        // Serialize Promises (PeerID=Base64Promise;Base64Promise:::)
        if (this.collectedPromises.isEmpty()) {
            documentVariables.add(EMPTY_PLACEHOLDER);
        } else {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<CharSequence, List<byte[]>> entry : this.collectedPromises.entrySet()) {
                sb.append(entry.getKey()).append("=");
                StringJoiner joiner = new StringJoiner(";");
                for (byte[] pBytes : entry.getValue()) {
                    joiner.add(Base64.getEncoder().encodeToString(pBytes));
                }
                sb.append(joiner.toString().isEmpty() ? EMPTY : joiner.toString()).append(LIST_DELIMITER);
            }
            documentVariables.add(sb.toString());
        }
        if (this.computedHashes.isEmpty()) {
            documentVariables.add(EMPTY_PLACEHOLDER);
        } else {
            StringBuilder hashSb = new StringBuilder();
            for (Map.Entry<CharSequence, String> entry : this.computedHashes.entrySet()) {
                hashSb.append(entry.getKey()).append("=").append(entry.getValue()).append(LIST_DELIMITER);
            }
            documentVariables.add(hashSb.toString());
        }

        documentVariables.add(this.state.name());
        documentVariables.add(String.valueOf(this.createdAt));
        documentVariables.add(String.valueOf(this.expiresAt));

        return SerializationHelper.str2bytes(SerializationHelper.collection2String(documentVariables));

    }

    /**
     * Deserializes a SharkSettlementDocument from a byte array.
     * Triggers a Document update after deserialization to ensure the
     * state is up to date.
     * @param data the serialized byte array
     * @return the reconstructed SharkSettlementDocument, or null if data null
     * @throws IOException Exception if deserialization fails
     */
    public static SharkSettlementDocument deserialize(byte[] data) throws IOException {
        if (data == null) return null;
        List<CharSequence> documentVariables = SerializationHelper.string2CharSequenceList(SerializationHelper.bytes2str(data));

        int i = 0;
        byte[] pId = Base64.getDecoder().decode(documentVariables.get(i++).toString());
        byte[] gId = Base64.getDecoder().decode(documentVariables.get(i++).toString());
        String initId = documentVariables.get(i++).toString();

        String expPeersStr = documentVariables.get(i++).toString();
        String subPeersStr = documentVariables.get(i++).toString();

        String promisesData = documentVariables.get(i++).toString();

        String hashesData = documentVariables.get(i++).toString();

        String stateStr = documentVariables.get(i++).toString();
        long cAt = Long.parseLong(documentVariables.get(i++).toString());
        long eAt = Long.parseLong(documentVariables.get(i++).toString());

        SharkSettlementDocument party = new SharkSettlementDocument(pId, gId, initId, cAt, eAt);
        party.state = SettlementPartyState.valueOf(stateStr);
        party.expectedPeers.addAll(Arrays.asList(expPeersStr.split(SET_DELIMITER)));

        if (!subPeersStr.equals(EMPTY_PLACEHOLDER)) {
            party.submittedPeers.addAll(Arrays.asList(subPeersStr.split(SET_DELIMITER)));
        }

        if (!promisesData.equals(EMPTY_PLACEHOLDER)) {
            StringTokenizer st = new StringTokenizer(promisesData, LIST_DELIMITER);
            while (st.hasMoreTokens()) {
                String[] pair = st.nextToken().split("=", 2);
                if (pair.length == 2) {
                    List<byte[]> pList = new ArrayList<>();
                    if (!pair[1].equals(EMPTY)) {
                        for (String b64 : pair[1].split(";")) {
                            pList.add(Base64.getDecoder().decode(b64));
                        }
                    }
                    party.collectedPromises.put(pair[0], pList);
                }
            }
        }
        if (!hashesData.equals(EMPTY_PLACEHOLDER)) {
            StringTokenizer st = new StringTokenizer(hashesData, LIST_DELIMITER);
            while (st.hasMoreTokens()) {
                String[] pair = st.nextToken().split("=", 2);
                if (pair.length == 2) {
                    party.computedHashes.put(pair[0], pair[1]);
                }
            }
        }

        party.updateState();

        return party;
    }

    /**
     * Evaluated the current collected data and updates the party's state accordingly
     */
    public void updateState() {
        // 1. Check if state is CANCELED
        if (this.state == SettlementPartyState.CANCELLED) {
            return;
        }

        // 2. Timeout-Check
        if (this.isExpired()) {
            this.state = SettlementPartyState.CANCELLED;
            return;
        }

        // 3. Check if all peers submitted their result hash
        if (this.computedHashes.keySet().containsAll(this.expectedPeers) && !this.expectedPeers.isEmpty()) {
            Set<String> uniqueHashes = new HashSet<>(this.computedHashes.values());
            if (uniqueHashes.size() == 1) {
                this.state = SettlementPartyState.COMPLETED; // Konsens gefunden!
            } else {
                this.state = SettlementPartyState.CANCELLED; // Hashes stimmen nicht überein!
            }
            return;
        }

        // 4. Check if all peers submitted their promises but the hashes are still missing
        if (this.submittedPeers.containsAll(this.expectedPeers) && !this.expectedPeers.isEmpty()) {
            this.state = SettlementPartyState.VERIFYING;
            return;
        }

        // 5. Still waiting for promises
        this.state = SettlementPartyState.GATHERING;
    }

    public byte[] getGroupId() {
        return groupId;
    }

    public byte[] getPartyId() {
        return partyId;
    }

    public CharSequence getInitiatorId() {
        return initiatorId;
    }

    public Set<CharSequence> getExpectedPeers() {
        return expectedPeers;
    }

    public Set<CharSequence> getSubmittedPeers() {
        return submittedPeers;
    }

    public SettlementPartyState getState() {
        return state;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getExpiresAt() {
        return expiresAt;
    }

    public Map<CharSequence, List<byte[]>> getCollectedPromises() {
        return collectedPromises;
    }

    public Map<CharSequence, String> getComputedHashes() {
        return computedHashes;
    }
}
