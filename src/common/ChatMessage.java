package common;

import java.io.Serializable;

/**
 * Classe représentant un message de chat.
 * Cette classe encapsule toutes les informations
 * d'un message échangé dans l'application de chat.
 * 
 * Implémente Serializable pour permettre la transmission via RMI
 * et la persistance JSON via GSON.
 * 
 * Variables membres publiques :
 * - id : identifiant unique du message dans la conversation (0, 1, 2...)
 * - senderId : ID du client expéditeur
 * - senderName : pseudo de l'expéditeur
 * - content : contenu textuel du message
 * - timestamp : horodatage du message (millisecondes depuis epoch)
 * 
 * Utilisée par :
 * - ChatService pour retourner les historiques de conversations
 * - ChatServerStateStore pour la persistance JSON
 * - ChatFrame pour l'affichage des messages dans l'IHM
 * 
 * @see interfaces.server.ChatService
 * @see server.ChatServerStateStore
 * @see ihm.ChatFrame
 */
public class ChatMessage implements Serializable {
    /** Identifiant unique du message dans la conversation (commence à 0) */
    public int id;
    
    /** ID du client expéditeur */
    public int senderId;
    
    /** Pseudo de l'expéditeur du message */
    public String senderName;
    
    /** Contenu textuel du message */
    public String content;
    
    /** Horodatage du message (millisecondes depuis l'epoch Unix) */
    public long timestamp;

    /**
     * Constructeur de ChatMessage.
     * L'horodatage est automatiquement défini à l'instant de création du message.
     * 
     * @param id l'identifiant unique du message dans la conversation
     * @param senderId l'ID du client expéditeur
     * @param senderName le pseudo de l'expéditeur
     * @param content le contenu textuel du message
     */
    public ChatMessage(int id, int senderId, String senderName, String content) {
        this.id = id;
        this.senderId = senderId;
        this.senderName = senderName;
        this.content = content;
        this.timestamp = System.currentTimeMillis();
    }
}