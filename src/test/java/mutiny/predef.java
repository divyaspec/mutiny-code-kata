package mutiny;

import io.smallrye.mutiny.Uni;
import org.hamcrest.Matcher;
import org.hamcrest.MatcherAssert;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class predef {
    public static <T> T error(String msg){
         throw new RuntimeException(msg);
    }

    public static <T> Uni<T> pure(T x) {
        return Uni.createFrom().item(x);
    }

    public static <T> void eventually(Uni<T> x, Matcher<? super T> p){
        x.onItem().invoke(v -> assertThat(v, p))
                .await().atMost(Duration.ofSeconds(3));
    }

    public static <T> void eventually(Uni<T> x, Predicate<T> p){
        x.onItem().invoke(v -> assertTrue(p.test(v)))
                .await().atMost(Duration.ofSeconds(3));
    }

    public static <T> T eventually(Duration timeout, Uni<T> test) {
        Supplier<T> tSupplier = () -> test.runSubscriptionOn(pool).await().atMost(timeout);
        return Uni.createFrom()
                .item(tSupplier)
                .onFailure().invoke(t -> t.printStackTrace())
                .onFailure().retry().atMost(2)
                .await().atMost(timeout.multipliedBy(2));

    }

    public static <T> Function<T, Assertion> should(Matcher<T> matcher) {
        return acc -> {
            assertThat(acc, matcher);
            return Assertion.assertion;
        };
    }

    public static <T> List<T> list(T... items) {
        return Arrays.asList(items);
    }

    public static class Assertion {
        public static final Assertion assertion = new Assertion();
    }

    private static final ForkJoinPool pool = new ForkJoinPool(3);
}
