package controllers;

import actors.UserActor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import play.libs.F.Promise;
import play.libs.Json;
import play.libs.ws.WS;
import play.libs.ws.WSResponse;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import play.mvc.WebSocket;

import java.util.List;
import java.util.Random;

import static java.util.stream.Collectors.toList;
import static utils.Streams.stream;

public class Tweets extends Controller {

    public static Result index() {
        return ok(views.html.index.render("TweetMap"));
    }



    /**
     * Обещание результата.  Асинхронный запрос к серверу.
     * После колбека обещания, маппит результат(обещание) как новое значение.
     * The results of the Promise in this example are mapped to a result (HTTP 200 OK) that gets returned to the client.
     **/
    public static Promise<Result> search(String query) {
        return fetchTweets(query)
                .map(jsonNode -> ok(jsonNode));
    }

    /**
     * Получить последние твиты, и вернуть обещание json результата.
     * Получение твитов асинхронно и заполнить поля обещания когда результат вернется.
     * This fetches the tweets asynchronously and fulfills the promise when the results are returned.
     * Затем результат маппится(или трансформируется) в JSON
     * Результат покрывается, чтобы вернуть ошибку, если твитты небыли найдены.
     * The updated fetchTweets transforms the responses as it does the mapping to add the geo-coding.
     *
     * @param query
     * @return
     */
    public static Promise<JsonNode> fetchTweets(String query) {
        Promise<WSResponse> responsePromise = WS.url("http://twitter-search-proxy.herokuapp.com/search/tweets").setQueryParameter("q", query).get();
        //can also map using method references - WSResponse::asJson
        return responsePromise
                .filter(response -> response.getStatus() == Http.Status.OK)
                .map(response -> transformStatusResponses(response.asJson()))
                .recover(Tweets::errorResponse);
    }



    /**
     * Transform the json responses by adding geo coordinates to each tweet.
     * Not sure this is the best way to manipulate the Json.  Mostly an experiment
     * using streams and json based on reactive stocks activator template.
     *
     * @param jsonNode
     */
    private static JsonNode transformStatusResponses(JsonNode jsonNode) {
        //create a stream view of the jsonNode iterator
        List<JsonNode> newJsonList = stream(jsonNode.findPath("statuses"))
                //map the stream of json to update the values to have the geo-info
                .map(json -> setCoordinates((ObjectNode) json))
                .collect(toList());

        ObjectNode objectNode = JsonNodeFactory.instance.objectNode();
        objectNode.putArray("statuses").addAll(newJsonList);

        return objectNode;
    }

    /**
     * Most tweets don't actually have their geo-location set so just randomly set the latitude and longitude.
     * And sadly there is a bug in the randomizer where the tweets tend to locate themselves near the top of the window.
     *
     * @param nextStatus
     */
    private static ObjectNode setCoordinates(ObjectNode nextStatus) {
        if(nextStatus.get("geo").get("coordinates") != null)
            nextStatus.putArray("coordinates").add(nextStatus.get("geo").get("coordinates").get(0))
                                              .add(nextStatus.get("geo").get("coordinates").get(1));
        else
            nextStatus.putArray("coordinates").add(randomLat()).add(randomLon());

        return nextStatus;
    }

    private static Random rand = new java.util.Random();

    private static double randomLat() {
        return (rand.nextDouble() * 100) - 50;
    }

    private static double randomLon() {
        return (rand.nextDouble() * 300) - 150;
    }

    /**
     * The error response when the twitter search fails.
     *
     * @param ignored
     * @return
     */
    public static JsonNode errorResponse(Throwable ignored) {
        return Json.newObject().put("error", "Could not fetch the tweets");
    }

    /**
     * Создание контроллера ВебСокета который конвертирует отправленные данные в JSON
     * Создание ВебСокета через добавление лямбды на вход
     * Лямбда будет вызвана когда новое сообщение проходит через канал
     * Сообщение парсится как json запрос, и затем твитты получаются на основании этого запроса.
     * Полученные твитты возвращают обещание и колбэк в обещание(onRedeem) как лямбды которые пишут результат клиенту.
     */
    public static WebSocket<JsonNode> ws() {
        //создание UserActor соединенного с каналлом ВебСокета
        return WebSocket.withActor(out -> UserActor.props(out));
    }


}
