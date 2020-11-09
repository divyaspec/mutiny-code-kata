package mutiny;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.tuples.Tuple2;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static mutiny.predef.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.iterableWithSize;

public class UniKata {

    // Read THIS before you start this Kata: https://smallrye.io/smallrye-mutiny/

    public static final String SUCCESFULL_RESULT = "...";

    @Test
    public void supplier(){
        Supplier<Integer> f = () -> Integer.valueOf(42);

        assertThat(f.get(), is(42));
    }

    @Test
    public void function(){
        Function<Integer, Integer> square = t -> t * t;

        assertThat(square.apply(4), is(16));
    }

    @Test
    public void biFunction(){
        BiFunction<Integer, Integer, Integer> add = (a, b) -> a + b;

        assertThat(add.apply(2, 3), is(5));
    }

    @Test
    public void consumer(){
        Integer received = null;
        AtomicInteger r = new AtomicInteger();
        Consumer<Integer> callback = r::set;

        callback.accept(2);
        received = r.get();
        assertThat(received, is(2));
    }

    @Test
    public void constant_can_be_lifted_to_uni(){
        Uni<Integer> res = Uni.createFrom().item(() -> Integer.valueOf(3));

        eventually(res, is(3));
    }

    @Test
    public void unis_can_be_combined_to_tuple(){
        Uni<Integer> x = pure(3);
        Uni<String> y = pure("4");

        Uni<Tuple2<Integer, String>> xy = Uni.combine().all().unis(x, y).asTuple();

        eventually(xy, is(Tuple2.of(3, "4")));
    }

    @Test
    public void can_run_one_uni_multiple_times_and_get_different_results(){
        Random r = new Random();
        Uni<Integer> random = Uni.createFrom().emitter(e -> {
            e.complete(r.nextInt());
            e.complete(r.nextInt());
        });

        Uni<Tuple2<Integer, Integer>> xy = Uni.combine().all().unis(random, random).asTuple();

        eventually(xy, x -> x.getItem1() != x.getItem2() );
    }

    @Test
    public void uni_can_recover_on_failure(){
        Uni<Integer> random = Uni.createFrom().item(() -> error("Boom!"));
        final int FALLBACK = -1;
        random.onFailure().recoverWithItem(FALLBACK);

        eventually( random, is(-1) );
    }

    @Test
    public void uni_can_retry(){
        AtomicInteger attempts = new AtomicInteger(0);
        Uni<String> http_get = Uni.createFrom().item(() -> (attempts.incrementAndGet() < 3) ? error("Boom!") : SUCCESFULL_RESULT);
        http_get.onFailure().retry().atMost(2).subscribeAsCompletionStage().complete(SUCCESFULL_RESULT);
        eventually( http_get, is(SUCCESFULL_RESULT));
    }

    @Test public void uni_value_can_be_mapped(){
        int item1 = new Random().nextInt();
        int item2 = new Random().nextInt();
        Multi<Integer> multi = Multi.createFrom().emitter(e -> {
            e.emit(item1);
            e.emit(item2);
        });
        //multi to uni tansform
        Uni<List<Integer>> list = multi.transform().byTakingFirstItems(2).collectItems().asList();
        eventually(Duration.ofSeconds(1), list.map(should(is(list(item1, item2)))));

    }

    @Test public void uni_can_be_chained_with_uni(){
        int r = new Random().nextInt();
        Uni<Integer> uni = Uni.createFrom().item(() -> r + 1);
        Uni<Integer> chain = uni.chain(i -> Uni.createFrom().item(i + 1));
        eventually(chain, is(r + 2));
    }

    @Test public void uni_can_be_chained_with_uni_ignoring_the_output(){
        int r = new Random().nextInt();
        Uni<Integer> uni = Uni.createFrom().item(() -> r + 1);
        Uni<Integer> secondUni = uni.onItem().transformToUni(ignored -> Uni.createFrom().item(r));
        eventually(secondUni, is(r));

    }


}

