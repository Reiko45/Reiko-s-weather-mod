package com.reiko.weather.landform;

/** A channel a flash flood can run down: its centre line, bed and width, {@code u} blocks downstream of the middle. */
interface FloodChannel {
    double[] point(float u);

    float floorAt(float u);

    float halfWidth(float u);

    /** Along the channel and across it (from its winding centre line). */
    float[] local(double x, double z);
}
