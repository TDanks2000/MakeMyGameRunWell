package dev.makemygamerunwell.core;

/** One reversible reduction. Texture quality is deliberately preserved during a benchmark. */
public enum PlanAdjustment {
    CLOUDS("renderClouds"), SHADOWS("entityShadows"), WEATHER("weatherRadius"), PARTICLES("particles"),
    ENTITY_DISTANCE("entityDistanceScaling"), RENDER_DISTANCE("renderDistance"),
    SIMULATION_DISTANCE("simulationDistance"), BIOME_BLEND("biomeBlendRadius"),
    AMBIENT_OCCLUSION("ao"), LEAVES("cutoutLeaves");

    private static final int MIN_RENDER_DISTANCE = 5;
    private static final int RENDER_DISTANCE_STEP = 2;
    private static final int MIN_SIMULATION_DISTANCE = 5;
    private static final int BASIC_WEATHER_RADIUS = 3;
    private static final double MIN_ENTITY_DISTANCE = 0.5;
    private static final double ENTITY_DISTANCE_STEP = 0.25;
    private final String option;

    PlanAdjustment(String option) {
        this.option = option;
    }

    public String option() {
        return option;
    }

    public TuningPlan lower(TuningPlan p) {
        int render = p.renderDistance(), simulation = p.simulationDistance(), weather = p.weatherRadius();
        int blend = p.biomeBlend();
        double entities = p.entityDistance();
        boolean clouds = p.clouds(), shadows = p.shadows(), particles = p.allParticles();
        boolean ao = p.ambientOcclusion(), fancy = p.fancy();
        switch (this) {
            case CLOUDS -> clouds = false;
            case SHADOWS -> shadows = false;
            case WEATHER -> weather = Math.min(weather, BASIC_WEATHER_RADIUS);
            case PARTICLES -> particles = false;
            case ENTITY_DISTANCE -> entities = Math.max(MIN_ENTITY_DISTANCE, entities - ENTITY_DISTANCE_STEP);
            case RENDER_DISTANCE -> render = Math.max(Math.min(render, MIN_RENDER_DISTANCE), render - RENDER_DISTANCE_STEP);
            case SIMULATION_DISTANCE -> simulation = Math.max(MIN_SIMULATION_DISTANCE, simulation - 1);
            case BIOME_BLEND -> blend = Math.max(0, blend - 1);
            case AMBIENT_OCCLUSION -> ao = false;
            case LEAVES -> fancy = false;
        }
        if (render == p.renderDistance() && simulation == p.simulationDistance() && weather == p.weatherRadius()
                && blend == p.biomeBlend() && entities == p.entityDistance() && clouds == p.clouds()
                && shadows == p.shadows() && particles == p.allParticles() && ao == p.ambientOcclusion() && fancy == p.fancy()) {
            return p;
        }
        return new TuningPlan(p.tier(), TuningPlan.Limit.BENCHMARK, render, simulation, entities, p.fpsLimit(), fancy,
                clouds, shadows, ao, particles, p.vignette(), blend, weather, p.cloudRange(), p.mipmapLevels());
    }
}
