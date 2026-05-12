package com.project.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.project.model.User;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

public class CodeforcesUserService {

    private static final String BASE_URL = "https://codeforces.com";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);

    private final HttpClient httpClient;

    public CodeforcesUserService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .build();
    }

    public Optional<User> fetchUserProfile(String handle) throws IOException, InterruptedException {
        String encodedHandle = URLEncoder.encode(handle.trim(), StandardCharsets.UTF_8);
        URI uri = URI.create(BASE_URL + "/api/user.info?handles=" + encodedHandle);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("User-Agent", "AlgoProfiler/1.0")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() == 400 || response.statusCode() == 404) {
            return Optional.empty();
        }
        if (response.statusCode() >= 500) {
            throw new IOException("Codeforces API lỗi HTTP " + response.statusCode());
        }

        JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
        if (!"OK".equalsIgnoreCase(getString(root, "status"))) {
            return Optional.empty();
        }

        JsonArray result = root.getAsJsonArray("result");
        if (result == null || result.isEmpty()) {
            return Optional.empty();
        }

        JsonObject profile = result.get(0).getAsJsonObject();
        User user = new User();
        user.setHandle(getString(profile, "handle"));
        user.setDisplayName(getString(profile, "handle"));
        user.setRating(getNullableInt(profile, "rating"));
        user.setMaxRating(getNullableInt(profile, "maxRating"));
        user.setRankTitle(getString(profile, "rank"));
        user.setTotalScore(0D);
        user.setCrawlEnabled(true);
        return Optional.of(user);
    }

    private String getString(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return "";
        }
        return object.get(key).getAsString();
    }

    private Integer getNullableInt(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return null;
        }
        try {
            return object.get(key).getAsInt();
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
