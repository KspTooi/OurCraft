#import "Common/ShaderLib/GLSLCompat.glsllib"

uniform sampler2DArray m_ArrayTex;
uniform int m_Layer;

varying vec2 texCoord;

void main() {
    vec4 color = texture(m_ArrayTex, vec3(texCoord, m_Layer));
    gl_FragColor = color;
}
