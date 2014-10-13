package actors;

import akka.actor.*;
import akka.japi.pf.ReceiveBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import controllers.Tweets;
import scala.concurrent.duration.Duration;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class UserActor extends AbstractActor {

    /**
     * Запрос, чтобы искать пустое, если запрос не был отправлен пользователем
     */
    public Optional<String> optQuery = Optional.empty();

    /**
     * Creates a new UserActor using these Props.
     *
     * @param out
     * @return
     */
    public static Props props(ActorRef out) {
        return Props.create(UserActor.class, out);
    }

    /**
     * ActorRef используется чтобы отвечать веб-сокету клиента
     * Он создается фреймворком, и объявляется, когда UserActor инициализировано
     */
    private final ActorRef out;

    /**
     * Конструктор и блок получения данных
     *
     * @param out
     */
    public UserActor(ActorRef out) {
        this.out = out;

        receive(ReceiveBuilder.
                        //json сообдение от клиента, получаем запрос и получаем твитты.
                                match(JsonNode.class, jsonNode -> {
                            String query = jsonNode.findPath("query").textValue();
                            optQuery = Optional.of(query);
                            runFetchTweets(query);
                        }).
                        //The Update message is sent from the scheduler.  When the Actor recieves the
                                //message fetch the tweets only if there is a query from the user.
                                match(Update.class, update -> optQuery.ifPresent(this::runFetchTweets)).
                        matchAny(o -> System.out.println("received unknown message")).build()
        );
    }

    /**
     * Fetch the latest tweets for a given query and send the results to
     * the out actor - which in turns sends it back up to the client via a websocket.
     *
     * @param query
     */
    private void runFetchTweets(String query) {
        Tweets.fetchTweets(query).onRedeem(json -> {
            out.tell(json, self());
        });
    }


    /**
     * The Update class is used to send a message to this actor to
     * re-run the query and send the results to the client.
     */
    public static final class Update {
    }

    private final ActorSystem system = getContext().system();

   /* //This will schedule to send the Update message
    //to this actor after 0ms repeating every 5s.  This will cause this actor to search for new tweets every 5 seconds.
    Cancellable cancellable = system.scheduler().schedule(Duration.Zero(),
            Duration.create(5, TimeUnit.SECONDS), self(), new Update(),
            system.dispatcher(), null);*/

}