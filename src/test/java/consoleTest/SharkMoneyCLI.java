package consoleTest;

import currency.api.SharkCurrencyComponent;
import currency.classes.SharkCurrency;
import currency.classes.SharkLocalCurrency;
import currency.classes.SharkPromise;
import currency.classes.SharkPromiseSerializer;
import currency.storage.SharkCurrencyStorage;
import net.sharksystem.asap.pki.CredentialMessageInMemo;
import net.sharksystem.pki.SharkPKIComponent;
import testHelper.AsapCurrencyTestHelper;


import java.util.ArrayList;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;

public class SharkMoneyCLI extends AsapCurrencyTestHelper {

    private SharkCurrency currency;
    private byte[] groupId;
    private SharkPKIComponent alicePKI;
    private SharkPKIComponent bobPKI;
    private final Scanner scanner = new Scanner(System.in);

    public SharkMoneyCLI() {
        super("sharkMoneyCLI");
    }

    public static void main(String[] args) throws Exception {
        // Storage löschen
        java.io.File testFolder = new java.io.File("testResultsRootFolder/sharkMoneyCLI");
        if (testFolder.exists()) {
            org.apache.commons.io.FileUtils.deleteDirectory(testFolder);
        }

        SharkMoneyCLI app = new SharkMoneyCLI();
        app.printWelcome();
        app.init();
        app.run();
    }

    private void init() throws Exception {
        System.out.println("Initialisiere Peers...");
        setUpScenarioEstablishCurrency_2_BobAndAlice();

        alicePKI = (SharkPKIComponent) aliceSharkPeer.getComponent(SharkPKIComponent.class);
        bobPKI = (SharkPKIComponent) bobSharkPeer.getComponent(SharkPKIComponent.class);
        bobPKI.acceptAndSignCredential(new CredentialMessageInMemo(ALICE_ID, ALICE_NAME, System.currentTimeMillis(), alicePKI.getPublicKey()));
        alicePKI.acceptAndSignCredential(new CredentialMessageInMemo(BOB_ID, BOB_NAME, System.currentTimeMillis(), bobPKI.getPublicKey()));

        System.out.println("Peers bereit: Alice (" + ALICE_ID + ") und Bob (" + BOB_ID + ")");
    }

    private void run() throws Exception {
        boolean running = true;
        while (running) {
            printMenu();
            String input = scanner.nextLine().trim();
            switch (input) {
                case "1":
                    createGroup();
                    break;
                case "2":
                    claimDebt();
                    break;
                case "3":
                    settleDebt();
                case "4":
                    showBalance();
                    break;
                case "5":
                    running = false;
                    System.out.println("Auf Wiedersehen!");
                    break;
                default:
                    System.out.println("Ungültige Eingabe.");
            }
        }
    }

    private void printWelcome() {
        System.out.println("╔══════════════════════════════╗");
        System.out.println("║         SharkMoneyCLI        ║");
        System.out.println("║   Simuliertes Testszenario   ║");
        System.out.println("╚══════════════════════════════╝");
        System.out.println("Willkommen, " + ALICE_NAME + "!");
        System.out.println();
        System.out.print("Drücke Enter zum Starten...");
        scanner.nextLine();
    }
    private void printMenu() {
        System.out.println("╔══════════════════════════════╗");
        System.out.println("║         SharkMoneyCLI        ║");
        System.out.println("║   Simuliertes Testszenario   ║");
        System.out.println("╚══════════════════════════════╝");
        System.out.println("1. Gruppe erstellen");
        System.out.println("2. Zahlungsforderung stellen");
        System.out.println("3. Zahlung leisten");
        System.out.println("4. Guthaben anzeigen");
        System.out.println("5. Beenden");
        System.out.print("> ");
    }

