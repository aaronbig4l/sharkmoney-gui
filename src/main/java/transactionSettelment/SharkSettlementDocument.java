package transactionSettelment;

import net.sharksystem.utils.SerializationHelper;
import org.web3j.protocol.core.methods.response.EthLog;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.*;

public class SharkSettlementDocument {

    private static final String EMPTY_PLACEHOLDER = "NULL";
    private static final String EMPTY = "EMPTY";
    private static final String LIST_DELIMITER = ":::";
    private static final String SET_DELIMITER = ",";

    private final byte[] partyId;
    private final byte[] groupId;
    private final CharSequence initiatorId;
    private Set<CharSequence> expectedPeers;
    private Set<CharSequence> submittedPeers;
    private SettlementPartyState state;
    private final long createdAt;
    private final long expiresAt; // Timeout
    private Map<CharSequence, List<byte[]>> collectedPromises; // Maps a PeerID to a List of serialized SharkPromises
    private Map<CharSequence, String> computedHashes; // Maps a PeerID to a generated Hash for the Settlement result

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

    // private Constucutor to deserialize
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
     * Adds the Promises from a Peer to the Document
     * @param peerId Peer who is adding the data
     * @param serializedPromises serialized Promises
     */
    public void addPeerPromises(CharSequence peerId, List<byte[]> serializedPromises) {
        this.collectedPromises.put(peerId.toString(), new ArrayList<>(serializedPromises));
        this.submittedPeers.add(peerId.toString());

        this.updateState();
    }

    /**
     * Adds the calculated Hash of a Peer and check Consensus
     * @param peerId ID of the Peer
     * @param hash generated Hash Value of the Settlement Algorithm Result
     */
    public void addPeerHash(CharSequence peerId, String hash) {
        this.computedHashes.put(peerId.toString(), hash);

        this.updateState();
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > expiresAt;
    }


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
     * Evaluiert und aktualisiert den Status der Settlement Party basierend auf den gesammelten Daten.
     */
    public void updateState() {
        // 1. Wenn die Party bereits (z.B. durch Hash-Mismatch) fehlgeschlagen ist, bleibt sie es.
        if (this.state == SettlementPartyState.CANCELLED) {
            return;
        }

        // 2. Timeout-Prüfung: Ist die Zeit abgelaufen?
        if (this.isExpired()) {
            this.state = SettlementPartyState.CANCELLED;
            return;
        }

        // 3. Haben wir von allen erwarteten Peers den finalen Hash?
        if (this.computedHashes.keySet().containsAll(this.expectedPeers) && !this.expectedPeers.isEmpty()) {
            Set<String> uniqueHashes = new HashSet<>(this.computedHashes.values());
            if (uniqueHashes.size() == 1) {
                this.state = SettlementPartyState.COMPLETED; // Konsens gefunden!
            } else {
                this.state = SettlementPartyState.CANCELLED; // Hashes stimmen nicht überein!
            }
            return;
        }

        // 4. Haben wir von allen erwarteten Peers die Promises, aber noch nicht die Hashes?
        if (this.submittedPeers.containsAll(this.expectedPeers) && !this.expectedPeers.isEmpty()) {
            this.state = SettlementPartyState.VERIFYING;
            return;
        }

        // 5. Wenn nichts davon zutrifft, sammeln wir noch Daten.
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
