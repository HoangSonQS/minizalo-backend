package iuh.fit.se.minizalobackend.services.impl;

import iuh.fit.se.minizalobackend.dtos.response.LinkPreviewResponse;
import iuh.fit.se.minizalobackend.services.LinkPreviewService;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

@Service
@Slf4j
public class LinkPreviewServiceImpl implements LinkPreviewService {

    private static final int TIMEOUT_MS = 8_000;
    private static final int MAX_BODY_BYTES = 512_000;

    @Override
    public LinkPreviewResponse fetchPreview(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new IllegalArgumentException("URL is required");
        }
        String trimmed = rawUrl.trim();
        URI uri;
        try {
            uri = URI.create(trimmed);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid URL");
        }
        String scheme = uri.getScheme();
        if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
            throw new IllegalArgumentException("Only http(s) URLs are allowed");
        }
        String host = uri.getHost();
        if (host == null || !isHostAllowed(host)) {
            throw new IllegalArgumentException("URL host is not allowed");
        }

        try {
            Document doc = Jsoup.connect(trimmed)
                    .timeout(TIMEOUT_MS)
                    .maxBodySize(MAX_BODY_BYTES)
                    .userAgent("MiniZalo-LinkPreview/1.0")
                    .followRedirects(true)
                    .get();

            String title = metaOrNull(doc, "og:title");
            if (title == null || title.isBlank()) {
                Element t = doc.selectFirst("title");
                title = t != null ? t.text() : null;
            }
            String description = metaOrNull(doc, "og:description");
            if (description == null || description.isBlank()) {
                Element m = doc.selectFirst("meta[name='description']");
                description = m != null ? m.attr("content") : null;
            }
            String imageUrl = metaOrNull(doc, "og:image");

            return LinkPreviewResponse.builder()
                    .url(trimmed)
                    .title(title != null ? title.strip() : null)
                    .description(description != null ? description.strip() : null)
                    .imageUrl(imageUrl != null && !imageUrl.isBlank() ? resolveUrl(uri, imageUrl.strip()) : null)
                    .build();
        } catch (Exception e) {
            log.warn("Link preview failed for {}: {}", trimmed, e.getMessage());
            return LinkPreviewResponse.builder()
                    .url(trimmed)
                    .title(null)
                    .description(null)
                    .imageUrl(null)
                    .build();
        }
    }

    private static String metaOrNull(Document doc, String property) {
        Element el = doc.selectFirst("meta[property='" + property + "']");
        if (el == null) {
            el = doc.selectFirst("meta[name='" + property + "']");
        }
        if (el == null) {
            return null;
        }
        String c = el.attr("content");
        return c != null && !c.isBlank() ? c : null;
    }

    private static String resolveUrl(URI base, String ref) {
        try {
            return base.resolve(ref).normalize().toString();
        } catch (Exception e) {
            return ref;
        }
    }

    private static boolean isHostAllowed(String host) {
        String h = host.toLowerCase();
        if ("localhost".equals(h) || h.endsWith(".localhost")) {
            return false;
        }
        if (h.startsWith("127.") || h.startsWith("0.")) {
            return false;
        }
        if (h.startsWith("192.168.") || h.startsWith("10.") || h.startsWith("169.254.")) {
            return false;
        }
        if (h.startsWith("172.")) {
            String[] p = h.split("\\.");
            if (p.length >= 2) {
                try {
                    int second = Integer.parseInt(p[1]);
                    if (second >= 16 && second <= 31) {
                        return false;
                    }
                } catch (NumberFormatException ignored) {
                    // continue
                }
            }
        }
        try {
            InetAddress addr = InetAddress.getByName(h);
            if (addr.isLoopbackAddress() || addr.isSiteLocalAddress() || addr.isLinkLocalAddress()) {
                return false;
            }
        } catch (UnknownHostException ignored) {
            // allow hostname that does not resolve here (may work from server later)
        }
        return true;
    }
}
