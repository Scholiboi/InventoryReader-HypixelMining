package inventoryreader.ir.recipes; // Uses data sourced from NotEnoughUpdates-REPO

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import inventoryreader.ir.FilePathManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class RemoteRecipeFetcher {
    private static final Logger LOGGER = LoggerFactory.getLogger("IR-RemoteRecipeFetcher");
    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();
    private RemoteRecipeFetcher() {}

    public static void fetchAsync() {
        CompletableFuture.runAsync(RemoteRecipeFetcher::runFetchSafe);
    }

    private static void runFetchSafe() {
        try { runFetch(); } catch (Throwable t) { LOGGER.warn("Remote fetch failed: {}", t.toString()); }
    }

    private static void runFetch() throws Exception {
        File cfgFile = FilePathManager.REMOTE_SOURCES_JSON;
        if (!cfgFile.exists()) return;
        List<Map<String, String>> sources = readSources(cfgFile);
        if (sources.isEmpty()) return;

        for (Map<String, String> s : sources) {
            String type = String.valueOf(s.getOrDefault("type", "")).toLowerCase();
            String url = s.get("url");
            if (url == null || url.isBlank()) continue;
            boolean done = false;
            switch (type) {
                case "recipes":
                case "json":
                    done = fetchDirectJson(url);
                    break;
                case "neu-zip":
                case "neu_zip":
                case "neuzip":
                    done = fetchNeuZip(url);
                    break;
                default:
                    break;
            }
            if (done) return; 
        }
    }

    private static boolean fetchDirectJson(String url) {
        try {
            Map<String, String> meta = readMeta(FilePathManager.REMOTE_META_JSON);
            String etagKey = "etag::" + url;
            String etag = meta.getOrDefault(etagKey, "");

            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .GET();
            if (!etag.isEmpty()) b.header("If-None-Match", etag);
            HttpResponse<String> resp = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() == 304) { LOGGER.info("Remote recipes not modified (ETag)"); return true; }
            if (resp.statusCode() / 100 != 2) { LOGGER.warn("Remote fetch HTTP {}", resp.statusCode()); return false; }

            String body = resp.body();
            if (body == null || body.isBlank()) return false;
            java.lang.reflect.Type t = new TypeToken<Map<String, Object>>(){}.getType();
            Map<String, Object> parsed = GSON.fromJson(body, t);
            if (parsed == null || parsed.isEmpty()) { LOGGER.warn("Remote recipes JSON empty"); return false; }

            writeRemoteSnapshot(parsed);

            String newEtag = resp.headers().firstValue("etag").orElse("");
            if (!newEtag.isEmpty()) { meta.put(etagKey, newEtag); writeMeta(FilePathManager.REMOTE_META_JSON, meta); }

            RecipeRegistry.bootstrap();
            return true;
        } catch (Exception e) {
            LOGGER.warn("fetchDirectJson failed: {}", e.toString());
            return false;
        }
    }

    private static boolean fetchNeuZip(String url) {
        try {
            Map<String, String> meta = readMeta(FilePathManager.REMOTE_META_JSON);

            InputStream inputStream;
            String metaKey;
            String metaValToWrite = null;

            try {
                URI u = URI.create(url);
                String scheme = u.getScheme();
                if (scheme != null && scheme.equalsIgnoreCase("file")) {
                    java.io.File f = new java.io.File(u);
                    if (!f.exists()) { LOGGER.warn("NEU ZIP file does not exist: {}", f.getAbsolutePath()); return false; }
                    metaKey = "mtime::" + f.getAbsolutePath();
                    String prev = meta.getOrDefault(metaKey, "");
                    String cur = Long.toString(f.lastModified());
                    if (!prev.isEmpty() && prev.equals(cur)) { LOGGER.info("NEU ZIP file unchanged (mtime cache)"); return true; }
                    inputStream = new java.io.FileInputStream(f);
                    metaValToWrite = cur;
                } else {
                    String etagKey = "etag::" + url;
                    String etag = meta.getOrDefault(etagKey, "");
                    HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                            .timeout(Duration.ofSeconds(30))
                            .GET();
                    if (!etag.isEmpty()) b.header("If-None-Match", etag);
                    HttpResponse<java.io.InputStream> resp = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofInputStream());
                    if (resp.statusCode() == 304) { LOGGER.info("NEU ZIP not modified (ETag)"); return true; }
                    if (resp.statusCode() / 100 != 2) { LOGGER.warn("NEU ZIP fetch HTTP {}", resp.statusCode()); return false; }
                    inputStream = resp.body();
                    metaKey = etagKey;
                    metaValToWrite = resp.headers().firstValue("etag").orElse("");
                }
            } catch (IllegalArgumentException badUri) {
                java.io.File f = new java.io.File(url);
                if (!f.exists()) { LOGGER.warn("NEU ZIP path not found: {}", url); return false; }
                metaKey = "mtime::" + f.getAbsolutePath();
                String prev = meta.getOrDefault(metaKey, "");
                String cur = Long.toString(f.lastModified());
                if (!prev.isEmpty() && prev.equals(cur)) { LOGGER.info("NEU ZIP file unchanged (mtime cache)"); return true; }
                inputStream = new java.io.FileInputStream(f);
                metaValToWrite = cur;
            }

            java.util.zip.ZipInputStream zin = new java.util.zip.ZipInputStream(inputStream);
            // First pass: collect recipes keyed by output internal name, and collect internal->display map
            Map<String, Map<String, Integer>> outToIngInternal = new LinkedHashMap<>();
            Map<String, String> internalToDisplay = new LinkedHashMap<>();

            java.util.zip.ZipEntry ze;
            byte[] buf = new byte[16 * 1024];
            while ((ze = zin.getNextEntry()) != null) {
                String name = ze.getName();
                if (ze.isDirectory()) continue;
                if (!isNeuItemsJsonEntry(name)) continue;
                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream((int)Math.min(ze.getSize() > 0 ? ze.getSize() : 4096, 1_000_000));
                int r;
                while ((r = zin.read(buf)) > 0) baos.write(buf, 0, r);
                String json = baos.toString(StandardCharsets.UTF_8);
                try {
                    JsonElement el = JsonParser.parseString(json);
                    if (!el.isJsonObject()) continue;
                    JsonObject obj = el.getAsJsonObject();
                    String internal = optString(obj, "internalname");
                    String display = stripMC(optString(obj, "displayname"));
                    if (!internal.isEmpty()) {
                        if (!display.isEmpty()) internalToDisplay.putIfAbsent(internal, display);
                        JsonObject recipe = obj.has("recipe") && obj.get("recipe").isJsonObject() ? obj.getAsJsonObject("recipe") : null;
                        if (recipe != null && !recipe.entrySet().isEmpty()) {
                            Map<String, Integer> ing = outToIngInternal.computeIfAbsent(internal, k -> new LinkedHashMap<>());
                            for (Map.Entry<String, JsonElement> e : recipe.entrySet()) {
                                String v = e.getValue().isJsonPrimitive() ? e.getValue().getAsString() : "";
                                if (v == null || v.isBlank()) continue;
                                String item;
                                int count = 1;
                                int colon = v.lastIndexOf(':');
                                if (colon > 0) {
                                    item = v.substring(0, colon);
                                    try { count = Integer.parseInt(v.substring(colon + 1)); } catch (NumberFormatException ignore) { count = 1; }
                                } else {
                                    item = v;
                                }
                                if (item.isBlank()) continue;
                                ing.merge(item, count, Integer::sum);
                            }
                        }
                    }
                } catch (Exception ignore) {
                }
            }
            try { zin.close(); } catch (Exception ignore) {}

            if (outToIngInternal.isEmpty()) { LOGGER.warn("NEU ZIP contained no recipes"); return false; }

            Map<String, Map<String, Integer>> wire = new LinkedHashMap<>(outToIngInternal.size());
            for (Map.Entry<String, Map<String, Integer>> e : outToIngInternal.entrySet()) {
                String outInternal = e.getKey();
                String outDisplay = internalToDisplay.getOrDefault(outInternal, outInternal);
                Map<String, Integer> ingDisplay = new LinkedHashMap<>();
                for (Map.Entry<String, Integer> in : e.getValue().entrySet()) {
                    String ingName = internalToDisplay.getOrDefault(in.getKey(), in.getKey());
                    ingDisplay.put(ingName, in.getValue());
                }
                wire.put(outDisplay, ingDisplay);
            }

            writeRemoteSnapshot(wire);

            java.util.Set<String> names = new java.util.LinkedHashSet<>();
            names.addAll(wire.keySet());
            for (java.util.Map<String, Integer> m : wire.values()) names.addAll(m.keySet());
            inventoryreader.ir.FilePathManager.ensureResourceNames(names);

            if (metaValToWrite != null && !metaValToWrite.isEmpty()) {
                meta.put(metaKey, metaValToWrite);
                writeMeta(FilePathManager.REMOTE_META_JSON, meta);
            }

            RecipeRegistry.bootstrap();
            return true;
        } catch (Exception e) {
            LOGGER.warn("fetchNeuZip failed: {}", e.toString());
            return false;
        }
    }

    private static boolean isNeuItemsJsonEntry(String name) {
        if (name == null || !name.endsWith(".json")) return false;
        return name.startsWith("items/") || name.contains("/items/");
    }

    private static void writeRemoteSnapshot(Map<String, ?> data) throws Exception {
        File tmp = new File(FilePathManager.DATA_DIR, "recipes_remote.json.tmp");
        try (FileWriter fw = new FileWriter(tmp, StandardCharsets.UTF_8)) {
            GSON.toJson(data, fw);
        }
        try {
            Files.move(tmp.toPath(), FilePathManager.REMOTE_RECIPES_JSON.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            Files.move(tmp.toPath(), FilePathManager.REMOTE_RECIPES_JSON.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static List<Map<String, String>> readSources(File f) {
        try (FileReader fr = new FileReader(f, StandardCharsets.UTF_8)) {
            java.lang.reflect.Type t = new TypeToken<Map<String, Object>>(){}.getType();
            Map<String, Object> root = GSON.fromJson(fr, t);
            Object arr = root == null ? null : root.get("sources");
            List<Map<String, String>> out = new ArrayList<>();
            if (arr instanceof List<?>) {
                for (Object o : (List<?>) arr) {
                    if (o instanceof Map<?, ?> m) {
                        Map<String, String> entry = new LinkedHashMap<>();
                        Object type = m.get("type");
                        Object url = m.get("url");
                        if (type != null && url != null) {
                            entry.put("type", String.valueOf(type));
                            entry.put("url", String.valueOf(url));
                            out.add(entry);
                        }
                    }
                }
            }
            return out;
        } catch (Exception e) {
            return List.of();
        }
    }

    private static Map<String, String> readMeta(File f) {
        if (!f.exists()) return new LinkedHashMap<>();
        try (FileReader fr = new FileReader(f, StandardCharsets.UTF_8)) {
            java.lang.reflect.Type t = new TypeToken<Map<String, String>>(){}.getType();
            Map<String, String> m = GSON.fromJson(fr, t);
            return m == null ? new LinkedHashMap<>() : m;
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private static void writeMeta(File f, Map<String, String> meta) {
        try (FileWriter fw = new FileWriter(f, StandardCharsets.UTF_8)) {
            GSON.toJson(meta, fw);
        } catch (Exception ignored) {}
    }

    private static String optString(JsonObject o, String k) {
        if (o == null || k == null) return "";
        JsonElement e = o.get(k);
        return e == null || e.isJsonNull() ? "" : (e.isJsonPrimitive() ? e.getAsString() : e.toString());
    }

    private static String stripMC(String s) {
        if (s == null) return "";
        return s.replaceAll("§.", "").trim();
    }
}
