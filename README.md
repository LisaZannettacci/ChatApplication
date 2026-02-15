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
java -cp classes client.HelloClient2 localhost 6090 <nom_client>
```
