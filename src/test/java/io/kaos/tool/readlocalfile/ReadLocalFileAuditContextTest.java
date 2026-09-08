package io.kaos.tool.readlocalfile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.kaos.tool.readlocalfile.ReadLocalFileAuditRecord.Decision;
import io.kaos.tool.readlocalfile.ReadLocalFileAuditRecord.Outcome;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReadLocalFileAuditContextTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void assignsAnOpaqueRandomIdentityInsteadOfDerivingOneFromThePath() throws Exception {
        ReadLocalFileTarget target = target();

        ReadLocalFileAuditRecord first = ReadLocalFileAuditContext.start(target)
                .recordSucceeded();
        ReadLocalFileAuditRecord second = ReadLocalFileAuditContext.start(target)
                .recordSucceeded();

        assertEquals(4, first.targetIdentity().version());
        assertNotEquals(first.targetIdentity(), second.targetIdentity());
        assertFalse(first.targetIdentity().toString().contains(target.request().path()));
    }

    @Test
    void recordsOneSuccessfulApprovedExecution() throws Exception {
        UUID identity = UUID.fromString("5145c84f-39b7-4bc7-bb05-89d817457804");

        ReadLocalFileAuditRecord record = context(identity).recordSucceeded();

        assertEquals("read_local_file", record.operation());
        assertEquals(identity, record.targetIdentity());
        assertEquals(Decision.APPROVED, record.decision());
        assertEquals(Outcome.SUCCEEDED, record.outcome());
    }

    @Test
    void recordsFailedAndCancelledApprovedExecutionsWithoutDetails() throws Exception {
        ReadLocalFileAuditRecord failed = context(UUID.randomUUID()).recordFailed();
        ReadLocalFileAuditRecord cancelled = context(UUID.randomUUID())
                .recordExecutionCancelled();

        assertEquals(Decision.APPROVED, failed.decision());
        assertEquals(Outcome.FAILED, failed.outcome());
        assertEquals(Decision.APPROVED, cancelled.decision());
        assertEquals(Outcome.CANCELLED, cancelled.outcome());
    }

    @Test
    void mapsEveryNoAuthorityDecisionToNotExecuted() throws Exception {
        assertNotExecuted("deny", Decision.DENIED);
        assertNotExecuted("invalid", Decision.INVALID_RESPONSE);
        assertNotExecuted(null, Decision.END_OF_INPUT);

        ReadLocalFileApprovalRequest cancelledRequest = approvalRequest();
        ReadLocalFileAuditRecord cancelled = context(UUID.randomUUID())
                .recordNotExecuted(cancelledRequest.cancel());
        assertEquals(Decision.CANCELLED, cancelled.decision());
        assertEquals(Outcome.NOT_EXECUTED, cancelled.outcome());
    }

    @Test
    void refusesToRecordApprovedAsNotExecuted() throws Exception {
        ReadLocalFileApprovalOutcome approved = approvalRequest().decide("approve");
        ReadLocalFileAuditContext context = context(UUID.randomUUID());

        assertThrows(IllegalArgumentException.class,
                () -> context.recordNotExecuted(approved));
        assertEquals(Outcome.SUCCEEDED, context.recordSucceeded().outcome());
    }

    @Test
    void emitsAtMostOneFinalRecord() throws Exception {
        ReadLocalFileAuditContext context = context(UUID.randomUUID());
        context.recordFailed();

        assertThrows(IllegalStateException.class, context::recordSucceeded);
        assertThrows(IllegalStateException.class, context::recordExecutionCancelled);
        assertThrows(IllegalStateException.class,
                () -> context.recordNotExecuted(approvalRequest().decide("deny")));
    }

    @Test
    void recordAndContextNeverContainPathContentOrFailureDetail() throws Exception {
        String privatePath = target().resolvedPath().toString();
        String privateContent = "private customer content";
        UUID identity = UUID.fromString("33197898-d026-4f74-89d5-41d022421eef");
        ReadLocalFileAuditContext context = context(identity);
        ReadLocalFileAuditRecord record = context.recordFailed();

        assertFalse(record.toString().contains(privatePath));
        assertFalse(record.toString().contains(privateContent));
        assertFalse(context.toString().contains(privatePath));
        assertFalse(context.toString().contains(privateContent));
        assertEquals(
                "ReadLocalFileAuditRecord[operation=read_local_file, "
                        + "targetIdentity=33197898-d026-4f74-89d5-41d022421eef, "
                        + "decision=APPROVED, outcome=FAILED]",
                record.toString());
    }

    private void assertNotExecuted(String response, Decision decision) throws Exception {
        ReadLocalFileAuditRecord record = context(UUID.randomUUID())
                .recordNotExecuted(approvalRequest().decide(response));
        assertEquals(decision, record.decision());
        assertEquals(Outcome.NOT_EXECUTED, record.outcome());
    }

    private ReadLocalFileAuditContext context(UUID identity) throws Exception {
        return ReadLocalFileAuditContext.start(target(), identity);
    }

    private ReadLocalFileApprovalRequest approvalRequest() throws Exception {
        return new ReadLocalFileApprovalRequest(target());
    }

    private ReadLocalFileTarget target() throws Exception {
        Path file = temporaryDirectory.resolve("private.java");
        Files.writeString(file, "private customer content", StandardCharsets.UTF_8);
        return new ReadLocalFilePermissionValidator(temporaryDirectory)
                .validate(new ReadLocalFileRequest("private.java"));
    }
}
