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

