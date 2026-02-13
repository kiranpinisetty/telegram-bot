package telegramaibot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(BotConfigurationProperties.class)
public class TelegramAiBotApplication {
    public static void main(String[] args) {
        SpringApplication.run(TelegramAiBotApplication.class, args);
    }
}
