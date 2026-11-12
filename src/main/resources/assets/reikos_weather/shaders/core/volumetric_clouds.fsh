#version 150

// Volumetric clouds: each pixel marches through the cloud layer, gathering light from the sun (or moon) and the sky.

uniform sampler2D Sampler0; // the scene's depth
uniform sampler2D Sampler1; // value noise (green holds red shifted by (37, 17), so one fetch gives two slices)
uniform sampler2D Sampler2; // around the camera: storm cloud cover (red), fair-weather blotches (green), storm towers (blue)
uniform sampler3D Sampler3; // tileable Worley noise, 1 at each cell's centre: red for the shapes, green for the detail

uniform mat4 InvViewProj;
uniform vec3 CameraPos;   // x and z: where the camera is in the drifting noise's frame; y: the camera's height
uniform vec2 CoverOrigin; // the cover map's corner, relative to the camera
uniform float CoverSize;
uniform vec4 Cover;       // fair-weather cover, cumulus, overcast, time (in ticks, wrapping every 262144)
uniform vec4 Layer;       // cloud base, how much shows through the fog, how far out clouds are drawn, steps
uniform vec3 LightDir;
uniform vec3 LightColor;
uniform vec3 SkyColor;
uniform vec3 GroundColor;
uniform vec3 HazeColor;
uniform vec4 Extra;       // rain, lightning flash, how dense, how high the tallest cloud reaches above the base

in vec2 texCoord;

out vec4 fragColor;

float noise(vec3 x) {
    vec3 p = floor(x);
    vec3 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    vec2 uv = p.xz + vec2(37.0, 17.0) * p.y + f.xz;
    vec2 rg = textureLod(Sampler1, (uv + 0.5) / 256.0, 0.0).yx;
    return mix(rg.x, rg.y, f.y);
}

float noise2(vec2 x) {
    vec2 p = floor(x);
    vec2 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    return textureLod(Sampler1, (p + f + 0.5) / 256.0, 0.0).x;
}

// The clouds through a column at this spot: x = base, y = depth, z = cover, w = how heaped (cumulus) they are; and how
// much of it is storm cloud. (Every noise scale here divides the period the camera's noise position wraps at,
// 196608 blocks, so the wrap is seamless.)
vec4 column(vec2 rel, vec2 np, out float storm, out float tower) {
    // the storm cover, and the big, slow blotches the fair-weather clouds come and go in, are worked out on the CPU
    vec3 map = texture(Sampler2, (rel - CoverOrigin) / CoverSize).rgb;
    storm = map.r;
    tower = map.b;
    float fair = Cover.x, cumulus = Cover.y;
    float fairCover = clamp(fair * 1.1 + (map.g - 0.5) * 0.8, 0.0, 1.0) * step(0.02, fair);
    // heaps of cumulus over the warm ground, each over its own bubble of rising air
    float cuCover = 0.0;
    if (cumulus > 0.01) {
        float heaps = noise2(np / 96.0 + 3.0) * 0.7 + noise2(np / 48.0 + 5.0) * 0.3;
        cuCover = cumulus * clamp((heaps - 0.42) * 3.5, 0.0, 1.0);
    }
    // (over a thunderstorm's heart the cloud towers up far higher, rounding off at the top like a giant cumulus)
    float heaped = max(cuCover > fairCover ? 1.0 - storm : 0.0, tower);
    float cover = max(max(fairCover, cuCover), storm);
    float depth = mix(mix(36.0, 30.0 + 70.0 * cuCover, heaped), 150.0 + 170.0 * tower, storm);
    // (a storm's ragged base hangs lower, and rolls)
    float base = Layer.x;
    if (storm > 0.01) base -= storm * (10.0 + 14.0 * noise2(np / 96.0 + 11.0));
    return vec4(base, depth, cover, heaped);
}

// The cloud's density at a spot in a column, 0 to 1: the fine detail only up close, where it shows.
float density(vec4 col, float storm, float tower, vec3 np, bool detailed) {
    float h = (np.y - col.x) / col.y;
    if (h <= 0.0 || h >= 1.0 || col.z < 0.01) return 0.0;
    // flat bases; the cover thins with height, so heaps round off into domes; a storm's tower stays solid most of
    // the way up before it rounds off
    float taper = smoothstep(0.0, 0.05, h) * mix(smoothstep(1.0, mix(0.3, 0.1, col.w), h), 1.0 - smoothstep(0.6, 1.0, h), tower);
    float cover = col.z * taper;
    if (cover < 0.01) return 0.0;
    float evolve = Cover.w / 1024.0;
    // the shapes: billowing cells (unions of rounded lumps, big and small), roughened by smoother noise
    float cells = texture(Sampler3, np / vec3(384.0, 192.0, 384.0) + vec3(0.0, evolve / 8.0, 0.0)).r;
    vec3 q = np * vec3(1.0 / 48.0, 1.0 / 30.0, 1.0 / 48.0) + vec3(0.0, evolve, 0.0);
    float n = noise(q) * 0.6 + noise(q * 2.0 + vec3(0.31, 0.17, 0.83)) * 0.4;
    float d = (cells * 0.6 + n * 0.4 - 0.8 + 0.62 * cover) * 3.0;
    if (d <= 0.0) return 0.0;
    if (detailed) {
        // small billows eat into the edges: cauliflower tops, wispy, ragged bases
        float fine = texture(Sampler3, np / vec3(64.0, 48.0, 64.0) + vec3(0.37, evolve / 2.0, 0.71)).g;
        d -= (1.0 - fine) * mix(0.45, 0.25, h) * (1.0 - 0.6 * storm);
        if (d <= 0.0) return 0.0;
    }
    return min(d * mix(2.6, 1.8, storm), 1.0) * Extra.z;
}

