package server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import common.TchatMessage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class ServerStateStore {

    // Cette classe interne sert de conteneur pour tout ce qu'on veut sauver
    public static class LoadedState {
        private final int nextClientId;
        private final Map<Integer, String> mapIdPseudo;
        private final Map<String, List<TchatMessage>> allHistories;
        private final Map<String, Map<Integer, Integer>> readCursors;

        public LoadedState(int nextClientId, Map<Integer, String> mapIdPseudo, 
                           Map<String, List<TchatMessage>> allHistories,
                           Map<String, Map<Integer, Integer>> readCursors) {
            this.nextClientId = nextClientId;
            this.mapIdPseudo = (mapIdPseudo != null) ? mapIdPseudo : new HashMap<>();
            this.allHistories = (allHistories != null) ? allHistories : new HashMap<>();
            this.readCursors = (readCursors != null) ? readCursors : new HashMap<>();
        }

        public Map<Integer, String> getMapIdPseudo() { 
            return mapIdPseudo != null ? mapIdPseudo : new HashMap<>(); 
        }
        public Map<String, List<TchatMessage>> getAllHistories() { 
            return allHistories != null ? allHistories : new HashMap<>(); 
        }
        public Map<String, Map<Integer, Integer>> getReadCursors() { 
            return readCursors != null ? readCursors : new HashMap<>(); 
        }
        
        public int getNextClientId() { return nextClientId; }
    }

    private final Path filePath;
    private final Gson gson;

    public ServerStateStore(String fileName) {
        this.filePath = Paths.get(fileName);
        // On crée un objet GSON capable de gérer les Maps proprement avec indentation
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    public synchronized void save(int nextClientId, 
                                 Map<Integer, String> mapIdPseudo,
                                 Map<String, List<TchatMessage>> allHistories,
                                 Map<String, Map<Integer, Integer>> readCursors) {
        
        // On emballe tout dans un objet LoadedState
        LoadedState state = new LoadedState(nextClientId, mapIdPseudo, allHistories, readCursors);
        
        try (Writer writer = Files.newBufferedWriter(filePath, StandardCharsets.UTF_8)) {
            gson.toJson(state, writer);
            System.out.println("[JSON] Sauvegarde réussie dans " + filePath);
        } catch (IOException e) {
            System.err.println("[JSON] Erreur sauvegarde : " + e.getMessage());
        }
    }

    public synchronized LoadedState load() {
        if (!Files.exists(filePath)) {
            return new LoadedState(1, new HashMap<>(), new HashMap<>(), new HashMap<>());
        }

        try (Reader reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
            // GSON reconstruit tout l'arbre d'objets (Maps, Listes, TchatMessage) d'un coup
            LoadedState loaded = gson.fromJson(reader, LoadedState.class);
            
            if (loaded == null) {
                return new LoadedState(1, new HashMap<>(), new HashMap<>(), new HashMap<>());
            }
            return loaded;
        } catch (Exception e) {
            System.err.println("[JSON] Erreur chargement : " + e.getMessage());
            return new LoadedState(1, new HashMap<>(), new HashMap<>(), new HashMap<>());
        }
    }
}