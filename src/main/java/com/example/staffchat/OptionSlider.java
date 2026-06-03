package com.example.staffchat;

import java.util.function.Consumer;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

/**
 * A generic numeric slider that maps its 0..1 position onto a {@code [min, max]} range with an
 * optional step, formats its own label, and pushes changes through a setter.
 */
public class OptionSlider extends AbstractSliderButton {

    public interface Label {
        String format(double value);
    }

    private final double min;
    private final double max;
    private final double step;
    private final Label label;
    private final Consumer<Double> setter;

    public OptionSlider(int x, int y, int w, int h, double min, double max, double step,
                        double current, Label label, Consumer<Double> setter) {
        super(x, y, w, h, Component.empty(), clamp01((current - min) / (max - min)));
        this.min = min;
        this.max = max;
        this.step = step;
        this.label = label;
        this.setter = setter;
        updateMessage();
    }

    private double actual() {
        double raw = min + this.value * (max - min);
        if (step > 0) {
            raw = Math.round(raw / step) * step;
        }
        return Math.max(min, Math.min(max, raw));
    }

    @Override
    protected void updateMessage() {
        setMessage(Component.literal(label.format(actual())));
    }

    @Override
    protected void applyValue() {
        setter.accept(actual());
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}
