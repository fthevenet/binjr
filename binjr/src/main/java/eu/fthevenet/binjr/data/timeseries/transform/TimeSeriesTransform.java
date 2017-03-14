package eu.fthevenet.binjr.data.timeseries.transform;

import eu.fthevenet.binjr.data.adapters.TimeSeriesBinding;
import eu.fthevenet.binjr.data.workspace.TimeSeriesInfo;
import eu.fthevenet.binjr.logging.Profiler;
import eu.fthevenet.binjr.data.timeseries.TimeSeries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * The base class for time series transformation functions.
 *
 * @author Frederic Thevenet
 */
public abstract class TimeSeriesTransform<T extends Number> {
    private static final Logger logger = LogManager.getLogger(TimeSeriesTransform.class);
    private final String name;

    /**
     * Base constructor for {@link TimeSeriesTransform} instances.
     *
     * @param name the name of the transform function
     */
    public TimeSeriesTransform(String name) {
        this.name = name;
    }

    /**
     * The actual transform implementation
     *
     * @param series the time series to apply the transform to.
     * @return A map of the transformed series.
     */
    protected abstract Map<TimeSeriesInfo<T>, TimeSeries<T>> apply(Map<TimeSeriesInfo<T>, TimeSeries<T>> series);

    /**
     * Applies the transform function to the provided series
     *
     * @param series  the time series to apply the transform to.
     * @param enabled true if the transform should be applied, false otherwise.
     * @return A map of the transformed series.
     */
    public Map<TimeSeriesInfo<T>, TimeSeries<T>> transform(Map<TimeSeriesInfo<T>, TimeSeries<T>> series, boolean enabled) {
        String names = series.keySet().stream().map(tTimeSeriesInfo -> tTimeSeriesInfo.getBinding().getLabel()).reduce((s, s2) -> s +" "+s2).orElse("null");
        if (enabled) {
            try (Profiler ignored = Profiler.start("Applying transform" + getName() + " to series " + names, logger::trace)) {
                series = apply(series);
            }
        }
        else {
            logger.debug(() -> "Transform " + getName() + " on series " + names + " is disabled.");
        }
        return series;
    }

    /**
     * Gets the name of the transform function
     *
     * @return the name of the transform function
     */
    public String getName() {
        return name;
    }
}
