package ee.rik.test;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.ResourceAccessException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SpiderTests {

    private static Logger LOG = LoggerFactory.getLogger(SpiderTests.class);

    private Set<String> urls = new HashSet<>();
    private Set<SiteData> report = new HashSet<>();
    private TestRestTemplate restTemplate = new TestRestTemplate();

    private List<String> rows = new ArrayList<>();

    @Test
    public void someTest() throws IOException {
        var site = "https://www.rik.ee/";

        scrape(site);
        LOG.info("urls size: {}", urls.size());

        // TODO: for each URL, perform GET, HEAD, POST requests

        urls.forEach(u -> makeRequests(u));

        LOG.info("report size: {}", report.size());

        writeCsvFile();
    }

    private void writeCsvFile() throws FileNotFoundException {
        List<String> lines = new ArrayList<>();
        lines.add(Stream.of("URL", "METHOD", "CONTENT TYPE", "CONTENT LENGTH", "STATUS CODE").collect(Collectors.joining(",")));
        report.forEach(siteData -> {
            siteData.methodResponses.forEach((method, data) -> {
                List<String> row = new ArrayList<>();
                row.add(siteData.url);
                row.add(method);
                row.add(data.contentType);
                row.add(data.contentLength.toString());
                row.add(data.statusCode);
                lines.add(row.stream().collect(Collectors.joining(",")));
            });
        });

        var csvOutputFile = new File("test.csv");
        try (PrintWriter pw = new PrintWriter(csvOutputFile)) {
            lines.stream().forEach(pw::println);
        }

    }

    private void makeRequests(String url) {
        var methods = List.of(HttpMethod.GET, HttpMethod.POST, HttpMethod.OPTIONS, HttpMethod.HEAD, HttpMethod.TRACE);
        var siteData = new SiteData(url);

        methods.forEach(httpMethod -> {
            try {
                var response = restTemplate.exchange(url, httpMethod, new HttpEntity<>(null), String.class);
                LOG.info("{} status: {} contentLength: {} contentType: {}", httpMethod.toString(), response.getStatusCode(), response.getHeaders().getContentLength(), response.getHeaders().getContentType());

                var contentType = response.getHeaders().getContentType() != null ? response.getHeaders().getContentType().getType() : null;
                var data = new Data(response.getStatusCode().toString(), response.getHeaders().getContentLength(), contentType);
                siteData.addHttpMethodData(httpMethod.toString(), data);
            } catch(ResourceAccessException e) {
                LOG.info("error: {}", e.getMessage());
            }
        });
        report.add(siteData);
    }

    private void scrape(String url) throws IOException {
        var document = Jsoup.connect(url).get();
        var anchors = document.getElementsByTag("a");

        anchors.forEach(a -> {
            var href = a.attr("href");
            if(href.startsWith("/")) {
                var site = "https://www.rik.ee" + href;

                if(!urls.contains(site)) {
                    urls.add(site);

                    LOG.info("--- new site: {}", site);

                    try {
                        scrape(site);
                        //throw new IOException("");
                    } catch (IOException e) {
                        LOG.info("error fetching {}, message {}", site, e.getMessage());
                    }
                }
            }
        });

    }

    private class Data {
        private String statusCode;
        private Long contentLength;
        private String contentType;

        public Data() {}

        public Data(String statusCode, Long contentLength, String contentType) {
            this.statusCode = statusCode;
            this.contentLength = contentLength;
            this.contentType = contentType;
        }
    }

    private class SiteData {
        private String url;

        private HashMap<String, Data> methodResponses = new HashMap<>();

        public SiteData() {}
        public SiteData(String url) {
            this.url = url;
        }

        public void addHttpMethodData(String method, Data data) {
            methodResponses.put(method, data);
        }

    }
}
