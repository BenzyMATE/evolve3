package org.benski.evolv3.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import lombok.Getter;
import org.benski.evolv3.Main;
import org.benski.evolv3.api.dto.ApiResponseDto;
import org.benski.evolv3.api.dto.PendingDataDto;
import org.benski.evolv3.api.dto.PendingType;
import org.benski.evolv3.api.dto.PlayerProfileDto;

/**
 * HTTP client for communicating with the Evolve C# API. All methods return {@link
 * CompletableFuture} that complete on the background executor thread. Callers that require main
 * thread callbacks should chain {@code .thenAcceptAsync(cb, TaskManager.getInstance()::runSync)}.
 */
public final class EvolveApiClient {

  private static final Type PLAYER_RESPONSE_TYPE =
      new TypeToken<ApiResponseDto<PlayerProfileDto>>() {}.getType();

  private static final Type PENDING_DATA_LIST_TYPE =
      new TypeToken<ApiResponseDto<List<PendingDataDto>>>() {}.getType();

  @Getter private static EvolveApiClient instance;

  private final String baseUrl;
  private final String apiKey;
  private final HttpClient http;
  private final Gson gson;
  @Getter private final ExecutorService executor;
  private final Logger logger = Main.getInstance().getLogger();

  /**
   * Creates and registers the singleton API client.
   *
   * @param baseUrl the base URL of the Evolve C# API (trailing slash is stripped automatically)
   * @param sslVerify when {@code false}, SSL certificate verification is disabled (dev only)
   * @param apiKey shared secret sent as the {@code X-Api-Key} header on every request, or {@code
   *     null}/empty to send no header
   */
  public EvolveApiClient(String baseUrl, boolean sslVerify, String apiKey) {
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    this.apiKey = apiKey;
    this.executor =
        Executors.newFixedThreadPool(Math.max(4, Runtime.getRuntime().availableProcessors()));
    HttpClient.Builder builder =
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).executor(this.executor);
    if (!sslVerify) {
      builder.sslContext(buildTrustAllSslContext());
    }
    this.http = builder.build();
    this.gson = new GsonBuilder().serializeNulls().create();
    instance = this;
  }

  /**
   * Applies the shared {@code Content-Type} header and, when configured, the {@code X-Api-Key}
   * header to a request builder.
   */
  private HttpRequest.Builder withAuth(HttpRequest.Builder builder) {
    builder.header("Content-Type", "application/json");
    if (apiKey != null && !apiKey.isEmpty()) {
      builder.header("X-Api-Key", apiKey);
    }
    return builder;
  }

  /** Builds trust all ssl context. */
  private static SSLContext buildTrustAllSslContext() {
    try {
      TrustManager[] trustAll =
          new TrustManager[] {
            new X509TrustManager() {
              /** Returns the accepted issuers. */
              public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
              }

              /** Checks client trusted. */
              public void checkClientTrusted(X509Certificate[] c, String a) {}

              /** Checks server trusted. */
              public void checkServerTrusted(X509Certificate[] c, String a) {}
            }
          };
      SSLContext ctx = SSLContext.getInstance("TLS");
      ctx.init(null, trustAll, new SecureRandom());
      return ctx;
    } catch (NoSuchAlgorithmException | KeyManagementException e) {
      throw new IllegalStateException("Failed to build trust-all SSLContext", e);
    }
  }

  /**
   * Fetches a player profile by UUID via {@code GET /api/player/{uuid}}.
   *
   * @param uuid the player's UUID
   * @return a future that resolves to the profile DTO, or {@code null} on 404 or error
   */
  public CompletableFuture<PlayerProfileDto> getByIdAsync(UUID uuid) {
    return CompletableFuture.supplyAsync(
        () -> {
          try {
            HttpRequest request =
                withAuth(HttpRequest.newBuilder())
                    .uri(URI.create(baseUrl + "/api/player/" + uuid))
                    .GET()
                    .timeout(Duration.ofSeconds(60))
                    .build();

            HttpResponse<String> response =
                http.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 404 || response.statusCode() == 204) return null;

            if (response.statusCode() != 200) {
              logger.warning("GET /api/player/" + uuid + " returned " + response.statusCode());
              return null;
            }

            ApiResponseDto<PlayerProfileDto> apiResponse =
                gson.fromJson(response.body(), PLAYER_RESPONSE_TYPE);

            return (apiResponse == null || !apiResponse.success) ? null : apiResponse.data;
          } catch (Exception e) {
            logger.severe("Failed to GET player " + uuid + ": " + e.getMessage());
            return null;
          }
        },
        executor);
  }

  /**
   * Creates a new player profile via {@code POST /api/player}.
   *
   * @param dto the profile data to create
   * @return a future that resolves to {@code true} on success
   */
  public CompletableFuture<Boolean> createAsync(PlayerProfileDto dto) {
    return CompletableFuture.supplyAsync(
        () -> {
          try {
            String body = gson.toJson(dto);
            HttpRequest request =
                withAuth(HttpRequest.newBuilder())
                    .uri(URI.create(baseUrl + "/api/player"))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(60))
                    .build();

            HttpResponse<String> response =
                http.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
              logger.warning(
                  "POST /api/player returned " + response.statusCode() + ": " + response.body());
              return false;
            }

            return true;
          } catch (Exception e) {
            logger.severe("Failed to POST player: " + e.getMessage());
            return false;
          }
        },
        executor);
  }

  /**
   * Updates an existing player profile via {@code PUT /api/player}. The returned future completes
   * on the executor thread — suitable for blocking shutdown saves via {@link
   * CompletableFuture#allOf}. Chain {@code .thenAcceptAsync(cb, TaskManager::runSync)} when a
   * main-thread callback is needed.
   *
   * @param dto the profile data to persist
   * @return a future that resolves to {@code true} on success
   */
  public CompletableFuture<Boolean> updateAsync(PlayerProfileDto dto) {
    return CompletableFuture.supplyAsync(
        () -> {
          try {
            String body = gson.toJson(dto);
            HttpRequest request =
                withAuth(HttpRequest.newBuilder())
                    .uri(URI.create(baseUrl + "/api/player"))
                    .PUT(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(60))
                    .build();

            HttpResponse<String> response =
                http.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
              logger.warning(
                  "PUT /api/player returned " + response.statusCode() + ": " + response.body());
              return false;
            }

            return true;
          } catch (Exception e) {
            logger.severe("Failed to PUT player: " + e.getMessage());
            return false;
          }
        },
        executor);
  }

  /**
   * Fetches all pending animation records via {@code GET /api/pending}. Returns every type
   * (enchant, evolution, prestige) in a single call; callers filter by {@link PendingDataDto#type}.
   *
   * @return a future resolving to the full list, or {@code null} on error
   */
  public CompletableFuture<List<PendingDataDto>> getAllPendingDataAsync() {
    return CompletableFuture.supplyAsync(
        () -> {
          try {
            HttpRequest request =
                withAuth(HttpRequest.newBuilder())
                    .uri(URI.create(baseUrl + "/api/pending"))
                    .GET()
                    .timeout(Duration.ofSeconds(60))
                    .build();

            HttpResponse<String> response =
                http.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 404 || response.statusCode() == 204) return null;

            if (response.statusCode() != 200) {
              logger.warning("GET /api/pending returned " + response.statusCode());
              return null;
            }

            ApiResponseDto<List<PendingDataDto>> apiResponse =
                gson.fromJson(response.body(), PENDING_DATA_LIST_TYPE);

            return (apiResponse == null || !apiResponse.success) ? null : apiResponse.data;
          } catch (Exception e) {
            logger.severe("Failed to GET pending data: " + e.getMessage());
            return null;
          }
        },
        executor);
  }

  /**
   * Upserts a pending animation record via {@code PUT /api/pending}. The record is identified on
   * the server by the composite key ({@link PendingDataDto#playerUuid}, {@link
   * PendingDataDto#type}).
   *
   * @param dto the pending record to save
   * @return a future resolving to {@code true} on success
   */
  public CompletableFuture<Boolean> upsertPendingDataAsync(PendingDataDto dto) {
    return CompletableFuture.supplyAsync(
        () -> {
          try {
            String body = gson.toJson(dto);
            HttpRequest request =
                withAuth(HttpRequest.newBuilder())
                    .uri(URI.create(baseUrl + "/api/pending"))
                    .PUT(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(60))
                    .build();

            HttpResponse<String> response =
                http.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
              logger.warning(
                  "PUT /api/pending returned " + response.statusCode() + ": " + response.body());
              return false;
            }

            return true;
          } catch (Exception e) {
            logger.severe("Failed to PUT pending data: " + e.getMessage());
            return false;
          }
        },
        executor);
  }

  /**
   * Deletes a pending animation record via {@code DELETE /api/pending/{playerUuid}/{type}}.
   *
   * @param playerUuid the player's UUID
   * @param type the pending type to delete (e.g. {@link PendingType#ENCHANT})
   * @return a future resolving to {@code true} on success or 404
   */
  public CompletableFuture<Boolean> deletePendingDataAsync(UUID playerUuid, PendingType type) {
    return CompletableFuture.supplyAsync(
        () -> {
          try {
            HttpRequest request =
                withAuth(HttpRequest.newBuilder())
                    .uri(URI.create(baseUrl + "/api/pending/" + playerUuid + "/" + type.name()))
                    .DELETE()
                    .timeout(Duration.ofSeconds(60))
                    .build();

            HttpResponse<String> response =
                http.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200 && response.statusCode() != 404) {
              logger.warning(
                  "DELETE /api/pending/"
                      + playerUuid
                      + "/"
                      + type.name()
                      + " returned "
                      + response.statusCode());
              return false;
            }

            return true;
          } catch (Exception e) {
            logger.severe(
                "Failed to DELETE pending " + type + " for " + playerUuid + ": " + e.getMessage());
            return false;
          }
        },
        executor);
  }

  /**
   * Shuts down the background executor, blocking until all in-flight requests complete or the
   * 10-second timeout elapses.
   */
  public void shutdown() {
    executor.shutdown();
    try {
      if (!executor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)) {
        executor.shutdownNow();
      }
    } catch (InterruptedException e) {
      executor.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }
}
