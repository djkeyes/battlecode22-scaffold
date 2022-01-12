package bytecodes;

import battlecode.common.*;

import java.util.*;

import static bytecodes.Assert.assertEquals;

/**
 * This class tests the bytecode usage of various functions and language constructs. It does it by creating a
 * RobotPlayer, and then literally executing functions and timing their runtime. If you want to run these tests, you
 * can do it with, for example, the following command (use gradle on windows):
 * <pre>
 *      ./gradlew run -PteamA=bytecodes -PteamB=noop -Pmaps=WaterBot
 * </pre>
 * If you would like to test something yourself, have a look at the file. Most of the functions in this file are
 * structured in the following form:
 * <p>
 * <pre>{@code
 *       before = Clock.getBytecodeNum();
 *       <YOUR FUNCTION>
 *       after = Clock.getBytecodeNum();
 *       expected = <YOUR BYTECODES>;
 *       actual = after - before;
 *       assertEquals(expected, actual);
 * }</pre>
 * <p>
 * Just insert the code you would like to test in <YOUR FUNCTION>, and insert a dummy value like 123 in <YOUR
 * BYTECODES>. Then run this player. Your code will fail (unless you guessed correctly, and your function uses
 * exactly 123 bytecodes). When it fails, the logs will show the true bytecode usage. You can copy and paste those
 * into the code to make the assertion succeed.
 * <p>
 * TODO(daniel):
 * It might be nice to have some kind of automated test / generator for this information. I'm not sure how to inject
 * the code profiler into junit tests or how to mock the various battlecode classes, so instead this is a
 * full-fledged player.
 */
