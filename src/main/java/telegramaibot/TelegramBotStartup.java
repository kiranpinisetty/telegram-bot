package telegramaibot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@Component
public class TelegramBotStartup implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(TelegramBotStartup.class);
    private final BotConfigurationProperties botProperties;

    public TelegramBotStartup(BotConfigurationProperties botProperties) {
        this.botProperties = botProperties;
    }

    @Override
    public void run(String... args) throws Exception {
        if (!canStartBot()) {
            log.warn("Bot startup skipped. Configure TELEGRAM_BOT_TOKEN, TELEGRAM_BOT_NAME and at least one AI key.");
            return;
        }

        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(new TelegramAiBot(
                    botProperties.getTelegramBotToken(),
                    botProperties.getTelegramBotName(),
                    botProperties.getOpenaiApiKey(),
                    botProperties.getGeminiApiKey(),
                    botProperties.getHuggingfaceApiKey(),
                    botProperties.getHuggingfaceModel()
            ));
            log.info("Bot started successfully.");
        } catch (Exception ex) {
            log.error("Bot registration failed: {}", ex.getMessage());
        }
    }

    private boolean canStartBot() {
        boolean hasToken = hasText(botProperties.getTelegramBotToken());
        boolean hasName = hasText(botProperties.getTelegramBotName());
        boolean hasOpenAi = hasText(botProperties.getOpenaiApiKey());
        boolean hasGemini = hasText(botProperties.getGeminiApiKey());
        boolean hasHuggingFace = hasText(botProperties.getHuggingfaceApiKey());
        return hasToken && hasName && (hasOpenAi || hasGemini || hasHuggingFace);
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