    private void createGroup() throws Exception {

        if (groupId != null) {
            System.out.println("Gruppe existiert bereits.");
            return;
        }
        System.out.println("\nInfo: Erstelle eine neue Gruppe mit einer eigenen Währung.");
        System.out.println(BOB_NAME + " wird automatisch als Mitglied hinzugefügt.");
        System.out.println();
        System.out.print("Währungsname (z.B. TestCoin): ");
        String currencyName = scanner.nextLine().trim();
        System.out.print("Währungskürzel (z.B. TC): ");
        String currencySymbol = scanner.nextLine().trim();

        currency = new SharkLocalCurrency(currencyName, currencySymbol);
        ArrayList<CharSequence> whitelist = new ArrayList<>();
        whitelist.add(BOB_ID);
        groupId = aliceCurrencyComponent.establishGroup(currency, whitelist, false, true, true);

        // Bob einladen
        aliceCurrencyComponent.invitePeerToGroup(groupId, "Hey Bob!", BOB_ID);
        runEncounter(aliceSharkPeer, bobSharkPeer, true);
        Thread.sleep(1000);

        // Bob nimmt an
        bobImpl.acceptInviteAndSign(currencyName);
        runEncounter(bobSharkPeer, aliceSharkPeer, true);
        Thread.sleep(1000);

        System.out.println("Gruppe '" + currencyName + "' erstellt. Bob ist Mitglied.");
    }

    private void settleDebt() throws Exception {
        if (groupId == null) {
            System.out.println("Bitte zuerst eine Gruppe erstellen (Option 1).");
            return;
        }

        System.out.println("\nInfo: Leiste eine Zahlung an" + BOB_NAME + ".");
        System.out.println(BOB_NAME +  " muss die Zahlung bestätigen bevor sie eingetragen wird.");
        System.out.println();
        System.out.print("Ich zahle " + BOB_NAME + ": ");
        int amount;
        try {
            amount = Integer.parseInt(scanner.nextLine().trim());
        } catch (NumberFormatException e) {
            System.out.println("Ungültiger Betrag.");
            return;
        }

        // Alice ist Debtor, Bob ist Creditor
        CharSequence promiseId = bobCurrencyComponent.createPromise(amount, currency, groupId, BOB_ID, ALICE_ID, true);

        Set<CharSequence> receiver = new HashSet<>();
        receiver.add(ALICE_ID);
        bobCurrencyComponent.sendPromise(promiseId, true, BOB_ID, receiver, true, false,
                SharkPromise.SHARK_PROMISE_ASK_FOR_SIGNATURE_AS_DEB);

        // Encounter: Alice -> Bob
        runEncounter(aliceSharkPeer, bobSharkPeer, true);
        Thread.sleep(1000);

        // Bob empfängt und bestätigt
        SharkCurrencyStorage bobStorage = bobCurrencyComponent.getSharkCurrencyStorage();
        SharkPromise bobPromise = bobStorage.getSharkPendingPromiseFromStorage(promiseId);

        if (bobPromise == null) {
            System.out.println("Fehler: Bob hat die Zahlung nicht empfangen.");
            return;
        }

        System.out.println("\n" + ALICE_NAME + " zahlt " + amount + " " + currency.getCurrencyName() + " an " + BOB_NAME + ".");
        System.out.print(BOB_NAME + ": Bestätigst du diese Zahlung? (j/n): ");
        String answer = scanner.nextLine().trim();

        if (!answer.equalsIgnoreCase("j")) {
            System.out.println(BOB_NAME + " hat die Zahlung abgelehnt.");
            return;
        }

        // Bob signiert und schickt zurück
        Set<CharSequence> aliceReceivers = new HashSet<>();
        aliceReceivers.add(ALICE_ID);

        byte[] msg = SharkPromiseSerializer.serializeSignAndSendBackMessage(
                promiseId,
                bobPromise.getDebtorSignature(),
                BOB_ID,
                aliceReceivers,
                false,
                bobPKI.getASAPKeyStore()
        );

        bobSharkPeer.getASAPPeer().sendASAPMessage(
                SharkCurrencyComponent.CURRENCY_FORMAT,
                SharkPromise.SHARK_PROMISE_RECEIVE_SIGNED_SIG,
                msg
        );

        // Encounter: Bob -> Alice
        runEncounter(bobSharkPeer, aliceSharkPeer, true);
        Thread.sleep(1000);

        // Balance aktualisieren
        SharkCurrencyStorage aliceStorage = aliceCurrencyComponent.getSharkCurrencyStorage();
        SharkPromise signed = aliceStorage.getSharkSignedPromiseFromStorage(promiseId);
        if (signed != null) {
            bobCurrencyComponent.addBalance(signed);
            System.out.println("Zahlung erfolgreich eingetragen!");
        } else {
            System.out.println("Fehler: Zahlung konnte nicht eingetragen werden.");
        }
    }

