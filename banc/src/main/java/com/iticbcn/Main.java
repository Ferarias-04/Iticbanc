package com.iticbcn;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.pool.HikariPool.PoolInitializationException;
public class Main {

    public static void main(String[] args) {

        // ---------- 1. Carreguem les credencials del fitxer .properties ----------
        Properties prop = new Properties();
        try {
            prop.load(Files.newBufferedReader(Path.of("banc", "src", "main", "resources", "conn.properties")));
        } catch (IOException ex) {
            System.out.println("No s'ha pogut llegir db.properties: " + ex.getMessage());
            return; // sense credencials no té sentit continuar
        }

        // ---------- 2. Configurem el pool de connexions (HikariCP) ----------
        HikariConfig hkcfg = new HikariConfig();
        hkcfg.setJdbcUrl(prop.getProperty("DB_URL"));
        hkcfg.setUsername(prop.getProperty("DB_USER"));
        hkcfg.setPassword(prop.getProperty("DB_PASSWORD"));
        hkcfg.setMaximumPoolSize(Integer.parseInt(prop.getProperty("DB_MAX_POOL", "5")));

        // ---------- 3. Obrim el datasource i una connexió ----------
        try (HikariDataSource hkds = new HikariDataSource(hkcfg);
             Connection cnn = hkds.getConnection();
             Scanner sc = new Scanner(System.in)) {

            System.out.println("Connectat!");

            // ---------- Punts 4 i 5: tots els moviments ----------
            System.out.println("\n=== Tots els moviments ===");
            for (String resultat : readAllStatements(cnn)) {
                System.out.println("moviment amb id: " + resultat);
            }

            // ---------- Exercici 1: moviments amb import >= quantitat ----------
            System.out.print("\nImport mínim (€): ");
            double quantitat = Double.parseDouble(sc.nextLine().trim());
            System.out.println("=== Moviments amb import >= " + quantitat + " ===");
            for (String linia : readStatementsByImport(cnn, quantitat)) {
                System.out.println(linia);
            }

            // ---------- Exercici 2: inserir un client nou ----------
            System.out.println("\n=== Nou client ===");
            List<String> dadesClient = new ArrayList<>();
            System.out.print("Nom: ");
            dadesClient.add(sc.nextLine().trim());
            System.out.print("Cognoms: ");
            dadesClient.add(sc.nextLine().trim());
            System.out.print("ID fiscal: ");
            dadesClient.add(sc.nextLine().trim());

            insertClient(cnn, dadesClient);

        } catch (PoolInitializationException e) {
            System.out.println("No s'ha pogut connectar a la base de dades: " + e.getMessage());
        } catch (SQLException e) {
            System.out.println("Error SQL: " + e.getMessage());
            e.printStackTrace();
        } catch (NumberFormatException e) {
            System.out.println("L'import introduït no és un número vàlid.");
        }
    }

    // ===================== PUNT 5 =====================
    public static List<String> readAllStatements(Connection conn) throws SQLException {
        List<String> resultatQuery = new ArrayList<>();
        String query = "SELECT * FROM statements";

        try (PreparedStatement ps = conn.prepareStatement(query);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String resultat = String.join(";",
                        rs.getString("id_statement"),
                        rs.getString("tipus_moviment"),
                        rs.getString("import"),
                        rs.getString("divisa"));
                resultatQuery.add(resultat);
            }
        }
        return resultatQuery;
    }

    // ===================== EXERCICI 1 =====================
    public static List<String> readStatementsByImport(Connection conn, double quantitat) throws SQLException {
        List<String> resultatQuery = new ArrayList<>();
        String query = "SELECT * FROM statements WHERE import >= ?";

        try (PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setDouble(1, quantitat);

            try (ResultSet rs = ps.executeQuery()) {
                // Capçalera amb els noms de les columnes 1, 3, 8 i 10
                ResultSetMetaData meta = rs.getMetaData();
                String header = String.join(";",
                        meta.getColumnName(1),
                        meta.getColumnName(3),
                        meta.getColumnName(8),
                        meta.getColumnName(10));
                resultatQuery.add(header);

                while (rs.next()) {
                    String resultat = String.join(";",
                            rs.getString("id_statement"),
                            rs.getString("tipus_moviment"),
                            rs.getString("import"),
                            rs.getString("divisa"));
                    resultatQuery.add(resultat);
                }
            }
        }
        return resultatQuery;
    }

    // ===================== EXERCICI 2 =====================

    // Comprova si ja existeix un client amb aquest id_fiscal
    public static boolean existeixClient(Connection conn, String idFiscal) throws SQLException {
        String query = "SELECT 1 FROM clients WHERE id_fiscal = ?";
        try (PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, idFiscal);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next(); // true si ha trobat alguna fila
            }
        }
    }

    // Insereix el client si no existeix, gestionant commit i rollback
    public static void insertClient(Connection conn, List<String> dades) throws SQLException {
        String nom = dades.get(0);
        String cognoms = dades.get(1);
        String idFiscal = dades.get(2);

        boolean autoCommitOriginal = conn.getAutoCommit();
        conn.setAutoCommit(false); // comencem una transacció manual

        try {
            if (existeixClient(conn, idFiscal)) {
                System.out.println("Ja existeix un client amb id fiscal " + idFiscal + ". No s'insereix.");
                conn.rollback(); // tanquem la transacció sense canvis
                return;
            }

            String insert = "INSERT INTO clients (nom, cognoms, id_fiscal) VALUES (?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(insert)) {
                ps.setString(1, nom);
                ps.setString(2, cognoms);
                ps.setString(3, idFiscal);
                int files = ps.executeUpdate();

                conn.commit(); // tot ha anat bé: confirmem
                System.out.println("Client inserit correctament (" + files + " fila).");
            }

        } catch (SQLException e) {
            conn.rollback(); // alguna cosa ha fallat: desfem
            System.out.println("Error en inserir el client. S'ha fet rollback.");
            throw e;
        } finally {
            conn.setAutoCommit(autoCommitOriginal); // deixem la connexió com estava (torna al pool)
        }
    }
}