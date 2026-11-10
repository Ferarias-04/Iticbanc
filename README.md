# Iticbanc – Connexió a PostgreSQL des de Java

Pràctica **Accés a Dades**. Programa en Java que es connecta a una base de dades PostgreSQL (executada en un contenidor Docker) mitjançant **JDBC + HikariCP** i treballa amb els moviments bancaris (`statements`) i els clients (`clients`) d'un banc.

## Què fa el programa

1. Llegeix les credencials de connexió d'un fitxer `conn.properties`.
2. Crea un **pool de connexions** amb HikariCP i obté una connexió.
3. Mostra per pantalla **tots els moviments** de la taula `statements`.
4. **Exercici 1:** demana un import mínim i mostra els moviments amb `import >= quantitat`, amb una capçalera generada a partir de les metadades del `ResultSet`.
5. **Exercici 2:** demana les dades d'un client nou (nom, cognoms, id fiscal), comprova que no existeixi i, si no existeix, l'insereix gestionant la transacció amb `commit` i `rollback`.

## Tecnologies

| Eina | Versió | Per a què |
|---|---|---|
| Java | 21 | Llenguatge del programa |
| Maven | – | Gestió del projecte i dependències |
| PostgreSQL | 17 (Docker) | Base de dades |
| `org.postgresql:postgresql` | 42.7.13 | Driver JDBC de PostgreSQL |
| `com.zaxxer:HikariCP` | 7.1.0 | Pool de connexions |
| `org.slf4j:slf4j-simple` | 2.0.17 | Logs de HikariCP per consola |

## Estructura del projecte

```
docker/
└── banc/
    ├── pom.xml
    ├── .gitignore
    └── src/main/
        ├── java/com/iticbcn/Main.java
        └── resources/
            ├── conn.properties           ← NO es puja (credencials reals)
            └── conn.properties.example   ← plantilla sí que es puja
```

## Posada en marxa

### 1. Contenidor de PostgreSQL

Requisit: tenir Docker instal·lat i funcionant.

Crear un fitxer `db.env` amb les variables d'entorn:

```
POSTGRES_USER=el_teu_usuari
POSTGRES_PASSWORD=la_teva_contrasenya
POSTGRES_DB=Iticbanc
```

Crear i arrencar el contenidor:

```
docker run -d --name pgbanc -p 5432:5432 -v pgdata:/var/lib/postgresql/data --env-file db.env postgres:17
```

- `-p 5432:5432` exposa el port de PostgreSQL perquè Java hi pugui accedir des de `localhost`.
- `-v pgdata:...` crea un **volum**: les dades es conserven encara que s'esborri el contenidor.

> ⚠️ Les variables `POSTGRES_*` només s'apliquen la **primera vegada** que s'inicialitza el volum. Si el volum `pgdata` ja existia, es mantenen l'usuari i la contrasenya amb què es va crear.

### 2. Carregar les taules i les dades

```
docker cp acc_statement_2026.sql pgbanc:/tmp/acc_statement_2026.sql
docker exec pgbanc psql -U el_teu_usuari -d Iticbanc -f /tmp/acc_statement_2026.sql
```

Comprovar-ho:

```
docker exec -it pgbanc psql -U el_teu_usuari -d Iticbanc -c "\dt"
```

Han d'aparèixer les taules `clients` i `statements`.

### 3. Configurar la connexió

Copiar la plantilla i posar-hi les credencials reals:

```
banc/src/main/resources/conn.properties.example  →  banc/src/main/resources/conn.properties
```

```
DB_URL=jdbc:postgresql://localhost:5432/Iticbanc
DB_USER=el_teu_usuari
DB_PASSWORD=la_teva_contrasenya
DB_MAX_POOL=5
```

### 4. Executar

Des de la carpeta `docker` (el programa busca el fitxer a `banc/src/main/resources/conn.properties`, ruta relativa a aquesta carpeta), executar la classe `com.iticbcn.Main`.

## Per què les coses estan fetes així

### Per què `conn.properties` no es puja al repositori

Conté l'usuari i la **contrasenya** de la base de dades. Si es pugés a GitHub, qualsevol podria veure-la. Per això:

- `conn.properties` està inclòs al `.gitignore`.
- Es puja `conn.properties.example` amb valors falsos, perquè qui cloni el projecte sàpiga quines claus ha d'omplir.

A més, separar la configuració del codi permet canviar de base de dades sense recompilar.

### Per què HikariCP i no `DriverManager`

`DriverManager.getConnection()` obre una connexió física nova cada vegada, cosa que és lenta. HikariCP manté un **pool** de connexions obertes que es van reutilitzant. És el que fa servir Spring Boot per defecte. `DB_MAX_POOL` limita quantes connexions pot tenir obertes alhora.

### Per què *try-with-resources*

`HikariDataSource`, `Connection`, `PreparedStatement`, `ResultSet` i `Scanner` són recursos que s'han de tancar. Declarant-los dins `try (...)` es tanquen automàticament, fins i tot si hi ha un error, i s'eviten fuites de connexions.

### Per què `PreparedStatement` amb `?`

En lloc de concatenar valors dins la query (`"... WHERE import >= " + quantitat`), es fan servir paràmetres (`?`) i `setDouble` / `setString`. Així:

- s'evita la **injecció SQL**;
- el driver s'encarrega de convertir correctament els tipus.

### Per què les funcions llancen `SQLException` (`throws`)

Les funcions (`readAllStatements`, `readStatementsByImport`, `existeixClient`, `insertClient`) no decideixen què fer amb l'error: el propaguen al `main`, que és qui gestiona els missatges a l'usuari. Així cada funció té una sola responsabilitat.

### Per què `ResultSetMetaData` per a la capçalera

`rs.getMetaData()` dona informació sobre les columnes del resultat. Amb `getColumnName(n)` s'obtenen els noms de les columnes 1, 3, 8 i 10 (`id_statement`, `tipus_moviment`, `import`, `divisa`) sense haver-los d'escriure a mà.

### Per què `commit` i `rollback` a la inserció

Per defecte JDBC funciona en mode *autocommit* (cada sentència es confirma sola). A `insertClient`:

1. `setAutoCommit(false)`: comença una transacció manual.
2. Es comprova amb un `SELECT` si el client ja existeix (per `id_fiscal`).
   - Si existeix, `rollback()` i no s'insereix res.
3. Si no existeix, `INSERT` + `executeUpdate()` i després `commit()`.
4. Si hi ha qualsevol error, `rollback()` per deixar la base de dades com estava.
5. Al `finally` es restaura l'autocommit original, perquè la connexió torna al pool i es pot reutilitzar.

## Exemple d'execució

```
Connectat!

=== Tots els moviments ===
moviment amb id: 1;Recargas;5.00;EUR
moviment amb id: 2;Transferir;-0.55;EUR
...

Import mínim (EUR): 10
=== Moviments amb import >= 10.0 ===
id_statement;tipus_moviment;import;divisa
3;Recargas;15.00;EUR
5;Recargas;10.00;EUR
...

=== Nou client ===
Nom: ...
Cognoms: ...
ID fiscal: ...
Client inserit correctament (1 fila).
```

Si s'introdueix un `id_fiscal` que ja existeix:

```
Ja existeix un client amb id fiscal XXXXXXXX. No s'insereix.
```