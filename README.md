# ChatApplication

Compilation :

```bash
bash compilation.sh
```

Dans un terminal, pour lancer le serveur :

```bash
java -Djava.rmi.server.hostname=localhost \
     -cp "lib/*:classes" \
     server.ChatServer 6090
```

Dans un autre terminal, pour lancer un client :
```bash
# Interface textuelle :
java -cp "lib/*:classes" \
     client.ChatClient localhost 6090 <nom_client> <id_client>

# Interface graphique :
java -cp "lib/*:classes" \
      client.ChatClient localhost 6090 <nom_client> <id_client> --ihm

```

`id_client`:
- `0` pour une première connexion (le serveur renvoie un nouvel ID)
- l'ID attribué précédemment pour une reconnexion

(Lisa serveur)
java -Djava.rmi.server.hostname=192.168.74.136 -cp "lib/*:classes" server.ChatServer 6090

(Lisa client)
java -cp "lib/*:classes" client.ChatClient 192.168.74.136 6090 --ihm

(Justine Client)
java -Djava.rmi.server.hostname=192.168.74.36 -cp "lib/*:classes" client.ChatClient 192.168.74.136 6090 --ihm

0) Lancer le serveur
1) Connecter 3 clients
2) Envoyer des messages dans le chat général et montrer que tout le monde le reçoit
3) Faire une conersation privée
4) Déconnecter un client
5) Envoyer des message sur le chat général
6) Envoyer des messages au client déconnecter
7) Le reconnecter avec un identifiant trop grand
8) Le reconnecter avec un mauvais pseudo mais le bon identifiant
9) Connexion réussie 
10) Montrer les messages généraux reçus et les messages privés

11) Montrer rapidement l'interface textuelle

