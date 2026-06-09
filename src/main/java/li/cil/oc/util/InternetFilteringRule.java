package li.cil.oc.util;

import li.cil.oc.OpenComputersMod;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;

public class InternetFilteringRule {
    private static final InternetFilteringRule[] DEFAULT_RULES = {
        new InternetFilteringRule("deny private"),
        new InternetFilteringRule("deny bogon"),
        new InternetFilteringRule("allow all"),
    };

    private static final InetAddressRange[] BOGON_RULES = buildBogonRules();

    private static InetAddressRange[] buildBogonRules() {
        String[] cidrs = {
            "0.0.0.0/8", "10.0.0.0/8", "100.64.0.0/10", "127.0.0.0/8",
            "169.254.0.0/16", "172.16.0.0/12", "192.0.0.0/24", "192.0.2.0/24",
            "192.168.0.0/16", "198.18.0.0/15", "198.51.100.0/24", "203.0.113.0/24",
            "224.0.0.0/3", "::/128", "::1/128", "::ffff:0:0/96", "::/96",
            "100::/64", "2001:10::/28", "2001:db8::/32", "fc00::/7",
            "fe80::/10", "fec0::/10", "ff00::/8",
        };
        InetAddressRange[] ranges = new InetAddressRange[cidrs.length];
        for (int i = 0; i < cidrs.length; i++) {
            String[] parts = cidrs[i].split("/", 2);
            ranges[i] = InetAddressRange.parse(parts[0], parts[1]);
        }
        return ranges;
    }

    public final String ruleString;
    private boolean _invalid = false;
    private final BiFunction<InetAddress, String, Optional<Boolean>> validator;

    public InternetFilteringRule(String ruleString) {
        this.ruleString = ruleString;
        this.validator = buildValidator();
    }

    private BiFunction<InetAddress, String, Optional<Boolean>> buildValidator() {
        try {
            String[] parts = ruleString.split(" ");
            String action = parts[0];
            if (action.equals("allow") || action.equals("deny")) {
                boolean allow = action.equals("allow");
                List<BiFunction<InetAddress, String, Boolean>> predicates = new ArrayList<>();
                for (int i = 1; i < parts.length; i++) {
                    String filter = parts[i];
                    String[] filterParts = filter.split(":", 2);
                    String filterType = filterParts[0];
                    switch (filterType) {
                        case "default" -> {
                            if (!allow) {
                                predicates.add((addr, host) -> false);
                            } else {
                                predicates.add((addr, host) -> {
                                    for (InternetFilteringRule rule : DEFAULT_RULES) {
                                        Optional<Boolean> r = rule.apply(addr, host);
                                        if (r.isPresent()) return r.get();
                                    }
                                    return false;
                                });
                            }
                        }
                        case "private" ->
                            predicates.add((addr, host) ->
                                addr.isAnyLocalAddress() || addr.isLoopbackAddress() ||
                                addr.isLinkLocalAddress() || addr.isSiteLocalAddress());
                        case "bogon" ->
                            predicates.add((addr, host) -> {
                                for (InetAddressRange r : BOGON_RULES) {
                                    if (r.matches(addr)) return true;
                                }
                                return false;
                            });
                        case "ipv4" ->
                            predicates.add((addr, host) -> addr instanceof Inet4Address);
                        case "ipv6" ->
                            predicates.add((addr, host) -> addr instanceof Inet6Address);
                        case "ipv4-embedded-ipv6" ->
                            predicates.add((addr, host) ->
                                addr instanceof Inet6Address ipv6 && isEmbeddedIPv4(ipv6));
                        case "domain" -> {
                            String domain = filterParts[1];
                            InetAddress[] addresses;
                            try {
                                addresses = InetAddress.getAllByName(domain);
                            } catch (Exception e) {
                                addresses = new InetAddress[0];
                            }
                            final InetAddress[] finalAddresses = addresses;
                            predicates.add((addr, host) -> {
                                if (host.equals(domain)) return true;
                                for (InetAddress a : finalAddresses) {
                                    if (a.equals(addr)) return true;
                                }
                                return false;
                            });
                        }
                        case "ip" -> {
                            String[] ipParts = filterParts[1].split("/", 2);
                            if (ipParts.length == 2) {
                                InetAddressRange range = InetAddressRange.parse(ipParts[0], ipParts[1]);
                                predicates.add((addr, host) -> range.matches(addr));
                            } else {
                                InetAddress specific;
                        try { specific = InetAddress.getByName(ipParts[0]); }
                        catch (java.net.UnknownHostException e) { throw new IllegalArgumentException("Invalid IP: " + ipParts[0]); }
                                predicates.add((addr, host) -> specific.equals(addr));
                            }
                            predicates.add((addr, host) ->
                                addr.isAnyLocalAddress() || addr.isLoopbackAddress() ||
                                addr.isLinkLocalAddress() || addr.isSiteLocalAddress());
                        }
                        case "all" -> { /* no predicate needed — match everything */ }
                    }
                }
                return (addr, host) -> {
                    for (BiFunction<InetAddress, String, Boolean> p : predicates) {
                        if (!p.apply(addr, host)) return Optional.empty();
                    }
                    return Optional.of(allow);
                };
            } else if (action.equals("removeme")) {
                return (addr, host) -> Optional.empty();
            } else {
                _invalid = true;
                return (addr, host) -> Optional.of(false);
            }
        } catch (Throwable t) {
            OpenComputersMod.LOGGER.error("Invalid Internet filteringRules rule in configuration: \"{}\".", ruleString, t);
            _invalid = true;
            return (addr, host) -> Optional.of(false);
        }
    }

    public boolean invalid() {
        return _invalid;
    }

    public Optional<Boolean> apply(InetAddress address, String host) {
        return validator.apply(address, host);
    }

    private static boolean isEmbeddedIPv4(Inet6Address addr) {
        byte[] b = addr.getAddress();
        // IPv4-mapped: ::ffff:x.x.x.x — bytes 10-11 are 0xFF
        if (b[10] == (byte)0xff && b[11] == (byte)0xff) return true;
        // IPv4-compatible: ::x.x.x.x — bytes 0-11 all zero, bytes 12-15 non-zero
        for (int i = 0; i < 12; i++) if (b[i] != 0) return false;
        return b[12] != 0 || b[13] != 0 || b[14] != 0 || b[15] != 0;
    }
}
