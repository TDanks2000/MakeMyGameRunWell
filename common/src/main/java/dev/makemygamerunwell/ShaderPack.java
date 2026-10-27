package dev.makemygamerunwell;

/**
 * Whether Iris is rendering a shader pack. A pack's cost is part of every frame the benchmark measures, and the
 * options this mod changes cannot reduce it. Read by reflection so Iris stays an optional, untouched neighbour.
 */
final class ShaderPack {
    private static final String IRIS_API = "net.irisshaders.iris.api.v0.IrisApi";

    private ShaderPack() {}

    static boolean active() {
        try {
            Class<?> api = Class.forName(IRIS_API);
            Object instance = api.getMethod("getInstance").invoke(null);
            return (boolean) api.getMethod("isShaderPackInUse").invoke(instance);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            return false;
        }
    }
}
