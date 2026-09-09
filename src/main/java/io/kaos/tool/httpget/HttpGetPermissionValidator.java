package io.kaos.tool.httpget;

import java.net.IDN;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Validates explicit host configuration, URL syntax, and resolved destinations. */
public final class HttpGetPermissionValidator {
    public static final String HOSTS_SYSTEM_PROPERTY = "kaos.tool.http.allowed-hosts";
    public static final String HOSTS_ENVIRONMENT_VARIABLE = "KAOS_HTTP_ALLOWED_HOSTS";

    private final Set<String> allowedHosts;
    private final Function<String, InetAddress[]> resolver;

    public HttpGetPermissionValidator(Set<String> allowedHosts) {
        this(allowedHosts, HttpGetPermissionValidator::resolveHost);
    }

    HttpGetPermissionValidator(
            Set<String> allowedHosts, Function<String, InetAddress[]> resolver) {
        Objects.requireNonNull(allowedHosts, "allowedHosts");
        if (allowedHosts.isEmpty()) {
            throw failure(HttpGetException.Reason.INVALID_CONFIGURATION);
        }
        try {
            this.allowedHosts = allowedHosts.stream()
                    .map(HttpGetPermissionValidator::normalizeConfiguredHost)
                    .collect(Collectors.toUnmodifiableSet());
        } catch (IllegalArgumentException exception) {
            throw failure(HttpGetException.Reason.INVALID_CONFIGURATION);
        }
        this.resolver = Objects.requireNonNull(resolver, "resolver");
    }

    public static HttpGetPermissionValidator load() {
        try {
            String property = System.getProperty(HOSTS_SYSTEM_PROPERTY);
            String selected = property != null
                    ? property : System.getenv(HOSTS_ENVIRONMENT_VARIABLE);
            if (selected == null || selected.isBlank()) {
                throw failure(HttpGetException.Reason.INVALID_CONFIGURATION);
            }
            Set<String> hosts = Arrays.stream(selected.split(",", -1))
                    .map(String::strip)
                    .collect(Collectors.toSet());
            return new HttpGetPermissionValidator(hosts);
        } catch (SecurityException exception) {
            throw failure(HttpGetException.Reason.INVALID_CONFIGURATION);
        }
    }

    /** Validates only local syntax and configured authority before user approval. */
    public HttpGetTarget validate(HttpGetRequest request) {
        Objects.requireNonNull(request, "request");
        try {
            URI uri = new URI(request.url()).normalize();
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null
                    || uri.getRawUserInfo() != null || uri.getRawFragment() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443)
                    || !uri.isAbsolute() || uri.isOpaque()) {
                throw failure(HttpGetException.Reason.INVALID_REQUEST);
            }
            String normalizedHost = normalizeHost(host);
            if (!allowedHosts.contains(normalizedHost)) {
                throw failure(HttpGetException.Reason.DISALLOWED_HOST);
            }
            URI normalized = new URI("https", null, normalizedHost, uri.getPort(),
                    uri.getRawPath().isEmpty() ? "/" : uri.getRawPath(),
                    uri.getRawQuery(), null);
            return new HttpGetTarget(request, normalized);
        } catch (URISyntaxException | IllegalArgumentException exception) {
            throw failure(HttpGetException.Reason.INVALID_REQUEST);
        }
    }

    /** Performs the post-approval DNS check immediately before the HTTP attempt. */
    public void validateResolvedDestination(HttpGetTarget target) {
        Objects.requireNonNull(target, "target");
        InetAddress[] addresses;
        try {
            addresses = resolver.apply(target.uri().getHost());
        } catch (RuntimeException exception) {
            throw failure(HttpGetException.Reason.UNAVAILABLE);
        }
        if (addresses == null || addresses.length == 0
                || Arrays.stream(addresses).anyMatch(HttpGetPermissionValidator::notPublic)) {
            throw failure(HttpGetException.Reason.NON_PUBLIC_DESTINATION);
        }
    }

    private static String normalizeConfiguredHost(String host) {
        if (host.isBlank() || host.contains("*") || host.contains("/") || host.contains(":")) {
            throw new IllegalArgumentException("Invalid allowed host.");
        }
        return normalizeHost(host);
    }

    private static String normalizeHost(String host) {
        String ascii = IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES)
                .toLowerCase(Locale.ROOT);
        if (ascii.isBlank() || ascii.length() > 253 || ascii.endsWith(".")) {
            throw new IllegalArgumentException("Invalid host.");
        }
        return ascii;
    }

    private static InetAddress[] resolveHost(String host) {
        try {
            return InetAddress.getAllByName(host);
        } catch (UnknownHostException exception) {
            throw new IllegalStateException("Host unavailable.", exception);
        }
    }

    private static boolean notPublic(InetAddress address) {
        if (address == null || address.isAnyLocalAddress() || address.isLoopbackAddress()
                || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) return reservedIpv4(bytes);
        return bytes.length != 16 || reservedIpv6(bytes);
    }

    private static boolean reservedIpv4(byte[] bytes) {
        int first = Byte.toUnsignedInt(bytes[0]);
        int second = Byte.toUnsignedInt(bytes[1]);
        int third = Byte.toUnsignedInt(bytes[2]);
        return first == 0 || first == 10 || first == 127 || first >= 224
                || (first == 100 && second >= 64 && second <= 127)
                || (first == 169 && second == 254)
                || (first == 172 && second >= 16 && second <= 31)
                || (first == 192 && second == 0 && (third == 0 || third == 2))
                || (first == 192 && second == 168)
                || (first == 198 && (second == 18 || second == 19))
                || (first == 198 && second == 51 && third == 100)
                || (first == 203 && second == 0 && third == 113);
    }

    private static boolean reservedIpv6(byte[] bytes) {
        int first = Byte.toUnsignedInt(bytes[0]);
        int second = Byte.toUnsignedInt(bytes[1]);
        boolean uniqueLocal = (first & 0xfe) == 0xfc;
        boolean documentation = first == 0x20 && second == 0x01
                && Byte.toUnsignedInt(bytes[2]) == 0x0d
                && Byte.toUnsignedInt(bytes[3]) == 0xb8;
        return uniqueLocal || documentation;
    }

    private static HttpGetException failure(HttpGetException.Reason reason) {
        return new HttpGetException(reason);
    }

    @Override
    public String toString() {
        return "HttpGetPermissionValidator[allowedHosts=REDACTED]";
    }
}
