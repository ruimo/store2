package models;

public enum FavoKind {
    NEWS;

    private static final FavoKind byIndex[] = FavoKind.class.getEnumConstants();

    public static FavoKind byIndex(int index) {
        return byIndex[index];
    }

    public int code() {
        return ordinal();
    }
}
