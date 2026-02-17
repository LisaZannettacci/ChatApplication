package ihm;

import javax.swing.*;
import java.awt.*;
import client.HelloClient2;

public class LoginFrame extends JFrame {
    private JTextField pseudoField = new JTextField();
    private JTextField idField = new JTextField();
    private JButton loginButton = new JButton("Se connecter");
    private String host;
    private int port;

    public LoginFrame(String host, int port) {
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

    private void handleLogin() {
    String pseudo = pseudoField.getText();
    int id = Integer.parseInt(idField.getText());

    try {
        // On appelle la méthode statique de HelloClient2
        HelloClient2 client = HelloClient2.launchConnection(host, port, pseudo, id);
        
        // Si ça réussit, on ouvre la fenêtre principale du chat
        // On passe le client et le service RMI à la fenêtre suivante
        TchatFrame tchatFrame = new TchatFrame(client, client.getTchatService());
        client.setIhm(tchatFrame);
        tchatFrame.setVisible(true);
        
        this.dispose(); // On ferme la fenêtre de login
    } catch (Exception e) {
        JOptionPane.showMessageDialog(this, "Erreur de connexion : " + e.getMessage(), 
                                      "Erreur", JOptionPane.ERROR_MESSAGE);
    }
}
}