package server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import common.ChatMessage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Gestionnaire de persistance de l'état du serveur de chat.
 * Cette classe utilise la bibliothèque GSON pour sérialiser/désérialiser
 * l'état du serveur dans un fichier JSON.
 * 
 * État persisté :
 * - nextClientId : prochain identifiant client à attribuer
 * - mapIdPseudo : mapping ID client -> pseudo
 * - allHistories : tous les historiques de conversations (GENERAL et privées)
 * - readCursors : curseurs de lecture par conversation et par utilisateur
 * 
 * Variables membres :
 * - filePath : chemin du fichier JSON de persistance
 * - gson : instance Gson configurée pour l'indentation
 * 
 * @see ChatServiceImpl
 * @see common.ChatMessage
 */
public class ChatServerStateStore {

    /**
     * Classe interne conteneur pour l'état chargé depuis le fichier JSON.
     * Encapsule toutes les données persistées du serveur.
     * 
     * Variables membres :
     * - nextClientId : prochain ID à attribuer
     * - mapIdPseudo : association ID -> pseudo
     * - allHistories : historiques de toutes les conversations
     * - readCursors : position de lecture de chaque utilisateur par conversation
     */
    public static class LoadedState {
        /** Prochain identifiant client à attribuer lors d'une nouvelle connexion */
        private final int nextClientId;
        
        /** Mapping des identifiants clients vers leurs pseudos */
        private final Map<Integer, String> mapIdPseudo;
        
        /** Historiques de toutes les conversations (clé = "GENERAL" ou "id1-id2") */
        private final Map<String, List<ChatMessage>> allHistories;
        
        /** Curseurs de lecture : convId -> (userId -> lastReadMessageId) */
        private final Map<String, Map<Integer, Integer>> readCursors;

        /**
         * Constructeur de LoadedState.
         * Initialise l'état avec les valeurs fournies, en garantissant des Maps non-nulles.
         * 
         * @param nextClientId le prochain ID client à attribuer
         * @param mapIdPseudo la map ID -> pseudo (null accepté, sera remplacé par une HashMap vide)
         * @param allHistories la map des historiques (null accepté, sera remplacé par une HashMap vide)
         * @param readCursors la map des curseurs de lecture (null accepté, sera remplacé par une HashMap vide)
         */
        public LoadedState(int nextClientId, Map<Integer, String> mapIdPseudo, 
                           Map<String, List<ChatMessage>> allHistories,
                           Map<String, Map<Integer, Integer>> readCursors) {
            this.nextClientId = nextClientId;
            this.mapIdPseudo = (mapIdPseudo != null) ? mapIdPseudo : new HashMap<>();
            this.allHistories = (allHistories != null) ? allHistories : new HashMap<>();
            this.readCursors = (readCursors != null) ? readCursors : new HashMap<>();
        }

        /**
         * Retourne la map ID -> pseudo.
         * 
         * @return une map non-nulle associant les IDs clients à leurs pseudos
         */
        public Map<Integer, String> getMapIdPseudo() { 
            return mapIdPseudo != null ? mapIdPseudo : new HashMap<>(); 
        }
        
        /**
         * Retourne la map des historiques de conversations.
         * 
         * @return une map non-nulle associant les IDs de conversation à leurs historiques
         */
        public Map<String, List<ChatMessage>> getAllHistories() { 
            return allHistories != null ? allHistories : new HashMap<>(); 
        }
        
        /**
         * Retourne la map des curseurs de lecture.
         * 
         * @return une map non-nulle des curseurs (convId -> (userId -> position))
         */
        public Map<String, Map<Integer, Integer>> getReadCursors() { 
            return readCursors != null ? readCursors : new HashMap<>(); 
        }
        
        /**
         * Retourne le prochain ID client à attribuer.
         * 
         * @return le prochain identifiant disponible
         */
        public int getNextClientId() { return nextClientId; }
    }

    /** Chemin du fichier JSON de persistance */
    private final Path filePath;
    
    /** Instance Gson configurée pour générer du JSON indenté */
    private final Gson gson;

    /**
     * Constructeur de ChatServerStateStore.
     * Initialise le gestionnaire de persistance avec le fichier spécifié.
     * 
     * @param fileName le nom du fichier JSON (ex: "server_state.json")
     */
    public ChatServerStateStore(String fileName) {
        this.filePath = Paths.get(fileName);
        // On crée un objet GSON capable de gérer les Maps proprement avec indentation
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    /**
     * Sauvegarde l'état du serveur dans le fichier JSON.
     * Méthode synchronisée pour éviter les écritures concurrentes.
     * Affiche un message de succès ou d'erreur selon le résultat.
     * 
     * @param nextClientId le prochain ID client à attribuer
     * @param mapIdPseudo la map des IDs clients vers leurs pseudos
     * @param allHistories la map des historiques de conversations
     * @param readCursors la map des curseurs de lecture
     */
    public synchronized void save(int nextClientId, 
                                 Map<Integer, String> mapIdPseudo,
                                 Map<String, List<ChatMessage>> allHistories,
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

    /**
     * Charge l'état du serveur depuis le fichier JSON.
     * Méthode synchronisée pour éviter les lectures concurrentes.
     * Si le fichier n'existe pas ou est corrompu, retourne un état par défaut.
     * 
     * @return un objet LoadedState contenant l'état chargé ou un état par défaut
     *         (nextClientId=1, maps vides) en cas d'erreur ou de fichier absent
     */
    public synchronized LoadedState load() {
        if (!Files.exists(filePath)) {
            return new LoadedState(1, new HashMap<>(), new HashMap<>(), new HashMap<>());
        }

        try (Reader reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
            // GSON reconstruit tout l'arbre d'objets (Maps, Listes, ChatMessage) d'un coup
            LoadedState loaded = gson.fromJson(reader, LoadedState.class);
            
            if (loaded == null) {
                return new LoadedState(1, new HashMap<>(), new HashMap<>(), new HashMap<>());
            }
            return loaded;
        } catch (IOException e) {
            System.err.println("[JSON] Erreur I/O lors du chargement : " + e.getMessage());
            return new LoadedState(1, new HashMap<>(), new HashMap<>(), new HashMap<>());
        } catch (Exception e) {
            System.err.println("[JSON] Erreur de parsing JSON : " + e.getMessage());
            return new LoadedState(1, new HashMap<>(), new HashMap<>(), new HashMap<>());
        }
    }
}