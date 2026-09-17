#version 150
uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
in vec2 texCoord0;
in vec4 vertexColor;
out vec4 fragColor;
void main() {
    vec2 uv = texCoord0;
    vec4 color = texture(Sampler0, uv);
    // Both longitude boundaries converge to the same average, with a smooth shoulder.
    float edge = min(uv.x, 1.0 - uv.x);
    float seam = 0.5 * (1.0 - smoothstep(0.0, 0.065, edge));
    if (seam > 0.0) color = mix(color, texture(Sampler0, vec2(1.0 - uv.x, uv.y)), seam);
    // Equirectangular poles must converge to a single colour, not stretched radial spokes.
    float pole = 1.0 - smoothstep(0.0, 0.065, min(uv.y, 1.0 - uv.y));
    if (pole > 0.0) {
        float v = uv.y < 0.5 ? 0.015 : 0.985;
        vec4 cap = (texture(Sampler0, vec2(0.125, v)) + texture(Sampler0, vec2(0.375, v))
                  + texture(Sampler0, vec2(0.625, v)) + texture(Sampler0, vec2(0.875, v))) * 0.25;
        color = mix(color, cap, pole);
    }
    // No alpha discard: even the first percent of dawn/healing must crossfade smoothly.
    fragColor = color * vertexColor * ColorModulator;
}
