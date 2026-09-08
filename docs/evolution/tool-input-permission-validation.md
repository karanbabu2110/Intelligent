# `read_local_file` Input and Permission Validation

Feature [007.05](https://github.com/karanbabu2110/KAOS/issues/888) establishes
the exact local target that the approval boundary can safely present. It
performs metadata-only validation and does not open or decode file content.

## Sequence correction

The roadmap originally placed User Approval Feature 007.04 before Input and
Permission Validation Feature 007.05. Current implementation evidence shows
that approval would be misleading without a resolved, contained, stable target.
Feature 007.05 therefore precedes 007.04. The two feature scopes remain intact:
this feature supplies the permission-validated target; 007.04 owns the user's
decision about that exact target.

## Implemented outcome

`ReadLocalFilePermissionValidator` combines one structurally valid
`ReadLocalFileRequest` with one explicitly configured local read root. A clean
validation returns an immutable `ReadLocalFileTarget` containing:

- the original validated relative request;
- the exact absolute, normalized, real target path for approval display;
- the non-zero byte count within the 2,048-byte result ceiling;
- the last-modified value; and
- the filesystem file key when the provider supplies one.

The target's ordinary string representation redacts the path. The absolute
path accessor exists specifically so the approval UI can identify what would
be read; it must not be used in routine logs or failure messages.

## Configuration

The local user must set one of:

```powershell
$env:KAOS_TOOL_READ_ROOT = "D:\work\approved-files"
```

The system property `kaos.tool.read-root` takes precedence over
`KAOS_TOOL_READ_ROOT`. There is no home-directory, working-directory, or
repository default. The selected root must be absolute so its authority cannot
change with the process working directory. Null, blank, relative, invalid,
unavailable, non-directory, linked, or filesystem-root configuration is
rejected without echoing the value.

No application command loads this configuration yet. The examples describe
the implemented configuration contract, not a runnable tool command.

## Validation flow

For one request, the validator:

1. requires the configured root to exist as a real, non-linked directory;
2. resolves the requested relative path beneath the real root;
3. checks lexical containment before target access;
4. walks each requested segment without following a final symbolic link and
   rejects linked segments;
5. requires a non-empty regular file no larger than 2,048 bytes;
6. requires the platform to report the target readable;
7. resolves the real target and checks containment again, catching link or
   junction escapes that lexical normalization cannot see;
8. checks that metadata did not change during validation; and
9. returns the exact target fingerprint without opening the file.

Missing paths and inaccessible metadata are `UNAVAILABLE`. Targets outside the
root are `OUTSIDE_ROOT`. Directories, links, empty files, and other unsupported
objects are `INVALID_TARGET`; explicit unreadability is `UNREADABLE`; size is
`TOO_LARGE`; and a fingerprint mismatch is `CHANGED`.

## Approval-time revalidation

`revalidate` resolves and validates the original request again, then compares
the real path, byte count, last-modified value, and available file keys with the
previous target. A mismatch returns `CHANGED`; it never silently replaces the
target associated with an approval decision.

This metadata fingerprint detects ordinary replacement or modification but is
not a cryptographic content identity. Some filesystems may preserve the same
size, timestamp, and file key across a content change. The later executor must
therefore open with no-follow semantics and revalidate around its bounded read.
Reading content before approval solely to hash it would violate the selected
privacy boundary. The later executor instead revalidates around its bounded
approved read.

## Privacy and diagnostics

All expected failures become `ReadLocalFilePermissionException` with one stable
reason and one fixed public message. Filesystem exceptions are deliberately not
retained as causes because their messages commonly contain private absolute
paths. Request, validator, and target string representations also redact paths.

The validator reads only attributes and real-path information. A deterministic
test supplies malformed UTF-8 bytes and proves that they still pass this stage;
encoding and content validation belong to the later bounded executor.

## Verification

Focused temporary-filesystem tests cover:

- property precedence, mandatory configuration, and filesystem-root rejection;
- valid nested files, exact byte ceiling, and stable revalidation;
- missing roots and targets, non-directory roots, directories, empty files,
  and oversized files;
- changed-target detection and privacy-safe diagnostics;
- portable root, target, and ancestor symbolic-link rejection where the test
  process can create symbolic links; and
- a Windows directory-junction escape, exercised successfully on the current
  platform when ordinary symlink creation was unavailable.

No test opens the target through production code, contacts Ollama, or depends
on an external service.

## Deliberate limits and handoff

This feature adds no file-content read, UTF-8 decoding, MIME detection,
application command, model continuation, audit persistence,
watcher, directory listing, generic permission framework, module, service,
plugin, or new dependency.

The successor
[007.04 - User Approval](https://github.com/karanbabu2110/KAOS/issues/885)
presents the exact `ReadLocalFileTarget.resolvedPath`, states that its content
will enter the configured local model request, and binds one decision to that
target. The Feature 007.06 executor revalidates before and after consuming
content.
