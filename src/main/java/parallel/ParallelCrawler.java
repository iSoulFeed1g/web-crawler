package parallel;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ParallelCrawler {

    private static final int MAX_DEPTH = 2;
    private static final int MAX_PAGES = 1000;
    private static final int THREAD_COUNT = 8;
    private static final String START_URL = "https://www.famnit.upr.si";

    private final Set<String> visited = ConcurrentHashMap.newKeySet();
    private final BlockingQueue<CrawlTask> queue = new LinkedBlockingQueue<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT, new ThreadFactory() {
        private final AtomicInteger count = new AtomicInteger(1);

        public Thread newThread(Runnable r) {
            return new Thread(r, "Worker-" + count.getAndIncrement());
        }
    });

    private final AtomicInteger pagesCrawled = new AtomicInteger(0);
    private final AtomicInteger activeWorkers = new AtomicInteger(0);
    private final ConcurrentLinkedQueue<String> crawlLogs = new ConcurrentLinkedQueue<>();

    public void start() {
        long start = System.currentTimeMillis();

        boolean offered = queue.offer(new CrawlTask(START_URL, 0));
        boolean added = visited.add(START_URL);
        logInfo("Root offered to queue: " + offered + ", visited added: " + added);

        for (int i = 0; i < THREAD_COUNT; i++) {
            executor.submit(() -> {
                try {
                    workerLoop();
                } catch (Exception e) {
                    logError("Worker crashed: " + e.getMessage());
                }
            });
        }

        new Thread(() -> {
            while (true) {
                if (queue.isEmpty() && activeWorkers.get() == 0) {
                    logInfo("Queue empty and no active workers. Shutting down.");
                    executor.shutdown();
                    break;
                }

                if (pagesCrawled.get() >= MAX_PAGES) {
                    logInfo("Page limit reached. Shutting down.");
                    executor.shutdown();
                    break;
                }

                try {
                    Thread.sleep(500);
                } catch (InterruptedException ignored) {
                }
            }
        }).start();

        try {
            executor.awaitTermination(10, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            logError("Interrupted");
        }

        long elapsed = System.currentTimeMillis() - start;
        logInfo(String.format("Crawl completed. Total pages: %d. Time: %d ms (%.2f seconds)",
                pagesCrawled.get(), elapsed, elapsed / 1000.0));

        try (PrintWriter writer = new PrintWriter("crawled_links_par.txt")) {
            for (String line : crawlLogs) {
                writer.println(line);
            }
            logInfo("Saved crawl log to crawled_links_par.txt");
        } catch (IOException e) {
            logError("Failed to write crawl log: " + e.getMessage());
        }
    }

    private void workerLoop() {
        while (!executor.isShutdown()) {
            if (pagesCrawled.get() >= MAX_PAGES)
                break;

            CrawlTask task = null;
            try {
                task = queue.poll(1, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
            }

            if (task == null) {
                if (queue.isEmpty() && activeWorkers.get() == 0)
                    break;
                continue;
            }

            activeWorkers.incrementAndGet();

            try {
                if (task.depth > MAX_DEPTH) {
                    logInfo("Skipping task due to depth limit: " + task.url);
                    continue;
                }

                logInfo("[Thread " + Thread.currentThread().getName() + "] Crawling (Depth " + task.depth + "): "
                        + task.url);
                crawlLogs.add(getTimestamp() + " | " + Thread.currentThread().getName() +
                        " | Depth: " + task.depth + " | URL: " + task.url);

                Document doc = Jsoup.connect(task.url).get();
                pagesCrawled.incrementAndGet();

                Elements links = doc.select("a[href]");
                for (Element link : links) {
                    String absHref = link.attr("abs:href").split("#")[0];
                    if (absHref.isEmpty())
                        continue;

                    if (isSameDomain(absHref)) {
                        boolean added = visited.add(absHref);
                        if (added) {
                            logInfo("Found new URL: " + absHref + " (Depth " + (task.depth + 1) + ")");
                            queue.offer(new CrawlTask(absHref, task.depth + 1));
                        }
                    } else {
                        logInfo("Skipped (different domain): " + absHref);
                    }
                }

            } catch (IOException e) {
                logError("Failed to fetch (" + e.getMessage() + ")");
            } finally {
                activeWorkers.decrementAndGet();
            }
        }
    }

    private boolean isSameDomain(String url) {
        try {
            URI base = new URI(START_URL);
            URI target = new URI(url);
            return target.getHost() != null && target.getHost().endsWith(base.getHost());
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private String getTimestamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
    }

    private void logInfo(String message) {
        System.out.println("[INFO] [" + Thread.currentThread().getName() + "] " +
                getTimestamp() + " - " + message);
    }

    private void logError(String message) {
        System.err.println("[ERROR] [" + Thread.currentThread().getName() + "] " +
                getTimestamp() + " - " + message);
    }

    private record CrawlTask(String url, int depth) {
    }

    public static void main(String[] args) {
        new ParallelCrawler().start();
    }
}
