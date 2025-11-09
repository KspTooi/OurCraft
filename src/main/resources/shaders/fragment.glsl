#version 330 core

in vec2 TexCoord;
in float v_ShouldTint;
in vec3 v_AnimData;

out vec4 FragColor;

uniform sampler2D textureSampler;
uniform vec3 u_TintColor;
uniform vec3 ambientLight;
uniform float u_Time;
uniform float u_TextureSize;
uniform float u_AtlasSize;

void main() {
    vec2 finalTexCoord = TexCoord;
    
    if (v_AnimData.x > 0.0) {
        float frameCount = v_AnimData.x;
        float frameTime = v_AnimData.y;
        float v0 = v_AnimData.z;
        
        float currentFrame = floor(mod(u_Time / frameTime, frameCount));
        float frameHeight = u_TextureSize / u_AtlasSize;
        
        float v_off = clamp(TexCoord.y - v0, 0.0, frameHeight - (1.0 / u_AtlasSize));
        finalTexCoord.y = v0 + (currentFrame * frameHeight) + v_off;
    }
    
    vec4 texColor = texture(textureSampler, finalTexCoord);
    
    vec3 finalColor = texColor.rgb;
    if (v_ShouldTint > 0.5) {
        finalColor = texColor.rgb * u_TintColor;
    }
    
    finalColor = finalColor * ambientLight;
    
    FragColor = vec4(finalColor, texColor.a);
}

