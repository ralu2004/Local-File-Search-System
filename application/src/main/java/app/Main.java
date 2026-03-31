package app;

import app.cli.CLI;
import app.server.ApiServer;

/**
 * Application entry point.
 * <p>
 * Runs the REST API server when started with {@code server [port]}, otherwise
 * dispatches to the CLI.
 */
public class Main {
    public static void main(String[] args) {
        if (args.length > 0 && "server".equalsIgnoreCase(args[0])) {
            int port = 7070;
            if (args.length > 1) {
                try {
                    port = Integer.parseInt(args[1]);
                } catch (NumberFormatException ignored) {}
            }
            ApiServer server = new ApiServer(port);
            Runtime.getRuntime().addShutdownHook(new Thread(server::close));
            server.start();
            return;
        }
        CLI.main(args);
    }
}