package master;

import common.*;
import java.io.*;
import java.net.*;
import java.util.List;

public class MasterNode {

    public static void main(String[] args) throws Exception {

        int nWorkers = args.length > 0 ? Integer.parseInt(args[0]) : 3;
        int width    = args.length > 1 ? Integer.parseInt(args[1]) : 960;
        int height   = args.length > 2 ? Integer.parseInt(args[2]) : 540;
        int maxDepth = args.length > 3 ? Integer.parseInt(args[3]) : 5;
        int nThreads = args.length > 4 ? Integer.parseInt(args[4]) : 8;

        System.out.printf(
            "Master: starting — %dx%d  depth=%d  workers=%d  threads/worker=%d%n",
            width, height, maxDepth, nWorkers, nThreads);

        SceneConfig      scene      = SceneConfig.buildDefaultScene(width, height, maxDepth);
        List<Tile>       tiles      = WorkPartitioner.partition(height, nWorkers);
        ResultAggregator aggregator = new ResultAggregator(width, height);

        for (Tile t : tiles) {
            System.out.println("Master: planned " + t);
        }

        Socket[]             sockets = new Socket[nWorkers];
        ObjectOutputStream[] outs    = new ObjectOutputStream[nWorkers];
        ObjectInputStream[]  ins     = new ObjectInputStream[nWorkers];

        // Keep ServerSocket open until all workers connected
        ServerSocket server = new ServerSocket(MessageProtocol.DEFAULT_PORT);
        server.setSoTimeout(60_000);
        System.out.println("Master: listening on port " + MessageProtocol.DEFAULT_PORT
                           + " — start your workers now");

        for (int i = 0; i < nWorkers; i++) {
            sockets[i] = server.accept();

            // CRITICAL: Master does OIS first, then OOS.
            // Worker does OOS first, then OIS.
            // Opposite order on each end prevents deadlock on stream header exchange.
            ins[i]  = new ObjectInputStream(sockets[i].getInputStream());
            outs[i] = new ObjectOutputStream(sockets[i].getOutputStream());
            outs[i].flush();

            int connectMsg = ins[i].readInt();
            if (connectMsg != MessageProtocol.CONNECT) {
                System.err.println("Master: worker " + i + " bad handshake: " + connectMsg);
            }
            System.out.println("Master: worker " + i + " connected from "
                               + sockets[i].getRemoteSocketAddress());
        }

        server.close();

        long startTime = System.nanoTime();

        for (int i = 0; i < nWorkers; i++) {
            outs[i].writeInt(MessageProtocol.TASK_ASSIGN);
            outs[i].writeObject(scene);
            outs[i].writeObject(tiles.get(i));
            outs[i].writeInt(nThreads);
            outs[i].flush();
            System.out.println("Master: dispatched " + tiles.get(i) + " → worker " + i);
        }

        for (int i = 0; i < nWorkers; i++) {
            int msgType = ins[i].readInt();
            if (msgType != MessageProtocol.RESULT_RETURN) {
                System.err.println("Master: expected RESULT_RETURN from worker " + i
                                   + " but got " + msgType);
                continue;
            }
            Tile   resultTile = (Tile)   ins[i].readObject();
            byte[] pixels     = (byte[]) ins[i].readObject();
            aggregator.integrate(resultTile, pixels);
            System.out.println("Master: received result for " + resultTile);
        }

        long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;
        System.out.printf("Master: total parallel render time: %d ms (%.2f s)%n",
                          elapsedMs, elapsedMs / 1000.0);

        for (int i = 0; i < nWorkers; i++) {
            outs[i].writeInt(MessageProtocol.SHUTDOWN);
            outs[i].flush();
            sockets[i].close();
            System.out.println("Master: sent SHUTDOWN → worker " + i);
        }

        String imgPath = String.format("output/par_%dx%d_d%d.png", width, height, maxDepth);
        aggregator.savePNG(imgPath);

        new java.io.File("results").mkdirs();
        String  csvFile = "results/parallel_benchmark.csv";
        boolean newFile = !new java.io.File(csvFile).exists();
        try (java.io.PrintWriter pw = new java.io.PrintWriter(
                new java.io.FileWriter(csvFile, true))) {
            if (newFile) pw.println("width,height,maxDepth,workers,threads,time_ms");
            pw.printf("%d,%d,%d,%d,%d,%d%n",
                      width, height, maxDepth, nWorkers, nThreads, elapsedMs);
        }
        System.out.println("Master: benchmark saved → " + csvFile);
        System.out.println("Master: done.");
    }
}