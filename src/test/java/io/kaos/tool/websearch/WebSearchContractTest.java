package io.kaos.tool.websearch;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class WebSearchContractTest {
    private final ObjectMapper json = new ObjectMapper();
    @Test void exactQueryAndUnicodeBoundary() {
        String query = "  Spring Boot 😀  ";
        assertEquals(query, new WebSearchRequest(query).query());
        assertEquals(400, new WebSearchRequest("😀".repeat(400)).query().codePointCount(0, 800));
        for (String invalid : new String[] { null, "", "  ", "a\n", "a\t", "\u0000", "\ud800",
                "a".repeat(401), "😀".repeat(401), "!google x", "x !!", ":fr x", "x :en",
                "\u00a0", "x\u2003:fr", "\u202efoo" }) {
            assertThrows(WebSearchException.class, () -> new WebSearchRequest(invalid));
        }
        assertEquals("site:spring.io release", new WebSearchRequest("site:spring.io release").query());
    }
    @Test void strictArguments() throws Exception {
        assertEquals("x", WebSearchToolContract.decodeArguments(json.readTree("{\"query\":\"x\"}")).query());
        for (String invalid : List.of("null", "[]", "{}", "{\"query\":null}",
                "{\"query\":5}", "{\"query\":\"x\",\"url\":\"http://evil\"}")) {
            assertThrows(WebSearchException.class,
                    () -> WebSearchToolContract.decodeArguments(json.readTree(invalid)));
        }
        assertEquals("web_search", WebSearchToolContract.definition().path("function").path("name").asText());
        assertFalse(WebSearchToolContract.definition().toString().contains("searxng"));
    }
    @Test void approvalIsExactAndSingleUse() {
        var request = new WebSearchRequest("exact query");
        var approval = new WebSearchApproval(request);
        assertTrue(approval.prompt().contains(request.query()));
        assertTrue(approval.prompt().contains("external search engines"));
        var grant = approval.decide("approve").grant().orElseThrow();
        assertEquals(request, grant.claim());
        assertThrows(WebSearchException.class, grant::claim);
        assertThrows(WebSearchException.class, () -> approval.decide("approve"));
        assertFalse(grant.toString().contains(request.query()));
        for (String response : new String[] {"deny", "yes", "approve twice", null}) {
            assertTrue(new WebSearchApproval(request).decide(response).grant().isEmpty());
        }
        Thread.currentThread().interrupt();
        try {
            assertEquals(WebSearchApproval.Status.CANCELLED,
                    new WebSearchApproval(request).decide("approve").status());
        } finally { Thread.interrupted(); }
    }
    @Test void resultFieldsAndTotalPayloadAreBounded() {
        for (String url : List.of("javascript:alert(1)", "file:///tmp/test", "relative",
                "https://user:pass@example.com/")) {
            assertThrows(WebSearchException.class, () -> new WebSearchResult.Entry("Title", url, ""));
        }
        assertThrows(WebSearchException.class,
                () -> new WebSearchResult.Entry("x".repeat(257), "https://example.com", ""));
        assertThrows(WebSearchException.class,
                () -> new WebSearchResult.Entry("Title", "https://example.com", "x".repeat(513)));
        var entry = new WebSearchResult.Entry("😀".repeat(256),
                "https://example.com/" + "x".repeat(2000), "😀".repeat(512));
        var result = new WebSearchResult(new WebSearchRequest("x"), java.util.Collections.nCopies(5, entry));
        assertThrows(WebSearchException.class, () -> WebSearchToolContract.encodeResult(result));
        assertThrows(WebSearchException.class,
                () -> new WebSearchResult(new WebSearchRequest("x"), java.util.Collections.nCopies(6, entry)));
    }
    @Test void configurationIsOptionalAndEndpointIsOperatorOwned() {
        assertEquals(WebSearchException.Reason.SEARCH_SERVICE_NOT_CONFIGURED,
                assertThrows(WebSearchException.class, () -> new SearxngClient(null)).reason());
        for (String value : List.of("ftp://localhost", "http://user:pass@localhost",
                "http://localhost/?q=x", "http://localhost/#x", "http://localhost/other", "not a URL")) {
            assertThrows(WebSearchException.class, () -> new SearxngClient(value));
        }
        assertFalse(new SearxngClient("http://127.0.0.1:8080").toString().contains("8080"));
        String before = System.getProperty(SearxngClient.URL_SYSTEM_PROPERTY);
        try {
            System.setProperty(SearxngClient.URL_SYSTEM_PROPERTY, "");
            assertThrows(WebSearchException.class, SearxngClient::load);
            System.setProperty(SearxngClient.URL_SYSTEM_PROPERTY, "http://127.0.0.1:8080");
            assertNotNull(SearxngClient.load());
        } finally {
            if (before == null) System.clearProperty(SearxngClient.URL_SYSTEM_PROPERTY);
            else System.setProperty(SearxngClient.URL_SYSTEM_PROPERTY, before);
        }
    }
}
