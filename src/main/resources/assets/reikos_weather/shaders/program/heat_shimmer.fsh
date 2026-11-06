#version 150

uniform sampler2D DiffuseSampler;
uniform sampler2D DiffuseDepthSampler;
uniform float Time;
uniform float Strength;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    float depth = texture(DiffuseDepthSampler, texCoord).r;
    // only distant ground shimmers: not the sky (cleared depth) and not anything close by
    float far = smoothstep(0.9975, 0.9993, depth) * (1.0 - step(0.99999, depth));
    float t = Time * 6.2831853;
    float wave = sin(texCoord.y * 260.0 + t * 3.0) * 0.6 + sin(texCoord.y * 410.0 - t * 5.0 + texCoord.x * 40.0) * 0.4;
    vec2 offset = vec2(wave * 0.0018, wave * 0.0007) * Strength * far;
    vec2 uv = texCoord + offset;
    // don't pull in pixels from the sky above the horizon
    if (texture(DiffuseDepthSampler, uv).r > 0.99999) {
        uv = texCoord;
    }
    fragColor = vec4(texture(DiffuseSampler, uv).rgb, 1.0);
}
