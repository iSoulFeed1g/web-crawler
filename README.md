# Web Crawler: Sequential, Parallel, and Distributed Implementation

## Overview
This project showcases the design and development of a domain-restricted web crawler written in Java.  
The crawler was built in three execution modes:

- **Sequential:** Single-threaded BFS
- **Parallel:** Multithreaded using Java’s `ExecutorService`
- **Distributed:** Multi-process implementation using MPJ Express (MPI for Java)

The goal was to explore how crawling performance scales when moving from simple to more advanced concurrent and distributed programming models.

This project was developed for the course **Programming III – Concurrent Programming** during the 2024/25 academic year at the  
**Faculty of Mathematics, Natural Sciences and Information Technologies (UP FAMNIT), Koper, Slovenia**.

---

## Project Description
The web crawler navigates a given website and extracts internal links up to a specified depth.  
It classifies each link as working or broken based on HTTP response codes and avoids re-visiting duplicate URLs.

---

## Key Components

### URL Parsing
Uses the **JSoup** library to parse HTML and extract anchor tags (`<a>`).

### Breadth-First Search
Core strategy for crawling, with support for depth-limiting and domain filtering.

### Link Validation
HTTP request check to identify working (e.g., `200 OK`) vs broken (e.g., `404 Not Found`) links.

### Logging
Each crawl stores:
- Discovered URLs
- Status codes
- Source page of the link

---

## Implementation Modes

### Sequential Mode
Performs BFS crawl on a single thread.  
**Best suited for small websites or initial testing.**

### Parallel Mode
Uses multiple threads to process pages concurrently.  
Implemented using Java’s `ExecutorService`.

### Distributed Mode
Employs **MPJ Express** to distribute crawling tasks across multiple JVM processes.  
Enables scalability on multi-node systems.

---

## How to Run

1. Clone the repository.
2. Open in IntelliJ or any Java-compatible IDE.
3. Select the desired mode: `SequentialCrawler`, `ParallelCrawler`, or `DistributedCrawler`.
4. Provide a root URL and depth.
5. View crawl logs in the terminal or output files.

---

## Technologies Used

- Java 17
- JSoup
- MPJ Express
- Java Concurrency API
- IntelliJ IDEA

---

## Author

**Vanja Antonovic**  
University of Primorska – FAMNIT  
`89211069@student.upr.si`

---

## License

This project is provided for academic use only.