    private void claimDebt() throws Exception {
        if (groupId == null) {
            System.out.println("Bitte zuerst eine Gruppe erstellen (Option 1).");
            return;
        }

        System.out.println("\nInfo: Fordere einen Betrag von Bob ein.");
        System.out.println("Bob muss die Forderung bestätigen bevor sie eingetragen wird.");
        System.out.println();
        // ...
        System.out.print(BOB_NAME + " schuldet mir: ");
        int amount;
        try {
            amount = Integer.parseInt(scanner.nextLine().trim());
        } catch (NumberFormatException e) {
            System.out.println("Ungültiger Betrag.");
            return;
        }

        // Promise erstellen und senden
        CharSequence promiseId = aliceCurrencyComponent.createPromise(amount, currency, groupId, ALICE_ID, BOB_ID, true);

        Set<CharSequence> receiver = new HashSet<>();
        receiver.add(BOB_ID);
        aliceCurrencyComponent.sendPromise(promiseId, true, ALICE_ID, receiver, true, false,
                SharkPromise.SHARK_PROMISE_ASK_FOR_SIGNATURE_AS_DEB);

        // Encounter: Alice -> Bob
        runEncounter(aliceSharkPeer, bobSharkPeer, true);
        Thread.sleep(1000);

        // Bob bestätigen
        SharkCurrencyStorage bobStorage = bobCurrencyComponent.getSharkCurrencyStorage();
        SharkPromise bobPromise = bobStorage.getSharkPendingPromiseFromStorage(promiseId);

        if (bobPromise == null) {
            System.out.println("Fehler: Bob hat die Schuld nicht empfangen.");
            return;
        }

        System.out.println("\n" + ALICE_NAME + " fordert " + amount + " " + currency.getCurrencyName() + " von " + BOB_NAME + ".");
        System.out.print(BOB_NAME + ": Bestätigst du diese Schuld? (j/n): ");
        String answer = scanner.nextLine().trim();

        if (!answer.equalsIgnoreCase("j")) {
            System.out.println(BOB_NAME + " hat die Schuld abgelehnt.");
            return;
        }

        // Bob signiert und schickt zurück
        Set<CharSequence> aliceReceivers = new HashSet<>();
        aliceReceivers.add(ALICE_ID);

        byte[] msg = SharkPromiseSerializer.serializeSignAndSendBackMessage(
                promiseId,
                bobPromise.getDebtorSignature(),
                BOB_ID,
                aliceReceivers,
                false,
                bobPKI.getASAPKeyStore()
        );

        bobSharkPeer.getASAPPeer().sendASAPMessage(
                SharkCurrencyComponent.CURRENCY_FORMAT,
                SharkPromise.SHARK_PROMISE_RECEIVE_SIGNED_SIG,
                msg
        );

        // Encounter: Bob -> Alice
        runEncounter(bobSharkPeer, aliceSharkPeer, true);
        Thread.sleep(1000);

        // Bob Balance aktualisieren
        SharkCurrencyStorage aliceStorage = aliceCurrencyComponent.getSharkCurrencyStorage();
        SharkPromise signed = aliceStorage.getSharkSignedPromiseFromStorage(promiseId);
        if (signed != null) {
            bobCurrencyComponent.addBalance(signed);
            System.out.println("Schuld erfolgreich eingetragen!");
        } else {
            System.out.println("Fehler: Schuld konnte nicht eingetragen werden.");
        }
    }

    private void showBalance() throws Exception {
        if (currency == null) {
            System.out.println("Noch keine Währung erstellt.");
            return;
        }
        System.out.println("\nInfo: Zeigt den aktuellen Kontostand aller Mitglieder.");
        System.out.println("\n--- Kontostand ---");
        System.out.println("Alice: " + aliceCurrencyComponent.getBalance(currency.getCurrencyId()));
        System.out.println("Bob:   " + bobCurrencyComponent.getBalance(currency.getCurrencyId()));
    }
}