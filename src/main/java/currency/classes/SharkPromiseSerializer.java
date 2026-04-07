package currency.classes;

import currency.storage.SharkCurrencyStorage;
import net.sharksystem.asap.ASAPException;
import net.sharksystem.asap.ASAPMessages;
import net.sharksystem.asap.ASAPSecurityException;
import net.sharksystem.asap.crypto.ASAPCryptoAlgorithms;
import net.sharksystem.asap.crypto.ASAPKeyStore;
import net.sharksystem.asap.utils.ASAPSerialization;


import java.io.*;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class SharkPromiseSerializer {

    public static byte [] serializePromise(SharkPromise promise, CharSequence sender,
                                       Set<CharSequence> receiver,
                                       boolean sign, boolean encrypt,
                                       ASAPKeyStore asapKeyStore, boolean excludeSignature,
                                       int usedFor, boolean asCred) throws ASAPSecurityException, IOException {

        if( (receiver != null && receiver.size() > 1) && encrypt) {
            throw new ASAPSecurityException("cannot (yet) encrypt one message for more than one recipient - split it into more messages");
        }

        if(receiver == null || receiver.isEmpty()) {
            if(encrypt) throw new ASAPSecurityException("impossible to encrypt a message without a receiver");
            receiver = new HashSet<>();
            receiver.add(promise.getDebtorID());
        }

        if(sender == null) {
            sender = promise.getCreditorID();
        }

        // Convert promise to byteArray
        // use excludeSignature to exclude or include previous signatures (that can be helpful for a late verification of the the bond signatures)
        byte[] content = sharkPromiseToByteArray(promise, excludeSignature);
        // merge content, sender and recipient
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ///// content
        ASAPSerialization.writeByteArray(content, baos);
        ///// sender is the creditor
        ASAPSerialization.writeCharSequenceParameter(sender, baos);
        ///// recipients
        ASAPSerialization.writeCharSequenceSetParameter(receiver, baos);
        content = baos.toByteArray();

        byte flags = 0;
        // Sign Promise
        if(sign) {
            byte[] existingSignature = asCred
                    ? promise.getCreditorSignature()
                    : promise.getDebtorSignature();

            byte[] signature = (existingSignature != null)
                    ? existingSignature
                    : ASAPCryptoAlgorithms.sign(content, asapKeyStore);

            System.out.println("DEBUG: content length that is written: " + content.length);
            // usedFor == 1 function is used for signature purpose
            if (usedFor == 1) {
                return signature;
            }

            baos = new ByteArrayOutputStream();
            ASAPSerialization.writeByteArray(content, baos);

            // usedFor == 2 function is used for verification purpose
            if (usedFor == 2) {
                return content;
            }

            // append signature
            ASAPSerialization.writeByteArray(signature, baos);
            // attach signature to message
            content = baos.toByteArray();
            flags += SharkPromise.SIGNED_MASK;
        }

        if(encrypt) {
            // Encrypt Message
            content = ASAPCryptoAlgorithms.produceEncryptedMessagePackage(
                    content,
                    receiver.iterator().next(),
                    asapKeyStore);
            flags += SharkPromise.ENCRYPTED_MASK;
        }

        baos = new ByteArrayOutputStream();
        ASAPSerialization.writeByteParameter(flags, baos);
        ASAPSerialization.writeByteArray(content, baos);

        return baos.toByteArray();
    }

    public static SharkPromise deserializePromise(byte [] asapMessage, ASAPKeyStore asapKeyStore) throws IOException, ASAPException, ClassNotFoundException {
        ByteArrayInputStream bais = new ByteArrayInputStream(asapMessage);
        byte flags = ASAPSerialization.readByte(bais);
        byte[] tmpMessage = ASAPSerialization.readByteArray(bais);

        boolean signed = (flags & SharkPromise.SIGNED_MASK) != 0;
        boolean encrypted = (flags & SharkPromise.ENCRYPTED_MASK) != 0;

        if (encrypted) {
            // decrypt
            bais = new ByteArrayInputStream(tmpMessage);
            ASAPCryptoAlgorithms.EncryptedMessagePackage
                    encryptedMessagePackage = ASAPCryptoAlgorithms.parseEncryptedMessagePackage(bais);

            // for me?
            if (!asapKeyStore.isOwner(encryptedMessagePackage.getReceiver())) {
                throw new ASAPException("SharkPromise Message: message not for me. Current user: "
                        + asapKeyStore.getOwner()
                        + ", recipient: "
                        + encryptedMessagePackage.getReceiver());
            }
            // replace message with decrypted message
            tmpMessage = ASAPCryptoAlgorithms.decryptPackage(
                    encryptedMessagePackage, asapKeyStore);
        }

        byte[] signature = null;
        byte[] signedMessage = null;
        if (signed) {
            bais = new ByteArrayInputStream(tmpMessage);
            byte[] wrappedContent = ASAPSerialization.readByteArray(bais); // = writeByteArray(promiseBytes)+sender+receivers
            signedMessage = wrappedContent; //what was signed
            signature = ASAPSerialization.readByteArray(bais);
            tmpMessage = wrappedContent;
        }

        bais = new ByteArrayInputStream(tmpMessage);
        byte[] snMessage = ASAPSerialization.readByteArray(bais);   // promiseBytes
        String snSender = ASAPSerialization.readCharSequenceParameter(bais);
        Set<CharSequence> snReceivers = ASAPSerialization.readCharSequenceSetParameter(bais);

        boolean verified = false; // initialize
        if (signature != null) {
            try {
                System.out.println("DEBUG: content length that is being verified: " + signedMessage.length + " and sender: " + snSender);
                verified = ASAPCryptoAlgorithms.verify(
                        signedMessage, signature, snSender, asapKeyStore);
            } catch (ASAPSecurityException e) {
                // verified definitely false
                System.out.println("DEBUG: security exception: " + e.getMessage());
                verified = false;
            }
        }

        if (signed && !verified) {
            throw new ASAPException("Signature verification failed – message may be tampered");
        }

        // replace special sn symbols
        return byteArrayToSharkPromise(snMessage);
    }

    public static byte[] sharkPromiseToByteArray(SharkPromise promise, boolean excludeSignature) {
        byte[] byteArray = null;
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            // Temporarily strip signatures for consistent signing content
            byte[] savedCredSig = promise.getCreditorSignature();
            byte[] savedDebSig  = promise.getDebtorSignature();

            if (excludeSignature) {
                promise.setCreditorSignature(null);
                promise.setDebtorSignature(null);
            }

            ObjectOutputStream out = new ObjectOutputStream(bos);
            out.writeObject(promise);
            out.flush();
            byteArray = bos.toByteArray();

            // Restore signatures
            if (excludeSignature) {
                promise.setCreditorSignature(savedCredSig);
                promise.setDebtorSignature(savedDebSig);
            }
            bos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return byteArray;
    }

    public static SharkPromise byteArrayToSharkPromise(byte [] byteArray) throws IOException, ClassNotFoundException {
        ByteArrayInputStream bis = new ByteArrayInputStream(byteArray);
        try (ObjectInputStream in = new ObjectInputStream(bis)) {
            Object obj = in.readObject();
            return (SharkPromise) obj;
        }
    }

    public static byte[] serializeSignAndSendBackMessage(CharSequence promiseId,
                                                         byte[] signature,
                                                         CharSequence sender,
                                                         Set<CharSequence> receiver,
                                                         Boolean encrypted,
                                                         ASAPKeyStore asapKeyStore) throws ASAPSecurityException, IOException {
        if( (receiver != null && receiver.size() > 1) && encrypted) {
            throw new ASAPSecurityException("cannot (yet) encrypt one message for more than one recipient - split it into more messages");
        }

        if(receiver == null || receiver.isEmpty()) {
            if(encrypted) throw new ASAPSecurityException("impossible to encrypt a message without a receiver");
        }

        ByteArrayOutputStream innerBaos = new ByteArrayOutputStream();
        ASAPSerialization.writeCharSequenceParameter(promiseId, innerBaos);
        ASAPSerialization.writeByteArray(signature, innerBaos);
        byte[] innerContent = innerBaos.toByteArray();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ASAPSerialization.writeByteArray(innerContent, baos);
        ASAPSerialization.writeCharSequenceParameter(sender, baos);
        ASAPSerialization.writeCharSequenceSetParameter(receiver, baos);
        byte[] content = baos.toByteArray();

        byte flags = 0;
        flags += SharkPromise.SIGNED_MASK;
        if(encrypted) {
            content = ASAPCryptoAlgorithms.produceEncryptedMessagePackage(
                    content,
                    receiver.iterator().next(),
                    asapKeyStore);
            flags += SharkPromise.ENCRYPTED_MASK;
        }

        baos = new ByteArrayOutputStream();
        ASAPSerialization.writeByteParameter(flags, baos);
        ASAPSerialization.writeByteArray(content, baos);
        return baos.toByteArray();
    }

    public static SharkPromise deserializeSignAndSendBackMessage(byte[] asapMessage, ASAPKeyStore asapKeyStore, SharkCurrencyStorage sharkCurrencyStorage) throws ASAPException, IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(asapMessage);
        byte flags = ASAPSerialization.readByte(bais);
        byte[] tmpMessage = ASAPSerialization.readByteArray(bais);

        boolean encrypted = (flags & SharkPromise.ENCRYPTED_MASK) != 0;

        if (encrypted) {
            // decrypt
            bais = new ByteArrayInputStream(tmpMessage);
            ASAPCryptoAlgorithms.EncryptedMessagePackage
                    encryptedMessagePackage = ASAPCryptoAlgorithms.parseEncryptedMessagePackage(bais);

            // for me?
            if (!asapKeyStore.isOwner(encryptedMessagePackage.getReceiver())) {
                throw new ASAPException("SharkPromise Message: message not for me. Current user: "
                        + asapKeyStore.getOwner()
                        + ", recipient: "
                        + encryptedMessagePackage.getReceiver());
            }
            // replace message with decrypted message
            tmpMessage = ASAPCryptoAlgorithms.decryptPackage(
                    encryptedMessagePackage, asapKeyStore);
        }

        byte[] signature = null;
        byte[] signedMessage = null;

        bais = new ByteArrayInputStream(tmpMessage);
        byte[] snMessage = ASAPSerialization.readByteArray(bais);
        String snSender = ASAPSerialization.readCharSequenceParameter(bais);
        Set<CharSequence> snReceivers = ASAPSerialization.readCharSequenceSetParameter(bais);

        ByteArrayInputStream bais2 = new ByteArrayInputStream(snMessage);
        String promiseId = ASAPSerialization.readCharSequenceParameter(bais2);
        byte[] signatureSender = ASAPSerialization.readByteArray(bais2);

        SharkPromise promise = sharkCurrencyStorage
                .getSharkPendingPromiseFromStorage(promiseId);

        if (promise == null) {
            if (sharkCurrencyStorage.containsSignedPromise(promiseId)) {
                return null; // Wurde bereits verarbeitet
            } else {
                throw new ASAPException("Promise " + promiseId + " not found in pending storage");
            }
        }

        if(snSender.equals(promise.getCreditorID().toString())) {
            promise.setCreditorSignature(signatureSender);
        } else if(snSender.equals(promise.getDebtorID().toString())) {
            promise.setDebtorSignature(signatureSender);
        } else {
            throw new ASAPException("Sender is neither creditor nor debtor of this promise");
        }
        promise.updateState();
        sharkCurrencyStorage.removeSharkPendingPromiseFromStorage(promiseId);
        sharkCurrencyStorage.addSharkSignedPromiseToStorage(promise);
        return promise;
    }
}
