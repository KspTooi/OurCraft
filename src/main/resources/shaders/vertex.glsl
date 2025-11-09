#version 330 core

layout (location = 0) in vec3 aPos;
layout (location = 1) in vec2 aTexCoord;
layout (location = 2) in float a_ShouldTint;
layout (location = 3) in vec3 a_AnimData;

out vec2 TexCoord;
out float v_ShouldTint;
out vec3 v_AnimData;

uniform mat4 model;
uniform mat4 view;
uniform mat4 projection;

void main() {
    gl_Position = projection * view * model * vec4(aPos, 1.0);
    TexCoord = aTexCoord;
    v_ShouldTint = a_ShouldTint;
    v_AnimData = a_AnimData;
}

