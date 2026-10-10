package com.taxapp.render;

import com.taxapp.model.FormatType;
import com.taxapp.render.impl.CheckboxRenderer;
import com.taxapp.render.impl.CombedBoxRenderer;
import com.taxapp.render.impl.StandardTextRenderer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Factory for resolving appropriate FieldRenderer strategies.
 */
public class RendererFactory {

    private final List<FieldRenderer> renderers = new ArrayList<>();
    private final FieldRenderer defaultRenderer = new StandardTextRenderer();

    public RendererFactory() {
        registerDefaultRenderers();
    }

    private void registerDefaultRenderers() {
        renderers.add(new CheckboxRenderer());
        renderers.add(new CombedBoxRenderer());
        renderers.add(new StandardTextRenderer());
    }

    /**
     * Registers a custom renderer at the highest evaluation priority.
     */
    public void registerRenderer(FieldRenderer renderer) {
        Objects.requireNonNull(renderer, "Renderer cannot be null");
        renderers.add(0, renderer);
    }

    /**
     * Finds the first renderer supporting the given FormatType.
     *
     * @param formatType Target format type
     * @return Matching FieldRenderer, or defaultRenderer fallback
     */
    public FieldRenderer getRenderer(FormatType formatType) {
        if (formatType == null) {
            return defaultRenderer;
        }
        for (FieldRenderer renderer : renderers) {
            if (renderer.supports(formatType)) {
                return renderer;
            }
        }
        return defaultRenderer;
    }
}
