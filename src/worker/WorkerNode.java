package worker;

import common.*;
import java.io.*;
import java.net.*;

public class WorkerNode {

    public static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : MessageProtocol.DEFAULT_HOST;
        int port = args.length > 1 ? Integer.parseInt(args[1]) : MessageProtocol.DEFAULT_PORT;
        int threads = args.length > 2 ? Integer.parseInt(args[2]) : 8;

        System.out.printf("Worker starting — connecting to %s:%d with %d threads%n", host, port, threads);

        try (Socket socket = new Socket(host, port)) {
            socket.setSoTimeout(MessageProtocol.SOCKET_TIMEOUT_MS);

            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

            out.writeInt(MessageProtocol.CONNECT);
            out.flush();
            System.out.println("Worker: connected to master");

            int msgType = in.readInt();
            if (msgType != MessageProtocol.TASK_ASSIGN) {
                System.err.println("Worker: expected TASK_ASSIGN, got " + msgType);
                return;
            }

            SceneConfig scene = (SceneConfig) in.readObject();
            Tile tile = (Tile) in.readObject();
            System.out.printf("Worker: received %s  scene=%dx%d depth=%d%n",
                    tile, scene.width, scene.height, scene.maxDepth);

            long start = System.nanoTime();
            TileRenderer renderer = new TileRenderer(scene, threads);
            byte[] pixels = renderer.render(tile);
            long elapsed = (System.nanoTime() - start) / 1_000_000;
            System.out.printf("Worker: rendered %s in %d ms%n", tile, elapsed);

            out.writeInt(MessageProtocol.RESULT_RETURN);
            out.writeObject(tile);
            out.writeObject(pixels);
            out.flush();
            System.out.println("Worker: result sent");

            int shutdown = in.readInt();
            if (shutdown == MessageProtocol.SHUTDOWN) {
                System.out.println("Worker: received SHUTDOWN — exiting cleanly");
            }
        }
    }
}