float phase(float c, float g) {
    float g2 = g * g;
    return (1.0 - g2) / (12.566 * pow(1.0 + g2 - 2.0 * g * c, 1.5));
}

void main() {
    float depth = texture(Sampler0, texCoord).r;
    vec4 far = InvViewProj * vec4(texCoord * 2.0 - 1.0, 1.0, 1.0);
    vec3 dir = normalize(far.xyz / far.w);
    float scene = 1.0e9;
    if (depth < 1.0) {
        vec4 hit = InvViewProj * vec4(texCoord * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
        scene = length(hit.xyz / hit.w);
    }
    // where the ray is inside the cloud layer
    float bottom = Layer.x - 40.0 - CameraPos.y, top = Layer.x + max(150.0, Extra.w) - CameraPos.y;
    float t0, t1;
    if (abs(dir.y) < 1.0e-4) {
        if (bottom > 0.0 || top < 0.0) {
            fragColor = vec4(0.0);
            return;
        }
        t0 = 0.0;
        t1 = Layer.z;
    } else {
        float a = bottom / dir.y, b = top / dir.y;
        t0 = max(min(a, b), 0.0);
        t1 = max(a, b);
    }
    t1 = min(min(t1, scene), Layer.z);
    if (t1 <= t0) {
        fragColor = vec4(0.0);
        return;
    }
    int steps = min(int(Layer.w) * 2, int(Layer.w) + int((t1 - t0) / 30.0));
    float dt = (t1 - t0) / float(steps);
    // (a fixed dither, so the banding of the steps turns into a fine grain the upscaling smooths over)
    float jitter = fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))));
    float t = t0 + dt * jitter;
    float cosAngle = dot(dir, LightDir);
    // (bright silver linings toward the sun, and plenty of light scattered back from the sunlit side)
    float ph = mix(phase(cosAngle, 0.6), phase(cosAngle, -0.2), 0.5) * 12.566;
    vec3 light = vec3(0.0);
    float transmittance = 1.0;
    float sigma = 0.09;
    vec3 origin = vec3(CameraPos.x, 0.0, CameraPos.z);
    for (int i = 0; i < 128; i++) {
        if (i >= steps || transmittance < 0.02) break;
        vec3 rel = dir * t;
        vec3 np = origin + vec3(rel.x, CameraPos.y + rel.y, rel.z);
        float storm, tower;
        vec4 col = column(rel.xz, np.xz, storm, tower);
        float d = density(col, storm, tower, np, t < 900.0);
        if (d > 0.002) {
            // how much cloud lies between here and the sun (through the same column: close enough for its own shadow)
            float toward = 0.0, reach = 0.0;
            int lights = t < 1200.0 ? 3 : 2;
            for (int k = 1; k <= 3; k++) {
                if (k > lights) break;
                float s = 8.0 * float(k * k);
                toward += density(col, storm, tower, np + LightDir * s, false) * (s - reach);
                reach = s;
            }
            // (Beer's law, with a little extra light scattered on through the cloud)
            float sun = exp(-toward * sigma * 1.4) * 0.65 + 0.35 * exp(-toward * sigma * 0.25);
            float powder = 1.0 - exp(-d * 4.0);
            float h = clamp((np.y - col.x) / col.y, 0.0, 1.0);
            vec3 ambient = mix(GroundColor, SkyColor, h) * (0.6 + 0.6 * h) * (1.0 - 0.3 * d);
            vec3 lit = LightColor * sun * ph * mix(0.5, 1.0, powder) + ambient + vec3(Extra.y);
            float stepT = exp(-d * sigma * dt);
            light += transmittance * (1.0 - stepT) * lit;
            transmittance *= stepT;
        }
        t += dt;
    }
    float alpha = 1.0 - transmittance;
    // a soft shoulder instead of blowing out to flat white
    vec3 c = light / max(alpha, 1.0e-3);
    light = (1.0 - exp(-c * 1.6)) * alpha;
    // distant cloud fades into the haze, and out altogether toward the edge of the drawn range
    float haze = 1.0 - exp(-t0 / (Layer.z * 0.45));
    light = mix(light, HazeColor * alpha, haze * 0.85);
    float edge = smoothstep(Layer.z, Layer.z * 0.6, t0);
    fragColor = vec4(light, alpha) * edge * Layer.y;
}
