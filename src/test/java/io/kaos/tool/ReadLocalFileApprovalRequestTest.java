package io.kaos.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.kaos.tool.ReadLocalFileApprovalOutcome.Status;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReadLocalFileApprovalRequestTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void promptIdentifiesTheExactTargetAndDisclosureWithoutReadingContent() throws Exception {
        ReadLocalFileTarget target = target("private.java", "class Private {}");
        ReadLocalFileApprovalRequest request = new ReadLocalFileApprovalRequest(target);

        String prompt = request.prompt();

        assertTrue(prompt.contains("Tool: read_local_file"));
        assertTrue(prompt.contains("Exact file: " + target.resolvedPath()));
        assertTrue(prompt.contains("Size: " + target.byteCount() + " bytes"));
        assertTrue(prompt.contains("configured local Ollama model"));
        assertTrue(prompt.contains("current answer only"));
        assertTrue(prompt.contains("'approve'"));
        assertTrue(prompt.contains("'deny'"));
        assertFalse(prompt.contains("class Private"));
    }

    @Test
    void exactApprovalCreatesOneGrantBoundToTheValidatedTarget() throws Exception {
        ReadLocalFileTarget target = target("approved.txt", "approved content");
        ReadLocalFileApprovalRequest request = new ReadLocalFileApprovalRequest(target);

        ReadLocalFileApprovalOutcome outcome = request.decide("  approve  ");

        assertEquals(Status.APPROVED, outcome.status());
        assertTrue(outcome.approved());
        ReadLocalFileApprovalGrant grant = outcome.grant().orElseThrow();
        assertSame(target, grant.claimTarget());
        assertThrows(IllegalStateException.class, grant::claimTarget);
    }

    @Test
    void explicitDenialProducesNoGrant() throws Exception {
        ReadLocalFileApprovalOutcome outcome = approval("deny");

        assertEquals(Status.DENIED, outcome.status());
        assertFalse(outcome.approved());
        assertTrue(outcome.grant().isEmpty());
    }

    @Test
    void cancellationProducesNoGrantAndConsumesTheRequest() throws Exception {
        ReadLocalFileApprovalRequest request = request();

        ReadLocalFileApprovalOutcome outcome = request.cancel();

        assertEquals(Status.CANCELLED, outcome.status());
        assertFalse(outcome.approved());
        assertTrue(outcome.grant().isEmpty());
        assertThrows(IllegalStateException.class, () -> request.decide("approve"));
    }

    @Test
    void endOfInputAndEveryUnrecognizedResponseFailClosed() throws Exception {
        assertNotApproved(null, Status.END_OF_INPUT);
        for (String response : new String[] {
            "", "yes", "APPROVE", "approve please", "deny please", "approve\ndeny"
        }) {
            assertNotApproved(response, Status.INVALID_RESPONSE);
        }
    }

    @Test
    void oneRequestCannotBeDecidedTwice() throws Exception {
        ReadLocalFileApprovalRequest request = request();
        request.decide("invalid");

        assertThrows(IllegalStateException.class, () -> request.decide("approve"));
    }

    @Test
    void diagnosticStringsDoNotExposeTheTargetPath() throws Exception {
        ReadLocalFileApprovalRequest request = request();
        String privatePath = request.prompt().split("Exact file: ")[1].split("\\n")[0];
        ReadLocalFileApprovalOutcome outcome = request.decide("approve");
        ReadLocalFileApprovalGrant grant = outcome.grant().orElseThrow();

        assertFalse(request.toString().contains(privatePath));
        assertFalse(outcome.toString().contains(privatePath));
        assertFalse(grant.toString().contains(privatePath));
    }

    private ReadLocalFileApprovalRequest request() throws Exception {
        return new ReadLocalFileApprovalRequest(target("private.txt", "private content"));
    }

    private ReadLocalFileApprovalOutcome approval(String response) throws Exception {
        return request().decide(response);
    }

    private void assertNotApproved(String response, Status status) throws Exception {
        ReadLocalFileApprovalOutcome outcome = approval(response);
        assertEquals(status, outcome.status());
        assertFalse(outcome.approved());
        assertTrue(outcome.grant().isEmpty());
    }

    private ReadLocalFileTarget target(String name, String content) throws Exception {
        Path file = temporaryDirectory.resolve(name);
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return new ReadLocalFilePermissionValidator(temporaryDirectory)
                .validate(new ReadLocalFileRequest(name));
    }
}
