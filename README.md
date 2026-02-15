# ChatApplication

Architecture reorganisee :

- `src/client`
- `src/server`
- `src/interfaces/client`
- `src/interfaces/server`

Compilation :

```bash
bash compilation.sh
```

Execution :

```bash
java -Djava.rmi.server.hostname=localhost -cp classes server.HelloServer 6090
java -cp classes client.HelloClient2 localhost 6090 <nom_client> <id_client>
```

`id_client`:
- `0` pour une première connexion (le serveur renvoie un nouvel ID)
- l'ID attribué précédemment pour une reconnexion

Persistance:
- Le serveur sauvegarde automatiquement `nexClientID` et `map_id_pseudo` dans `server_state.json`.
- Au redémarrage, ces deux valeurs sont rechargées.
