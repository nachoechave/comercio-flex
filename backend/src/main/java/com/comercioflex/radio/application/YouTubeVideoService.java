package com.comercioflex.radio.application;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.net.http.HttpClient;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.springframework.stereotype.Service;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.comercioflex.radio.domain.RadioSite;

@Service
public class YouTubeVideoService {
	private static final Duration CACHE_TTL = Duration.ofMinutes(10);
	private final RestClient client = client();
	private final Map<String, CachedVideos> cache = new ConcurrentHashMap<>();

	public List<RadioSite.Video> latest(String channelId) {
		if (channelId == null || channelId.isBlank()) return List.of();
		String normalized = channelId.strip();
		CachedVideos cached = cache.get(normalized);
		if (cached != null && cached.expiresAt().isAfter(Instant.now())) return cached.videos();
		try {
			String xml = client.get().uri(uri -> uri.path("/feeds/videos.xml").queryParam("channel_id", normalized).build())
				.retrieve().body(String.class);
			List<RadioSite.Video> videos = parse(xml);
			cache.put(normalized, new CachedVideos(videos, Instant.now().plus(CACHE_TTL)));
			return videos;
		} catch (RuntimeException exception) {
			// A public site must remain usable if YouTube is unavailable or rate-limits the feed.
			cache.put(normalized, new CachedVideos(List.of(), Instant.now().plus(Duration.ofMinutes(1))));
			return List.of();
		}
	}

	private List<RadioSite.Video> parse(String xml) {
		if (xml == null || xml.isBlank()) return List.of();
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setNamespaceAware(true);
			factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
			factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
			Document document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
			NodeList entries = document.getElementsByTagNameNS("http://www.w3.org/2005/Atom", "entry");
			List<RadioSite.Video> result = new ArrayList<>();
			for (int i = 0; i < Math.min(entries.getLength(), 6); i++) {
				Element entry = (Element) entries.item(i);
				String id = childText(entry, "http://www.youtube.com/xml/schemas/2015", "videoId");
				if (id == null || id.isBlank()) continue;
				String title = childText(entry, "http://www.w3.org/2005/Atom", "title");
				String description = childText(entry, "http://search.yahoo.com/mrss/", "description");
				String publishedAt = childText(entry, "http://www.w3.org/2005/Atom", "published");
				String thumbnail = thumbnail(entry);
				result.add(new RadioSite.Video(id, title, description, publishedAt, thumbnail, "https://www.youtube.com/watch?v=" + id));
			}
			return List.copyOf(result);
		} catch (Exception exception) {
			return List.of();
		}
	}

	private String thumbnail(Element entry) {
		NodeList thumbnails = entry.getElementsByTagNameNS("http://search.yahoo.com/mrss/", "thumbnail");
		if (thumbnails.getLength() == 0) return null;
		Node node = thumbnails.item(0);
		return node instanceof Element element ? element.getAttribute("url") : null;
	}

	private String childText(Element parent, String namespace, String name) {
		NodeList nodes = parent.getElementsByTagNameNS(namespace, name);
		return nodes.getLength() == 0 ? null : nodes.item(0).getTextContent();
	}

	private record CachedVideos(List<RadioSite.Video> videos, Instant expiresAt) {}

	private static RestClient client() {
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
		factory.setReadTimeout(Duration.ofSeconds(5));
		return RestClient.builder().baseUrl("https://www.youtube.com").requestFactory(factory).build();
	}
}
