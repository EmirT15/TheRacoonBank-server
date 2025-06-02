import java.io.*;
import java.net.*;
import java.sql.*;
import org.sqlite.JDBC;

public class ServidorTheRacoonbank {
    private static final int PUERTO = Integer.parseInt(System.getenv().getOrDefault("PORT", "12345"));
    private static Connection conexionBD;
    private static ServerSocket serverSocket;
    private static volatile boolean ejecutando = true;

     public static void main(String[] args) {
        // Registrar el driver de SQLite manualmente
        try {
            DriverManager.registerDriver(new JDBC());
        } catch (SQLException e) {
            System.err.println("Error al registrar el driver SQLite: " + e.getMessage());
            System.exit(1);
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            ejecutando = false;
            cerrarRecursos();
        }));

        try {
            conectarBD();
            crearTablasSiNoExisten();

            serverSocket = new ServerSocket(PUERTO);
            System.out.println("Servidor iniciado en el puerto " + PUERTO);

            while (ejecutando) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    // Configurar timeout para desconexiones silenciosas
                    clientSocket.setSoTimeout(30000); // 30 segundos
                    
                    new Thread(new ManejadorCliente(clientSocket)).start();
                } catch (SocketTimeoutException e) {
                    // Timeout normal durante accept()
                } catch (IOException e) {
                    if (ejecutando) {
                        System.err.println("Error aceptando conexión: " + e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            cerrarRecursos();
        }
    }

    private static void cerrarRecursos() {
        System.out.println("Cerrando recursos del servidor...");
        
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            if (conexionBD != null && !conexionBD.isClosed()) {
                conexionBD.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static void conectarBD() throws SQLException {
        // Asegurarse de que la URL de conexión es correcta
        conexionBD = DriverManager.getConnection("jdbc:sqlite:finanzas.db");
        System.out.println("Conectado a SQLite correctamente");
    }

    private static void crearTablasSiNoExisten() throws SQLException {
        Statement stmt = null;
        try {
            stmt = conexionBD.createStatement();

            // Tabla de transacciones (ingresos y gastos)
            stmt.execute("CREATE TABLE IF NOT EXISTS transacciones (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "fecha TEXT NOT NULL, " +
                    "tipo TEXT NOT NULL, " +  // 'Ingreso' o 'Gasto'
                    "monto REAL NOT NULL)");
        } finally {
            if (stmt != null) {
                stmt.close();
            }
        }
    }

    private static class ManejadorCliente implements Runnable {
        private final Socket socket;

        public ManejadorCliente(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try (BufferedReader entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter salida = new PrintWriter(socket.getOutputStream(), true)) {
                
                // Leer primera línea para determinar el tipo de conexión
                String primeraLinea = entrada.readLine();
                if (primeraLinea == null) {
                    return; // Conexión cerrada sin datos
                }

                // Manejar health checks de Render
                if (primeraLinea.startsWith("HEAD ") || primeraLinea.startsWith("GET ")) {
                    handleHealthCheck(salida);
                    return;
                }

                // Procesar comandos normales del protocolo TheRacoonBank
                String respuesta = procesarMensaje(primeraLinea);
                salida.println(respuesta);

                // Procesar líneas adicionales si existen (para sesiones multi-comando)
                String lineaAdicional;
                while ((lineaAdicional = entrada.readLine()) != null) {
                    respuesta = procesarMensaje(lineaAdicional);
                    salida.println(respuesta);
                }
            } catch (IOException e) {
                System.err.println("Error con cliente " + socket.getInetAddress() + ": " + e.getMessage());
            } finally {
                try {
                    socket.close();
                } catch (IOException e) {
                    System.err.println("Error al cerrar socket: " + e.getMessage());
                }
            }
        }

        private void handleHealthCheck(PrintWriter salida) {
            // Respuesta simple para health checks de Render
            salida.println("HTTP/1.1 200 OK\r\n");
            salida.println("Content-Type: text/plain\r\n");
            salida.println("\r\n");
            salida.println("OK");
            salida.flush();
        }

        private String procesarMensaje(String mensaje) {
            String[] partes = mensaje.split("\\|");
            String comando = partes[0];

            try {
                switch (comando) {
                    case "AGREGAR_TRANSACCION":
                        if (partes.length < 4) {
                            return "ERROR|Faltan parámetros. Formato esperado: AGREGAR_TRANSACCION|fecha|tipo|monto";
                        }
                        return agregarTransaccion(partes[1], partes[2], Double.parseDouble(partes[3]));
                    case "OBTENER_TRANSACCIONES":
                        return obtenerTransacciones();
                    case "OBTENER_SALDO":
                        return obtenerSaldo();
                    default:
                        return "ERROR|Comando no reconocido";
                }
            } catch (Exception e) {
                e.printStackTrace();
                return "ERROR|" + e.getMessage();
            }
        }

        private String agregarTransaccion(String fecha, String tipo, double monto) throws SQLException {
            // Validar el tipo de transacción
            if (!tipo.equalsIgnoreCase("Ingreso") && !tipo.equalsIgnoreCase("Gasto")) {
                return "ERROR|Tipo de transacción inválido. Debe ser 'Ingreso' o 'Gasto'";
            }

            PreparedStatement stmt = null;
            try {
                stmt = conexionBD.prepareStatement(
                        "INSERT INTO transacciones (fecha, tipo, monto) VALUES (?, ?, ?)");

                stmt.setString(1, fecha);
                stmt.setString(2, tipo);
                stmt.setDouble(3, monto);

                stmt.executeUpdate();
                return "OK|Transacción agregada correctamente";
            } finally {
                if (stmt != null) {
                    stmt.close();
                }
            }
        }

        private String obtenerTransacciones() throws SQLException {
            PreparedStatement stmt = null;
            ResultSet rs = null;
            try {
                stmt = conexionBD.prepareStatement(
                        "SELECT id, fecha, tipo, monto FROM transacciones ORDER BY fecha DESC");
                rs = stmt.executeQuery();

                StringBuilder sb = new StringBuilder();
                boolean hayTransacciones = false;

                while (rs.next()) {
                    hayTransacciones = true;
                    sb.append(String.format("%d|%s|%s|%.2f;",
                            rs.getInt("id"),
                            rs.getString("fecha"),
                            rs.getString("tipo"),
                            rs.getDouble("monto")));
                }

                return hayTransacciones ? "OK|" + sb.toString() : "OK|NO_HAY";
            } finally {
                if (rs != null) rs.close();
                if (stmt != null) stmt.close();
            }
        }

        private String obtenerSaldo() throws SQLException {
            PreparedStatement stmt = null;
            ResultSet rs = null;
            try {
                stmt = conexionBD.prepareStatement(
                        "SELECT " +
                        "SUM(CASE WHEN tipo = 'Ingreso' THEN monto ELSE 0 END) as total_ingresos, " +
                        "SUM(CASE WHEN tipo = 'Gasto' THEN monto ELSE 0 END) as total_gastos " +
                        "FROM transacciones");
                rs = stmt.executeQuery();

                if (rs.next()) {
                    double ingresos = rs.getDouble("total_ingresos");
                    double gastos = rs.getDouble("total_gastos");
                    double saldo = ingresos - gastos;
                    return String.format("OK|%.2f|%.2f|%.2f", ingresos, gastos, saldo);
                }

                return "OK|0.00|0.00|0.00";
            } finally {
                if (rs != null) rs.close();
                if (stmt != null) stmt.close();
            }
        }
    }
}