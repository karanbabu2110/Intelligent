package io.kaos.tool;

import java.util.Objects;

/** Immutable public metadata; model wording and schemas remain in concrete contracts. */
public record ToolDescriptor(String name, String purpose, boolean approvalRequired) {
    public ToolDescriptor {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(purpose, "purpose");
        if (!name.matches("[a-z][a-z0-9_]*") || purpose.isBlank()
                || purpose.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Invalid tool descriptor.");
        }
    }
}
