package io.kaos.tool.readlocalfile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ReadLocalFileFailureMapperTest {
    @Test
    void mapsEveryPermissionReasonToOneSpecificFailure() {
        assertPermission(ReadLocalFilePermissionException.Reason.INVALID_CONFIGURATION,
                ReadLocalFileFailure.INVALID_CONFIGURATION);
        assertPermission(ReadLocalFilePermissionException.Reason.UNAVAILABLE,
                ReadLocalFileFailure.UNAVAILABLE);
        assertPermission(ReadLocalFilePermissionException.Reason.OUTSIDE_ROOT,
                ReadLocalFileFailure.OUTSIDE_ROOT);
        assertPermission(ReadLocalFilePermissionException.Reason.INVALID_TARGET,
                ReadLocalFileFailure.INVALID_TARGET);
        assertPermission(ReadLocalFilePermissionException.Reason.UNREADABLE,
                ReadLocalFileFailure.UNREADABLE);
        assertPermission(ReadLocalFilePermissionException.Reason.TOO_LARGE,
                ReadLocalFileFailure.TOO_LARGE);
        assertPermission(ReadLocalFilePermissionException.Reason.CHANGED,
                ReadLocalFileFailure.CHANGED);
    }

    @Test
    void mapsEveryExecutionReasonToOneSpecificFailure() {
        assertExecution(ReadLocalFileExecutionException.Reason.UNAVAILABLE,
                ReadLocalFileFailure.UNAVAILABLE);
        assertExecution(ReadLocalFileExecutionException.Reason.CHANGED,
                ReadLocalFileFailure.CHANGED);
        assertExecution(ReadLocalFileExecutionException.Reason.INVALID_UTF8,
                ReadLocalFileFailure.INVALID_UTF8);
        assertExecution(ReadLocalFileExecutionException.Reason.INVALID_CONTENT,
                ReadLocalFileFailure.INVALID_CONTENT);
        assertExecution(ReadLocalFileExecutionException.Reason.CANCELLED,
                ReadLocalFileFailure.CANCELLED);
    }

    @Test
    void exposesExplicitRequestAndConsumedStateFailures() {
        assertEquals(ReadLocalFileFailure.INVALID_REQUEST,
                ReadLocalFileFailureMapper.invalidRequest());
        assertEquals(ReadLocalFileFailure.INVALID_STATE,
                ReadLocalFileFailureMapper.invalidState());
    }

    @Test
    void everyFailureHasAUniqueStableCodeAndActionableMessage() {
        Set<String> codes = new HashSet<>();
        for (ReadLocalFileFailure failure : ReadLocalFileFailure.values()) {
            assertTrue(failure.code().matches("KAOS-TOOL-READ-\\d{3}"));
            assertTrue(codes.add(failure.code()));
            assertFalse(failure.message().isBlank());
            assertEquals("ERROR [" + failure.code() + "] " + failure.message(),
                    failure.diagnostic());
        }
        assertEquals(ReadLocalFileFailure.values().length, codes.size());
    }

    @Test
    void diagnosticsContainNoPrivateOrExceptionData() {
        String[] privateMarkers = {
            "C:\\private\\customer.java",
            "customer.java",
            "private file content",
            "AccessDeniedException",
            "java.nio.file"
        };

        for (ReadLocalFileFailure failure : ReadLocalFileFailure.values()) {
            for (String marker : privateMarkers) {
                assertFalse(failure.diagnostic().contains(marker),
                        () -> failure + " exposed " + marker);
            }
        }
    }

    @Test
    void mapperRejectsMissingTypedFailures() {
        assertThrows(NullPointerException.class,
                () -> ReadLocalFileFailureMapper.from(
                        (ReadLocalFilePermissionException) null));
        assertThrows(NullPointerException.class,
                () -> ReadLocalFileFailureMapper.from(
                        (ReadLocalFileExecutionException) null));
    }

    @Test
    void allSourceReasonsAreCoveredExactlyOnce() {
        assertEquals(7, Arrays.stream(ReadLocalFilePermissionException.Reason.values()).count());
        assertEquals(5, Arrays.stream(ReadLocalFileExecutionException.Reason.values()).count());
    }

    private static void assertPermission(
            ReadLocalFilePermissionException.Reason reason,
            ReadLocalFileFailure expected) {
        assertEquals(expected, ReadLocalFileFailureMapper.from(
                new ReadLocalFilePermissionException(reason)));
    }

    private static void assertExecution(
            ReadLocalFileExecutionException.Reason reason,
            ReadLocalFileFailure expected) {
        assertEquals(expected, ReadLocalFileFailureMapper.from(
                new ReadLocalFileExecutionException(reason)));
    }
}
