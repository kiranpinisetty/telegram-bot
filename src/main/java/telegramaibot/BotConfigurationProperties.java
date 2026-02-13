package telegramaibot;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "bot")
public class BotConfigurationProperties {
    private String telegramBotToken;
    private String telegramBotName;
    private String openaiApiKey;
    private String geminiApiKey;
    private String huggingfaceApiKey;
    private String huggingfaceModel = "gpt2";

    public String getTelegramBotToken() {
        return telegramBotToken;
    }

    public void setTelegramBotToken(String telegramBotToken) {
        this.telegramBotToken = telegramBotToken;
    }

    public String getTelegramBotName() {
        return telegramBotName;
    }

    public void setTelegramBotName(String telegramBotName) {
        this.telegramBotName = telegramBotName;
    }

    public String getOpenaiApiKey() {
        return openaiApiKey;
    }

    public void setOpenaiApiKey(String openaiApiKey) {
        this.openaiApiKey = openaiApiKey;
    }

    public String getGeminiApiKey() {
        return geminiApiKey;
    }

    public void setGeminiApiKey(String geminiApiKey) {
        this.geminiApiKey = geminiApiKey;
    }

    public String getHuggingfaceApiKey() {
        return huggingfaceApiKey;
    }

    public void setHuggingfaceApiKey(String huggingfaceApiKey) {
        this.huggingfaceApiKey = huggingfaceApiKey;
    }

    public String getHuggingfaceModel() {
        return huggingfaceModel;
    }

    public void setHuggingfaceModel(String huggingfaceModel) {
        this.huggingfaceModel = huggingfaceModel;
    }
}
