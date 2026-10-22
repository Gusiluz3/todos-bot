package com.amigos.todosbot;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Guarda los suscriptores de cada grupo en un archivo JSON con este formato:
 * { "<chatId>": [ { "id": 123, "nombre": "Ana" }, ... ] }
 */
public final class Suscripciones {

    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static final TypeReference<TreeMap<String, List<Miembro>>> TIPO = new TypeReference<>() {};

    private final Path archivo;

    public Suscripciones(Path archivo) {
        this.archivo = archivo;
    }

    /** Devuelve los suscriptores del grupo. Se lee en cada uso, así que puedes editar el archivo a mano. */
    public List<Miembro> de(long chatId) throws IOException {
        return leer().getOrDefault(String.valueOf(chatId), List.of());
    }

    /** Suscribe al usuario o actualiza su nombre. Devuelve false si ya estaba suscrito. */
    public boolean suscribir(long chatId, Miembro miembro) throws IOException {
        var todos = leer();
        var lista = new ArrayList<>(todos.getOrDefault(String.valueOf(chatId), List.of()));
        boolean yaEstaba = lista.removeIf(m -> m.id() == miembro.id());
        lista.add(miembro);
        todos.put(String.valueOf(chatId), lista);
        guardar(todos);
        return !yaEstaba;
    }

    /** Quita al usuario del grupo. Devuelve false si no estaba suscrito. */
    public boolean desuscribir(long chatId, long userId) throws IOException {
        var todos = leer();
        var lista = new ArrayList<>(todos.getOrDefault(String.valueOf(chatId), List.of()));
        if (!lista.removeIf(m -> m.id() == userId)) return false;
        if (lista.isEmpty()) todos.remove(String.valueOf(chatId));
        else todos.put(String.valueOf(chatId), lista);
        guardar(todos);
        return true;
    }

    private Map<String, List<Miembro>> leer() throws IOException {
        if (Files.notExists(archivo) || Files.size(archivo) == 0) return new TreeMap<>();
        var raiz = MAPPER.readTree(archivo.toFile());
        // Compatibilidad con el formato anterior (una lista sin grupos): se trata como vacío.
        if (raiz.isArray()) return new TreeMap<>();
        return MAPPER.convertValue(raiz, TIPO);
    }

    /** Escribe en un archivo temporal y lo renombra, para no dejar el JSON a medias si el bot se cae. */
    private void guardar(Map<String, List<Miembro>> todos) throws IOException {
        var temporal = archivo.resolveSibling(archivo.getFileName() + ".tmp");
        MAPPER.writeValue(temporal.toFile(), todos);
        Files.move(temporal, archivo, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}
