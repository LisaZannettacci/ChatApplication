# ChatApplication

Compilation :

```bash
bash compilation.sh
```

Execution :

```bash
java -Djava.rmi.server.hostname=localhost -cp classes server.HelloServer 6090

# Interface textuelle :
java -cp "lib/*:classes" client.HelloClient2 localhost 6090 <nom_client> <id_client>

# Interface graphique :
java -cp "lib/*:classes" \client.HelloClient2 localhost 6090 <nom_client> <id_client> --ihm

```

`id_client`:
- `0` pour une première connexion (le serveur renvoie un nouvel ID)
- l'ID attribué précédemment pour une reconnexion

