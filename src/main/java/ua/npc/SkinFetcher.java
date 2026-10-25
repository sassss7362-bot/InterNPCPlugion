package ua.npc;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

/** Асинхронно бере скін з Mojang API за ніком. */
public class SkinFetcher {
    public record Skin(String value, String signature) {}

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    public static CompletableFuture<Skin> byName(String nick) {
        return get("https://api.mojang.com/users/profiles/minecraft/" + nick).thenCompose(body -> {
            if (body == null) return CompletableFuture.completedFuture(null);
            String id = JsonParser.parseString(body).getAsJsonObject().get("id").getAsString();
            return get("https://sessionserver.mojang.com/session/minecraft/profile/" + id + "?unsigned=false")
                    .thenApply(b -> {
                        if (b == null) return null;
                        JsonArray props = JsonParser.parseString(b).getAsJsonObject().getAsJsonArray("properties");
                        for (var el : props) {
                            JsonObject o = el.getAsJsonObject();
                            if (o.get("name").getAsString().equals("textures"))
                                return new Skin(o.get("value").getAsString(), o.get("signature").getAsString());
                        }
                        return null;
                    });
        }).exceptionally(t -> null);
    }

    private static CompletableFuture<String> get(String url) {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).GET().build();
        return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenApply(r -> r.statusCode() == 200 ? r.body() : null);
    }
}
