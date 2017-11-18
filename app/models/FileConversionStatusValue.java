package models;

public enum FileConversionStatusValue {
    WAITING, CONVERTING, COMPLETED, ERROR;

    private static final FileConversionStatusValue byIndex[] = FileConversionStatusValue.class.getEnumConstants();

    public static FileConversionStatusValue byIndex(int index) {
        return byIndex[index];
    }

    public static FileConversionStatusValue[] all() {
        return byIndex.clone();
    }
}
