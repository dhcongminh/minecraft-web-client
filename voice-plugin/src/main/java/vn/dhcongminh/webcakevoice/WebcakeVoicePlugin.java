package vn.dhcongminh.webcakevoice;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.scheduler.BukkitTask;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Server-authoritative bridge for Minecraft Web Client's LiveKit voice channel. */
public final class WebcakeVoicePlugin extends JavaPlugin implements Listener, PluginMessageListener {
    private static final String CHANNEL = "minecraft-web-client:voice-chat";
    private final Map<UUID, Mode> modes = new ConcurrentHashMap<>();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private BukkitTask nearbyTask;
    private int availabilityTicks;

    private enum Mode { GLOBAL, AROUND }

    @Override public void onEnable() {
        saveDefaultConfig();
        getServer().getMessenger().registerIncomingPluginChannel(this, CHANNEL, this);
        getServer().getMessenger().registerOutgoingPluginChannel(this, CHANNEL);
        getServer().getPluginManager().registerEvents(this, this);
        nearbyTask = Bukkit.getScheduler().runTaskTimer(this, this::broadcastNearby, 20L, 20L);
        getLogger().info("Webcake voice bridge enabled");
    }

    @Override public void onDisable() {
        if (nearbyTask != null) nearbyTask.cancel();
        modes.clear();
        getServer().getMessenger().unregisterIncomingPluginChannel(this, CHANNEL);
        getServer().getMessenger().unregisterOutgoingPluginChannel(this, CHANNEL);
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) { modes.remove(event.getPlayer().getUniqueId()); }
    @EventHandler public void onQuit(PlayerQuitEvent event) { modes.remove(event.getPlayer().getUniqueId()); }

    @Override public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!CHANNEL.equals(channel)) return;
        try {
            Packet packet = Packet.decode(message);
            JsonObject payload = packet.json.isBlank() ? new JsonObject() : JsonParser.parseString(packet.json).getAsJsonObject();
            if ("enable".equals(packet.action)) requestToken(player, parseMode(payload.has("mode") ? payload.get("mode").getAsString() : getConfig().getString("default-mode", "around")));
            if ("mode".equals(packet.action)) requestToken(player, parseMode(payload.has("mode") ? payload.get("mode").getAsString() : "around"));
            if ("disable".equals(packet.action)) modes.remove(player.getUniqueId());
        } catch (Exception exception) {
            getLogger().warning("Bad voice packet from " + player.getName() + ": " + exception.getMessage());
        }
    }

    private Mode parseMode(String mode) { return "global".equalsIgnoreCase(mode) ? Mode.GLOBAL : Mode.AROUND; }

    private void requestToken(Player player, Mode mode) {
        if (!isAuthenticated(player)) {
            send(player, "error", object("message", "Authenticate with /login before using voice chat."));
            return;
        }
        modes.put(player.getUniqueId(), mode);
        JsonObject request = new JsonObject();
        request.addProperty("identity", player.getUniqueId().toString());
        request.addProperty("name", player.getName());
        request.addProperty("server", getConfig().getString("server-id"));
        request.addProperty("mode", mode.name().toLowerCase());
        request.addProperty("room", roomName(mode));
        request.addProperty("hearingRange", getConfig().getDouble("hearing-range", 32));
        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(getConfig().getString("issuer-url")))
                .timeout(Duration.ofSeconds(8))
                .header("Content-Type", "application/json")
                .header("X-Voice-Plugin-Secret", getConfig().getString("issuer-secret"))
                .POST(HttpRequest.BodyPublishers.ofString(request.toString())).build();
        http.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString()).whenComplete((response, error) -> {
            if (error != null || response.statusCode() != 200) {
                getLogger().warning("Voice issuer failed for " + player.getName() + ": " + (error == null ? response.statusCode() : error.getMessage()));
                Bukkit.getScheduler().runTask(this, () -> send(player, "error", object("message", "Voice service is unavailable.")));
                return;
            }
            Bukkit.getScheduler().runTask(this, () -> {
                if (!player.isOnline() || !isAuthenticated(player)) return;
                JsonObject config = JsonParser.parseString(response.body()).getAsJsonObject();
                config.addProperty("mode", mode.name().toLowerCase());
                config.addProperty("hearingRange", getConfig().getDouble("hearing-range", 32));
                send(player, "config", config);
            });
        });
    }

    private String roomName(Mode mode) { return getConfig().getString("server-id") + ":" + (mode == Mode.GLOBAL ? "global" : "around"); }

    /** AuthMe is optional at compile time but mandatory by default at runtime. */
    private boolean isAuthenticated(Player player) {
        if (!getConfig().getBoolean("require-authme", true)) return true;
        try {
            Class<?> apiClass = Class.forName("fr.xephi.authme.api.v3.AuthMeApi");
            Method instance = apiClass.getMethod("getInstance");
            Object api = instance.invoke(null);
            Method authenticated = apiClass.getMethod("isAuthenticated", Player.class);
            return Boolean.TRUE.equals(authenticated.invoke(api, player));
        } catch (Exception ignored) {
            return false;
        }
    }

    private void broadcastNearby() {
        // The web client registers its custom channel after joining. Re-advertise
        // periodically so the first packet is never lost during that handshake.
        if (++availabilityTicks >= 5) {
            availabilityTicks = 0;
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (isAuthenticated(player)) send(player, "available", new JsonObject());
            }
        }
        double range = getConfig().getDouble("hearing-range", 32);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (modes.get(player.getUniqueId()) != Mode.AROUND || !isAuthenticated(player)) continue;
            JsonArray nearby = new JsonArray();
            for (Player other : player.getWorld().getPlayers()) {
                if (other.equals(player) || modes.get(other.getUniqueId()) != Mode.AROUND || !isAuthenticated(other)) continue;
                if (player.getLocation().distanceSquared(other.getLocation()) <= range * range) {
                    JsonObject entry = new JsonObject();
                    entry.addProperty("identity", other.getUniqueId().toString());
                    entry.addProperty("username", other.getName());
                    nearby.add(entry);
                }
            }
            JsonObject payload = new JsonObject();
            payload.add("players", nearby);
            payload.add("forceMuted", new JsonArray());
            send(player, "nearby", payload);
        }
    }

    private JsonObject object(String key, String value) { JsonObject result = new JsonObject(); result.addProperty(key, value); return result; }
    private void send(Player player, String action, JsonObject payload) { if (player.isOnline()) player.sendPluginMessage(this, CHANNEL, Packet.encode(action, payload.toString())); }

    private record Packet(String action, String json) {
        static Packet decode(byte[] data) throws IOException {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
            return new Packet(read(in), read(in));
        }
        static byte[] encode(String action, String json) {
            try {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(bytes);
                write(out, action); write(out, json); return bytes.toByteArray();
            } catch (IOException impossible) { throw new IllegalStateException(impossible); }
        }
        private static String read(DataInputStream in) throws IOException { byte[] value = in.readNBytes(in.readUnsignedShort()); return new String(value, StandardCharsets.UTF_8); }
        private static void write(DataOutputStream out, String value) throws IOException { byte[] bytes = value.getBytes(StandardCharsets.UTF_8); out.writeShort(bytes.length); out.write(bytes); }
    }
}
