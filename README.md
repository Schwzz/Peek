# Peek

An Android screen assistant. Select a region over another app, attach the image, and ask a local or cloud model about it.

## Use Peek

1. Open **Settings → Models & appearance**.
2. Choose a provider, enter its API key if required, then use **Load models** or enter an exact model ID.
3. Use **Test text** to check the connection. **Test image** sends a generated blue swatch to check image understanding; it does not send your current screenshot.
4. Tap **Use this model**. Each provider retains its own saved settings and encrypted key.
5. Allow display over other apps, tap **Start Peek**, and approve Android screen sharing.
6. Return to your other app. Tap the floating button to open the toolbox, then choose **Select**, **Images**, **Chat**, or **Stop**. The toolbox opens beside the button. Selection starts only when you choose Select. Holding the button still opens chat directly.

Screen sharing belongs to a foreground service and survives closing the main activity. It ends when you stop it, Android revokes access, or the app is force-stopped. The notification includes stop controls.

Attach up to six images in one message, including additional screen selections. Tap a thumbnail to preview it, crop the selected attachment, or remove images individually. Successful sends clear the attachment tray and retain the images in recent conversation context. Failed sends keep attachments for retry. Older image data is released after it leaves the six-message model context.

The panel can be moved and resized using its labeled **Resize** grip. Tap its model row to switch between saved, configured provider profiles. System, light, and dark appearance are available in settings. Under **Appearance → Floating button**, adjust opacity from 20% to 100%; changes apply immediately and persist after restarting. The toolbox and chat stay opaque.

## Providers

| Connection | API format | API key |
| --- | --- | --- |
| Google Gemini | Gemini | Required |
| OpenAI, OpenRouter, Groq, xAI, DeepSeek | OpenAI chat completions | Required |
| Anthropic | Messages | Required |
| Ollama Cloud | Ollama | Required |
| Ollama local server | Ollama | Optional |
| LM Studio local server | OpenAI chat completions | Optional |
| Custom endpoint, including compatible vLLM or llama.cpp servers | OpenAI chat completions | Optional |

Model availability comes from your provider account or server. Image support varies by model. The picker displays image capability when the server exposes it; otherwise use **Test image**. A model known to be text-only remains usable for text chat. Choose a vision model for screenshots and image conversations.

## Connect a local server

Local mode connects to models running on another computer. It does not load GGUF files or run inference directly on the phone.

Connect the computer and phone to the same Wi-Fi network. Enable local network serving in your server application and allow the server through the computer's firewall on your private network.

- **Ollama:** configure the server to listen on the LAN, then use a URL such as `http://192.168.1.10:11434/`.
- **LM Studio:** enable **Serve on Local Network** in the server settings, load a model, and use a URL such as `http://192.168.1.10:1234/v1/`.

Replace the example address with your computer's actual address. `localhost` and `127.0.0.1` refer to the phone itself. On an Android emulator, `10.0.2.2` refers to the host computer. Local HTTP addresses are supported; public servers require HTTPS. Credential-bearing requests do not follow redirects.

If model discovery is unavailable, enter the server's exact model ID manually. If connection fails, check the address, port, firewall, and whether the server is running. Large local models may take longer to load on the first question.

## Development and validation

Requires JDK 21 and Android SDK platform 36.1. CI runs:

```sh
./gradlew testDebugUnitTest assembleDebug assembleDebugAndroidTest
```

GitHub Actions also runs Android 11 and Android 14 emulator tests. They destroy the main activity, verify that opening the toolbox does not start capture, capture twice over a separate test app, send a cropped JPEG to a local mock Ollama API, verify the answer appears in the floating panel, import and submit two distinct image files, cancel selection with Back, and stop the overlay from the side toolbox. Protocol tests verify Gemini current-turn parts, all multi-image provider request formats and model discovery without paid accounts. Live cloud inference needs your own provider account and model access.

The **app-debug** Actions artifact contains the installable APK. Provider tests, screenshots, and device logs are uploaded separately.
