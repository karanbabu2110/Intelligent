package io.kaos.tool.httpget;

import static org.junit.jupiter.api.Assertions.*;

import java.net.InetAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class PinnedHttpTransportTest {
    @Test void dnsCompletingAfterTimeoutCannotStartLateRetrieval() throws Exception {
        try (var fixture = new HttpSourceFixture("body", "text/plain", 200, false)) {
            var started = new java.util.concurrent.CountDownLatch(1);
            var release = new java.util.concurrent.CountDownLatch(1);
            var finished = new java.util.concurrent.CountDownLatch(1);
            try {
                var failure = assertThrows(HttpGetException.class, () -> PinnedHttpTransport.execute(target -> {
                    started.countDown();
                    boolean interrupted = false;
                    while (true) {
                        try { release.await(); break; }
                        catch (InterruptedException exception) { interrupted = true; }
                    }
                    if (interrupted) Thread.currentThread().interrupt();
                    finished.countDown();
                    return InetAddress.getLoopbackAddress();
                }, fixture.mapped(target(fixture)), Duration.ofMillis(200)));
                assertEquals(HttpGetException.Reason.TIMEOUT, failure.reason());
                assertEquals(0, started.getCount());
            } finally { release.countDown(); }
            assertTrue(finished.await(2, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(0, fixture.calls.get());
        }
    }

    @Test void excessiveHeadersAndCompressedBodiesAreRejected() throws Exception {
        try (var fixture = new HttpSourceFixture("body", "text/plain", 200, false)) {
            fixture.responseHeaders.put("Content-Encoding", "gzip");
            assertEquals(HttpGetException.Reason.UNSUPPORTED_MEDIA_TYPE,
                    assertThrows(HttpGetException.class,
                            () -> fixture.retrieve(target(fixture), Duration.ofSeconds(2))).reason());
            fixture.responseHeaders.clear();
            fixture.responseHeaders.put("X-Large", "x".repeat(5000));
            assertThrows(HttpGetException.class, () -> fixture.retrieve(target(fixture), Duration.ofSeconds(2)));
            assertEquals(2, fixture.calls.get());
        }
    }

    @Test void executorRejectsGrantWhoseTargetDiffersFromItsRequest() {
        var validator = new HttpGetPermissionValidator(Set.of("one.example"));
        var forged = new HttpGetTarget(new HttpGetRequest("https://one.example/approved"),
                URI.create("https://one.example/different"));
        var grant = new HttpGetApproval(forged).decide("approve").grant().orElseThrow();
        assertEquals(HttpGetException.Reason.INVALID_REQUEST,
                assertThrows(HttpGetException.class, () -> new HttpGetExecutor(validator).execute(grant)).reason());
    }

    @Test void realHttpUsesPinnedAddressOnceAndIgnoresSystemProxyCookiesAndRedirects() throws Exception {
        ProxySelector old = ProxySelector.getDefault();
        var proxyCalls = new AtomicInteger();
        ProxySelector.setDefault(new ProxySelector() {
            public List<Proxy> select(URI uri) { proxyCalls.incrementAndGet(); throw new AssertionError(); }
            public void connectFailed(URI uri, SocketAddress address, java.io.IOException error) { }
        });
        try (var fixture = new HttpSourceFixture("body", "text/plain", 200, false)) {
            var target = fixture.validator().validate(new HttpGetRequest("https://one.example/a%20b?q=a%2Fb"));
            assertEquals("body", fixture.retrieve(target, Duration.ofSeconds(2)).content());
            assertEquals("body", fixture.retrieve(target, Duration.ofSeconds(2)).content());
            assertEquals(2, fixture.calls.get());
            assertEquals(2, fixture.resolutions.get());
            assertEquals("/a%20b?q=a%2Fb", fixture.paths.getFirst());
            assertEquals(0, proxyCalls.get());
            fixture.headers.forEach(headers -> headers.keySet().forEach(name -> {
                assertNotEquals("cookie", name.toLowerCase());
                assertNotEquals("authorization", name.toLowerCase());
                assertNotEquals("proxy-authorization", name.toLowerCase());
            }));
        } finally { ProxySelector.setDefault(old); }
        assertFailure(302, "text/plain", "redirect", HttpGetException.Reason.REDIRECTED);
        assertFailure(503, "text/plain", "failure", HttpGetException.Reason.REQUEST_FAILED);
    }

    @Test void rejectsUnsupportedOversizedAndEmptyRealResponses() throws Exception {
        assertFailure(200, "application/octet-stream", "binary", HttpGetException.Reason.UNSUPPORTED_MEDIA_TYPE);
        assertFailure(200, "text/javascript", "script", HttpGetException.Reason.UNSUPPORTED_MEDIA_TYPE);
        assertFailure(200, "text/plain", "x".repeat(32769), HttpGetException.Reason.TOO_LARGE);
        assertFailure(200, "text/plain", " ", HttpGetException.Reason.INVALID_CONTENT);
        for (String type : List.of("text/html", "text/plain", "application/json", "application/xml")) {
            try (var fixture = new HttpSourceFixture("bounded text", type, 200, false)) {
                assertEquals(type, fixture.retrieve(target(fixture), Duration.ofSeconds(2)).mediaType());
            }
        }
    }

    @Test void totalDeadlineIncludesStalledBody() throws Exception {
        try (var fixture = new HttpSourceFixture("", "text/plain", 200, true)) {
            long start = System.nanoTime();
            var failure = assertThrows(HttpGetException.class,
                    () -> fixture.retrieve(target(fixture), Duration.ofMillis(300)));
            assertEquals(HttpGetException.Reason.TIMEOUT, failure.reason());
            assertTrue(Duration.ofNanos(System.nanoTime() - start).toMillis() < 2000);
            assertEquals(1, fixture.calls.get());
        }
    }

    @Test void privateMixedAndSpecialDnsAnswersFailBeforeAnyConnection() throws Exception {
        for (String address : List.of("127.0.0.1", "0.0.0.0", "10.0.0.1", "169.254.169.254",
                "100.64.0.1", "224.0.0.1", "192.0.2.1", "::", "::1", "fc00::1", "fe80::1",
                "ff02::1", "64:ff9b::7f00:1", "2001:db8::1", "2002:7f00:1::", "3fff::1")) {
            var bad = InetAddress.getByName(address);
            var validator = new HttpGetPermissionValidator(Set.of("one.example"),
                    host -> new InetAddress[] {publicAddress(), bad});
            var target = validator.validate(new HttpGetRequest("https://one.example/"));
            var grant = new HttpGetApproval(target).decide("approve").grant().orElseThrow();
            assertEquals(HttpGetException.Reason.NON_PUBLIC_DESTINATION,
                    assertThrows(HttpGetException.class, () -> new HttpGetExecutor(validator).execute(grant)).reason());
            assertEquals(HttpGetException.Reason.APPROVAL_REUSED,
                    assertThrows(HttpGetException.class, grant::claim).reason());
        }
    }

    @Test void normalizationIsIdempotentAndRawIpOrAmbiguousAuthoritiesAreRejected() {
        var validator = new HttpGetPermissionValidator(Set.of("one.example"));
        var target = validator.validate(new HttpGetRequest("HTTPS://ONE.EXAMPLE:443/a/../b%20c?q=a%2Fb"));
        assertEquals("https://one.example/b%20c?q=a%2Fb", target.uri().toASCIIString());
        assertEquals(target.uri(), validator.validate(new HttpGetRequest(target.uri().toASCIIString())).uri());
        for (String url : List.of("http://one.example/", "https://user:pass@one.example/",
                "https://one.example:444/", "https://one.example/#fragment", "https://8.8.8.8/",
                "https://2130706433/", "https://0x7f000001/", "https://[::1]/", "https://one.example./")) {
            assertThrows(HttpGetException.class, () -> validator.validate(new HttpGetRequest(url)), url);
        }
    }

    @Test void credentialQueryParametersAreRejectedBeforeApprovalIncludingEncodedNames() {
        var validator = new HttpGetPermissionValidator(Set.of("one.example"));
        for (String query : List.of("accessToken=private", "access_token=private", "ACCESS_TOKEN=private",
                "%61ccessToken=private", "api-key=private", "x-amz-signature=private", "password=private",
                "q=public;sessionid=private", "q=public&token=private", "client_secret=private", "code=private")) {
            assertEquals(HttpGetException.Reason.INVALID_REQUEST,
                    assertThrows(HttpGetException.class,
                            () -> validator.validate(new HttpGetRequest("https://one.example/?" + query))).reason());
        }
        assertEquals("https://one.example/?q=public", validator.validate(
                new HttpGetRequest("https://one.example/?q=public")).uri().toASCIIString());
    }

    @Test void cancellationPreventsRetrieval() throws Exception {
        var validator = new HttpGetPermissionValidator(Set.of("one.example"), host -> new InetAddress[]{publicAddress()});
        var grant = new HttpGetApproval(validator.validate(new HttpGetRequest("https://one.example/")))
                .decide("approve").grant().orElseThrow();
        try {
            Thread.currentThread().interrupt();
            assertEquals(HttpGetException.Reason.INTERRUPTED,
                    assertThrows(HttpGetException.class, () -> new HttpGetExecutor(validator).execute(grant)).reason());
        } finally { Thread.interrupted(); }
    }

    private static InetAddress publicAddress() {
        try { return InetAddress.getByAddress(new byte[]{93, (byte)184, (byte)216, 34}); }
        catch (Exception exception) { throw new AssertionError(exception); }
    }
    private static HttpGetTarget target(HttpSourceFixture fixture) {
        return fixture.validator().validate(new HttpGetRequest("https://one.example/page"));
    }
    private static void assertFailure(int status, String media, String content, HttpGetException.Reason reason)
            throws Exception {
        try (var fixture = new HttpSourceFixture(content, media, status, false)) {
            assertEquals(reason, assertThrows(HttpGetException.class,
                    () -> fixture.retrieve(target(fixture), Duration.ofSeconds(2))).reason());
            assertEquals(1, fixture.calls.get());
        }
    }
}
