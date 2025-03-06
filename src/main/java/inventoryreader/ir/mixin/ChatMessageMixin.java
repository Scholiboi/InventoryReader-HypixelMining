package inventoryreader.ir.mixin;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import inventoryreader.ir.HttpUtil;
import inventoryreader.ir.InventoryReader;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Mixin(ClientPlayNetworkHandler.class)
public class ChatMessageMixin {
    @Unique
    private static final Gson GSON = new Gson();

    @Unique
    private static final Pattern SACKS_PATTERN_1 = Pattern.compile(
        "\\[Sacks\\] [+-].+ items?, [+-].+ items?\\. \\(Last .+s\\.\\).*"
    );

    @Unique
    private static final Pattern SACKS_PATTERN_2 = Pattern.compile(
        "\\[Sacks\\] [+-].+ items?\\. \\(Last .+s\\.\\).*"
    );

    @Unique
    private static final AtomicBoolean isProcessing = new AtomicBoolean(false);

    @Unique
    private static final ExecutorService networkExecutor = Executors.newSingleThreadExecutor();

    @Inject(method = "onGameMessage", at = @At("HEAD"))
    private void onGameMessage(GameMessageS2CPacket packet, CallbackInfo ci) {
        if (!isProcessing.compareAndSet(false, true)) {
            return;
        }
        try{
            Text text = packet.content();
            String messageString = text.getString();

            // InventoryReader.LOGGER.info("Received message: " + messageString + " on thread: " + Thread.currentThread().getName());

            int count = 0;
            String name = "";
            boolean hasCount = false;
            boolean hasName = false;
            boolean matchFound = false;
            boolean twocheck = false;

            Matcher matcher1 = SACKS_PATTERN_1.matcher(messageString);
            Matcher matcher2 = SACKS_PATTERN_2.matcher(messageString);

            JsonArray itemsArray = new JsonArray();
            if ((matcher1.find() || matcher2.find())&&(Thread.currentThread().getName().contains("Render"))) {
                matchFound = true;
                InventoryReader.LOGGER.info("Match found: " + messageString);

                // Convert the Text object to a JSON string using Gson
                String jsonString = GSON.toJson(text);
                // InventoryReader.LOGGER.info("Chat Message JSON: " + jsonString);

                // Parse the JSON and extract item details
                JsonObject jsonObject = GSON.fromJson(jsonString, JsonObject.class);
                JsonArray rootArray = jsonObject.getAsJsonArray("field_39006");
                
                if (rootArray.size() > 3) twocheck = true;

                // InventoryReader.LOGGER.info("rootArray: " + rootArray);
                if (rootArray.size() > 2) {
                    JsonElement element = rootArray.get(0);
                    if (element.isJsonObject()) {
                        JsonObject targetObject = element.getAsJsonObject();
                        // InventoryReader.LOGGER.info("targetObject: " + targetObject);
                        if (targetObject.has("field_39007")) {
                            JsonObject field_39007 = targetObject.getAsJsonObject("field_39007");
                            if (field_39007.has("field_11858")) {
                                JsonObject field_11858 = field_39007.getAsJsonObject("field_11858");
                                if (field_11858.has("field_46602")) {
                                    JsonObject field_46602 = field_11858.getAsJsonObject("field_46602");
                                    if (field_46602.has("comp_1986")) {
                                        JsonObject comp_1986 = field_46602.getAsJsonObject("comp_1986");
                                        if (comp_1986.has("field_39006")) {
                                            itemsArray = comp_1986.getAsJsonArray("field_39006");
                                            // InventoryReader.LOGGER.info("itemsArray: " + itemsArray);
                                        } else {
                                            InventoryReader.LOGGER.warn("comp_1986 does not contain field_39006");
                                        }
                                    } else {
                                        InventoryReader.LOGGER.warn("field_46602 does not contain comp_1986");
                                    }
                                } else {
                                    InventoryReader.LOGGER.warn("field_11858 does not contain field_46602");
                                }
                            } else {
                                InventoryReader.LOGGER.warn("field_39007 does not contain field_11858");
                            }
                        } else {
                            InventoryReader.LOGGER.warn("targetObject does not contain field_39007");
                        }
                    } else {
                        InventoryReader.LOGGER.warn("rootArray element is not a JsonObject");
                    }
                } else {
                    InventoryReader.LOGGER.warn("rootArray is null or does not have enough elements");
                }
            if (twocheck){
                InventoryReader.LOGGER.info("Twocheck");
                JsonArray itemsArray2 = new JsonArray();
                if (rootArray.size() > 2) {
                    JsonElement element = rootArray.get(3);
                    if (element.isJsonObject()) {
                        JsonObject targetObject = element.getAsJsonObject();
                        // InventoryReader.LOGGER.info("targetObject: " + targetObject);
                        if (targetObject.has("field_39007")) {
                            JsonObject field_39007 = targetObject.getAsJsonObject("field_39007");
                            if (field_39007.has("field_11858")) {
                                JsonObject field_11858 = field_39007.getAsJsonObject("field_11858");
                                if (field_11858.has("field_46602")) {
                                    JsonObject field_46602 = field_11858.getAsJsonObject("field_46602");
                                    if (field_46602.has("comp_1986")) {
                                        JsonObject comp_1986 = field_46602.getAsJsonObject("comp_1986");
                                        if (comp_1986.has("field_39006")) {
                                            itemsArray2 = comp_1986.getAsJsonArray("field_39006");
                                            // InventoryReader.LOGGER.info("itemsArray: " + itemsArray);
                                        } else {
                                            InventoryReader.LOGGER.warn("comp_1986 does not contain field_39006");
                                        }
                                    } else {
                                        InventoryReader.LOGGER.warn("field_46602 does not contain comp_1986");
                                    }
                                } else {
                                    InventoryReader.LOGGER.warn("field_11858 does not contain field_46602");
                                }
                            } else {
                                InventoryReader.LOGGER.warn("field_39007 does not contain field_11858");
                            }
                        } else {
                            InventoryReader.LOGGER.warn("targetObject does not contain field_39007");
                        }
                    } else {
                        InventoryReader.LOGGER.warn("rootArray element is not a JsonObject");
                    }
                } else {
                    InventoryReader.LOGGER.warn("rootArray is null or does not have enough elements");
                }
                itemsArray.addAll(itemsArray2);
            }
        }
            Map<String, Integer> itemMap = new HashMap<>();

            if (matchFound) {
                for (JsonElement itemElement : itemsArray) {
                    JsonObject itemObject = itemElement.getAsJsonObject();
                    if (!itemObject.has("field_39005")) {
                        continue;
                    }
                    String comp737 = itemObject.getAsJsonObject("field_39005").get("comp_737").getAsString().trim();

                    if (comp737.matches("[+-].+")) {
                        count = Integer.parseInt(comp737.replace(",", ""));
                        hasCount = true;
                        continue;
                    }
                    if (hasCount) {
                        if (comp737.contains("Gemstone")) {
                            name = comp737.substring(2);
                        } else {
                            name = comp737;
                        }
                        hasName = true;
                    }
                    if (hasName) {
                        if (itemMap.containsKey(name)) {
                            count += itemMap.get(name);
                        }
                        itemMap.put(name, count);
                        hasName = false;
                        hasCount = false;
                    }
                }
                itemMap.forEach((key, value) -> InventoryReader.LOGGER.info("Item: " + key + ", Count: " + value));

                sendItemMapToEndpoint(itemMap);
                matchFound = false;
                twocheck = false;
            }
        } finally {
            isProcessing.set(false);
        }
    }

    @Unique
    private void sendItemMapToEndpoint(Map<String, Integer> itemMap) {
        HttpUtil.HTTP_EXECUTOR.submit(() -> {
            try {
                HttpClient client = HttpClient.newHttpClient();
                String jsonInputString = GSON.toJson(itemMap);
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(new URI("http://localhost:5000/api/mod/modify-resources"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonInputString, StandardCharsets.UTF_8))
                        .build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                InventoryReader.LOGGER.info("POST Response Code :: " + response.statusCode());
            } catch (Exception e) {
                InventoryReader.LOGGER.error("Error sending item map to endpoint", e);
            }
        });
    }
}