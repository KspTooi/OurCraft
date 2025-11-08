#version 330 core

in vec2 TexCoord;
in float v_ShouldTint;

out vec4 FragColor;

uniform sampler2D textureSampler;
uniform float timeOfDay;
uniform vec3 skyColor;
uniform vec3 u_TintColor;

void main() {
    vec4 texColor = texture(textureSampler, TexCoord);
    
    vec3 finalColor = texColor.rgb;
    if (v_ShouldTint > 0.5) {
        finalColor = texColor.rgb * u_TintColor;
    }
    
    float brightness = mix(0.3, 1.0, timeOfDay);
    finalColor = finalColor * brightness;
    
    finalColor = mix(finalColor, finalColor * skyColor, 0.1);
    
    FragColor = vec4(finalColor, texColor.a);
}

