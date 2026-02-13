# Telegram AI Bot

Spring Boot Telegram bot with support for OpenAI, Gemini, and Hugging Face.

## How To Create Telegram Bot (BotFather Setup)

1. Open Telegram and search for `@BotFather`.
2. Start BotFather:
   - `/start`
3. Create a new bot:
   - `/newbot`
4. Provide details:
   - Bot Name (display name)
   - Bot Username (must end with `bot`)
5. Copy the token you get, for example:
   - `1234567890:AAEXAMPLE-TOKEN`

Keep this token private. Do not commit it to GitHub.

## Required Keys

- `TELEGRAM_BOT_TOKEN` (required)
- `TELEGRAM_BOT_NAME` (required)
- At least one AI key:
  - `OPENAI_API_KEY` or
  - `GEMINI_API_KEY` or
  - `HUGGINGFACE_API_KEY`
- Optional:
  - `HUGGINGFACE_MODEL` (default: `gpt2`)

## Configuration (Recommended)

Use local file `application-local.properties` (already git-ignored):

1. Copy template:
```bash
cp application-local.properties.example application-local.properties
```
2. Fill your values:
```properties
bot.telegram-bot-token=
bot.telegram-bot-name=
bot.openai-api-key=
bot.gemini-api-key=
bot.huggingface-api-key=
bot.huggingface-model=gpt2
```

You can also use environment variables:

macOS / Linux:
```bash
export TELEGRAM_BOT_TOKEN=your_token_here
export TELEGRAM_BOT_NAME=your_bot_username
export OPENAI_API_KEY=your_key_here
export GEMINI_API_KEY=your_key_here
export HUGGINGFACE_API_KEY=your_key_here
```

Windows (PowerShell):
```powershell
setx TELEGRAM_BOT_TOKEN "your_token_here"
setx TELEGRAM_BOT_NAME "your_bot_username"
setx OPENAI_API_KEY "your_key_here"
setx GEMINI_API_KEY "your_key_here"
setx HUGGINGFACE_API_KEY "your_key_here"
```

## Build And Run

1. Clone repository:
```bash
git clone https://github.com/yourusername/telegram-ai-bot.git
cd telegram-ai-bot
```

2. Build project:
```bash
mvn clean install
```

3. Run bot:
```bash
mvn spring-boot:run
```

## Telegram Commands

- `/start` - Start conversation
- `/menu` - Show menu
- `/help` - Show help
- `/chooseai` - Select AI engine
- `/ai <openai|gemini|hf> <message>` - Force specific AI
- `/bye` - End chat

## Clean Project Structure

```text
src/main/java/telegramaibot/
  TelegramAiBotApplication.java
  TelegramAiBot.java
  BotConfigurationProperties.java
  TelegramBotStartup.java
src/main/resources/
  application.properties
application-local.properties.example
application-local.properties  # local only, git-ignored
```

## Safe For GitHub

- Real keys are not stored in source code.
- `application-local.properties` is git-ignored.
- `.env` files are git-ignored.

Before pushing, check:
```bash
git status --ignored
```
