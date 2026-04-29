package net.cocoonmc.loom.core;

public class Version {

    // example: 1.18.2-SNAPSHOT) => 18.2-SNAPSHOT => 18.2 => 18|02|00 => 180200 - 1 => 180199
    public static int parse(String version, int undefined) {
        // check the limiter offset.
        var offset = 0;
        if (version.startsWith("(") || version.endsWith(")")) {
            if (version.length() == 1) {
                return undefined;
            }
            offset = 1;
        }
        // remove limiter: 1.18.2-SNAPSHOT
        // remove 1. part: 18.2-SNAPSHOT
        // remove -SNAPSHOT part: 1.18.2
        // split version part to major, minor, patch: 18, 2
        var versions = version.replaceAll("[()\\[\\]]", "").replaceFirst("^1\\.", "").split("-")[0].split("\\.");

        // combine the all version part: 180200
        // apply the limiter offset: 180199
        var major = Integer.parseInt(versions.length > 0 ? versions[0] : "0");
        var minor = Integer.parseInt(versions.length > 1 ? versions[1] : "0");
        var patch = Integer.parseInt(versions.length > 2 ? versions[2] : "0");
        return Integer.parseInt(String.format("%d%02d%02d", major, minor, patch)) - offset;
    }

    public static int compare(String a, String b) {
        var aParts = a.split("[.\\-+_]");
        var bParts = b.split("[.\\-+_]");
        var length = Math.max(aParts.length, bParts.length);

        for (int i = 0; i < length; ++i) {
            var aPart = i < aParts.length ? aParts[i] : "0";
            var bPart = i < bParts.length ? bParts[i] : "0";
            var aNumber = aPart.matches("\\d+");
            var bNumber = bPart.matches("\\d+");
            int result;

            if (aNumber && bNumber) {
                result = Long.compare(Long.parseLong(aPart), Long.parseLong(bPart));
            } else if (aNumber != bNumber) {
                result = aNumber ? 1 : -1;
            } else {
                result = aPart.compareTo(bPart);
            }

            if (result != 0) {
                return result;
            }
        }
        return 0;
    }
}
