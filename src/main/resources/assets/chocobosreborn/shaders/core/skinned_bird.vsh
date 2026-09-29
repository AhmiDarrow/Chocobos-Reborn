#version 150

// Vanilla's rendertype_entity_cutout_no_cull, skinned on the GPU (client/GpuBirds.java).
// The mesh stays on the card in its rest pose; per draw only the posed bones travel:
// Bones holds, per bone, the 3x4 affine of the entity pose folded into the bone (rows,
// the same matrices MeshSkinner uses on the CPU), so a vertex lands where the CPU path put it.

#moj_import <light.glsl>
#moj_import <fog.glsl>

in vec3 Position;      // rest pose
in vec4 Color;         // vertex colour; alpha 1 marks plumage, tinted by the breed
in vec2 UV0;
in vec3 Normal;        // rest pose
in vec4 BoneIds;       // up to four bones (0..255, as floats)
in vec4 BoneWeights;   // their weights, sorted, normalised at load

uniform sampler2D Sampler1;
uniform sampler2D Sampler2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform int FogShape;

uniform vec3 Light0_Direction;
uniform vec3 Light1_Direction;

uniform float Bones[384];
uniform float BoneVisible[32];
uniform vec3 PlumageTint;
uniform ivec2 OverlayUV;
uniform ivec2 LightUV;

out float vertexDistance;
out vec4 vertexColor;
out vec4 lightMapColor;
out vec4 overlayColor;
out vec2 texCoord0;
out float hiddenVertex;

vec3 bonePoint(int b, vec3 p) {
    int o = b * 12;
    return vec3(Bones[o] * p.x + Bones[o + 1] * p.y + Bones[o + 2] * p.z + Bones[o + 3],
                Bones[o + 4] * p.x + Bones[o + 5] * p.y + Bones[o + 6] * p.z + Bones[o + 7],
                Bones[o + 8] * p.x + Bones[o + 9] * p.y + Bones[o + 10] * p.z + Bones[o + 11]);
}

vec3 boneDirection(int b, vec3 n) {
    int o = b * 12;
    return vec3(Bones[o] * n.x + Bones[o + 1] * n.y + Bones[o + 2] * n.z,
                Bones[o + 4] * n.x + Bones[o + 5] * n.y + Bones[o + 6] * n.z,
                Bones[o + 8] * n.x + Bones[o + 9] * n.y + Bones[o + 10] * n.z);
}

void main() {
    // A vertex that mostly belongs to a hidden bone (the saddle on a bare bird) is dropped with its
    // triangle; soft-joint neighbours renormalise over the visible bones (MeshSkinner#skin).
    float visible = 0.0;
    float best = 0.0;
    bool hide = false;
    for (int k = 0; k < 4; k++) {
        float w = BoneWeights[k];
        bool h = BoneVisible[int(BoneIds[k] + 0.5)] < 0.5;
        if (!h) visible += w;
        if (w > best) {
            best = w;
            hide = h;
        }
    }
    hide = hide || visible <= 1.0e-6;
    float rescale = hide ? 1.0 : 1.0 / visible;

    vec3 p = vec3(0.0);
    vec3 n = vec3(0.0);
    for (int k = 0; k < 4; k++) {
        float w = BoneWeights[k];
        int b = int(BoneIds[k] + 0.5);
        if (w <= 0.0 || BoneVisible[b] < 0.5) continue;
        w *= rescale;
        p += w * bonePoint(b, Position);
        n += w * boneDirection(b, Normal);
    }
    if (hide) {
        p = bonePoint(int(BoneIds[0] + 0.5), Position);   // a sane place; the fragment shader drops the triangle
    }
    float nl = length(n);
    n = nl > 1.0e-6 ? n / nl : vec3(0.0, 1.0, 0.0);
    hiddenVertex = hide ? 1.0 : 0.0;

    gl_Position = ProjMat * ModelViewMat * vec4(p, 1.0);
    vertexDistance = fog_distance(p, FogShape);
    vec4 base = vec4(Color.rgb, 1.0);
    if (Color.a > 0.5) {
        base.rgb = min(vec3(1.0), base.rgb * PlumageTint);
    }
    vertexColor = minecraft_mix_light(Light0_Direction, Light1_Direction, n, base);
    lightMapColor = texelFetch(Sampler2, LightUV / 16, 0);
    overlayColor = texelFetch(Sampler1, OverlayUV, 0);
    texCoord0 = UV0;
}
