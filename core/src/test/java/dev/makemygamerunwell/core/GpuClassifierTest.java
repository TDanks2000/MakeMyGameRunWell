package dev.makemygamerunwell.core;

import org.junit.jupiter.api.Test;
import static dev.makemygamerunwell.core.Hardware.Gpu.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class GpuClassifierTest {
    private static void check(Hardware.Gpu expected, Hardware.Gpu reported, String name) {
        assertEquals(expected, GpuClassifier.classify(reported, name), name);
    }

    @Test void softwareRenderersAreRecognisedWhateverTheDriverReports() {
        check(SOFTWARE, UNKNOWN, "llvmpipe (LLVM 17.0.6, 256 bits)");
        check(SOFTWARE, DISCRETE, "Microsoft Basic Render Driver");
        check(SOFTWARE, SOFTWARE, "Anything");
    }

    @Test void intelGraphicsAreIntegratedExceptDiscreteArcCards() {
        check(INTEGRATED, UNKNOWN, "Intel(R) UHD Graphics 630");
        check(INTEGRATED, UNKNOWN, "Intel(R) Iris(R) Xe Graphics");
        check(INTEGRATED, UNKNOWN, "Intel(R) Arc(TM) Graphics");
        check(DISCRETE, INTEGRATED, "Intel(R) Arc(TM) A770 Graphics");
        check(DISCRETE, DISCRETE, "Intel(R) Arc(TM) B580 Graphics");
    }

    @Test void amdApusAreIntegratedAndCardsAreDiscrete() {
        check(INTEGRATED, UNKNOWN, "AMD Radeon(TM) Graphics");
        check(INTEGRATED, UNKNOWN, "AMD Radeon 780M Graphics");
        check(INTEGRATED, UNKNOWN, "AMD Radeon(TM) 780M Graphics");
        check(INTEGRATED, DISCRETE, "AMD Radeon(TM) RX Vega 11 Graphics");
        check(INTEGRATED, UNKNOWN, "AMD Radeon(TM) Vega 8 Graphics");
        check(DISCRETE, UNKNOWN, "AMD Radeon RX 7600");
        check(DISCRETE, UNKNOWN, "Radeon RX 580 Series");
    }

    @Test void nvidiaCardsAreDiscreteAndTopModelsAreHighEnd() {
        check(DISCRETE, UNKNOWN, "NVIDIA GeForce GTX 1650");
        check(DISCRETE, UNKNOWN, "NVIDIA GeForce RTX 3060 Ti");
        check(HIGH_END, UNKNOWN, "NVIDIA GeForce RTX 4070 SUPER");
        check(HIGH_END, DISCRETE, "NVIDIA GeForce RTX 5090");
        check(HIGH_END, DISCRETE, "AMD Radeon RX 7900 XTX");
    }

    @Test void previousGenerationEnthusiastCardsAreHighEnd() {
        check(HIGH_END, DISCRETE, "NVIDIA GeForce RTX 2080 SUPER");
        check(HIGH_END, DISCRETE, "NVIDIA GeForce RTX 2070");
        check(HIGH_END, DISCRETE, "NVIDIA GeForce GTX 1080 Ti");
        check(HIGH_END, DISCRETE, "AMD Radeon RX 5700 XT");
        check(DISCRETE, DISCRETE, "NVIDIA GeForce RTX 2060");
        check(DISCRETE, DISCRETE, "NVIDIA GeForce GTX 1660 Ti");
    }

    @Test void linuxMesaNamesAreRecognised() {
        check(INTEGRATED, UNKNOWN, "Mesa Intel(R) UHD Graphics 630 (CFL GT2)");
        check(INTEGRATED, UNKNOWN, "AMD Radeon Graphics (radeonsi, raphael_mendocino, LLVM 17.0.6, DRM 3.57)");
        check(DISCRETE, UNKNOWN, "AMD Radeon RX 7600 (radeonsi, navi33, LLVM 17.0.6, DRM 3.57)");
        check(DISCRETE, UNKNOWN, "NVIDIA GeForce RTX 3060/PCIe/SSE2");
    }

    @Test void laptopVariantsFollowTheirDesktopNames() {
        check(DISCRETE, UNKNOWN, "NVIDIA GeForce RTX 4060 Laptop GPU");
        check(HIGH_END, DISCRETE, "NVIDIA GeForce RTX 4080 Laptop GPU");
        check(DISCRETE, DISCRETE, "AMD Radeon RX 6600M");
    }

    @Test void appleSiliconIsIntegrated() {
        check(INTEGRATED, UNKNOWN, "Apple M2");
    }

    @Test void unrecognisedNamesKeepTheReportedClass() {
        check(UNKNOWN, UNKNOWN, "Some Future GPU");
        check(DISCRETE, DISCRETE, "Some Future GPU");
        check(UNKNOWN, UNKNOWN, "");
    }
}