public final strictfp class RobotPlayer {

    public static void runTests(final RobotController rc) {
        testSimpleAssertion();
        timeNoop();
        timeDeclarePrimitive();
        timeAssignPrimitive();
        timeCreateString();

        timeSimpleForLoop();
        Clock.yield();

        timeCreateArray();
        Clock.yield();

        timeAllocateObject();
        timeAllocateCollections();
        timeSimpleLambda();
        Clock.yield();

        timeInheritance();
        timeStaticInstanceAndLocal();
        timeDebugMethods();
        Clock.yield();

        timeUpdateCollections();
        timeRobotControllerMethods(rc);
        timeMethodReference();
        timeSwitchStatement();
        Clock.yield();

        timePrinting();
        Clock.yield();

        timeMapLocationMethods(rc);
        time2DArrayOptions();
        Clock.yield();
    }

    @SuppressWarnings("unused")
    public static void run(RobotController rc) {
        // TODO(daniel): it would be nice to test the cost of static initialization
        final int bytecodesAtStart = Clock.getBytecodeNum();
        System.out.println("bytecodes at start of tests: " + bytecodesAtStart);
        final int bytecodesAfterPrint = Clock.getBytecodeNum();
        assertEquals(9, bytecodesAfterPrint - bytecodesAtStart);

        try {
            runTests(rc);

            System.out.println("All tests passed!");
        } catch (final AssertionError e1) {
            System.out.println("Test(s) failed!");
            e1.printStackTrace();
        } catch (final Exception e2) {
            System.out.println("Caught unknown exception!");
            e2.printStackTrace();
        }

        while (true) ;
    }

    public static void testSimpleAssertion() {
        assert (true);
    }

    public static void timeNoop() {
        int before, after;
        before = Clock.getBytecodeNum();
        after = Clock.getBytecodeNum();
        int expected = 1;
        int actual = after - before;
        assertEquals(expected, actual);
    }

    public static void timeDeclarePrimitive() {
        int before, after;
        before = Clock.getBytecodeNum();
        int x;
        after = Clock.getBytecodeNum();
        int expected = 1;
        int actual = after - before;
        assertEquals(expected, actual);
    }

    public static void timeAssignPrimitive() {
        int before, after;
        int x = 123;
        before = Clock.getBytecodeNum();
        x = 5;
        after = Clock.getBytecodeNum();
        int expected = 3;
        int actual = after - before;
        assertEquals(expected, actual);

        before = Clock.getBytecodeNum();
        int y = 7;
        after = Clock.getBytecodeNum();
        expected = 3;
        actual = after - before;
        assertEquals(expected, actual);
    }

    public static void timeCreateString() {
        int before, after;
        String x = "init value";
        before = Clock.getBytecodeNum();
        x = "a";
        after = Clock.getBytecodeNum();
        int expected = 3;
        int actual = after - before;
        assertEquals(expected, actual);

        before = Clock.getBytecodeNum();
        x = "ab";
        after = Clock.getBytecodeNum();
        expected = 3;
        actual = after - before;
        assertEquals(expected, actual);

        before = Clock.getBytecodeNum();
        x = "abc";
        after = Clock.getBytecodeNum();
        expected = 3;
        actual = after - before;
        assertEquals(expected, actual);
    }

    public static void timeSimpleForLoop() {
        // general observations:
        // if possible, it's usually better to go through for-loops backwards

        int before, after;
        before = Clock.getBytecodeNum();
        for (int i = 0; i < 100; i++) ;
        after = Clock.getBytecodeNum();
        int expected = 506;
        int actual = after - before;
        assertEquals(expected, actual);

        // try a longer loop
        before = Clock.getBytecodeNum();
        for (int i = 0; i < 1000; i++) ;
        after = Clock.getBytecodeNum();
        expected = 5006;
        actual = after - before;
        assertEquals(expected, actual);

        // try a prefix increment
        before = Clock.getBytecodeNum();
        for (int i = 0; i < 100; ++i) ;
        after = Clock.getBytecodeNum();
        expected = 506;
        actual = after - before;
        assertEquals(expected, actual);

        // loop backwards
        before = Clock.getBytecodeNum();
        for (int i = 100; i >= 0; i--) ;
        after = Clock.getBytecodeNum();
        expected = 409;
        actual = after - before;
        assertEquals(expected, actual);

        // loop backwards, change operand order
        before = Clock.getBytecodeNum();
        for (int i = 100; --i >= 0; ) ;
        after = Clock.getBytecodeNum();
        expected = 406;
        actual = after - before;
        assertEquals(expected, actual);
    }

    public static void timeCreateArray() {
        int before, after;
        before = Clock.getBytecodeNum();
        int[] tmp1;
        after = Clock.getBytecodeNum();
        int expected = 1;
        int actual = after - before;
        assertEquals(expected, actual);

        before = Clock.getBytecodeNum();
        int[] tmp2 = null;
        after = Clock.getBytecodeNum();
        expected = 3;
        actual = after - before;
        assertEquals(expected, actual);

        int[] arr;
        before = Clock.getBytecodeNum();
        arr = new int[10];
        after = Clock.getBytecodeNum();
        expected = 13;
        actual = after - before;
        assertEquals(expected, actual);

        before = Clock.getBytecodeNum();
        arr = new int[100];
        after = Clock.getBytecodeNum();
        expected = 103;
        actual = after - before;
        assertEquals(expected, actual);

        before = Clock.getBytecodeNum();
        arr = new int[1000];
        after = Clock.getBytecodeNum();
        expected = 1003;
        actual = after - before;
        assertEquals(expected, actual);
        assertEquals(expected, actual);

        before = Clock.getBytecodeNum();
        arr = new int[0];
        after = Clock.getBytecodeNum();
        expected = 4;
        actual = after - before;
        assertEquals(expected, actual);
        assertEquals(expected, actual);

        long[] bigArr;
        before = Clock.getBytecodeNum();
        bigArr = new long[10];
        after = Clock.getBytecodeNum();
        expected = 13;
        actual = after - before;
        assertEquals(expected, actual);

        Object[] objArr;
        before = Clock.getBytecodeNum();
        objArr = new Object[10];
        after = Clock.getBytecodeNum();
        expected = 13;
        actual = after - before;
        assertEquals(expected, actual);

    }

    public static void timeAllocateObject() {
        int before, after;
        Object x;
        before = Clock.getBytecodeNum();
        x = new Object();
        after = Clock.getBytecodeNum();
        int expected = 4;
        int actual = after - before;
        assertEquals(expected, actual);
    }

    public static void timeAllocateCollections() {
        int before, after;
        Collection x;
        before = Clock.getBytecodeNum();
        x = new ArrayList();
        after = Clock.getBytecodeNum();
        int expected = 23;
        int actual = after - before;
        assertEquals(expected, actual);

        before = Clock.getBytecodeNum();
        x = new LinkedList();
        after = Clock.getBytecodeNum();
        expected = 18;
        actual = after - before;
        assertEquals(expected, actual);

        before = Clock.getBytecodeNum();
        x = new HashSet();
        after = Clock.getBytecodeNum();
        expected = 25;
        actual = after - before;
        assertEquals(expected, actual);

        AbstractMap y;
        before = Clock.getBytecodeNum();
        y = new HashMap();
        after = Clock.getBytecodeNum();
        expected = 11;
        actual = after - before;
        assertEquals(expected, actual);

        before = Clock.getBytecodeNum();
        x = new PriorityQueue();
        after = Clock.getBytecodeNum();
        expected = 40;
        actual = after - before;
        assertEquals(expected, actual);

        before = Clock.getBytecodeNum();
        x = new TreeSet();
        after = Clock.getBytecodeNum();
        expected = 38;
        actual = after - before;
        assertEquals(expected, actual);

        before = Clock.getBytecodeNum();
        y = new TreeMap();
        after = Clock.getBytecodeNum();
        expected = 17;
        actual = after - before;
        assertEquals(expected, actual);

        before = Clock.getBytecodeNum();
        x = new LinkedHashSet();
        after = Clock.getBytecodeNum();
        expected = 92;
        actual = after - before;
        assertEquals(expected, actual);

        before = Clock.getBytecodeNum();
        y = new LinkedHashMap();
        after = Clock.getBytecodeNum();
        expected = 16;
        actual = after - before;
        assertEquals(expected, actual);
    }

    private interface SimpleLambda {
        void doIt();
    }

    public static void timeSimpleLambda() {
        int before, after;
        SimpleLambda x;
        before = Clock.getBytecodeNum();
        x = () -> {
        };
        after = Clock.getBytecodeNum();
        int expected = 2;
        int actual = after - before;
        assertEquals(expected, actual);

        before = Clock.getBytecodeNum();
        x.doIt();
        after = Clock.getBytecodeNum();
        expected = 3;
        actual = after - before;
        assertEquals(expected, actual);
    }

    private static class EmptyClass {
    }

    private static class OneVarClass {
        private int a;

        private void assignVar() {
            a = 123;
        }
    }

    private static class TwoVarClass {
        private int a;
        private int b;
    }

    private static class InitVarClass {
        private int a = 5;
    }

    private static class SimpleClass {
        protected void foo() {
        }
    }

    private static class DerivedClass extends SimpleClass {
    }

    private static class DerivedClassWithOverriding extends SimpleClass {
        @Override
        protected void foo() {
        }
    }

    private interface SimpleInterface {
    }

    private static class SimpleImpl implements SimpleInterface {
    }

    private static class SimpleLambdaImpl implements SimpleLambda {
        @Override
        public void doIt() {
        }
    }

    public static void timeInheritance() {
        int before, after;
        EmptyClass empty;
        before = Clock.getBytecodeNum();
        empty = new EmptyClass();
        after = Clock.getBytecodeNum();
        int expected = 9;
        int actual = after - before;
        assertEquals(expected, actual);

        OneVarClass oneVar;
        before = Clock.getBytecodeNum();
        oneVar = new OneVarClass();
        after = Clock.getBytecodeNum();
        expected = 9;
        actual = after - before;
        assertEquals(expected, actual);

        TwoVarClass twoVar;
        before = Clock.getBytecodeNum();
        twoVar = new TwoVarClass();
        after = Clock.getBytecodeNum();
        expected = 9;
        actual = after - before;
        assertEquals(expected, actual);

        InitVarClass initVar;
        before = Clock.getBytecodeNum();
        initVar = new InitVarClass();
        after = Clock.getBytecodeNum();
        expected = 12;
        actual = after - before;
        assertEquals(expected, actual);

        SimpleClass x;
        before = Clock.getBytecodeNum();
        x = new SimpleClass();
        after = Clock.getBytecodeNum();
        expected = 9;
        actual = after - before;
        assertEquals(expected, actual);

        SimpleClass y;
        before = Clock.getBytecodeNum();
        y = new DerivedClass();
        after = Clock.getBytecodeNum();
        expected = 14;
        actual = after - before;
        assertEquals(expected, actual);

        SimpleClass z;
        before = Clock.getBytecodeNum();
        z = new DerivedClassWithOverriding();
        after = Clock.getBytecodeNum();
        expected = 14;
        actual = after - before;
        assertEquals(expected, actual);

        DerivedClass w;
        before = Clock.getBytecodeNum();
        w = new DerivedClass();
        after = Clock.getBytecodeNum();
        expected = 14;
        actual = after - before;
        assertEquals(expected, actual);

        before = Clock.getBytecodeNum();
        x.foo();
        after = Clock.getBytecodeNum();
        expected = 3;
        actual = after - before;
        assertEquals(expected, actual);

        before = Clock.getBytecodeNum();
        y.foo();
        after = Clock.getBytecodeNum();
        expected = 3;
        actual = after - before;
        assertEquals(expected, actual);

        before = Clock.getBytecodeNum();
        z.foo();
        after = Clock.getBytecodeNum();
        expected = 3;
        actual = after - before;
        assertEquals(expected, actual);

        before = Clock.getBytecodeNum();
        w.foo();
        after = Clock.getBytecodeNum();
        expected = 3;
        actual = after - before;
        assertEquals(expected, actual);

        SimpleInterface a;
        before = Clock.getBytecodeNum();
        a = new SimpleImpl();
        after = Clock.getBytecodeNum();
        expected = 9;
        actual = after - before;
        assertEquals(expected, actual);

        // anonymous class
        before = Clock.getBytecodeNum();
        a = new SimpleImpl() {
        };
        after = Clock.getBytecodeNum();
        expected = 11;
        actual = after - before;
        assertEquals(expected, actual);

        SimpleLambda b;
        before = Clock.getBytecodeNum();
        b = new SimpleLambdaImpl();
        after = Clock.getBytecodeNum();
        expected = 9;
        actual = after - before;
        assertEquals(expected, actual);

        before = Clock.getBytecodeNum();
        b.doIt();
        after = Clock.getBytecodeNum();
        expected = 3;
        actual = after - before;
        assertEquals(expected, actual);
    }

    private static class ClassWithMethods {
        public void noop() {
        }

        public void callNoop() {
            noop();
        }

        public void callNoopTwice() {
            noop();
            noop();
        }

        protected void protectedNoop() {
        }

        private void privateNoop() {
        }

        void packageNoop() {
        }

        public final void finalNoop() {
        }
    }

    private static final class FinalClass {
        public void noop() {
        }

        public final void finalNoop() {
        }
    }

    private static final class ClassWithStaticVar {
        static int a;
    }

    private static int staticVar;

    private static void staticMethod() {
    }

    public static void timeStaticInstanceAndLocal() {
        int before, after;
        before = Clock.getBytecodeNum();
        staticVar = 123;
        after = Clock.getBytecodeNum();
        int expected = 3;
        int actual = after - before;
        assertEquals(expected, actual);

        // huh. in this case, it's faster to call an instance method that assigns values within the instance than to
        // directly assign values outside of the instance.
        OneVarClass oneVar = new OneVarClass();
        oneVar.a = 0;
        before = Clock.getBytecodeNum();
        oneVar.a = 456;
        after = Clock.getBytecodeNum();
        expected = 9;
        actual = after - before;
        assertEquals(expected, actual);

        before = Clock.getBytecodeNum();
        oneVar.assignVar();
        after = Clock.getBytecodeNum();
        expected = 8;
        actual = after - before;
        assertEquals(expected, actual);

        before = Clock.getBytecodeNum();
        staticMethod();
        after = Clock.getBytecodeNum();
        expected = 2;
        actual = after - before;
        assertEquals(expected, actual);

        ClassWithMethods methodic = new ClassWithMethods();
        before = Clock.getBytecodeNum();
        methodic.noop();
        after = Clock.getBytecodeNum();
        expected = 3;
        actual = after - before;
        assertEquals(expected, actual);

        before = Clock.getBytecodeNum();
        methodic.callNoop();
        after = Clock.getBytecodeNum();
        expected = 5;
        actual = after - before;
        assertEquals(expected, actual);

        before = Clock.getBytecodeNum();
        methodic.callNoopTwice();
        after = Clock.getBytecodeNum();
        expected = 7;
        actual = after - before;
        assertEquals(expected, actual);

        before = Clock.getBytecodeNum();
        methodic.protectedNoop();
        after = Clock.getBytecodeNum();
        expected = 3;
        actual = after - before;
        assertEquals(expected, actual);

        // only this one is more expensive. weird, eh?
        before = Clock.getBytecodeNum();
        methodic.privateNoop();
        after = Clock.getBytecodeNum();
        expected = 5;
        actual = after - before;
        assertEquals(expected, actual);

        before = Clock.getBytecodeNum();
        methodic.packageNoop();
        after = Clock.getBytecodeNum();
        expected = 3;
        actual = after - before;
        assertEquals(expected, actual);

        before = Clock.getBytecodeNum();
        methodic.finalNoop();
        after = Clock.getBytecodeNum();
        expected = 3;
        actual = after - before;
        assertEquals(expected, actual);

        before = Clock.getBytecodeNum();
        Dummy.noop();
        after = Clock.getBytecodeNum();
        expected = 2;
        actual = after - before;
        assertEquals(expected, actual);

        FinalClass finalWithMethods = new FinalClass();
        before = Clock.getBytecodeNum();
        finalWithMethods.noop();
        after = Clock.getBytecodeNum();
        expected = 3;
        actual = after - before;
        assertEquals(expected, actual);

        before = Clock.getBytecodeNum();
        finalWithMethods.finalNoop();
        after = Clock.getBytecodeNum();
        expected = 3;
        actual = after - before;
        assertEquals(expected, actual);

        before = Clock.getBytecodeNum();
        ClassWithStaticVar.a = 123;
        after = Clock.getBytecodeNum();
        expected = 3;
        actual = after - before;
        assertEquals(expected, actual);

        before = Clock.getBytecodeNum();
        AnotherClass.x = 789;
        after = Clock.getBytecodeNum();
        expected = 6;
        actual = after - before;
        assertEquals(expected, actual);

        before = Clock.getBytecodeNum();
        AnotherClass.x = 159;
        after = Clock.getBytecodeNum();
        expected = 3;
        actual = after - before;
        assertEquals(expected, actual);

        // Seems like static initialization happens the first time you access the class
        before = Clock.getBytecodeNum();
        AnotherClassWithManyStaticVariables.x = 42;
        after = Clock.getBytecodeNum();
        expected = 16;
        actual = after - before;
        assertEquals(expected, actual);

        before = Clock.getBytecodeNum();
        AnotherClassWithManyStaticVariables.x = 700;
        after = Clock.getBytecodeNum();
        expected = 3;
        actual = after - before;
        assertEquals(expected, actual);

        before = Clock.getBytecodeNum();
        int calculation = AnotherClassWithStaticInitialization.numOddNumbersBelow10;
        after = Clock.getBytecodeNum();
        assertEquals(5, calculation);
        expected = 131;
        actual = after - before;
        assertEquals(expected, actual);
    }

    private static void debug_noop() {
    }

    private static void debug_oneOp() {
        int x = 1337;
    }

    private static int sideEffect;

    private static void debug_withSideEffect() {
        sideEffect = 345;
    }

    private static void debug_withOneParam(int x) {
    }

    private static void debug_withTwoParams(int x, int y) {
    }

    public static void timeDebugMethods() {
        // result: debug methods always cost (1 + numParams), whether they are enabled or disabled.
        // (you can disable debug methods with the -Dbc.engine.debug-methods arg in gradle.build)

        int before, after;
        before = Clock.getBytecodeNum();
        debug_noop();
        after = Clock.getBytecodeNum();
        int expected = 1;
        int actual = after - before;
        assertEquals(expected, actual);

        before = Clock.getBytecodeNum();
        debug_oneOp();
        after = Clock.getBytecodeNum();
        expected = 1;
        actual = after - before;
        assertEquals(expected, actual);

        sideEffect = -1;
        before = Clock.getBytecodeNum();
        debug_withSideEffect();
        after = Clock.getBytecodeNum();
        expected = 1;
        actual = after - before;
        assertEquals(expected, actual);
        assertEquals(345, sideEffect);

        int x = 0, y = 0;
        before = Clock.getBytecodeNum();
        debug_withOneParam(x);
        after = Clock.getBytecodeNum();
        expected = 2;
        actual = after - before;
        assertEquals(expected, actual);

        before = Clock.getBytecodeNum();
        debug_withTwoParams(x, y);
        after = Clock.getBytecodeNum();
        expected = 3;
        actual = after - before;
        assertEquals(expected, actual);

        before = Clock.getBytecodeNum();
        debug_withTwoParams(x, x);
        after = Clock.getBytecodeNum();
        expected = 3;
        actual = after - before;
        assertEquals(expected, actual);
    }

    public static void timeUpdateCollections() {
        int before, after, expected, actual;

        ArrayList<Integer> arrayList;
        LinkedList<Integer> linkedList;
        HashSet<Integer> hashSet;

        // this is cheaper than before. Does ArrayList do some static initialization that costs 5 bytecodes?
        before = Clock.getBytecodeNum();
        arrayList = new ArrayList<>();
        after = Clock.getBytecodeNum();
        expected = 16;
        actual = after - before;
        assertEquals(expected, actual);

        // this is significantly more expensive. weird.
        before = Clock.getBytecodeNum();
        arrayList = new ArrayList<>(10);
        after = Clock.getBytecodeNum();
        expected = 30;
        actual = after - before;
        assertEquals(expected, actual);

        before = Clock.getBytecodeNum();
        arrayList = new ArrayList<>(20);
        after = Clock.getBytecodeNum();
        expected = 40;
        actual = after - before;
        assertEquals(expected, actual);

        before = Clock.getBytecodeNum();
        arrayList = new ArrayList<>(30);
        after = Clock.getBytecodeNum();
        expected = 50;
        actual = after - before;
        assertEquals(expected, actual);

        // holy shit, 45? geez that's expensive!
        for (int i = 0; i < 10; i++) {
            before = Clock.getBytecodeNum();
            arrayList.add(i);
            after = Clock.getBytecodeNum();
            expected = 45;
            actual = after - before;
            assertEquals(expected, actual);
        }

        linkedList = new LinkedList<>();
        for (int i = 0; i < 10; i++) {
            before = Clock.getBytecodeNum();
            linkedList.add(i);
            after = Clock.getBytecodeNum();
            if (i == 0) {
                expected = 50;
            } else {
                expected = 49;
            }
            actual = after - before;
            assertEquals(expected, actual);
        }

        // by default, hash set has size 16 and load factor 0.75, so we can store 12 elements before resizing
        hashSet = new HashSet<>();
        for (int i = 0; i < 12; i++) {
            before = Clock.getBytecodeNum();
            hashSet.add(i);
            after = Clock.getBytecodeNum();
            actual = after - before;
            if (i == 0) {
                expected = 155;
            } else {
                expected = 103;
            }
            assertEquals(expected, actual);
        }
        // add duplicates
        for (int i = 0; i < 12; i++) {
            before = Clock.getBytecodeNum();
            hashSet.add(i);
            after = Clock.getBytecodeNum();
            expected = 79;
            actual = after - before;
            assertEquals(expected, actual);
        }
        before = Clock.getBytecodeNum();
        hashSet.add(12);
        after = Clock.getBytecodeNum();
        expected = 587;
        actual = after - before;
        assertEquals(expected, actual);

        // simple example of an arraylist that's cheap to mantain
        before = Clock.getBytecodeNum();
        int capacity = 30;
        int size = 0;
        int[] simpleArrayList = new int[capacity];
        after = Clock.getBytecodeNum();
        expected = 37;
        actual = after - before;
        assertEquals(expected, actual);

        for (int i = 0; i < 10; i++) {
            before = Clock.getBytecodeNum();
            simpleArrayList[size] = i;
            size++;
            after = Clock.getBytecodeNum();
            expected = 6;
            actual = after - before;
            assertEquals(expected, actual);
        }
        // in this scenario, the cost is the same when using a one-liner
        for (int i = 0; i < 10; i++) {
            before = Clock.getBytecodeNum();
            simpleArrayList[size++] = i;
            after = Clock.getBytecodeNum();
            expected = 6;
            actual = after - before;
            assertEquals(expected, actual);
        }

        // todo:
        // hash map
    }


    public static void timeRobotControllerMethods(RobotController rc) {
        // this doesn't test everything, just a few interesting ones.
        // result: so the official bytecode cost is a bit of a lie. It actually costs:
        // 3 + numParams + officialBytecodeCost
        // Which means, for many methods, it's a good idea to store the result in a local variable instead of calling
        // it again.
        int before, after;
        int expected, actual;

        before = Clock.getBytecodeNum();
        rc.getID();
        after = Clock.getBytecodeNum();
        expected = 4;
        actual = after - before;
        assertEquals(expected, actual);

        before = Clock.getBytecodeNum();
        rc.getLocation();
        after = Clock.getBytecodeNum();
        expected = 4;
        actual = after - before;
        assertEquals(expected, actual);

        before = Clock.getBytecodeNum();
        MapLocation loc = rc.getLocation();
        after = Clock.getBytecodeNum();
        expected = 4;
        actual = after - before;
        assertEquals(expected, actual);

        before = Clock.getBytecodeNum();
        rc.canSenseRadiusSquared(4);
        after = Clock.getBytecodeNum();
        expected = 9;
        actual = after - before;
        assertEquals(expected, actual);

        before = Clock.getBytecodeNum();
        rc.senseNearbyRobots();
        after = Clock.getBytecodeNum();
        expected = 103;
        actual = after - before;
        assertEquals(expected, actual);

        before = Clock.getBytecodeNum();
        rc.canMove(Direction.NORTH);
        after = Clock.getBytecodeNum();
        expected = 14;
        actual = after - before;
        assertEquals(expected, actual);
    }

    private interface VoidInterface {
        void doIt();
    }

    private static void noop() {
    }

    public static void timeMethodReference() {
        int before, after;
        int expected, actual;

        before = Clock.getBytecodeNum();
        VoidInterface foo;
        after = Clock.getBytecodeNum();
        expected = 1;
        actual = after - before;
        assertEquals(expected, actual);

        before = Clock.getBytecodeNum();
        foo = RobotPlayer::noop;
        after = Clock.getBytecodeNum();
        expected = 2;
        actual = after - before;
        assertEquals(expected, actual);

        before = Clock.getBytecodeNum();
        foo.doIt();
        after = Clock.getBytecodeNum();
        expected = 3;
        actual = after - before;
        assertEquals(expected, actual);
    }

    private enum SmallEnum {
        ONE, TWO, THREE;
    }

    public static void timeSwitchStatement() {
        int before, after;
        int expected, actual;

        int x = 5;
        before = Clock.getBytecodeNum();
        switch (x) {
            case 5:
                break;
        }
        after = Clock.getBytecodeNum();
        expected = 3;
        actual = after - before;
        assertEquals(expected, actual);

        before = Clock.getBytecodeNum();
        switch (x) {
            default:
                break;
        }
        after = Clock.getBytecodeNum();
        expected = 3;
        actual = after - before;
        assertEquals(expected, actual);
        before = Clock.getBytecodeNum();
        switch (x) {
            case 0:
                break;
            case 1:
                break;
            case 2:
                break;
            case 3:
                break;
            case 4:
                break;
            case 5:
                break;
        }
        after = Clock.getBytecodeNum();
        expected = 3;
        actual = after - before;
        assertEquals(expected, actual);

        SmallEnum y = SmallEnum.TWO;
        before = Clock.getBytecodeNum();
        switch (y) {
            case ONE:
                break;
            case TWO:
                break;
            case THREE:
                break;
        }
        after = Clock.getBytecodeNum();
        expected = 39;
        actual = after - before;
        assertEquals(expected, actual);

        // same test as before, to check for static initialization
        before = Clock.getBytecodeNum();
        switch (y) {
            case ONE:
                break;
            case TWO:
                break;
            case THREE:
                break;
        }
        after = Clock.getBytecodeNum();
        expected = 6;
        actual = after - before;
        assertEquals(expected, actual);

        // same test as before, to check for static initialization
        before = Clock.getBytecodeNum();
        switch (y) {
            case ONE:
                break;
            case TWO:
                break;
            case THREE:
                break;
        }
        after = Clock.getBytecodeNum();
        expected = 6;
        actual = after - before;
        assertEquals(expected, actual);
        // conclusion: the first usage of an enum has a much higher cost (possibly due to static initialization)

        before = Clock.getBytecodeNum();
        switch (y) {
            case ONE:
                break;
            case THREE:
                break;
            case TWO:
                break;
        }
        after = Clock.getBytecodeNum();
        expected = 5;
        actual = after - before;
        assertEquals(expected, actual);

        before = Clock.getBytecodeNum();
        switch (y) {
            case TWO:
                break;
        }
        after = Clock.getBytecodeNum();
        expected = 5;
        actual = after - before;
        assertEquals(expected, actual);

        before = Clock.getBytecodeNum();
        switch (y) {
            default:
                break;
        }
        after = Clock.getBytecodeNum();
        expected = 5;
        actual = after - before;
        assertEquals(expected, actual);

        RobotType z = RobotType.MINER;
        before = Clock.getBytecodeNum();
        switch (z) {
            default:
                break;
        }
        after = Clock.getBytecodeNum();
        expected = 5;
        actual = after - before;
        assertEquals(expected, actual);

    }

    public static void timePrinting() {
        int before, after;
        int expected, actual;

        before = Clock.getBytecodeNum();
        System.out.println("timing print function--ignore this line");
        after = Clock.getBytecodeNum();
        expected = 3;
        actual = after - before;
        assertEquals(expected, actual);

        // try again, to ignore static initialization
        // dk: we print something as the start, so this is unnecessary
        before = Clock.getBytecodeNum();
        System.out.println("timing print function--ignore this line");
        after = Clock.getBytecodeNum();
        expected = 3;
        actual = after - before;
        assertEquals(expected, actual);

        before = Clock.getBytecodeNum();
        System.out.println("timing print function--ignore this line");
        after = Clock.getBytecodeNum();
        expected = 3;
        actual = after - before;
        assertEquals(expected, actual);

        // try a new turn
        Clock.yield();

        before = Clock.getBytecodeNum();
        System.out.println();
        after = Clock.getBytecodeNum();
        expected = 2;
        actual = after - before;
        assertEquals(expected, actual);

        before = Clock.getBytecodeNum();
        System.out.println();
        after = Clock.getBytecodeNum();
        expected = 2;
        actual = after - before;
        assertEquals(expected, actual);

        before = Clock.getBytecodeNum();
        System.out.println("timing print function--ignore this line");
        after = Clock.getBytecodeNum();
        expected = 3;
        actual = after - before;
        assertEquals(expected, actual);

        String a = "timing print function--";
        String b = "ignore this line";
        before = Clock.getBytecodeNum();
        String c = a + b;
        after = Clock.getBytecodeNum();
        expected = 9;
        actual = after - before;
        assertEquals(expected, actual);

        before = Clock.getBytecodeNum();
        System.out.println(a + b);
        after = Clock.getBytecodeNum();
        expected = 9;
        actual = after - before;
        assertEquals(expected, actual);
    }

    public static void timeMapLocationMethods(RobotController rc) {
        // this doesn't test everything, just a few interesting ones.
        // result: each call differs by just a few bytecodes, so initialize maplocations based on what's available to
        // you.
        int before, after;
        int expected, actual;

        before = Clock.getBytecodeNum();
        MapLocation a = new MapLocation(0, 0);
        after = Clock.getBytecodeNum();
        expected = 6;
        actual = after - before;
        assertEquals(expected, actual);

        before = Clock.getBytecodeNum();
        MapLocation b = rc.getLocation();
        after = Clock.getBytecodeNum();
        expected = 4;
        actual = after - before;
        assertEquals(expected, actual);

        MapLocation c = new MapLocation(0, 0);
        before = Clock.getBytecodeNum();
        MapLocation d = c.translate(30, 30);
        after = Clock.getBytecodeNum();
        expected = 7;
        actual = after - before;
        assertEquals(expected, actual);

        before = Clock.getBytecodeNum();
        MapLocation e = c.add(Direction.EAST);
        after = Clock.getBytecodeNum();
        expected = 6;
        actual = after - before;
        assertEquals(expected, actual);

        before = Clock.getBytecodeNum();
        MapLocation f = c.add(Direction.EAST);
        after = Clock.getBytecodeNum();
        expected = 6;
        actual = after - before;
        assertEquals(expected, actual);

        before = Clock.getBytecodeNum();
        MapLocation g = c.add(Direction.CENTER);
        after = Clock.getBytecodeNum();
        expected = 6;
        actual = after - before;
        assertEquals(expected, actual);
    }

    private static int idx(int x, int y, int height) {
        return x * height + y;
    }

    private static class Mat3x3 {
        int m00, m01, m02, m10, m11, m12, m20, m21, m22;

        public int get(int x, int y) {
            switch (x) {
                case 0:
                    switch (y) {
                        case 0:
                            return m00;
                        case 1:
                            return m01;
                        case 2:
                            return m02;
                    }
                case 1:
                    switch (y) {
                        case 0:
                            return m10;
                        case 1:
                            return m11;
                        case 2:
                            return m12;
                    }
                case 2:
                    switch (y) {
                        case 0:
                            return m20;
                        case 1:
                            return m21;
                        case 2:
                            return m22;
                    }
            }
            return -1;
        }
    }

    private static class Mat4x4 {
        int m00, m01, m02, m03, m10, m11, m12, m13, m20, m21, m22, m23, m30, m31, m32, m33;

        public int get(int x, int y) {
            switch (x) {
                case 0:
                    switch (y) {
                        case 0:
                            return m00;
                        case 1:
                            return m01;
                        case 2:
                            return m02;
                        case 3:
                            return m03;
                    }
                case 1:
                    switch (y) {
                        case 0:
                            return m10;
                        case 1:
                            return m11;
                        case 2:
                            return m12;
                        case 3:
                            return m13;
                    }
                case 2:
                    switch (y) {
                        case 0:
                            return m20;
                        case 1:
                            return m21;
                        case 2:
                            return m22;
                        case 3:
                            return m23;
                    }
                case 3:
                    switch (y) {
                        case 0:
                            return m30;
                        case 1:
                            return m31;
                        case 2:
                            return m32;
                        case 3:
                            return m33;
                    }
            }
            return -1;
        }
    }

    private static int makeNotConstant(int x) {
        return x;
    }

    public static void time2DArrayOptions() {
        // general observations:
        // A 2d array is often faster than a badly implemented 1d array. However, for certain operations, a 1d array
        // still wins. And if your size and width are known in advance, and all your memory access have constant
        // array indices, then just declaring a bunch of local variables might be the best option of all.
        // If you can pack a small datatype into a big one (e.g. 64 bools into a long), you can save bytecodes during
        // initialization. However, it will probably cost you more bytecodes on each lookup, so only use this
        // strategy for sparse data.

        int before, after;
        int expected, actual;

        final int WIDTH = 3, HEIGHT = 3;
        // by returning x and y from a function, we trick the compiler into thinking they are not constant.
        final int x = makeNotConstant(1), y = makeNotConstant(2);

        before = Clock.getBytecodeNum();
        int[][] plain2d = new int[WIDTH][HEIGHT];
        after = Clock.getBytecodeNum();
        expected = 13;
        actual = after - before;
        assertEquals(expected, actual);

        before = Clock.getBytecodeNum();
        int plain2dAccessed = plain2d[x][y];
        after = Clock.getBytecodeNum();
        expected = 7;
        actual = after - before;
        assertEquals(expected, actual);

        before = Clock.getBytecodeNum();
        int[] packed1d = new int[WIDTH * HEIGHT];
        after = Clock.getBytecodeNum();
        expected = 12;
        actual = after - before;
        assertEquals(expected, actual);

        // just to check that x and y are really being treated as variables
        before = Clock.getBytecodeNum();
        final int constantExpression = 1 * 3 + 2;
        after = Clock.getBytecodeNum();
        expected = 3;
        actual = after - before;
        assertEquals(expected, actual);

        before = Clock.getBytecodeNum();
        final int variableExpression = x * HEIGHT + y;
        after = Clock.getBytecodeNum();
        expected = 7;
        actual = after - before;
        assertEquals(expected, actual);

        before = Clock.getBytecodeNum();
        int packed1dAccessedMult = packed1d[x * HEIGHT + y];
        after = Clock.getBytecodeNum();
        expected = 9;
        actual = after - before;
        assertEquals(expected, actual);

        before = Clock.getBytecodeNum();
        int packed1dAccessedMethod = packed1d[idx(x, y, HEIGHT)];
        after = Clock.getBytecodeNum();
        expected = 13;
        actual = after - before;
        assertEquals(expected, actual);

        before = Clock.getBytecodeNum();
        Mat3x3 localVars = new Mat3x3();
        after = Clock.getBytecodeNum();
        expected = 9;
        actual = after - before;
        assertEquals(expected, actual);

        before = Clock.getBytecodeNum();
        int localVarsAccessed = localVars.get(x, y);
        after = Clock.getBytecodeNum();
        expected = 12;
        actual = after - before;
        assertEquals(expected, actual);

        before = Clock.getBytecodeNum();
        int localVarsAccessedConstant = localVars.m12;
        after = Clock.getBytecodeNum();
        expected = 4;
        actual = after - before;
        assertEquals(expected, actual);

        before = Clock.getBytecodeNum();
        int sum2d = 0;
        for (int i = WIDTH; --i >= 0; ) {
            for (int j = HEIGHT; --j >= 0; ) {
                sum2d += plain2d[i][j];
            }
        }
        after = Clock.getBytecodeNum();
        expected = 143;
        actual = after - before;
        assertEquals(expected, actual);

        before = Clock.getBytecodeNum();
        int sum2d_local = 0;
        int[] col;
        for (int i = WIDTH; --i >= 0; ) {
            col = plain2d[i];
            for (int j = HEIGHT; --j >= 0; ) {
                sum2d_local += col[j];
            }
        }
        after = Clock.getBytecodeNum();
        expected = 137;
        actual = after - before;
        assertEquals(expected, actual);

        before = Clock.getBytecodeNum();
        int sum1d = 0;
        for (int i = WIDTH; --i >= 0; ) {
            for (int j = HEIGHT; --j >= 0; ) {
                sum1d += packed1d[i * HEIGHT + j];
            }
        }
        after = Clock.getBytecodeNum();
        expected = 161;
        actual = after - before;
        assertEquals(expected, actual);

        before = Clock.getBytecodeNum();
        int sum1d_local = 0;
        for (int i = WIDTH; --i >= 0; ) {
            int offset = i * HEIGHT;
            for (int j = HEIGHT; --j >= 0; ) {
                sum1d_local += packed1d[offset + j];
            }
        }
        after = Clock.getBytecodeNum();
        expected = 155;
        actual = after - before;
        assertEquals(expected, actual);

        before = Clock.getBytecodeNum();
        int sum1d_blit = 0;
        for (int i = WIDTH * HEIGHT; --i >= 0; ) {
            sum1d_blit += packed1d[i];
        }
        after = Clock.getBytecodeNum();
        expected = 98;
        actual = after - before;
        assertEquals(expected, actual);

        before = Clock.getBytecodeNum();
        int sum_object = 0;
        for (int i = WIDTH; --i >= 0; ) {
            for (int j = HEIGHT; --j >= 0; ) {
                sum_object += localVars.get(i, j);
            }
        }
        after = Clock.getBytecodeNum();
        expected = 188;
        actual = after - before;
        assertEquals(expected, actual);

        before = Clock.getBytecodeNum();
        int sum_object_hardcoded = localVars.m00 + localVars.m01 + localVars.m02
                + localVars.m10 + localVars.m11 + localVars.m12
                + localVars.m20 + localVars.m21 + localVars.m22;
        after = Clock.getBytecodeNum();
        expected = 28;
        actual = after - before;
        assertEquals(expected, actual);

        before = Clock.getBytecodeNum();
        int[] packed1dBig = new int[4 * 4];
        after = Clock.getBytecodeNum();
        expected = 19;
        actual = after - before;
        assertEquals(expected, actual);

        before = Clock.getBytecodeNum();
        int packed1dBigAccessedMult = packed1dBig[x * 4 + y];
        after = Clock.getBytecodeNum();
        expected = 9;
        actual = after - before;
        assertEquals(expected, actual);

        before = Clock.getBytecodeNum();
        int packed1dBigAccessedMethod = packed1dBig[idx(x, y, 4)];
        after = Clock.getBytecodeNum();
        expected = 13;
        actual = after - before;
        assertEquals(expected, actual);

        before = Clock.getBytecodeNum();
        Mat4x4 localVarsBig = new Mat4x4();
        after = Clock.getBytecodeNum();
        expected = 9; // same cost as Mat3x3!
        actual = after - before;
        assertEquals(expected, actual);

        before = Clock.getBytecodeNum();
        int localVarsBigAccessed = localVarsBig.get(x, y);
        after = Clock.getBytecodeNum();
        expected = 12;
        actual = after - before;
        assertEquals(expected, actual);

        before = Clock.getBytecodeNum();
        int localVarsBigAccessedConstant = localVarsBig.m12;
        after = Clock.getBytecodeNum();
        expected = 4;
        actual = after - before;
        assertEquals(expected, actual);

        before = Clock.getBytecodeNum();
        long[] packedDense1dBig = new long[2 * 4];
        after = Clock.getBytecodeNum();
        expected = 11;
        actual = after - before;
        assertEquals(expected, actual);

        before = Clock.getBytecodeNum();
        long packedDense1dBigAccessedMult = packedDense1dBig[x * 2 + (y >> 1)];
        // this actually gives us two blocks. in reality, we would then sometimes need to shift the result by 32 bits
        after = Clock.getBytecodeNum();
        expected = 11;
        actual = after - before;
        assertEquals(expected, actual);

    }

}