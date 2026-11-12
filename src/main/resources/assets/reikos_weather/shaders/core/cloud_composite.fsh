#version 150

// Lays the marched clouds (premultiplied, maybe at half resolution) over the scene. The depth written is where the
// cloud layer starts along this ray, so the depth test keeps the clouds off anything nearer, sharp at full resolution.

uniform sampler2D Sampler0;

uniform mat4 InvViewProj;
uniform mat4 ViewProj;
uniform float Below; // how far the bottom of the cloud layer is above the camera (negative once inside or above it)

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec4 cloud = texture(Sampler0, texCoord);
    if (cloud.a < 0.002) discard;
    float depth = 0.0;
    if (Below > 0.0) {
        vec4 far = InvViewProj * vec4(texCoord * 2.0 - 1.0, 1.0, 1.0);
        vec3 dir = normalize(far.xyz / far.w);
        if (dir.y <= 0.0) discard;
        vec4 clip = ViewProj * vec4(dir * (Below / dir.y), 1.0);
        depth = clamp(clip.z / clip.w * 0.5 + 0.5, 0.0, 1.0);
    }
    gl_FragDepth = depth;
    fragColor = cloud;
}
