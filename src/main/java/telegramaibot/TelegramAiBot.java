package telegramaibot;

import com.google.gson.*;
import com.theokanning.openai.completion.chat.*;
import com.theokanning.openai.service.OpenAiService;
import okhttp3.*;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class TelegramAiBot extends TelegramLongPollingBot {

    private final String botToken;
    private final String botUsername;
    private final String openAiApiKey;
    private final String geminiApiKey;
    private final String huggingFaceApiKey;
    private final String huggingFaceModel;

    // per-user chosen AI (openai, gemini, huggingface)
    private final Map<Long, String> userAiChoice = new ConcurrentHashMap<>();
    // track users we've shown the menu to already
    private final Set<Long> userSeen = ConcurrentHashMap.newKeySet();

    private final OkHttpClient httpClient;
    private final Gson gson;
    private final OpenAiService openAiService;

    public TelegramAiBot(String botToken,
                         String botUsername,
                         String openAiApiKey,
                         String geminiApiKey,
                         String huggingFaceApiKey,
                         String huggingFaceModel) {

        this.botToken = botToken;
        this.botUsername = botUsername;
        this.openAiApiKey = openAiApiKey;
        this.geminiApiKey = geminiApiKey;
        this.huggingFaceApiKey = huggingFaceApiKey;
        this.huggingFaceModel = huggingFaceModel;

        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();

        this.gson = new Gson();
        // Only initialize if key is present, so other engines can still run.
        this.openAiService = hasText(openAiApiKey)
                ? new OpenAiService(openAiApiKey, Duration.ofSeconds(60))
                : null;
    }

    @Override
    public String getBotToken() { return botToken; }

    @Override
    public String getBotUsername() { return botUsername; }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            // callback (inline button) handling
            if (update.hasCallbackQuery()) {
                handleCallbackQuery(update.getCallbackQuery());
                return;
            }

            if (!update.hasMessage() || !update.getMessage().hasText()) return;

            String text = update.getMessage().getText().trim();
            Long userId = update.getMessage().getFrom().getId();
            String chatId = update.getMessage().getChatId().toString();

            // show menu first time we see this user
            if (!userSeen.contains(userId)) {
                sendMenu(chatId);
                userSeen.add(userId);
                // we continue and still process user's message below
            }

            String lower = text.toLowerCase(Locale.ROOT);

            // Commands and shortcuts
            if (lower.equals("/start")) {
                sendMessage(chatId, "👋 Welcome! I can answer via OpenAI, Gemini or Hugging Face. Type /help for commands.");
                return;
            }

            if (lower.equals("/help") || lower.equals("/menu")) {
                String current = userAiChoice.getOrDefault(userId, "openai");
                String help = "📋 Available Commands:\n" +
                        "/start\n" +
                        "hi / hello\n" +
                        "/bye\n" +
                        "/help or /menu\n" +
                        "/chooseai - pick OpenAI / Gemini / HuggingFace\n" +
                        "/ai <openai|gemini|hf> <message> - force a specific engine\n\n" +
                        "Currently selected engine: " + current.toUpperCase();
                sendMessage(chatId, help);
                return;
            }

            if (lower.equals("/bye")) {
                sendMessage(chatId, "👋 Goodbye!");
                return;
            }

            if (lower.equals("hi") || lower.equals("hello")) {
                sendMessage(chatId, "Hello! Ask me anything or use /chooseai to pick an engine.");
                return;
            }

            if (lower.equals("/chooseai")) {
                sendAiSelectionMenu(chatId);
                return;
            }

            if (lower.startsWith("/ai")) {
                // format: /ai openai what is java
                String[] parts = text.split(" ", 3);
                if (parts.length < 3) {
                    sendMessage(chatId, "Usage: /ai <openai|gemini|hf> <your message>");
                    return;
                }
                String engine = parts[1].toLowerCase(Locale.ROOT);
                String prompt = parts[2];

                switch (engine) {
                    case "openai":
                        safeRespond(chatId, "🤖 [OpenAI] ", () -> callOpenAI(prompt));
                        break;
                    case "gemini":
                        safeRespond(chatId, "✨ [Gemini] ", () -> callGemini(prompt));
                        break;
                    case "hf":
                    case "huggingface":
                        safeRespond(chatId, "🦙 [HuggingFace] ", () -> callHuggingFace(prompt));
                        break;
                    default:
                        sendMessage(chatId, "Unknown engine. Use openai | gemini | hf");
                }
                return;
            }

            // normal conversation -> use selected engine (if any) then fallback
            String produced = produceWithFallback(text, userId);
            sendMessage(chatId, produced);

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    // Attempt engines in priority order (selected first, then the rest). Return response with footer.
    private String produceWithFallback(String prompt, Long userId) {
        String selected = userAiChoice.get(userId); // may be null
        List<String> order;
        if (selected == null) {
            order = Arrays.asList("openai", "gemini", "huggingface");
        } else {
            order = new ArrayList<>();
            order.add(selected);
            for (String s : Arrays.asList("openai", "gemini", "huggingface")) if (!s.equals(selected)) order.add(s);
        }

        Exception lastEx = null;
        for (String engine : order) {
            try {
                switch (engine) {
                    case "openai": {
                        String r = callOpenAI(prompt);
                        if (r != null && !r.isBlank()) return r + "\n\n[🤖 Response from: OpenAI]";
                        break;
                    }
                    case "gemini": {
                        String r = callGemini(prompt);
                        if (r != null && !r.isBlank()) return r + "\n\n[✨ Response from: Gemini]";
                        break;
                    }
                    case "huggingface": {
                        String r = callHuggingFace(prompt);
                        if (r != null && !r.isBlank()) return r + "\n\n[🦙 Response from: HuggingFace]";
                        break;
                    }
                }
            } catch (Exception e) {
                lastEx = e;
                System.out.println("Engine " + engine + " failed: " + e.getMessage());
            }
        }

        String msg = "❌ All AI services failed.";
        if (lastEx != null) msg += " Last error: " + lastEx.getMessage();
        return msg;
    }

    // safeRespond helper for /ai forced command
    private void safeRespond(String chatId, String label, Callable<String> fn) {
        try {
            String res = fn.call();
            if (res == null || res.isBlank()) res = "❌ No response from engine.";
            sendMessage(chatId, label + res);
        } catch (Exception e) {
            sendMessage(chatId, label + "Error: " + e.getMessage());
        }
    }

    // ---------------------------
    // OpenAI (theokanning wrapper)
    // ---------------------------
    private String callOpenAI(String prompt) {
        if (!hasText(openAiApiKey) || openAiService == null) {
            throw new IllegalStateException("OpenAI key not provided");
        }
        ChatCompletionRequest req = ChatCompletionRequest.builder()
                .model("gpt-3.5-turbo")
                .messages(Collections.singletonList(new ChatMessage("user", prompt)))
                .maxTokens(512)
                .build();
        var result = openAiService.createChatCompletion(req);
        if (result == null || result.getChoices() == null || result.getChoices().isEmpty()) {
            throw new RuntimeException("OpenAI returned empty result");
        }
        return result.getChoices().get(0).getMessage().getContent();
    }

    // ---------------------------
    // Gemini (REST call)
    // ---------------------------
    private String callGemini(String prompt) throws IOException {
        if (geminiApiKey == null || geminiApiKey.isBlank()) throw new IllegalStateException("Gemini key not provided");
        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent";

        // build payload
        JsonObject part = new JsonObject();
        part.addProperty("text", prompt);

        JsonObject partsWrapper = new JsonObject();
        partsWrapper.add("parts", gson.toJsonTree(Collections.singletonList(part)));

        JsonArray contents = new JsonArray();
        contents.add(partsWrapper);

        JsonObject payload = new JsonObject();
        payload.add("contents", contents);

        RequestBody body = RequestBody.create(payload.toString(), MediaType.get("application/json; charset=utf-8"));

        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .addHeader("x-goog-api-key", geminiApiKey)
                .addHeader("Content-Type", "application/json")
                .build();

        try (Response resp = httpClient.newCall(request).execute()) {
            String raw = resp.body() != null ? resp.body().string() : "";
            if (!resp.isSuccessful()) {
                throw new IOException("Gemini API error " + resp.code() + ": " + raw);
            }
            JsonElement parsed = JsonParser.parseString(raw);
            if (!parsed.isJsonObject()) throw new IOException("Unexpected Gemini response: " + raw);
            JsonObject obj = parsed.getAsJsonObject();

            // parse candidates -> content -> parts -> text
            if (obj.has("candidates") && obj.get("candidates").isJsonArray()) {
                JsonArray candidates = obj.getAsJsonArray("candidates");
                if (candidates.size() > 0 && candidates.get(0).isJsonObject()) {
                    JsonObject candidate = candidates.get(0).getAsJsonObject();
                    if (candidate.has("content") && candidate.get("content").isJsonObject()) {
                        JsonObject content = candidate.getAsJsonObject("content");
                        if (content.has("parts") && content.get("parts").isJsonArray()) {
                            JsonArray parts = content.getAsJsonArray("parts");
                            if (parts.size() > 0 && parts.get(0).isJsonObject()) {
                                JsonObject first = parts.get(0).getAsJsonObject();
                                if (first.has("text")) return first.get("text").getAsString();
                            }
                        }
                    }
                }
            }
            // fallback: top-level text
            if (obj.has("text")) return obj.get("text").getAsString();

            throw new IOException("Unexpected Gemini response: " + raw);
        }
    }

    // ---------------------------
    // Hugging Face (Inference API)
    // ---------------------------
    private String callHuggingFace(String prompt) throws IOException {
        if (huggingFaceApiKey == null || huggingFaceApiKey.isBlank()) throw new IllegalStateException("Hugging Face key not provided");

        String url = "https://api-inference.huggingface.co/models/" + huggingFaceModel;
        JsonObject payload = new JsonObject();
        payload.addProperty("inputs", prompt);

        RequestBody body = RequestBody.create(payload.toString(), MediaType.get("application/json; charset=utf-8"));

        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .addHeader("Authorization", "Bearer " + huggingFaceApiKey)
                .addHeader("x-wait-for-model", "true")
                .build();

        try (Response resp = httpClient.newCall(request).execute()) {
            String raw = resp.body() != null ? resp.body().string() : "";
            if (!resp.isSuccessful()) throw new IOException("HuggingFace error " + resp.code() + ": " + raw);

            JsonElement parsed = JsonParser.parseString(raw);
            // HF normally returns an array of outputs
            if (parsed.isJsonArray()) {
                JsonArray arr = parsed.getAsJsonArray();
                if (arr.size() > 0 && arr.get(0).isJsonObject()) {
                    JsonObject first = arr.get(0).getAsJsonObject();
                    if (first.has("generated_text")) return first.get("generated_text").getAsString();
                    // try common fields
                    for (Map.Entry<String, JsonElement> e : first.entrySet()) {
                        if (e.getValue().isJsonPrimitive() && e.getValue().getAsJsonPrimitive().isString()) {
                            return e.getValue().getAsString();
                        }
                    }
                }
            } else if (parsed.isJsonObject()) {
                JsonObject obj = parsed.getAsJsonObject();
                if (obj.has("generated_text")) return obj.get("generated_text").getAsString();
                if (obj.has("text")) return obj.get("text").getAsString();
            }
            throw new IOException("Unexpected HuggingFace response: " + raw);
        }
    }

    // ---------------------------
    // Menu and inline keyboard
    // ---------------------------
    private void sendMenu(String chatId) {
        String menu = "📋 Available Commands:\n" +
                "/start\n" +
                "hi / hello\n" +
                "/bye\n" +
                "/help or /menu\n" +
                "/chooseai - pick OpenAI / Gemini / HuggingFace\n" +
                "/ai <openai|gemini|hf> <message> - force a specific engine\n\n" +
                "Tip: Use /chooseai to select an engine; default order is OpenAI → Gemini → HuggingFace.";
        sendMessage(chatId, menu);
    }

    private void sendAiSelectionMenu(String chatId) {
        InlineKeyboardButton b1 = new InlineKeyboardButton();
        b1.setText("OpenAI");
        b1.setCallbackData("ai_openai");

        InlineKeyboardButton b2 = new InlineKeyboardButton();
        b2.setText("Gemini");
        b2.setCallbackData("ai_gemini");

        InlineKeyboardButton b3 = new InlineKeyboardButton();
        b3.setText("HuggingFace");
        b3.setCallbackData("ai_hf");

        List<InlineKeyboardButton> row = Arrays.asList(b1, b2, b3);
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup(Collections.singletonList(row));

        SendMessage m = new SendMessage(chatId, "Choose AI engine:");
        m.setReplyMarkup(markup);
        try {
            execute(m);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void handleCallbackQuery(CallbackQuery cq) {
        String data = cq.getData();
        Long userId = cq.getFrom().getId();
        String chatId = cq.getMessage().getChatId().toString();
        int messageId = cq.getMessage().getMessageId();

        String reply;
        switch (data) {
            case "ai_openai":
                userAiChoice.put(userId, "openai");
                reply = "✅ You selected OpenAI. Future messages will use OpenAI.";
                break;
            case "ai_gemini":
                userAiChoice.put(userId, "gemini");
                reply = "✅ You selected Gemini. Future messages will use Gemini.";
                break;
            case "ai_hf":
                userAiChoice.put(userId, "huggingface");
                reply = "✅ You selected HuggingFace. Future messages will use HuggingFace.";
                break;
            default:
                reply = "❌ Unknown option";
        }

        EditMessageText edit = new EditMessageText();
        edit.setChatId(chatId);
        edit.setMessageId(messageId);
        edit.setText(reply);

        try {
            execute(edit);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    // send message helper
    private void sendMessage(String chatId, String text) {
        SendMessage m = new SendMessage(chatId, text);
        try {
            execute(m);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
