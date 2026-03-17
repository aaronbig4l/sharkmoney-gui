package currency.classes;

public enum SharkPromiseState {
    UNSIGNED,
    SIGNED_BY_DEBITOR,
    SIGNED_BY_CREDITOR,
    FULLY_SIGNED,
    ANULLED //if the debt has been paid
}