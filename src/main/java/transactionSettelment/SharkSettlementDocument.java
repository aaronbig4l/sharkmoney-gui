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
    }

    /**
     * Adds the Promises from a Peer to the Document
     * @param peerId Peer who is adding the data
     * @param serializedPromises serialized Promises
     */
    public void addPeerPromises(CharSequence peerId, List<byte[]> serializedPromises) {
        this.collectedPromises.put(peerId.toString(), new ArrayList<>(serializedPromises));
        this.submittedPeers.add(peerId.toString());

        // check if all Peers submited
        if (this.submittedPeers.containsAll(this.expectedPeers)) {
            this.state = SettlementPartyState.READY;
        }
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
                String[] pair = st.nextToken().split("=");
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
        return party;
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
}
