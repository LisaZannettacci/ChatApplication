package ihm;

import javax.swing.*;
import java.awt.*;
import client.ChatClient;

/**
 * Fenêtre de connexion au serveur de chat RMI.
 * Cette fenêtre Swing permet à l'utilisateur de saisir son pseudo et son ID
 * pour se connecter au serveur de chat.
 * 
 * Comportement :
 * - Si ID = 0 : nouvelle connexion, le serveur attribue un nouvel ID
 * - Si ID > 0 : reconnexion avec un ID existant (le pseudo doit correspondre)
 * 
 * En cas de succès, la fenêtre LoginDialog se ferme et ChatFrame s'ouvre.
 * En cas d'échec, un message d'erreur s'affiche et la fenêtre reste ouverte.
 * 
 * Variables membres :
 * - pseudoField : champ de saisie du pseudo
 * - idField : champ de saisie de l'ID (0 pour nouveau client)
 * - loginButton : bouton de connexion
 * - host : adresse du serveur RMI
 * - port : port du registre RMI
 * 
 * @see ChatClient
 * @see ChatFrame
 */
public class LoginDialog extends JFrame {
    /** Champ de saisie du pseudo utilisateur */
    private JTextField pseudoField = new JTextField();
    
    /** Champ de saisie de l'ID client (0 pour nouvelle connexion) */
    private JTextField idField = new JTextField();
    
    /** Bouton pour déclencher la connexion */
    private JButton loginButton = new JButton("Se connecter");
    
    /** Adresse du serveur RMI (hostname ou IP) */
    private String host;
    
    /** Port du registre RMI */
    private int port;

    /**
     * Constructeur de LoginDialog.
     * Initialise la fenêtre de connexion avec l'adresse et le port du serveur.
     * Configure le layout, les composants Swing et les listeners.
     * 
     * @param host l'adresse du serveur RMI (ex: "localhost" ou "192.168.1.10")
     * @param port le port du registre RMI (ex: 6090)
     */
    public LoginDialog(String host, int port) {
        this.host = host;
        this.port = port;
        setTitle("Connexion au Chat RMI");
        setSize(350, 250);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null); // Centre la fenêtre

        JPanel panel = new JPanel(new GridLayout(5, 2, 10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        panel.add(new JLabel("Pseudo:"));
        panel.add(pseudoField);
        panel.add(new JLabel("ID (0 si nouveau):"));
        panel.add(idField);
        panel.add(new JLabel("")); // Case vide
        panel.add(loginButton);

        add(panel);

        // Action du bouton
        loginButton.addActionListener(e -> handleLogin());
    }

    /**
     * Gère la tentative de connexion au serveur.
     * Lit les valeurs des champs pseudo et ID, appelle ChatClient.launchConnection(),
     * puis ouvre ChatFrame en cas de succès ou affiche un message d'erreur en cas d'échec.
     * 
     * Exceptions gérées :
     * - NumberFormatException : si l'ID n'est pas un nombre valide
     * - RemoteException : si la connexion RMI échoue ou si l'enregistrement est refusé
     * - Exception générale : pour toute autre erreur inattendue
     */
    private void handleLogin() {
    String pseudo = pseudoField.getText();
    int id = Integer.parseInt(idField.getText());

    try {
        // On appelle la méthode statique de ChatClient
        ChatClient client = ChatClient.launchConnection(host, port, pseudo, id);
        
        // Si ça réussit, on ouvre la fenêtre principale du chat
        // On passe le client et le service RMI à la fenêtre suivante
        ChatFrame tchatFrame = new ChatFrame(client, client.getTchatService());
        client.setIhm(tchatFrame);
        tchatFrame.setVisible(true);
        
        this.dispose(); // On ferme la fenêtre de login
    } catch (Exception e) {
        JOptionPane.showMessageDialog(this, "Erreur de connexion : " + e.getMessage(), 
                                      "Erreur", JOptionPane.ERROR_MESSAGE);
    }
}
}