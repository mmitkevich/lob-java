package org.freeticks;

import java.util.function.Function;

public class Expect {

    @FunctionalInterface
    public interface  Condition<T> {
        void apply(T found, Test test);
    }

    public static <T> Condition<T> anything() {
        return null;
    }

    public static <T> Condition<T> all(Condition<T> ...conds) {
        return (T found, Test test) -> {
            for (Condition<T> cond : conds) {
                cond.apply(found, test);
            }
        };
    }

    public static <T> Condition<T> eq(T expected, String comment) {
        return (found, test) -> {
            if (!found.equals(expected)) {
                if (test != null && test.out != null)
                    test.fail("%s: %s instead of %s\n", comment, found, expected);
            }
        };
    }

    public static <T,Q> Condition<T> eq(Function<T,Q> fld, Q expected, String comment) {
        return (found, test) -> {
            Q value = fld.apply(found);
            if (!expected.equals(value)) {
                if (test!= null)
                    test.fail("%s: %s %s instead of %s %s\n", comment, value.getClass().getName(), value, expected.getClass().getName(), expected);
            }
        };
    }

}
