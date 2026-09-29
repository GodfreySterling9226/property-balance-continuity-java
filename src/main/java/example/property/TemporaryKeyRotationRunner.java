package example.property;

import java.time.Instant;

public final class TemporaryKeyRotationRunner {
    private TemporaryKeyRotationRunner() {}

    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("usage: TemporaryKeyRotationRunner <temporary-key-id>");
        }
        InfraiClient infrai = new InfraiClient(ServiceConfig.fromEnvironment());
        String operation = "property-rotation-" + Instant.now().getEpochSecond();
        infrai.rotateTemporaryKey(args[0], 24, operation + "-rotate");
        infrai.revokeTemporaryKey(args[0]);
        System.out.println("Temporary key rotation exercise completed.");
    }
}
