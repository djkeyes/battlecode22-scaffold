package bytecodes;

public strictfp class AnotherClassWithStaticInitialization {
    public static int numOddNumbersBelow10;
    static {
        numOddNumbersBelow10 = 0;
        for (int i=0; i < 10; i++) {
            if (i % 2 == 1) {
                numOddNumbersBelow10++;
            }
        }
    }
}