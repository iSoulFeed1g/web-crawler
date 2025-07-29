package distributed;

import mpi.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.PrintWriter;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.*;

public class DistributedCrawler {

    private static final String START_URL = "https://www.famnit.upr.si";
    private static final int MAX_DEPTH = 2;
    private static final int MAX_PAGES = 1000;
    private static final int MASTER = 0;
    private static final int BUFFER_SIZE = 200000;

    public static void main(String[] args) throws Exception {
        MPI.Init(args);
        int rank = MPI.COMM_WORLD.Rank();
        int size = MPI.COMM_WORLD.Size();

        if (rank == MASTER) {
            logInfo("[MASTER] Starting distributed crawl with " + size + " processes...");
            runMaster(size);
        } else {
            runWorker(rank);
        }

        MPI.Finalize();
    }

    private static void runMaster(int size) throws Exception {
        Queue<CrawlTask> queue = new LinkedList<>();
        Set<String> visited = new HashSet<>();
        List<String> crawlLog = new ArrayList<>();

        queue.add(new CrawlTask(START_URL, 0));
        visited.add(START_URL);

        while (!queue.isEmpty() && visited.size() < MAX_PAGES) {
            int activeWorkers = 0;
            Map<Integer, CrawlTask> workerTasks = new HashMap<>();

            for (int i = 1; i < size && !queue.isEmpty(); i++) {
                CrawlTask task = queue.poll();
                if (task == null) break;

                String message = task.url + " " + task.depth;
                MPI.COMM_WORLD.Send(message.getBytes(), 0, message.length(), MPI.BYTE, i, 0);
                workerTasks.put(i, task);
                activeWorkers++;
            }

            for (int i = 1; i <= activeWorkers; i++) {
                byte[] buffer = new byte[BUFFER_SIZE];
                Status status = MPI.COMM_WORLD.Recv(buffer, 0, buffer.length, MPI.BYTE, MPI.ANY_SOURCE, 1);
                int sender = status.source;
                String data = new String(buffer, 0, status.count).trim();

                if (!data.isEmpty()) {
                    String[] lines = data.split("\n");
                    for (String line : lines) {
                        String[] parts = line.trim().split(" ");
                        if (parts.length < 2) continue;
                        String url = parts[0];
                        int depth = Integer.parseInt(parts[1]);
                        if (!visited.contains(url) && depth <= MAX_DEPTH) {
                            visited.add(url);
                            queue.add(new CrawlTask(url, depth));
                            logInfo("Discovered: " + url + " (depth " + depth + ")");
                            crawlLog.add(getTimestamp() + " - Discovered: " + url + " (depth " + depth + ")");
                        }
                    }
                }
            }
        }

        for (int i = 1; i < size; i++) {
            MPI.COMM_WORLD.Send("STOP".getBytes(), 0, 4, MPI.BYTE, i, 0);
        }

        try (PrintWriter writer = new PrintWriter("crawled_links_dis.txt")) {
            for (String log : crawlLog) {
                writer.println(log);
            }
        }

        logInfo("[MASTER] Crawl complete. " + visited.size() + " pages visited.");
    }

    private static void runWorker(int rank) throws Exception {
        while (true) {
            byte[] buffer = new byte[1024];
            Status status = MPI.COMM_WORLD.Recv(buffer, 0, buffer.length, MPI.BYTE, 0, 0);
            String received = new String(buffer, 0, status.count).trim();

            if (received.equals("STOP")) break;

            String[] parts = received.split(" ", 2);
            String url = parts[0];
            int depth = Integer.parseInt(parts[1]);

            logInfo("Process " + rank + " is crawling: " + url);

            StringBuilder results = new StringBuilder();
            try {
                Document doc = Jsoup.connect(url).get();
                Elements links = doc.select("a[href]");

                for (Element link : links) {
                    String href = link.attr("abs:href").split("#")[0];
                    if (!href.isEmpty() && isSameDomain(href)) {
                        results.append(href).append(" ").append(depth + 1).append("\n");
                        if (results.length() > BUFFER_SIZE - 1000) break;
                    }
                }
            } catch (IOException ignored) {}

            byte[] resultBytes = results.toString().getBytes();
            if (resultBytes.length == 0) {
                resultBytes = " ".getBytes();
            }

            MPI.COMM_WORLD.Send(resultBytes, 0, resultBytes.length, MPI.BYTE, 0, 1);
        }
    }

    private static boolean isSameDomain(String url) {
        try {
            URI base = new URI(START_URL);
            URI target = new URI(url);
            return target.getHost() != null && target.getHost().endsWith(base.getHost());
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private static String getTimestamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
    }

    private static void logInfo(String message) {
        System.out.println("[INFO] " + getTimestamp() + " - " + message);
    }

    private record CrawlTask(String url, int depth) {}
}
