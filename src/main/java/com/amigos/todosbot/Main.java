package com.amigos.todosbot;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.file.Path;
import java.util.stream.Collectors;

import static java.lang.System.Logger.Level.*;

public final class Main {

    private static final System.Logger LOG = System.getLogger(Main.class.getName());

    public static void main(String[] args) throws InterruptedException {
        var token = System.getenv("TELEGRAM_TOKEN");
        if (token == null || token.isBlank()) {
            LOG.log(ERROR, "Falta la variable de entorno TELEGRAM_TOKEN");
            System.exit(1);
        }
        var archivo = Path.of(System.getenv().getOrDefault("MIEMBROS_FILE", "data/miembros.json"));
        var suscripciones = new Suscripciones(archivo);
        var bot = new TelegramClient(token);
        LOG.log(INFO, "Bot iniciado");

        long offset = 0;
        while (true) {
            try {
                for (JsonNode update : bot.getUpdates(offset)) {
                    offset = update.path("update_id").asLong() + 1;
                    procesar(bot, suscripciones, update.path("message"));
                }
            } catch (IOException | RuntimeException e) {
                LOG.log(ERROR, "Error; reintento en 5 s", e);
                Thread.sleep(5_000);
            }
        }
    }

    private static void procesar(TelegramClient bot, Suscripciones suscripciones, JsonNode msg)
            throws IOException, InterruptedException {
        var texto = msg.path("text").asText("");
        if (!texto.startsWith("/") || msg.path("from").isMissingNode()) return;

        var chat = msg.path("chat");
        long chatId = chat.path("id").asLong();
        var from = msg.path("from");
        long userId = from.path("id").asLong();
        var partes = texto.split("\\s+", 2);
        var comando = partes[0].toLowerCase().replaceFirst("@.*$", ""); // quita "@nombre_bot"
        var resto = partes.length > 1 ? partes[1].trim() : "";

        boolean esGrupo = switch (chat.path("type").asText("")) {
            case "group", "supergroup" -> true;
            default -> false;
        };

        switch (comando) {
            case "/suscribir", "/desuscribir", "/all" -> {
                if (!esGrupo) {
                    bot.sendMessage(chatId, "Este comando solo funciona dentro de un grupo.");
                    return;
                }
            }
            default -> { return; }
        }

        switch (comando) {
            case "/suscribir" -> {
                var miembro = new Miembro(userId, nombreDe(from));
                var mensaje = suscripciones.suscribir(chatId, miembro)
                        ? "✅ %s, te mencionaré cuando alguien use /all."
                        : "Ya estabas suscrito, %s.";
                bot.sendMessage(chatId, mensaje.formatted(escapar(miembro.nombre())));
            }
            case "/desuscribir" -> {
                var mensaje = suscripciones.desuscribir(chatId, userId)
                        ? "👋 %s, ya no te mencionaré."
                        : "%s, no estabas suscrito.";
                bot.sendMessage(chatId, mensaje.formatted(escapar(nombreDe(from))));
            }
            case "/all" -> avisarATodos(bot, suscripciones, chatId, userId, resto);
            default -> { }
        }
    }

    private static void avisarATodos(TelegramClient bot, Suscripciones suscripciones,
                                     long chatId, long userId, String resto)
            throws IOException, InterruptedException {
        var miembros = suscripciones.de(chatId);

        if (miembros.stream().noneMatch(m -> m.id() == userId)) {
            bot.sendMessage(chatId, "Solo los suscritos pueden usar /all. Usa /suscribir para unirte.");
            return;
        }

        var menciones = miembros.stream()
                .map(m -> "<a href=\"tg://user?id=%d\">%s</a>".formatted(m.id(), escapar(m.nombre())))
                .collect(Collectors.joining(" "));
        var aviso = resto.isBlank() ? "📣 ¡Atención, banda!" : "📣 " + escapar(resto);

        bot.sendMessage(chatId, aviso + "\n\n" + menciones);
    }

    private static String nombreDe(JsonNode from) {
        var nombre = (from.path("first_name").asText("") + " " + from.path("last_name").asText("")).trim();
        if (!nombre.isEmpty()) return nombre;
        var usuario = from.path("username").asText("");
        return usuario.isEmpty() ? "Miembro" : usuario;
    }

    private static String escapar(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
