package io.kaos.tool.httpget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.junit.jupiter.api.Test;

class HttpGetToolContractTest {
    @Test
    void definesAndDecodesOnlyOneHttpUrl() {
        JsonNode definition = HttpGetToolContract.definition();
        assertEquals("http_get",
                definition.get("function").get("name").textValue());
        JsonNode arguments = JsonNodeFactory.instance.objectNode()
                .put("url", "https://example.com/reference?q=java");

        HttpGetRequest request = HttpGetToolContract.decodeArguments(arguments);

        assertEquals("https://example.com/reference?q=java", request.url());
        assertThrows(IllegalArgumentException.class,
                () -> HttpGetToolContract.decodeArguments(
                        JsonNodeFactory.instance.objectNode().put("path", "secret")));
    }

    @Test
    void validatesHttpsAndExactAllowedHostBeforeApproval() {
        HttpGetPermissionValidator validator = validator(
                "example.com", new byte[] {93, (byte) 184, (byte) 216, 34});

        HttpGetTarget target = validator.validate(
                new HttpGetRequest("https://EXAMPLE.com/a/../reference?q=java"));

        assertEquals(URI.create("https://example.com/reference?q=java"), target.uri());
        assertReason(HttpGetException.Reason.INVALID_REQUEST,
                () -> validator.validate(new HttpGetRequest("http://example.com/reference")));
        assertReason(HttpGetException.Reason.DISALLOWED_HOST,
                () -> validator.validate(new HttpGetRequest("https://other.example/reference")));
    }

    @Test
    void rejectsNonPublicResolutionOnlyAfterLocalValidation() {
        HttpGetPermissionValidator validator = validator(
                "example.com", new byte[] {127, 0, 0, 1});
        HttpGetTarget target = validator.validate(
                new HttpGetRequest("https://example.com/reference"));

        assertReason(HttpGetException.Reason.NON_PUBLIC_DESTINATION,
                () -> validator.validateResolvedDestination(target));

        HttpGetPermissionValidator carrierGradeNat = validator(
                "example.com", new byte[] {100, 64, 0, 1});
        assertReason(HttpGetException.Reason.NON_PUBLIC_DESTINATION,
                () -> carrierGradeNat.validateResolvedDestination(target));
    }

    @Test
    void approvalIsExactAndSingleUseAndAuditContainsNoUrl() {
        HttpGetTarget target = new HttpGetTarget(
                new HttpGetRequest("https://example.com/private?q=secret"),
                URI.create("https://example.com/private?q=secret"));
        HttpGetApproval request = new HttpGetApproval(target);
        assertTrue(request.prompt().contains("Exact URL: https://example.com/private?q=secret"));

        HttpGetApproval.Outcome approval = request.decide("approve");
        assertTrue(approval.approved());
        assertEquals(target, approval.grant().orElseThrow().claim());
        assertReason(HttpGetException.Reason.APPROVAL_REUSED,
                () -> approval.grant().orElseThrow().claim());

        HttpGetAudit.Record audit = new HttpGetAudit().succeeded();
        assertEquals("http_get", audit.operation());
        assertFalse(audit.toString().contains("example.com"));
    }

    @Test
    void resultIsBoundedAndEncodedWithoutDiagnosticDisclosure() {
        HttpGetRequest request = new HttpGetRequest("https://example.com/reference");
        HttpGetResult result = new HttpGetResult(request, "public text", "text/plain");
        JsonNode encoded = HttpGetToolContract.encodeResult(result);

        assertEquals("public text", encoded.get("content").textValue());
        assertEquals("text/plain", encoded.get("media_type").textValue());
        assertEquals("public text".getBytes(StandardCharsets.UTF_8).length,
                encoded.get("utf8_bytes").intValue());
        assertFalse(result.toString().contains("public text"));
    }

    private static HttpGetPermissionValidator validator(String host, byte[] address) {
        return new HttpGetPermissionValidator(Set.of(host), ignored -> {
            try {
                return new InetAddress[] {InetAddress.getByAddress(address)};
            } catch (java.net.UnknownHostException exception) {
                throw new AssertionError(exception);
            }
        });
    }

    private static void assertReason(
            HttpGetException.Reason reason, org.junit.jupiter.api.function.Executable action) {
        HttpGetException failure = assertThrows(HttpGetException.class, action);
        assertEquals(reason, failure.reason());
    }
}
