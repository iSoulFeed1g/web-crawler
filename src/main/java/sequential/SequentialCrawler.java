package sequential;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

public class SequentialCrawler {
    private static int maxDepth = 3; // Default maximum crawl depth
    private static int maxPages = 1000; // Default maximum pages to crawl
    private static String domain = "famnit.upr.si"; // Default domain restriction
    private static final Set<String> visitedLinks = new HashSet<>(); // Store visited links
    private static int crawledCount = 0; // Counter for crawled pages

    public static void main(String[] args) {
        // Read parameters from command-line arguments if provided
        if (args.length >= 3) {
            maxDepth = Integer.parseInt(args[0]);
            maxPages = Integer.parseInt(args[1]);
            domain = args[2];
        }

        String startUrl = "https://" + domain;

        // Start timing
        long startTime = System.currentTimeMillis();

        try (FileWriter logFile = new FileWriter("crawled_links.txt")) {
            logInfo("Starting crawl with:\n" +
                    "       - Max Depth: " + maxDepth + "\n" +
                    "       - Max Pages: " + maxPages + "\n" +
                    "       - Domain: " + domain);

            crawl(startUrl, 0, logFile);

            // Stop timing and calculate total time
            long endTime = System.currentTimeMillis();
            long timeTaken = endTime - startTime;

            logInfo("Crawl completed. Total pages crawled: " + crawledCount);
            logInfo("Time taken for crawl: " + timeTaken + " ms (" + (timeTaken / 1000) + " seconds)");
        } catch (IOException e) {
            logError("Failed to initialize log file: " + e.getMessage());
        }
    }

    public static void crawl(String url, int depth, FileWriter logFile) {
        // Normalize URL and check if it has already been visited
        String normalizedUrl = normalizeUrl(url);
        if (normalizedUrl == null || visitedLinks.contains(normalizedUrl)) {
            logInfo("Skipping already visited or invalid URL: " + url);
            return;
        }

        try {
            visitedLinks.add(normalizedUrl); // Mark URL as visited immediately
            crawledCount++;
            logInfo("Crawling (Depth " + depth + "): " + normalizedUrl);

            // Fetch the page
            Document document = retryJsoupConnection(normalizedUrl);
            if (document == null) {
                logError("Failed to fetch URL after retries: " + normalizedUrl);
                return;
            }

            // Log the current page
            logFile.write(getTimestamp() + " | Depth: " + depth + " | URL: " + normalizedUrl + "\n");

            // Process links only if depth allows
            if (depth < maxDepth) {
                Elements links = document.select("a[href]");

                for (var link : links) {
                    String absUrl = normalizeUrl(link.attr("abs:href"));

                    // Skip invalid or non-relevant links
                    if (absUrl != null && absUrl.contains(domain) && absUrl.startsWith("http") && !absUrl.endsWith(".pdf")) {
                        crawl(absUrl, depth + 1, logFile); // Recursive call
                    }
                }
            }

            // Periodic progress feedback
            if (crawledCount % 100 == 0) {
                logInfo("Progress: " + crawledCount + " pages crawled so far.");
            }
        } catch (Exception e) {
            logError("Unexpected error while crawling URL: " + normalizedUrl + " - " + e.getMessage());
        }
    }

    public static String normalizeUrl(String url) {
        try {
            URI uri = new URI(url).normalize();
            String normalized = uri.getScheme() + "://" + uri.getHost() + uri.getPath();
            // Remove query parameters and fragments, standardize trailing slashes
            normalized = normalized.split("\\?")[0].split("#")[0];
            return normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
        } catch (URISyntaxException e) {
            logError("Invalid URL: " + url);
            return null;
        }
    }

    public static Document retryJsoupConnection(String url) {
        int retries = 3;
        int delay = 2000; // 2 seconds
        for (int i = 0; i < retries; i++) {
            try {
                return Jsoup.connect(url).timeout(10000).get();
            } catch (IOException e) {
                logError("Retry " + (i + 1) + " for URL: " + url);
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ignored) {
                }
            }
        }
        return null;
    }

    public static String getTimestamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
    }

    public static void logInfo(String message) {
        System.out.println("[INFO] " + getTimestamp() + " - " + message);
    }

    public static void logError(String message) {
        System.err.println("[ERROR] " + getTimestamp() + " - " + message);
    }
}
