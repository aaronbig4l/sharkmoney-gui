package transactionSettelment;

import net.sharksystem.utils.SerializationHelper;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.*;

public class SharkSettlementDocument {

    private static final String EMPTY_PLACEHOLDER = "NULL";
    private static final String EMPTY = "EMPTY";
    private static final String LIST_DELIMITER = ":::";

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

    public byte[] serialize() throws IOException {
        List<CharSequence> documentVariables = new ArrayList<>();

        // 1. serialize GroupID and allPeerBalances as Base64
        documentVariables.add(Base64.getEncoder().encodeToString(this.groupId));

        if(this.allPeerBalances.isEmpty()) {
            documentVariables.add(EMPTY_PLACEHOLDER);
        } else {
            StringBuilder balancesSb = new StringBuilder();
            for(Map.Entry<CharSequence, Map<CharSequence, Integer>> outerEntry : this.allPeerBalances.entrySet()) {
                balancesSb.append(outerEntry.getKey().toString()).append("=");

                Map<CharSequence, Integer> innerMap = outerEntry.getValue();
                if(innerMap.isEmpty()) {
                    balancesSb.append(EMPTY);
                } else {
                    StringJoiner stringJoiner = new StringJoiner(";");
                    for (Map.Entry<CharSequence, Integer> innerEntry : innerMap.entrySet()) {
                        stringJoiner.add(innerEntry.getKey().toString() + "," + innerEntry.getValue());
                    }
                    balancesSb.append(stringJoiner.toString());
                }
                balancesSb.append(LIST_DELIMITER);
            }
            documentVariables.add(balancesSb.toString());
        }

        // 2. Serialize the Signatures
        if (this.signatures.isEmpty()) {
            documentVariables.add(EMPTY_PLACEHOLDER);
        } else {
            StringBuilder sigSb = new StringBuilder();
            for (Map.Entry<CharSequence, byte[]> entry : this.signatures.entrySet()) {
                sigSb.append(entry.getKey().toString()).append("=")
                        .append(Base64.getEncoder().encodeToString(entry.getValue()))
                        .append(LIST_DELIMITER);
            }
            documentVariables.add(sigSb.toString());
        }

        // 3. Return String as Bytes
        String serializedSettlementString = SerializationHelper.collection2String(documentVariables);
        return SerializationHelper.str2bytes(serializedSettlementString);
    }

    public static SharkSettlementDocument deserialize(byte[] data) throws IOException {
        if (data == null) return null;

        String dataString = SerializationHelper.bytes2str(data);
        List<CharSequence> documentVariables = SerializationHelper.string2CharSequenceList(dataString);

        if(documentVariables.size() < 3) {
            throw new IllegalArgumentException("Illegal Format for SharkSettlementDocument: " +
                    documentVariables.size() + " Parts. Excpected 3.");
        }

        int idx = 0;

        // 1. extract GroupID
        byte[] groupId = Base64.getDecoder().decode(documentVariables.get(idx++).toString());
        SharkSettlementDocument settlementDocument = new SharkSettlementDocument(groupId);

        // 2. restore allPeerBalances
        String balacesData = documentVariables.get(idx++).toString();
        if(!balacesData.equals(EMPTY_PLACEHOLDER)) {
            StringTokenizer st = new StringTokenizer(balacesData, LIST_DELIMITER);
            while (st.hasMoreTokens()) {
                String outerPair = st.nextToken(); // e.g. "Alice_ID=Bob_ID,50;Clara_ID,-20"
                String[] splitOuter = outerPair.split("=");

                if(splitOuter.length == 2) {
                    String peerId = splitOuter[0];
                    Map<CharSequence, Integer> innerMap = new HashMap<>();

                    if (!splitOuter[1].equals(EMPTY)) {
                        String[] innerPairs = splitOuter[1].split(";");
                        for (String innerPair : innerPairs) {
                            String[] kv = innerPair.split(",");
                            if (kv.length == 2) {
                                innerMap.put(kv[0], Integer.parseInt(kv[1]));
                            }
                        }
                    }
                    settlementDocument.getAllPeerBalances().put(peerId, innerMap);
                }
            }
        }

        // 3. resotre signatures
        String sigData = documentVariables.get(idx++).toString();
        if(!sigData.equals(EMPTY_PLACEHOLDER)) {
            StringTokenizer st = new StringTokenizer(sigData, LIST_DELIMITER);
            while(st.hasMoreTokens()) {
                String pair = st.nextToken();
                String[] splitPair = pair.split("=");
                if (splitPair.length == 2) {
                    settlementDocument.signatures.put(splitPair[0], Base64.getDecoder().decode(splitPair[1]));
                }
            }
        }
        return settlementDocument;
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
