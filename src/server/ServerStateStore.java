package server;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Classe utilitaire simple: persiste uniquement nextClientId + map_id_pseudo.
public class ServerStateStore {

    public static class LoadedState {
        private final int nextClientId;
        private final Map<Integer, String> mapIdPseudo;

        public LoadedState(int nextClientId, Map<Integer, String> mapIdPseudo) {
            this.nextClientId = nextClientId;
            this.mapIdPseudo = mapIdPseudo;
        }

        public int getNextClientId() {
            return nextClientId;
        }

        public Map<Integer, String> getMapIdPseudo() {
            return mapIdPseudo;
        }
    }

    private final Path filePath;

    public ServerStateStore(String fileName) {
        this.filePath = Paths.get(fileName);
    }

    // Sauvegarde nextClientId et map_id_pseudo dans un JSON.
    public synchronized void save(int nextClientId, Map<Integer, String> mapIdPseudo) {
        String json = toJson(nextClientId, mapIdPseudo);
        try {
            Files.writeString(
                filePath,
                json,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
            );
        } catch (IOException e) {
            System.err.println("Erreur save JSON (" + filePath + "): " + e.getMessage());
        }
    }

    // Charge nextClientId et map_id_pseudo depuis le JSON.
    public synchronized LoadedState load() {
        if (!Files.exists(filePath)) {
            // Si le fichier n'existe pas, on considère que c'est un démarrage "propre" du serveur, sans clients connus.
            return new LoadedState(1, new HashMap<>());
        }
        try {
            String json = Files.readString(filePath, StandardCharsets.UTF_8);
            int nextClientId = parseNextClientId(json);
            Map<Integer, String> mapIdPseudo = parseMapIdPseudo(json);
            if (nextClientId <= 0) {
                nextClientId = computeNextClientId(mapIdPseudo);
            }
            return new LoadedState(nextClientId, mapIdPseudo);
        } catch (Exception e) {
            System.err.println("Erreur load JSON (" + filePath + "): " + e.getMessage());
            return new LoadedState(1, new HashMap<>());
        }
    }

    private static int parseNextClientId(String json) {
        Matcher m = Pattern.compile("\"nexClientID\"\\s*:\\s*(\\d+)").matcher(json);
        if (m.find()) {
            return Integer.parseInt(m.group(1));
        }
        return -1;
    }

    private static Map<Integer, String> parseMapIdPseudo(String json) {
        Map<Integer, String> result = new HashMap<>();
        String mapContent = extractObjectContent(json, "map_id_pseudo");
        Matcher m = Pattern.compile("\"(\\d+)\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"").matcher(mapContent);
        while (m.find()) {
            int id = Integer.parseInt(m.group(1));
            String pseudo = unescape(m.group(2));
            result.put(id, pseudo);
        }
        return result;
    }

    private static String extractObjectContent(String json, String key) {
        int keyPos = json.indexOf("\"" + key + "\"");
        if (keyPos < 0) {
            return "";
        }
        int start = json.indexOf('{', keyPos);
        if (start < 0) {
            return "";
        }
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
                continue;
            }
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return json.substring(start + 1, i);
                }
            }
        }
        return "";
    }

    private static int computeNextClientId(Map<Integer, String> mapIdPseudo) {
        int max = 0;
        for (Integer id : mapIdPseudo.keySet()) {
            if (id != null && id > max) {
                max = id;
            }
        }
        return max + 1;
    }

    private static String toJson(int nextClientId, Map<Integer, String> mapIdPseudo) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"nexClientID\": ").append(nextClientId).append(",\n");
        sb.append("  \"map_id_pseudo\": {\n");

        int i = 0;
        int size = mapIdPseudo.size();
        for (Map.Entry<Integer, String> entry : mapIdPseudo.entrySet()) {
            sb.append("    \"")
              .append(entry.getKey())
              .append("\": \"")
              .append(escape(entry.getValue()))
              .append("\"");
            i++;
            if (i < size) {
                sb.append(",");
            }
            sb.append("\n");
        }

        sb.append("  }\n");
        sb.append("}\n");
        return sb.toString();
    }

    private static String escape(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String unescape(String text) {
        StringBuilder sb = new StringBuilder();
        boolean escaped = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!escaped) {
                if (c == '\\') {
                    escaped = true;
                } else {
                    sb.append(c);
                }
            } else {
                sb.append(c);
                escaped = false;
            }
        }
        if (escaped) {
            sb.append('\\');
        }
        return sb.toString();
    }
}
