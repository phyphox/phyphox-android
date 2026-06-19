package de.rwth_aachen.phyphox.camera.analyzer;


import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public enum SpectrumOrientation {
    VERTICAL_BLUE_UP,
    VERTICAL_RED_UP,
    HORIZONTAL_BLUE_RIGHT,
    HORIZONTAL_RED_RIGHT,
    INVALID;

    private static final List<SpectrumOrientation> ROTATION_CYCLE = Arrays.asList(
            SpectrumOrientation.HORIZONTAL_RED_RIGHT,
            SpectrumOrientation.VERTICAL_RED_UP,
            SpectrumOrientation.HORIZONTAL_BLUE_RIGHT,
            SpectrumOrientation.VERTICAL_BLUE_UP
    );

    private static final Map<SpectrumOrientation, Integer> INDEX_MAP;

    static {
        Map<SpectrumOrientation, Integer> map = new HashMap<>();
        for (int i = 0; i < ROTATION_CYCLE.size(); i++) {
            map.put(ROTATION_CYCLE.get(i), i);
        }
        INDEX_MAP = Collections.unmodifiableMap(map);
    }

    public SpectrumOrientation rotateCounterClockwise() {
        Integer currentIndex = INDEX_MAP.get(this);
        if (currentIndex == null) {
            return this;
        }

        int nextIndex = (currentIndex + 1) % ROTATION_CYCLE.size();

        return ROTATION_CYCLE.get(nextIndex);
    }

    public SpectrumOrientation rotateClockwise() {

        Integer currentIndex = INDEX_MAP.get(this);
        if (currentIndex == null) {
            return this;
        }

        int previousIndex = (currentIndex - 1 + ROTATION_CYCLE.size()) % ROTATION_CYCLE.size();

        return ROTATION_CYCLE.get(previousIndex);
    }

}
