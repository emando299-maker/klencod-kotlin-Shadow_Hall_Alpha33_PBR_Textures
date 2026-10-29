package com.shadowhall.game

import android.opengl.GLES20
import android.graphics.BitmapFactory
import android.opengl.GLUtils
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.sqrt

/** Lightweight OpenGL ES 2.0 third-person prototype. Art and AI are intentionally placeholder-level. */
class HallRenderer : GLSurfaceView.Renderer {
    @Volatile var moveX = 0f
    @Volatile var moveZ = 0f
    @Volatile var cameraYaw = 0f
    @Volatile var hidden = false
    @Volatile var hidingPlace = ""
    @Volatile var flashlightOn = true
    @Volatile var flashlightBattery = 100f
    @Volatile var stamina = 100f
    /** User-adjustable touch sensitivity and lightweight scene detail preset. */
    /** Read-only world positions exposed for lightweight stereo sound panning. */
    val playerWorldX: Float get() = px
    val playerWorldZ: Float get() = pz
    val monsterWorldX: Float get() = mx
    val monsterWorldZ: Float get() = mz
    @Volatile var cameraSensitivity = 220f
    /** 0..2 scene brightness; 1 is the intended horror look. */
    @Volatile var brightness = 1f
    @Volatile var visualQuality = 3 // Ultra visual preset: prioritize detail over frame rate
    @Volatile var keysCount = 0
    @Volatile var statusText = "اجمع 3 مفاتيح"
    @Volatile var monsterState = "يتجول"
    @Volatile var gameWon = false
    @Volatile var gameOver = false
        private set
    /** Non-empty when the GL program failed to compile/link, so the HUD can show why the screen is black. */
    @Volatile var shaderError = ""
        private set
    @Volatile var exitDoorOpen = false
    @Volatile var drawerOpen = false
    @Volatile var hasFuse = false
    @Volatile var batteryPackCollected = false
    @Volatile var bedroomDoorOpen = false
    @Volatile var kitchenDoorOpen = false
    @Volatile var storageDoorOpen = false
    @Volatile var sideRoomDoorOpen = false
    // Smoothed door motion, updated every frame instead of teleporting panels.
    private var bedroomDoorAmount = 0f
    private var kitchenDoorAmount = 0f
    private var storageDoorAmount = 0f
    private var sideRoomDoorAmount = 0f
    private var program = 0
    private var posHandle = 0
    private var mvpHandle = 0
    private var colorHandle = 0
    private var mvHandle = 0
    private var textureHandle = 0
    private var useTextureHandle = 0
    private var fogHandle = 0
    private var timeHandle = 0
    private var viewportHandle = 0
    private var dimensionsHandle = 0
    private var smoothNormalHandle = 0
    private var modelHandle = 0
    private var flashPosHandle = 0
    private var flashDirHandle = 0
    private var flashOnHandle = 0
    private var flashPowerHandle = 0
    private var brightnessHandle = 0
    private var dangerHandle = 0
    private var shaderDanger = 0f
    private var eyePosHandle = 0
    private var eyeX = 0f; private var eyeY = 1.48f; private var eyeZ = 0f
    // Flashlight shadow mapping (hand-held light, 1 shadow-casting spot).
    @Volatile var shadowsEnabled = true
    private val shadowSize = 1024
    private val shadowFar = 18f
    private val shadowRange = 14f
    private var shadowProgram = 0
    private var shadowFbo = 0
    private var shadowTex = 0
    private var shadowsReady = false
    private var shadowActive = false
    private var shadowPass = false
    private var shPosHandle = 0
    private var shModelHandle = 0
    private var shLightVPHandle = 0
    private var shLightPosHandle = 0
    private var shLightFwdHandle = 0
    private var shFarHandle = 0
    private var lightVPHandle = 0
    private var shadowMapHandle = 0
    private var shadowFwdHandle = 0
    private var shadowFarHandle = 0
    private var shadowOnHandle = 0
    private var shadowTexelHandle = 0
    private val lightView = FloatArray(16)
    private val lightProj = FloatArray(16)
    private val lightVP = FloatArray(16)
    private var fx = 0f; private var fy = 1.3f; private var fz = 0f
    private var fdx = 0f; private var fdy = 0f; private var fdz = -1f
    private var walkPhase = 0f
    private var sphereVertices: FloatBuffer? = null
    private var sphereVertexCount = 0
    private var currentSmoothNormals = false
    private var currentForceUntextured = false
    private var flashlightFlickering = false
    private var textureIds = IntArray(3)
    private var normalRoughIds = IntArray(3)
    private val normalRoughLoaded = BooleanArray(3)
    private var normalMapHandle = 0
    private var useNormalMapHandle = 0
    private var viewportWidth = 1f
    private var viewportHeight = 1f
    private lateinit var vertices: FloatBuffer
    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val model = FloatArray(16)
    private val vp = FloatArray(16)
    private val mvp = FloatArray(16)
    private var px = 0f; private var pz = 3.8f
    // Start the monster in a separate side room so the opening is tense, not an instant attack.
    private var mx = -6.0f; private var mz = -4.5f
    private var monsterFacing = 0f
    private var monsterAwakened = false
    private var openingTimer = 0f
    private var patrolTargetTimer = 0f
    private var currentSx = 1f; private var currentSy = 1f; private var currentSz = 1f
    private val keys = arrayOf(floatArrayOf(-6.0f, -2.9f), floatArrayOf(6.0f, -2.9f), floatArrayOf(0.2f, 3.5f))
    private val collected = BooleanArray(3)
    private var lastTime = System.nanoTime()
    private var alertTimer = 0f
    private var patrolClock = 0f
    private var investigateTimer = 8f
    private var hideDiscoveryTimer = 0f
    private var lastKnownX = 0f
    private var lastKnownZ = 0f
    private var searchPhase = 0f
    private var searchTargetTimer = 0f
    private var searchTargetX = 0f
    private var searchTargetZ = 0f
    private var lastSeenTimer = 0f
    private var patrolTargetX = -5.5f
    private var patrolTargetZ = 0f
    private val hideSpots = arrayOf(floatArrayOf(-2.8f, -0.4f), floatArrayOf(2.5f, 2.0f), floatArrayOf(0f, -3.8f))

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.025f, 0.035f, 0.07f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDisable(GLES20.GL_CULL_FACE)
        GLES20.glDisable(GLES20.GL_BLEND)
        // Alpha 12: textured materials, face-aware directional shading, distance fog and subtle film grain.
        val vertexShader = """
            attribute vec4 aPosition;
            uniform mat4 uMVP;
            uniform mat4 uMV;
            uniform mat4 uModel;
            uniform mediump vec3 uDimensions;
            uniform mat4 uLightVP;
            varying vec2 vUV;
            varying vec3 vLocal;
            varying float vDepth;
            varying vec3 vWorldPos;
            varying vec3 vViewPos;
            varying vec4 vShadow;
            void main(){
              vec3 p=aPosition.xyz;
              vec3 a=abs(p);
              // Tile materials by world-space object dimensions instead of stretching one texture over a whole wall.
              if(a.x>a.y && a.x>a.z) vUV=vec2(p.z*uDimensions.z,p.y*uDimensions.y)+0.5;
              else if(a.y>a.x && a.y>a.z) vUV=vec2(p.x*uDimensions.x,p.z*uDimensions.z)+0.5;
              else vUV=vec2(p.x*uDimensions.x,p.y*uDimensions.y)+0.5;
              vLocal=p;
              vWorldPos=(uModel*aPosition).xyz;
              vShadow=uLightVP*(uModel*aPosition);
              vec4 eye=uMV*aPosition;
              vDepth=-eye.z;
              vViewPos=eye.xyz;
              gl_Position=uMVP*aPosition;
            }
        """.trimIndent()
        val fragmentShader = """
            #ifdef GL_FRAGMENT_PRECISION_HIGH
            precision highp float;
            #else
            precision mediump float;
            #endif
            uniform vec4 uColor;
            uniform sampler2D uTexture;
            uniform int uUseTexture;
            uniform float uFog;
            uniform float uTime;
            uniform vec2 uViewport;
            uniform int uSmoothNormal;
            uniform mediump vec3 uDimensions;
            uniform vec3 uFlashPos;
            uniform vec3 uFlashDir;
            uniform int uFlashOn;
            uniform float uFlashPower;
            uniform float uBrightness;
            uniform float uDanger;
            uniform vec3 uEyePos;
            uniform sampler2D uNormalMap;
            uniform float uUseNM;
            uniform sampler2D uShadowMap;
            uniform vec3 uShadowFwd;
            uniform float uShadowFar;
            uniform float uShadowOn;
            uniform float uShadowTexel;
            varying vec3 vWorldPos;
            varying vec2 vUV;
            varying vec3 vLocal;
            varying float vDepth;
            varying vec3 vViewPos;
            varying vec4 vShadow;
            float hash(vec2 p){ return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453); }
            float heightAt(vec2 uv){ return dot(texture2D(uTexture,uv).rgb,vec3(0.30,0.59,0.11)); }
            float vnoise(vec2 p){
              vec2 i=floor(p); vec2 f=fract(p); f=f*f*(3.0-2.0*f);
              float a=hash(i); float b=hash(i+vec2(1.0,0.0)); float c=hash(i+vec2(0.0,1.0)); float d=hash(i+vec2(1.0,1.0));
              return mix(mix(a,b,f.x),mix(c,d,f.x),f.y);
            }
            float fbm(vec2 p){
              float v=0.0; float amp=0.5;
              for(int i=0;i<4;i++){ v+=amp*vnoise(p); p=p*2.03+vec2(17.0,9.0); amp*=0.5; }
              return v;
            }
            // Cook-Torrance (GGX) specular: gives wet stone, polished brass and glossy flesh believable highlights.
            float ggx(vec3 N, vec3 V, vec3 L, float rough){
              vec3 H=normalize(V+L);
              float NH=max(dot(N,H),0.0); float NL=max(dot(N,L),0.0); float NV=max(dot(N,V),0.001);
              float a=rough*rough; float a2=a*a;
              float d=NH*NH*(a2-1.0)+1.0;
              float D=a2/(3.14159*d*d);
              float k=(rough+1.0); k=k*k/8.0;
              float G=(NL/(NL*(1.0-k)+k))*(NV/(NV*(1.0-k)+k));
              float F=0.04+0.96*pow(1.0-max(dot(H,V),0.0),5.0);
              return D*G*F/(4.0*NV*max(NL,0.001))*NL;
            }
            float lampFlick(float phase){
              float fl=0.90+0.10*sin(uTime*(8.0+phase*2.3)+phase*3.1);
              if(hash(vec2(floor(uTime*7.0),phase))>0.96) fl*=0.30;
              return fl;
            }
            vec3 lampLight(vec3 lp, vec3 col, float radius, vec3 n, vec3 wp, float phase){
              vec3 d=lp-wp; float dist=length(d); vec3 l=d/max(dist,0.001);
              float att=clamp(1.0-dist/radius,0.0,1.0); att*=att;
              return col*(0.25+0.75*max(dot(n,l),0.0))*att*lampFlick(phase);
            }
            vec3 lampSpec(vec3 lp, vec3 col, float radius, vec3 n, vec3 v, vec3 wp, float rough, float phase){
              vec3 d=lp-wp; float dist=length(d); vec3 l=d/max(dist,0.001);
              float att=clamp(1.0-dist/radius,0.0,1.0); att*=att;
              return col*ggx(n,v,l,rough)*att*lampFlick(phase);
            }
            float unpackDepth(vec4 c){ return dot(c,vec4(1.0,1.0/255.0,1.0/65025.0,1.0/16581375.0)); }
            // Flashlight shadow map lookup with 4-tap soft edges; returns 1.0 when lit, 0.0 when shadowed.
            float shadowFactor(vec3 wp, float facing){
              if(uShadowOn<0.5 || vShadow.w<=0.001) return 1.0;
              vec2 uv=vShadow.xy/vShadow.w*0.5+0.5;
              if(uv.x<0.03 || uv.x>0.97 || uv.y<0.03 || uv.y>0.97) return 1.0;
              float lin=dot(wp-uFlashPos,uShadowFwd)/uShadowFar-(0.0025+0.006*(1.0-facing));
              float t=uShadowTexel*1.4;
              float s=0.0;
              s+=(lin>unpackDepth(texture2D(uShadowMap,uv+vec2(-t,-t))))?0.0:1.0;
              s+=(lin>unpackDepth(texture2D(uShadowMap,uv+vec2( t,-t))))?0.0:1.0;
              s+=(lin>unpackDepth(texture2D(uShadowMap,uv+vec2(-t, t))))?0.0:1.0;
              s+=(lin>unpackDepth(texture2D(uShadowMap,uv+vec2( t, t))))?0.0:1.0;
              return s*0.25;
            }
            void main(){
              vec3 a=abs(vLocal);
              vec3 normal; vec3 T; vec3 B;
              if(uSmoothNormal==1){ normal=normalize(vLocal/max(uDimensions*uDimensions,vec3(0.0001))); T=vec3(1.0,0.0,0.0); B=vec3(0.0,1.0,0.0); }
              else if(a.x>a.y && a.x>a.z){ normal=vec3(sign(vLocal.x),0.0,0.0); T=vec3(0.0,0.0,1.0); B=vec3(0.0,1.0,0.0); }
              else if(a.y>a.x && a.y>a.z){ normal=vec3(0.0,sign(vLocal.y),0.0); T=vec3(1.0,0.0,0.0); B=vec3(0.0,0.0,1.0); }
              else { normal=vec3(0.0,0.0,sign(vLocal.z)); T=vec3(1.0,0.0,0.0); B=vec3(0.0,1.0,0.0); }
              vec3 wp=vWorldPos;
              vec3 V=normalize(uEyePos-wp);
              vec4 texel=vec4(1.0);
              float texRough=-1.0;
              if(uUseTexture==1){
                texel=texture2D(uTexture,vUV);
                if(uUseNM>0.5 && uSmoothNormal==0){
                  // Authored tangent-space normal map (RG) with roughness in B.
                  vec4 nr=texture2D(uNormalMap,vUV);
                  vec2 nxy=nr.rg*2.0-1.0;
                  float nz=sqrt(max(1.0-dot(nxy,nxy),0.05));
                  normal=normalize(T*nxy.x+B*nxy.y+normal*nz);
                  texRough=nr.b;
                }
                // Fallback bump mapping derived from the texture's brightness when no normal map is available.
                else if(uSmoothNormal==0 && uFog>0.5){
                  float e=0.0025;
                  float hc=heightAt(vUV);
                  vec2 g=vec2(heightAt(vUV+vec2(e,0.0))-hc,heightAt(vUV+vec2(0.0,e))-hc)/e;
                  normal=normalize(normal-0.032*(g.x*T+g.y*B));
                }
              }
              // Rounded edges: every box edge within ~3 cm bends its normal, so corners catch light like real chamfers.
              float edgeW=0.0;
              if(uSmoothNormal==0){
                vec3 faceMask=abs(normal);
                faceMask=step(0.5,faceMask);
                vec3 e3=(vec3(0.5)-a)*uDimensions+faceMask*10.0;
                vec3 dimsM=uDimensions+faceMask*100.0;
                float mind=min(dimsM.x,min(dimsM.y,dimsM.z));
                float bw=max(min(0.03,0.3*mind),0.002);
                vec3 tilt=clamp(1.0-e3/bw,0.0,1.0)*(1.0-faceMask)*sign(vLocal);
                normal=normalize(normal+tilt*0.9);
                edgeW=1.0-clamp(min(e3.x,min(e3.y,e3.z))/(bw*1.6),0.0,1.0);
              }
              // ---- material ----
              vec3 albedo=uColor.rgb*texel.rgb;
              float lumaT=dot(texel.rgb,vec3(0.30,0.59,0.11));
              float rough=(uUseTexture==1)?clamp(0.80-0.35*lumaT,0.40,0.90):((uSmoothNormal==1)?0.38:0.55);
              if(texRough>=0.0) rough=clamp(texRough,0.18,0.95);
              float puddle=0.0;
              if(uUseTexture==1){
                vec2 gp=(abs(normal.y)>0.5)?wp.xz:vec2(wp.x+wp.z,wp.y);
                float lowFloor=1.0-smoothstep(0.0,1.1,wp.y);
                float grime=smoothstep(0.45,0.80,fbm(gp*1.7))*(0.35+0.65*lowFloor);
                albedo*=mix(1.0,0.45,grime);
                albedo*=0.78+0.44*fbm(wp.xz*0.31+vec2(wp.y*0.2));
                float streak=smoothstep(0.55,0.90,fbm(vec2((wp.x+wp.z)*5.0,wp.y*0.45)))*smoothstep(0.4,1.9,wp.y)*(1.0-abs(normal.y));
                albedo*=1.0-0.35*streak;
                float floorFace=step(0.5,normal.y)*(1.0-smoothstep(0.02,0.12,wp.y));
                puddle=smoothstep(0.58,0.68,fbm(wp.xz*0.55+vec2(3.0)))*floorFace;
                albedo*=1.0-0.45*puddle;
                rough=mix(rough,0.12,puddle);
                albedo=mix(albedo,albedo*1.35+0.015,edgeW*0.6);
                rough=mix(rough,rough*0.7,edgeW*0.7);
              }
              // Fake ambient occlusion: darker near the floor, the ceiling and floor/wall corners.
              float aoH=mix(0.55,1.0,smoothstep(0.0,0.7,wp.y))*(1.0-0.35*smoothstep(1.85,2.45,wp.y));
              float dxw=min(abs(abs(wp.x)-4.0),abs(abs(wp.x)-8.1));
              float dzw=min(abs(wp.z-6.25),abs(wp.z+6.25));
              float aoW=(0.70+0.30*smoothstep(0.0,0.55,dxw))*(0.78+0.22*smoothstep(0.0,0.5,dzw));
              float ao=aoH*mix(1.0,aoW,step(0.5,abs(normal.y)))*(0.85+0.15*clamp((abs(vLocal.x)+abs(vLocal.y)+abs(vLocal.z))*0.75,0.0,1.0));
              // Hemispherical ambient: cold from above, warm dirty bounce from below.
              float ambientFloor=0.07+0.13*uBrightness;
              vec3 keyLight=normalize(vec3(-0.35,0.82,0.42));
              float moon=max(dot(normal,keyLight),0.0)*0.16*(0.5+0.5*uBrightness);
              vec3 hemi=mix(vec3(0.28,0.20,0.15),vec3(0.85,0.95,1.20),normal.y*0.5+0.5);
              vec3 lightCol=hemi*(ambientFloor+moon)*ao;
              vec3 lampW=vec3(1.0,0.72,0.40);
              vec3 lampS=vec3(0.95,0.55,0.25);
              vec3 lamps=lampLight(vec3(0.0,2.2,-4.0),lampW,4.4,normal,wp,1.0)
                        +lampLight(vec3(0.0,2.2,0.0),lampW,4.4,normal,wp,2.0)
                        +lampLight(vec3(0.0,2.2,4.0),lampW,4.4,normal,wp,3.0)
                        +lampLight(vec3(-3.6,1.84,-4.1),lampS,2.7,normal,wp,4.0)
                        +lampLight(vec3(3.6,1.84,-4.1),lampS,2.7,normal,wp,5.0)
                        +lampLight(vec3(-3.6,1.84,-0.6),lampS,2.7,normal,wp,6.0)
                        +lampLight(vec3(3.6,1.84,-0.6),lampS,2.7,normal,wp,7.0)
                        +lampLight(vec3(-3.6,1.84,3.0),lampS,2.7,normal,wp,8.0)
                        +lampLight(vec3(3.6,1.84,3.0),lampS,2.7,normal,wp,9.0);
              lightCol+=lamps*0.55*ao;
              vec3 spec=lampSpec(vec3(0.0,2.2,-4.0),lampW,4.4,normal,V,wp,rough,1.0)
                       +lampSpec(vec3(0.0,2.2,0.0),lampW,4.4,normal,V,wp,rough,2.0)
                       +lampSpec(vec3(0.0,2.2,4.0),lampW,4.4,normal,V,wp,rough,3.0)
                       +lampSpec(vec3(-3.6,1.84,-0.6),lampS,2.7,normal,V,wp,rough,6.0)
                       +lampSpec(vec3(3.6,1.84,-0.6),lampS,2.7,normal,V,wp,rough,7.0)
                       +lampSpec(vec3(-3.6,1.84,3.0),lampS,2.7,normal,V,wp,rough,8.0)
                       +lampSpec(vec3(3.6,1.84,3.0),lampS,2.7,normal,V,wp,rough,9.0);
              spec*=0.35;
              // Flashlight: warm cone with soft penumbra, bright core, distance falloff, shadows and GGX glints.
              vec3 fromLight=wp-uFlashPos;
              float ld=max(length(fromLight),0.001);
              vec3 lr=fromLight/ld;
              float cone=dot(normalize(uFlashDir),lr);
              float spot=smoothstep(0.74,0.93,cone);
              float core=smoothstep(0.92,0.99,cone);
              float wrapAmt=(uSmoothNormal==1)?0.45:0.0;
              float facing=max((dot(normal,-lr)+wrapAmt)/(1.0+wrapAmt),0.0);
              float att=1.0/(1.0+0.07*ld+0.040*ld*ld);
              float fl=float(uFlashOn)*(spot*0.85+core*0.45)*att*uFlashPower;
              float sh=shadowFactor(wp,facing);
              vec3 flashCol=vec3(1.0,0.90,0.72);
              spec+=flashCol*ggx(normal,V,-lr,rough)*fl*sh*1.3;
              spec=min(spec,vec3(2.5));
              vec3 color=albedo*(lightCol+flashCol*fl*facing*2.2*sh);
              color*=0.94+0.06*sin(vUV.x*43.0+sin(vUV.y*27.0));
              color*=0.93+hash(floor(vUV*180.0))*0.14;
              color+=spec;
              // Glowing materials: red eyes / warning lamps, warm lamp bulbs and green "ready" lights.
              float emis=0.0;
              if(uColor.r>0.70 && uColor.g<0.30 && uColor.b<0.25) emis=1.0;
              if(uColor.r>0.85 && uColor.g>0.50 && uColor.g<0.70 && uColor.b<0.35) emis=1.0;
              if(uColor.g>0.75 && uColor.r<0.30 && uColor.b<0.35) emis=1.0;
              color=mix(color,uColor.rgb*1.15,emis)+uColor.rgb*0.30*emis;
              // Distance fog with light scattering inside the flashlight beam.
              float fog=clamp((vDepth-3.8)/14.0,0.0,0.82)*uFog;
              float fogNoise=0.94+0.06*sin(wp.x*1.7+uTime*0.22)*sin(wp.y*2.1-uTime*0.17);
              color=mix(color,vec3(0.045,0.055,0.075)*fogNoise,fog);
              color+=flashCol*float(uFlashOn)*spot*att*fog*0.5*uFlashPower;
              // Grade: soft highlight shoulder, slight desaturation, heavy vignette, red panic edge, grain.
              color=color*1.15/(1.0+0.28*color);
              float luma=dot(color,vec3(0.299,0.587,0.114));
              color=mix(vec3(luma),color,0.90-0.35*uDanger);
              vec2 screen=gl_FragCoord.xy/max(uViewport,vec2(1.0))-0.5;
              float edge=clamp(dot(screen,screen)*2.6,0.0,1.0);
              color*=1.0-0.55*edge;
              color.r+=uDanger*edge*0.25*(0.6+0.4*sin(uTime*9.0));
              color.gb*=1.0-0.45*uDanger*edge;
              color+=vec3((hash(gl_FragCoord.xy+uTime*19.0)-0.5)*0.045*(1.0-luma));
              gl_FragColor=vec4(max(color,vec3(0.0)),uColor.a);
            }
        """.trimIndent()
        program = GLES20.glCreateProgram().also { p ->
            val vs = compile(GLES20.GL_VERTEX_SHADER, vertexShader); val fs = compile(GLES20.GL_FRAGMENT_SHADER, fragmentShader)
            GLES20.glAttachShader(p, vs); GLES20.glAttachShader(p, fs); GLES20.glLinkProgram(p)
            val linkStatus = IntArray(1)
            GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, linkStatus, 0)
            if (linkStatus[0] == 0) {
                val log = GLES20.glGetProgramInfoLog(p)
                shaderError = "خطأ ربط: " + log.take(120)
                android.util.Log.e("ShadowHallGL", "Program link failed: $log")
            }
        }
        posHandle = GLES20.glGetAttribLocation(program, "aPosition")
        mvpHandle = GLES20.glGetUniformLocation(program, "uMVP")
        colorHandle = GLES20.glGetUniformLocation(program, "uColor")
        mvHandle = GLES20.glGetUniformLocation(program, "uMV")
        textureHandle = GLES20.glGetUniformLocation(program, "uTexture")
        useTextureHandle = GLES20.glGetUniformLocation(program, "uUseTexture")
        fogHandle = GLES20.glGetUniformLocation(program, "uFog")
        timeHandle = GLES20.glGetUniformLocation(program, "uTime")
        viewportHandle = GLES20.glGetUniformLocation(program, "uViewport")
        dimensionsHandle = GLES20.glGetUniformLocation(program, "uDimensions")
        smoothNormalHandle = GLES20.glGetUniformLocation(program, "uSmoothNormal")
        modelHandle = GLES20.glGetUniformLocation(program, "uModel")
        flashPosHandle = GLES20.glGetUniformLocation(program, "uFlashPos")
        flashDirHandle = GLES20.glGetUniformLocation(program, "uFlashDir")
        flashOnHandle = GLES20.glGetUniformLocation(program, "uFlashOn")
        flashPowerHandle = GLES20.glGetUniformLocation(program, "uFlashPower")
        brightnessHandle = GLES20.glGetUniformLocation(program, "uBrightness")
        dangerHandle = GLES20.glGetUniformLocation(program, "uDanger")
        lightVPHandle = GLES20.glGetUniformLocation(program, "uLightVP")
        eyePosHandle = GLES20.glGetUniformLocation(program, "uEyePos")
        normalMapHandle = GLES20.glGetUniformLocation(program, "uNormalMap")
        useNormalMapHandle = GLES20.glGetUniformLocation(program, "uUseNM")
        shadowMapHandle = GLES20.glGetUniformLocation(program, "uShadowMap")
        shadowFwdHandle = GLES20.glGetUniformLocation(program, "uShadowFwd")
        shadowFarHandle = GLES20.glGetUniformLocation(program, "uShadowFar")
        shadowOnHandle = GLES20.glGetUniformLocation(program, "uShadowOn")
        shadowTexelHandle = GLES20.glGetUniformLocation(program, "uShadowTexel")
        loadMaterialTextures()
        setupShadows()
        val data = floatArrayOf(
            -0.5f,-0.5f,0.5f, 0.5f,-0.5f,0.5f, 0.5f,0.5f,0.5f, -0.5f,-0.5f,0.5f, 0.5f,0.5f,0.5f, -0.5f,0.5f,0.5f,
            0.5f,-0.5f,-0.5f, -0.5f,-0.5f,-0.5f, -0.5f,0.5f,-0.5f, 0.5f,-0.5f,-0.5f, -0.5f,0.5f,-0.5f, 0.5f,0.5f,-0.5f,
            -0.5f,-0.5f,-0.5f, -0.5f,-0.5f,0.5f, -0.5f,0.5f,0.5f, -0.5f,-0.5f,-0.5f, -0.5f,0.5f,0.5f, -0.5f,0.5f,-0.5f,
            0.5f,-0.5f,0.5f, 0.5f,-0.5f,-0.5f, 0.5f,0.5f,-0.5f, 0.5f,-0.5f,0.5f, 0.5f,0.5f,-0.5f, 0.5f,0.5f,0.5f,
            -0.5f,0.5f,0.5f, 0.5f,0.5f,0.5f, 0.5f,0.5f,-0.5f, -0.5f,0.5f,0.5f, 0.5f,0.5f,-0.5f, -0.5f,0.5f,-0.5f,
            -0.5f,-0.5f,-0.5f, 0.5f,-0.5f,-0.5f, 0.5f,-0.5f,0.5f, -0.5f,-0.5f,-0.5f, 0.5f,-0.5f,0.5f, -0.5f,-0.5f,0.5f
        )
        vertices = ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(data); position(0) }
        // Smooth UV sphere mesh used for the creature's major body forms; this breaks up the old box-only silhouette.
        val sphereData = ArrayList<Float>(20 * 24 * 6 * 3)
        val rings = 18; val segments = 24
        for (ring in 0 until rings) {
            val lat0 = -Math.PI / 2.0 + Math.PI * ring / rings
            val lat1 = -Math.PI / 2.0 + Math.PI * (ring + 1) / rings
            for (seg in 0 until segments) {
                val lon0 = 2.0 * Math.PI * seg / segments
                val lon1 = 2.0 * Math.PI * (seg + 1) / segments
                val p00 = floatArrayOf((kotlin.math.cos(lat0)*kotlin.math.cos(lon0)).toFloat(), kotlin.math.sin(lat0).toFloat(), (kotlin.math.cos(lat0)*kotlin.math.sin(lon0)).toFloat())
                val p10 = floatArrayOf((kotlin.math.cos(lat1)*kotlin.math.cos(lon0)).toFloat(), kotlin.math.sin(lat1).toFloat(), (kotlin.math.cos(lat1)*kotlin.math.sin(lon0)).toFloat())
                val p01 = floatArrayOf((kotlin.math.cos(lat0)*kotlin.math.cos(lon1)).toFloat(), kotlin.math.sin(lat0).toFloat(), (kotlin.math.cos(lat0)*kotlin.math.sin(lon1)).toFloat())
                val p11 = floatArrayOf((kotlin.math.cos(lat1)*kotlin.math.cos(lon1)).toFloat(), kotlin.math.sin(lat1).toFloat(), (kotlin.math.cos(lat1)*kotlin.math.sin(lon1)).toFloat())
                for (pt in arrayOf(p00,p10,p01,p01,p10,p11)) for (v in pt) sphereData.add(v)
            }
        }
        sphereVertexCount = sphereData.size / 3
        sphereVertices = ByteBuffer.allocateDirect(sphereData.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { sphereData.forEach { put(it) }; position(0) }
    }
    /** Builds the shadow-depth program and an RGBA8 render target (depth packed into 4 channels; works without OES_depth_texture). */
    private fun setupShadows() {
        shadowsReady = false
        val savedError = shaderError
        try {
            val vs = compile(GLES20.GL_VERTEX_SHADER, """
                attribute vec4 aPosition;
                uniform mat4 uModel;
                uniform mat4 uLightVP;
                uniform vec3 uLightPos;
                uniform vec3 uLightFwd;
                uniform float uFar;
                varying float vLin;
                void main(){
                  vec4 w=uModel*aPosition;
                  vLin=dot(w.xyz-uLightPos,uLightFwd)/uFar;
                  gl_Position=uLightVP*w;
                }
            """.trimIndent())
            val fs = compile(GLES20.GL_FRAGMENT_SHADER, """
                #ifdef GL_FRAGMENT_PRECISION_HIGH
                precision highp float;
                #else
                precision mediump float;
                #endif
                varying float vLin;
                vec4 packDepth(float d){
                  vec4 enc=vec4(1.0,255.0,65025.0,16581375.0)*d;
                  enc=fract(enc);
                  enc-=enc.yzww*vec4(1.0/255.0,1.0/255.0,1.0/255.0,0.0);
                  return enc;
                }
                void main(){ gl_FragColor=packDepth(clamp(vLin,0.0,0.9999)); }
            """.trimIndent())
            val p = GLES20.glCreateProgram()
            GLES20.glAttachShader(p, vs); GLES20.glAttachShader(p, fs); GLES20.glLinkProgram(p)
            val link = IntArray(1)
            GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, link, 0)
            if (link[0] == 0) { android.util.Log.e("ShadowHallGL", "Shadow program link failed: ${GLES20.glGetProgramInfoLog(p)}"); shaderError = savedError; return }
            shadowProgram = p
            shPosHandle = GLES20.glGetAttribLocation(p, "aPosition")
            shModelHandle = GLES20.glGetUniformLocation(p, "uModel")
            shLightVPHandle = GLES20.glGetUniformLocation(p, "uLightVP")
            shLightPosHandle = GLES20.glGetUniformLocation(p, "uLightPos")
            shLightFwdHandle = GLES20.glGetUniformLocation(p, "uLightFwd")
            shFarHandle = GLES20.glGetUniformLocation(p, "uFar")
            val tex = IntArray(1); GLES20.glGenTextures(1, tex, 0); shadowTex = tex[0]
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, shadowTex)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, shadowSize, shadowSize, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
            val fb = IntArray(1); GLES20.glGenFramebuffers(1, fb, 0); shadowFbo = fb[0]
            val rb = IntArray(1); GLES20.glGenRenderbuffers(1, rb, 0)
            GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, rb[0])
            GLES20.glRenderbufferStorage(GLES20.GL_RENDERBUFFER, GLES20.GL_DEPTH_COMPONENT16, shadowSize, shadowSize)
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, shadowFbo)
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, shadowTex, 0)
            GLES20.glFramebufferRenderbuffer(GLES20.GL_FRAMEBUFFER, GLES20.GL_DEPTH_ATTACHMENT, GLES20.GL_RENDERBUFFER, rb[0])
            val status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
            shadowsReady = status == GLES20.GL_FRAMEBUFFER_COMPLETE
            if (!shadowsReady) android.util.Log.e("ShadowHallGL", "Shadow framebuffer incomplete: $status")
        } catch (e: Exception) {
            android.util.Log.e("ShadowHallGL", "Shadow setup failed", e)
            shadowsReady = false
        }
        // A shadow failure must not show as a scary shader error: the game simply runs without shadows.
        if (!shadowsReady) shaderError = savedError
    }

    /** Renders the scene's depth from the hand-held flashlight into the shadow texture. */
    private fun renderShadowMap() {
        shadowActive = false
        if (!shadowsEnabled || !shadowsReady || visualQuality < 2) return
        val flashActive = flashlightOn && !hidden && flashlightBattery > 0f && !flashlightFlickering
        if (!flashActive) return
        Matrix.setLookAtM(lightView, 0, fx, fy, fz, fx + fdx, fy + fdy, fz + fdz, 0f, 1f, 0f)
        Matrix.perspectiveM(lightProj, 0, 95f, 1f, 0.1f, shadowFar)
        Matrix.multiplyMM(lightVP, 0, lightProj, 0, lightView, 0)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, shadowFbo)
        GLES20.glViewport(0, 0, shadowSize, shadowSize)
        GLES20.glClearColor(1f, 1f, 1f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glUseProgram(shadowProgram)
        GLES20.glUniformMatrix4fv(shLightVPHandle, 1, false, lightVP, 0)
        GLES20.glUniform3f(shLightPosHandle, fx, fy, fz)
        GLES20.glUniform3f(shLightFwdHandle, fdx, fdy, fdz)
        GLES20.glUniform1f(shFarHandle, shadowFar)
        shadowPass = true
        drawScene()
        shadowPass = false
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glViewport(0, 0, viewportWidth.toInt(), viewportHeight.toInt())
        shadowActive = true
    }

    private fun compile(type: Int, source: String): Int = GLES20.glCreateShader(type).also { s ->
        GLES20.glShaderSource(s, source)
        GLES20.glCompileShader(s)
        val status = IntArray(1)
        GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(s)
            shaderError = "خطأ شيدر: " + log.take(120)
            android.util.Log.e("ShadowHallGL", "Shader compile failed: $log")
        }
    }

    private fun loadMaterialTextures() {
        GLES20.glGenTextures(3, textureIds, 0)
        GLES20.glGenTextures(3, normalRoughIds, 0)
        val names = arrayOf("textures/plaster", "textures/old_wood", "textures/dirty_stone")
        for (i in names.indices) {
            uploadTexture(textureIds[i], names[i] + ".png")
            // Normal map in RG, roughness in B (no alpha channel, so Android cannot premultiply the data).
            normalRoughLoaded[i] = uploadTexture(normalRoughIds[i], names[i] + "_nr.png")
        }
    }

    private fun uploadTexture(id: Int, path: String): Boolean {
        return try {
            val bitmap = activityAssets?.open(path)?.use { BitmapFactory.decodeStream(it) } ?: return false
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, id)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT)
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
            GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D)
            bitmap.recycle()
            true
        } catch (e: Exception) {
            android.util.Log.e("ShadowHallGL", "Texture load failed: $path", e)
            false
        }
    }

    // Supplied by MainActivity's context without retaining an Activity reference.
    @Volatile var activityAssets: android.content.res.AssetManager? = null

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewportWidth = width.toFloat().coerceAtLeast(1f); viewportHeight = height.toFloat().coerceAtLeast(1f)
        GLES20.glViewport(0,0,width,height)
        Matrix.perspectiveM(projection,0,62f,width.toFloat()/height.coerceAtLeast(1),0.1f,60f)
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime(); val dt = ((now-lastTime)/1e9f).coerceIn(0f,0.04f); lastTime=now
        if (!gameOver) update(dt)
        val doorStep = (dt * 2.6f).coerceAtMost(1f)
        bedroomDoorAmount += ((if (bedroomDoorOpen) 1f else 0f) - bedroomDoorAmount) * doorStep
        kitchenDoorAmount += ((if (kitchenDoorOpen) 1f else 0f) - kitchenDoorAmount) * doorStep
        storageDoorAmount += ((if (storageDoorOpen) 1f else 0f) - storageDoorAmount) * doorStep
        sideRoomDoorAmount += ((if (sideRoomDoorOpen) 1f else 0f) - sideRoomDoorAmount) * doorStep
        // Flashlight battery makes light management part of the survival loop.
        if (flashlightOn) {
            flashlightBattery = (flashlightBattery - 2.2f * dt).coerceAtLeast(0f)
            if (flashlightBattery <= 0f) flashlightOn = false
        } else {
            flashlightBattery = (flashlightBattery + 1.25f * dt).coerceAtMost(100f)
        }
        // Danger feedback: the scene subtly pulses red as the monster closes in.
        // This is a cheap clear-color effect, so it does not add draw calls.
        val dangerDx = px - mx
        val dangerDz = pz - mz
        val dangerDistance = sqrt(dangerDx * dangerDx + dangerDz * dangerDz)
        shaderDanger = if (!hidden && !gameWon && !gameOver) ((4.2f - dangerDistance) / 4.2f).coerceIn(0f, 1f) else 0f
        val lowBatteryFlicker = flashlightBattery < 22f && (System.nanoTime() / 85000000L) % 5L == 0L
        val monsterFlicker = dangerDistance < 2.7f && (System.nanoTime() / 115000000L) % 4L == 0L
        flashlightFlickering = lowBatteryFlicker || monsterFlicker
        val dangerPulse = (kotlin.math.sin(System.nanoTime() / 150000000.0).toFloat() + 1f) * 0.5f
        val danger = if (!hidden && !gameWon && !gameOver && dangerDistance < 4.2f)
            ((4.2f - dangerDistance) / 4.2f).coerceIn(0f, 1f) * (0.025f + dangerPulse * 0.035f)
        else 0f
        val fogPulse = if (visualQuality == 0) 0f else (kotlin.math.sin(System.nanoTime()/900000000.0).toFloat()+1f)*0.5f
        val endRed = if (gameOver) 0.12f else 0f
        val endGreen = if (gameWon) 0.08f else 0f
        GLES20.glClearColor(0.035f + danger + endRed + fogPulse*0.004f, 0.045f + endGreen, 0.065f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        val yaw = Math.toRadians(cameraYaw.toDouble())
        // First-person camera, inspired by classic hide-and-escape horror games.
        val lookX = px + kotlin.math.sin(yaw).toFloat()
        val lookZ = pz - kotlin.math.cos(yaw).toFloat()
        val nearDanger = sqrt((px-mx)*(px-mx) + (pz-mz)*(pz-mz))
        val shake = if (!hidden && nearDanger < 2.5f && !gameOver && !gameWon) kotlin.math.sin(System.nanoTime()/36000000.0).toFloat() * ((2.5f-nearDanger)/2.5f) * 0.025f else 0f
        val moveAmt = kotlin.math.abs(moveX) + kotlin.math.abs(moveZ)
        val walking = !hidden && !gameOver && !gameWon && moveAmt > 0.08f
        if (walking) walkPhase += dt * (6.5f + moveAmt * 2.5f)
        val bob = if (walking) kotlin.math.sin(walkPhase) * 0.028f else 0f
        val eyeHeight = bob + if (hidden && hidingPlace == "تحت السرير") 0.48f else if (hidden) 0.92f else 1.48f
        val lookHeight = bob * 0.6f + if (hidden && hidingPlace == "تحت السرير") 0.46f else if (hidden) 0.90f else 1.38f
        Matrix.setLookAtM(view,0,px+shake,eyeHeight,pz-shake,lookX+shake,lookHeight,lookZ-shake,0f,1f,0f)
        Matrix.multiplyMM(vp,0,projection,0,view,0)
        eyeX = px + shake; eyeY = eyeHeight; eyeZ = pz - shake
        // Hand-held flashlight: offset right/down from the eye so objects cast visible shadows.
        val fyawRad = Math.toRadians(cameraYaw.toDouble())
        val fsn = kotlin.math.sin(fyawRad).toFloat(); val fcs = kotlin.math.cos(fyawRad).toFloat()
        fx = px + fcs * 0.22f + fsn * 0.18f
        fz = pz + fsn * 0.22f - fcs * 0.18f
        fy = (if (hidden) 0.80f else 1.28f) + bob
        val aimX = px + fsn * 5f - fx; val aimY = 0.95f - fy; val aimZ = pz - fcs * 5f - fz
        val aimLen = sqrt(aimX * aimX + aimY * aimY + aimZ * aimZ).coerceAtLeast(0.001f)
        fdx = aimX / aimLen; fdy = aimY / aimLen; fdz = aimZ / aimLen
        renderShadowMap()
        drawScene()
    }

    private fun drawScene() {
        // Multi-room house: central hall plus two side wings, kept low-poly for A23.
        box(0f,-0.22f,0f,8f,0.35f,12f,0.13f,0.15f,0.21f)
        box(-6f,-0.22f,0f,4.2f,0.35f,12f,0.12f,0.14f,0.19f)
        box(6f,-0.22f,0f,4.2f,0.35f,12f,0.12f,0.14f,0.19f)
        for (z in -5..5 step 2) box(0f,-0.035f,z.toFloat(),7.7f,0.012f,0.025f,0.22f,0.24f,0.30f)
        for (x in listOf(-6f, 6f)) for (z in -5..5 step 2) box(x,-0.035f,z.toFloat(),3.8f,0.012f,0.025f,0.18f,0.20f,0.26f)
        for (z in -4..4 step 4) {
            box(0f,2.18f,z.toFloat(),1.0f,0.04f,0.35f,0.52f,0.38f,0.16f)
            box(-1.8f,2.18f,z.toFloat(),0.16f,0.035f,0.3f,0.25f,0.38f,0.48f)
            box(1.8f,2.18f,z.toFloat(),0.16f,0.035f,0.3f,0.25f,0.38f,0.48f)
        }
        // Outer walls of the expanded house.
        box(-8.1f,1f,0f,0.3f,2.4f,12.5f,0.20f,0.22f,0.30f)
        box(8.1f,1f,0f,0.3f,2.4f,12.5f,0.20f,0.22f,0.30f)
        // Keep the escape doorway clear in the rear wall.
        box(-4.6f,1f,-6.25f,7.0f,2.4f,0.3f,0.16f,0.18f,0.25f)
        box(4.6f,1f,-6.25f,7.0f,2.4f,0.3f,0.16f,0.18f,0.25f)
        box(0f,1f,6.25f,16.2f,2.4f,0.3f,0.16f,0.18f,0.25f)
        // Side-hall partitions leave doorways at z=-2 and z=2.
        for (x in listOf(-4f, 4f)) {
            box(x,1f,-4.35f,0.18f,2.4f,3.8f,0.20f,0.22f,0.30f)
            box(x,1f,0f,0.18f,2.4f,2.0f,0.20f,0.22f,0.30f)
            box(x,1f,4.35f,0.18f,2.4f,3.8f,0.20f,0.22f,0.30f)
        }
        // Three named rooms: bedroom, kitchen and storage. Door panels slide aside when opened.
        box(-6.1f,0.72f,-3.9f,1.25f,1.35f,0.72f,0.22f,0.13f,0.12f)
        box(-6.1f,1.42f,-3.9f,1.28f,0.08f,0.74f,0.40f,0.25f,0.19f)
        box(-6.1f,0.20f,-3.9f,1.30f,0.35f,0.76f,0.15f,0.10f,0.10f)
        box(6.0f,0.48f,-3.9f,1.15f,0.92f,0.65f,0.26f,0.29f,0.31f)
        box(6.0f,0.98f,-3.9f,1.18f,0.08f,0.68f,0.48f,0.43f,0.30f)
        box(6.0f,0.30f,3.8f,1.05f,0.55f,0.72f,0.28f,0.23f,0.15f)
        box(6.0f,0.62f,3.8f,0.82f,0.08f,0.54f,0.42f,0.34f,0.21f)
        // Internal door between the right-side rooms.
        box(6.0f + sideRoomDoorAmount*1.35f,1f,0f,1.45f,2.0f,0.12f,0.27f+sideRoomDoorAmount*0.07f,0.19f+sideRoomDoorAmount*0.23f,0.12f)
        // Four doorway panels slide progressively, with eased motion instead of instant teleporting.
        box(-4.0f,1f,-2f + bedroomDoorAmount*1.95f,0.12f,1.95f,1.35f,0.30f,0.18f,0.12f)
        box(4.0f,1f,-2f + kitchenDoorAmount*2.05f,0.12f,1.95f,1.35f,0.22f,0.30f,0.24f)
        box(-4.0f,1f,2f + storageDoorAmount*1.95f,0.12f,1.95f,1.35f,0.28f,0.20f,0.13f)
        box(4.0f,1f,2f + sideRoomDoorAmount*1.95f,0.12f,1.95f,1.35f,0.25f,0.22f,0.15f)
        // High-quality architectural trim: layered skirting, door mouldings and wall panels.
        if (visualQuality >= 2) {
            for (x in listOf(-3.75f, 3.75f)) {
                box(x,0.16f,-4.8f,0.055f,0.22f,2.2f,0.30f,0.17f,0.10f)
                box(x,0.16f,4.8f,0.055f,0.22f,2.2f,0.30f,0.17f,0.10f)
                for (z in listOf(-4f,-2f,0f,2f,4f)) {
                    box(x,0.98f,z,0.045f,1.25f,0.72f,0.16f,0.12f,0.10f)
                    box(x- if (x<0f) 0.012f else -0.012f,0.98f,z,0.012f,1.12f,0.60f,0.23f,0.17f,0.12f)
                }
            }
            for (x in listOf(-6f,6f)) {
                box(x,0.15f,-5.4f,2.8f,0.20f,0.045f,0.31f,0.18f,0.10f)
                box(x,0.15f,5.4f,2.8f,0.20f,0.045f,0.31f,0.18f,0.10f)
            }
            // Ceiling beams and recessed lamp housings create stronger depth cues.
            for (z in listOf(-4f,0f,4f)) {
                box(-2.9f,2.23f,z,0.10f,0.10f,2.0f,0.20f,0.13f,0.09f)
                box(2.9f,2.23f,z,0.10f,0.10f,2.0f,0.20f,0.13f,0.09f)
                box(0f,2.31f,z,0.58f,0.045f,0.42f,0.20f,0.18f,0.13f)
                box(0f,2.27f,z,0.40f,0.018f,0.26f,0.78f,0.62f,0.34f)
            }
        }
        // Ultra preset: extra layered architectural details; intentionally prioritizes
        // visual density over performance, as requested.
        if (visualQuality >= 3) {
            for (x in listOf(-3.72f, 3.72f)) {
                for (z in listOf(-5.1f, -3.0f, -1.0f, 1.0f, 3.0f, 5.1f)) {
                    box(x, 1.02f, z, 0.025f, 1.72f, 0.025f, 0.38f, 0.25f, 0.15f)
                    box(x, 0.18f, z, 0.09f, 0.035f, 0.10f, 0.48f, 0.32f, 0.18f)
                }
            }
            // Repeated inset moulding catches the flashlight and breaks up flat walls.
            for (x in listOf(-3.78f, 3.78f)) for (z in listOf(-3.0f, -1.0f, 1.0f, 3.0f)) {
                box(x, 0.42f, z, 0.022f, 0.035f, 0.62f, 0.31f, 0.19f, 0.12f)
                box(x, 1.48f, z, 0.022f, 0.035f, 0.62f, 0.31f, 0.19f, 0.12f)
            }
            // Ceiling panel borders and additional dark seams increase perceived depth.
            for (z in listOf(-4f, 0f, 4f)) {
                box(-1.85f, 2.25f, z, 0.025f, 0.025f, 1.65f, 0.12f, 0.10f, 0.09f)
                box(1.85f, 2.25f, z, 0.025f, 0.025f, 1.65f, 0.12f, 0.10f, 0.09f)
            }
        }
        // Shared flicker value for wall sconces and ceiling fixtures in this render pass.
        val flicker = if ((System.nanoTime() / 100000000L) % 19L == 0L) 0.12f else 1f
        // Alpha 24: environment art pass — layered door casings, handles, wall frames,
        // aged sconces, book stacks, table details and small structural depth cues.
        if (visualQuality >= 2) {
            // Door frames and brass handles for all four side-room doors.
            for (x in listOf(-4f, 4f)) for (z in listOf(-2f, 2f)) {
                // Doorway width lies along the Z axis because the partition wall runs on X.
                box(x,1.02f,z-0.78f,0.14f,2.10f,0.075f,0.34f,0.20f,0.10f)
                box(x,1.02f,z+0.78f,0.14f,2.10f,0.075f,0.34f,0.20f,0.10f)
                box(x,2.04f,z,0.14f,0.09f,1.62f,0.42f,0.27f,0.13f)
                val doorOpen = if (x < 0f && z < 0f) bedroomDoorOpen else if (x > 0f && z < 0f) kitchenDoorOpen else if (x < 0f) storageDoorOpen else sideRoomDoorOpen
                if (!doorOpen) {
                    val handleZ = z + if (x < 0f) 0.37f else -0.37f
                    box(x+0.075f,0.98f,handleZ,0.035f,0.13f,0.045f,0.72f,0.49f,0.18f)
                    box(x+0.10f,0.98f,handleZ,0.025f,0.035f,0.025f,0.88f,0.72f,0.35f)
                }
            }
            // Picture frames and dark, faded canvases on the side walls.
            for (z in listOf(-3.9f,0.0f,3.9f)) {
                box(-3.79f,1.52f,z,0.055f,0.62f,0.82f,0.36f,0.23f,0.12f)
                box(-3.755f,1.52f,z,0.018f,0.49f,0.67f,0.075f,0.085f,0.095f)
                box(3.79f,1.52f,z,0.055f,0.62f,0.82f,0.36f,0.23f,0.12f)
                box(3.755f,1.52f,z,0.018f,0.49f,0.67f,0.085f,0.075f,0.07f)
            }
            // Wall sconces with metal backplates and warm, flickering bulbs.
            for (x in listOf(-3.72f,3.72f)) for (z in listOf(-4.25f,-0.15f,3.95f)) {
                box(x,1.72f,z,0.07f,0.25f,0.12f,0.22f,0.20f,0.16f)
                box(x*0.99f,1.84f,z,0.12f,0.10f,0.15f,0.82f*flicker,0.52f*flicker,0.22f*flicker)
                box(x*0.99f,1.65f,z,0.18f,0.035f,0.20f,0.12f,0.10f,0.09f)
            }
            // Books and clutter on the central table: varied widths and faded covers.
            for (i in 0..4) {
                val bx = -0.52f + i*0.24f
                box(bx,1.31f,-3.8f,0.19f,0.035f,0.28f,0.24f + i*0.035f,0.08f + i*0.018f,0.055f)
                box(bx,1.35f,-3.8f,0.17f,0.012f,0.25f,0.58f,0.43f,0.27f)
            }
            // Cabinet inset panels and old iron hinge strips.
            for (side in listOf(-1f,1f)) {
                box(-2.8f+side*0.28f,0.66f,-0.78f,0.018f,0.22f,0.025f,0.66f,0.43f,0.20f)
                box(-2.8f+side*0.28f,0.31f,-0.78f,0.018f,0.22f,0.025f,0.66f,0.43f,0.20f)
            }
            // More varied ceiling ribs and baseboards create real silhouette breaks.
            for (z in listOf(-5f,-3f,-1f,1f,3f,5f)) {
                box(-7.8f,1.1f,z,0.10f,2.15f,0.08f,0.15f,0.16f,0.20f)
                box(7.8f,1.1f,z,0.10f,2.15f,0.08f,0.15f,0.16f,0.20f)
            }
        }
        // Alpha 24: fake contact shadows/AO under major props to ground them in the room.
        // These are dark flattened meshes, not physically-cast shadows.
        if (visualQuality >= 1) {
            box(-2.8f,0.012f,-0.4f,1.20f,0.012f,1.34f,0.055f,0.045f,0.045f)
            box(2.5f,0.012f,2.0f,1.38f,0.012f,0.95f,0.045f,0.045f,0.055f)
            box(0f,0.012f,-3.8f,1.68f,0.012f,0.88f,0.06f,0.05f,0.04f)
            box(6.25f,0.012f,4.65f,1.0f,0.012f,0.54f,0.045f,0.05f,0.05f)
        }
        // Atmospheric pass: wall panels, ceiling fixtures and unsettling stains.
        // These simple meshes keep rendering inexpensive on mid-range Android phones.
        if (visualQuality >= 1) for (z in -4..4 step 2) {
            box(-3.82f,1.05f,z.toFloat(),0.025f,1.55f,0.72f,0.10f,0.12f,0.17f)
            box(3.82f,1.05f,z.toFloat(),0.025f,1.55f,0.72f,0.10f,0.12f,0.17f)
            box(-3.79f,1.78f,z.toFloat(),0.035f,0.045f,0.66f,0.32f,0.12f,0.10f)
            box(3.79f,1.78f,z.toFloat(),0.035f,0.045f,0.66f,0.32f,0.12f,0.10f)
        }
        for (z in -4..4 step 4) {
            box(0f,2.27f,z.toFloat(),0.72f,0.035f,0.24f,0.72f*flicker,0.68f*flicker,0.42f*flicker)
            box(0f,2.22f,z.toFloat(),0.92f,0.035f,0.34f,0.12f,0.13f,0.18f)
        }
        box(-1.45f,0.005f,1.35f,0.62f,0.012f,0.34f,0.12f,0.045f,0.055f)
        box(1.75f,0.006f,-2.7f,0.38f,0.013f,0.52f,0.09f,0.035f,0.045f)
        box(-3.78f,1.10f,-2.0f,0.018f,0.52f,0.30f,0.18f,0.035f,0.045f)
        // Exposed ceiling pipes and utility conduits add depth to the abandoned building.
        if (visualQuality >= 2) for (z in -4..4 step 2) {
            box(-2.65f,2.05f,z.toFloat(),0.055f,0.055f,1.05f,0.20f,0.22f,0.24f)
            box(2.65f,2.05f,z.toFloat(),0.055f,0.055f,1.05f,0.20f,0.22f,0.24f)
            box(-2.65f,2.00f,z.toFloat(),0.09f,0.025f,0.08f,0.31f,0.16f,0.10f)
            box(2.65f,2.00f,z.toFloat(),0.09f,0.025f,0.08f,0.31f,0.16f,0.10f)
        }
        // A damaged wall notice and rusty utility panel provide visual landmarks.
        box(3.78f,1.20f,0.25f,0.025f,0.48f,0.38f,0.30f,0.24f,0.16f)
        box(3.75f,1.20f,0.25f,0.025f,0.32f,0.25f,0.10f,0.12f,0.14f)
        box(3.72f,1.28f,0.17f,0.025f,0.035f,0.035f,0.65f,0.08f,0.06f)
        box(-3.78f,0.82f,2.4f,0.025f,0.25f,0.50f,0.11f,0.13f,0.15f)
        box(-3.75f,0.82f,2.4f,0.025f,0.15f,0.38f,0.34f,0.20f,0.10f)
        // cartoon furniture that can provide visual cover
        box(-2.8f,0.45f,-0.4f,1.0f,0.9f,1.2f,0.28f,0.12f,0.10f)
        box(2.5f,0.6f,2.0f,1.2f,1.2f,0.8f,0.18f,0.20f,0.27f)
        box(0f,0.5f,-3.8f,1.5f,1.0f,0.7f,0.25f,0.18f,0.11f)
        // Cabinet details, bed-like obstacle and scattered floor clutter.
        box(-2.8f,0.48f,-1.02f,0.68f,0.05f,0.025f,0.52f,0.30f,0.14f)
        // Searchable cabinet drawer; it slides outward after interaction.
        box(-2.8f,0.27f,if(drawerOpen) -1.30f else -0.94f,0.58f,0.20f,0.10f,0.36f,0.19f,0.09f)
        box(-2.8f,0.27f,if(drawerOpen) -1.355f else -0.995f,0.12f,0.035f,0.025f,0.72f,0.54f,0.28f)
        if(drawerOpen && !hasFuse) box(-2.8f,0.42f,-1.30f,0.16f,0.10f,0.12f,0.18f,0.88f,0.72f)
        box(-3.12f,0.50f,-0.4f,0.06f,0.74f,0.06f,0.42f,0.23f,0.11f)
        box(-2.48f,0.50f,-0.4f,0.06f,0.74f,0.06f,0.42f,0.23f,0.11f)
        box(2.5f,1.22f,2.0f,0.92f,0.08f,0.56f,0.35f,0.36f,0.43f)
        box(2.5f,0.18f,2.0f,1.0f,0.35f,0.62f,0.16f,0.13f,0.12f)
        box(1.0f,0.015f,0.8f,0.45f,0.025f,0.22f,0.32f,0.25f,0.18f)
        box(-1.1f,0.015f,-2.0f,0.28f,0.025f,0.35f,0.26f,0.27f,0.30f)
        // Alpha 11: additional room landmarks and puzzle props (decorative geometry, no extra textures).
        // Bathroom sink and cracked mirror in the far-right wing.
        box(6.25f,0.62f,4.65f,0.92f,0.16f,0.42f,0.42f,0.43f,0.40f)
        box(6.25f,0.88f,4.65f,0.72f,0.06f,0.30f,0.60f,0.63f,0.60f)
        box(6.25f,1.42f,4.88f,0.58f,0.58f,0.045f,0.16f,0.21f,0.25f)
        box(6.25f,1.42f,4.85f,0.42f,0.42f,0.025f,0.28f,0.34f,0.38f)
        // Wall-mounted fuse indicator: green when the fuse is installed, red otherwise.
        box(-3.72f,1.42f,-0.85f,0.035f,0.42f,0.30f,0.15f,0.14f,0.12f)
        box(-3.69f,1.48f,-0.85f,0.025f,0.09f,0.09f,if(hasFuse) 0.10f else 0.85f,if(hasFuse) 0.85f else 0.08f,0.05f)
        // Broken floorboards and warning stripes add readable landmarks without heavy effects.
        for (i in 0..3) box(-5.9f+i*0.34f,0.006f,1.55f,0.22f,0.012f,0.035f,0.20f,0.12f,0.09f)
        box(5.35f,0.012f,1.15f,0.75f,0.018f,0.045f,0.48f,0.24f,0.06f)
        box(5.35f,0.014f,1.25f,0.75f,0.018f,0.045f,0.12f,0.10f,0.08f)
        // Alpha 26 Ultra realism pass: dense environmental storytelling props.
        // These intentionally add geometry and draw calls instead of prioritising FPS.
        if (visualQuality >= 3) {
            // Old radiator and rusted pipework in the left wing.
            box(-7.55f,0.58f,-0.2f,0.12f,0.70f,1.25f,0.24f,0.22f,0.19f)
            for (i in 0..7) box(-7.42f+i*0.035f,0.58f,-0.2f,0.018f,0.58f,1.10f,0.34f,0.31f,0.26f)
            box(-7.25f,1.00f,-0.2f,0.035f,0.045f,1.35f,0.42f,0.29f,0.17f)
            // Broken timber planks, nail heads and debris scattered along the corridor.
            for (i in 0..8) {
                val x = -3.2f + i*0.79f
                val z = if (i%2==0) 1.0f else -1.35f
                box(x,0.012f,z,0.36f,0.018f,0.10f,0.19f+(i%3)*0.025f,0.105f,0.065f)
                box(x-0.12f,0.026f,z-0.025f,0.018f,0.008f,0.018f,0.42f,0.39f,0.32f)
            }
            // A toppled chair, cracked table, scattered papers and a fallen picture frame.
            box(5.15f,0.18f,-4.35f,0.70f,0.10f,0.48f,0.20f,0.13f,0.09f)
            for (side in listOf(-1f,1f)) {
                box(5.15f+side*0.28f,0.34f,-4.35f,0.055f,0.34f,0.055f,0.17f,0.105f,0.065f)
                box(5.15f+side*0.28f,0.13f,-4.35f,0.045f,0.12f,0.045f,0.15f,0.09f,0.06f)
            }
            box(5.75f,0.035f,-3.1f,0.62f,0.025f,0.40f,0.55f,0.48f,0.37f)
            box(5.75f,0.042f,-3.1f,0.54f,0.008f,0.32f,0.16f,0.12f,0.10f)
            // Leaking ceiling pipe with brackets and a dark water-stain trail.
            box(6.95f,2.05f,1.4f,0.065f,0.065f,3.6f,0.19f,0.23f,0.23f)
            for (z in listOf(0.0f,1.4f,2.8f)) box(6.95f,2.0f,z,0.15f,0.035f,0.08f,0.34f,0.25f,0.16f)
            for (i in 0..5) box(7.72f,1.62f+i*0.07f,1.3f,0.012f,0.04f,0.26f-i*0.025f,0.12f,0.14f,0.13f)
            // More moulding, chipped plaster patches and exposed brick around doorways.
            for (z in listOf(-2.7f,-2.45f,-2.2f,1.7f,1.95f,2.2f)) {
                box(-3.77f,0.35f,z,0.022f,0.10f,0.10f,0.34f,0.19f,0.12f)
                box(3.77f,1.68f,z,0.022f,0.13f,0.12f,0.26f,0.16f,0.11f)
            }
            // Dust motes drifting in the flashlight beam (small, animated floating particles).
            val dustTime = (System.nanoTime()/1.0e9).toFloat()
            for (i in 0..23) {
                val dx = kotlin.math.sin(dustTime*0.21f+i*12.31f)*3.1f
                val dy = 0.35f + ((i*37)%165)*0.01f + kotlin.math.sin(dustTime*0.7f+i)*0.04f
                val dz = -4.8f + ((i*19)%95)*0.10f
                val size = if (i%4==0) 0.018f else 0.009f
                box(dx,dy,dz,size,size,size,0.38f,0.34f,0.25f)
            }
            // Extra door hardware: hinge plates, bolts and worn kick plates.
            for (x in listOf(-4.0f,4.0f)) for (z in listOf(-2.0f,2.0f)) {
                for (y in listOf(0.24f,1.0f,1.78f)) {
                    box(x+if(x<0f) 0.095f else -0.095f,y,z-0.56f,0.025f,0.13f,0.08f,0.31f,0.27f,0.20f)
                    box(x+if(x<0f) 0.112f else -0.112f,y,z-0.56f,0.012f,0.018f,0.012f,0.65f,0.57f,0.38f)
                }
                box(x,0.16f,z,0.035f,0.18f,0.62f,0.32f,0.20f,0.12f)
            }
        }
        // The player is not drawn because this version uses a first-person camera.
        // Monster: expanded silhouette with a head, neck, chest, limbs, jaw and claws.
        // Its face rotates toward its direction of travel/player instead of remaining backward-facing.
        val monsterClock = (System.nanoTime()/1.0e9).toFloat()
        val monsterBob = kotlin.math.sin(monsterClock*5.2f)*0.035f
        val monsterStride = kotlin.math.sin(monsterClock*(if (alertTimer > 0f) 11f else 4f)) * (if (alertTimer > 0f) 0.105f else 0.035f)
        val monsterLean = if (alertTimer > 0f) 0.055f else 0f
        val faceDx = px - mx
        val faceDz = pz - mz
        monsterFacing = kotlin.math.atan2(faceDx.toDouble(), faceDz.toDouble()).toFloat()
        // Main anatomy uses smooth ellipsoids instead of stacked cubes for a less blocky silhouette.
        monsterEllipsoid(0f,0.68f+monsterBob,monsterLean,0.34f,0.49f,0.25f,0.15f,0.012f,0.030f)
        monsterEllipsoid(0f,1.00f+monsterBob,0.015f+monsterLean,0.43f,0.31f,0.25f,0.20f,0.018f,0.040f)
        monsterEllipsoid(0f,1.25f+monsterBob,0.01f,0.15f,0.17f,0.15f,0.12f,0.012f,0.028f)
        monsterEllipsoid(0f,1.48f+monsterBob,0.025f,0.24f,0.27f,0.22f,0.075f,0.009f,0.022f)
        monsterBox(0f,1.32f+monsterBob,0.24f,0.27f,0.13f,0.17f,0.035f,0.006f,0.012f)
        monsterBox(-0.115f,1.49f+monsterBob,0.225f,0.105f,0.09f,0.045f,0.025f,0.003f,0.008f)
        monsterBox(0.115f,1.49f+monsterBob,0.225f,0.105f,0.09f,0.045f,0.025f,0.003f,0.008f)
        monsterBox(-0.115f,1.49f+monsterBob,0.258f,0.047f,0.035f,0.025f,1f,0.015f,0.006f)
        monsterBox(0.115f,1.49f+monsterBob,0.258f,0.047f,0.035f,0.025f,1f,0.015f,0.006f)
        monsterBox(-0.13f,1.57f+monsterBob,0.22f,0.15f,0.055f,0.07f,0.055f,0.008f,0.018f)
        monsterBox(0.13f,1.57f+monsterBob,0.22f,0.15f,0.055f,0.07f,0.055f,0.008f,0.018f)
        // Uneven teeth and a split lower jaw help break up the block silhouette.
        for (tooth in 0..4) monsterBox(-0.105f+tooth*0.052f,1.285f+monsterBob,0.315f,0.018f,0.055f,0.018f,0.72f,0.67f,0.53f)
        monsterEllipsoid(-0.29f-monsterStride,0.98f+monsterBob,0.02f+monsterStride*0.45f,0.12f,0.24f,0.13f,0.22f,0.025f,0.05f)
        monsterEllipsoid(0.29f+monsterStride,0.98f+monsterBob,0.02f-monsterStride*0.45f,0.12f,0.24f,0.13f,0.22f,0.025f,0.05f)
        monsterEllipsoid(-0.22f,0.69f+monsterBob+monsterStride*0.18f,monsterStride,0.13f,0.40f,0.14f,0.11f,0.010f,0.025f)
        monsterEllipsoid(0.22f,0.69f+monsterBob-monsterStride*0.18f,-monsterStride,0.13f,0.40f,0.14f,0.11f,0.010f,0.025f)
        monsterEllipsoid(-0.13f,0.20f+monsterBob-monsterStride*0.35f,0.015f+monsterStride,0.13f,0.23f,0.14f,0.085f,0.010f,0.022f)
        monsterEllipsoid(0.13f,0.20f+monsterBob+monsterStride*0.35f,0.015f-monsterStride,0.13f,0.23f,0.14f,0.085f,0.010f,0.022f)
        // Rib-like raised details and long fingers give the silhouette more depth.
        for (rib in 0..2) monsterBox(0f,0.72f+monsterBob+rib*0.13f,0.218f,0.40f-rib*0.035f,0.035f,0.025f,0.35f,0.035f,0.07f)
        for (side in listOf(-1f,1f)) for (finger in 0..2) {
            monsterBox(side*0.31f+finger*0.045f,0.30f+monsterBob,-0.01f+finger*0.025f,0.035f,0.22f,0.04f,0.075f,0.006f,0.02f)
            monsterBox(side*0.31f+finger*0.045f,0.185f+monsterBob,-0.035f+finger*0.025f,0.022f,0.07f,0.025f,0.46f,0.39f,0.32f)
        }
        // Thin raised scars and collarbone ridges add small-scale silhouette detail.
        for (i in 0..3) monsterBox(-0.19f+i*0.11f,0.88f+monsterBob,0.237f,0.018f,0.12f,0.018f,0.34f,0.045f,0.065f)
        monsterBox(-0.08f,1.40f+monsterBob,0.208f,0.025f,0.10f,0.018f,0.27f,0.035f,0.055f)
        monsterBox(0.08f,1.40f+monsterBob,0.208f,0.025f,0.10f,0.018f,0.27f,0.035f,0.055f)
        // Ultra creature detail: torn clothing seams, cheek wounds, dark eye sockets and uneven skin.
        monsterBox(-0.075f,1.51f+monsterBob,0.265f,0.062f,0.035f,0.018f,0.018f,0.006f,0.009f)
        monsterBox(0.075f,1.51f+monsterBob,0.265f,0.062f,0.035f,0.018f,0.018f,0.006f,0.009f)
        monsterBox(-0.19f,1.43f+monsterBob,0.238f,0.025f,0.085f,0.018f,0.31f,0.045f,0.055f)
        monsterBox(0.19f,1.43f+monsterBob,0.238f,0.025f,0.085f,0.018f,0.31f,0.045f,0.055f)
        // Torn cloth strips hanging from the shoulders and waist move subtly with the stride.
        monsterBox(-0.30f-monsterStride*0.4f,0.76f+monsterBob,0.12f,0.055f,0.30f,0.035f,0.075f,0.012f,0.022f)
        monsterBox(0.30f+monsterStride*0.4f,0.73f+monsterBob,-0.02f,0.045f,0.26f,0.035f,0.065f,0.010f,0.018f)
        for (i in 0..4) monsterBox(-0.22f+i*0.105f,0.54f+monsterBob,0.205f,0.014f,0.16f,0.012f,0.20f,0.018f,0.032f)
        // A spare battery pack is a small optional resource placed in the side corridor.
        if (!batteryPackCollected) {
            val batteryBob = 0.24f + kotlin.math.sin((System.nanoTime()/220000000.0).toFloat())*0.035f
            box(1.15f,batteryBob,-1.85f,0.28f,0.34f,0.18f,0.18f,0.72f,0.34f)
            box(1.15f,batteryBob+0.19f,-1.85f,0.12f,0.055f,0.10f,0.72f,0.82f,0.70f)
            box(1.15f,batteryBob,-1.745f,0.055f,0.18f,0.018f,0.78f,0.95f,0.72f)
        }
        for (i in keys.indices) if (!collected[i]) {
            val bob=0.28f+kotlin.math.sin((System.nanoTime()/250000000.0+i).toFloat())*0.04f
            box(keys[i][0],bob,keys[i][1],0.22f,0.22f,0.22f,1f,0.72f,0.08f)
        }
        // Exit gate and its frame; the door only opens after interaction with all keys.
        box(-1.0f,1f,-5.72f,0.18f,2.15f,0.22f,0.30f,0.22f,0.13f)
        box(1.0f,1f,-5.72f,0.18f,2.15f,0.22f,0.30f,0.22f,0.13f)
        box(0f,2.05f,-5.72f,2.15f,0.18f,0.22f,0.30f,0.22f,0.13f)
        // Alpha 28: added environmental realism — wall sconces, damp streaks, hanging chains and floor grime.
        if (visualQuality >= 3) {
            // Wall-mounted old brass lamps with a warm inner core; shader flicker animates their surfaces.
            for (side in listOf(-1f, 1f)) for (z in listOf(-4.1f, -0.6f, 3.0f)) {
                box(side * 3.70f, 1.82f, z, 0.075f, 0.20f, 0.12f, 0.24f, 0.17f, 0.095f)
                box(side * 3.64f, 1.84f, z, 0.035f, 0.115f, 0.075f, 0.62f, 0.34f, 0.12f)
                box(side * 3.60f, 1.84f, z, 0.018f, 0.065f, 0.040f, 0.92f, 0.58f, 0.25f)
            }
            // Damp plaster streaks and irregular grime at the base of the walls.
            for (side in listOf(-1f, 1f)) for (i in 0..11) {
                val z = -5.0f + i * 0.82f
                val h = 0.18f + (i % 4) * 0.09f
                box(side * 3.748f, 0.20f + h * 0.5f, z, 0.009f, h, 0.035f + (i % 3) * 0.018f, 0.105f, 0.105f, 0.090f)
            }
            // Suspended chains with alternating links, deliberately dense geometry for close-up flashlight views.
            for (chain in 0..2) {
                val cx = if (chain % 2 == 0) -2.9f else 2.9f
                val cz = -3.2f + chain * 2.4f
                for (link in 0..11) {
                    val sway = kotlin.math.sin((System.nanoTime() / 1.0e9).toFloat() * 1.1f + link * 0.17f + chain) * 0.035f
                    box(cx + sway + (if (link % 2 == 0) 0.018f else -0.018f), 2.12f - link * 0.13f, cz, 0.025f, 0.075f, 0.022f, 0.20f, 0.19f, 0.16f)
                }
            }
            // Dirt patches and worn tile seams on the corridor floor.
            for (i in 0..17) {
                val x = -3.2f + (i % 9) * 0.78f
                val z = -4.6f + (i / 9) * 1.25f + (i % 2) * 0.22f
                box(x, 0.008f, z, 0.18f + (i % 3) * 0.07f, 0.009f, 0.10f + (i % 4) * 0.025f, 0.095f, 0.088f, 0.078f)
            }
        }
        // Alpha 27: ultra-detail pass — cracked plaster, warped floor seams, hanging chains,
        // water damage and extra door hardware. Geometry is deliberately dense for visual quality.
        if (visualQuality >= 3) {
            // Jagged plaster cracks on the side walls, built from short angled-looking segments.
            for (side in listOf(-1f, 1f)) {
                for (i in 0..5) {
                    val z0 = -4.4f + i * 1.55f
                    val y0 = 0.92f + (i % 3) * 0.27f
                    box(side * 3.765f, y0, z0, 0.018f, 0.19f, 0.025f, 0.075f, 0.068f, 0.061f)
                    box(side * 3.764f, y0 - 0.11f, z0 + 0.055f, 0.019f, 0.13f, 0.018f, 0.055f, 0.050f, 0.047f)
                    box(side * 3.763f, y0 - 0.20f, z0 + 0.085f, 0.020f, 0.035f, 0.07f, 0.065f, 0.056f, 0.051f)
                }
                // Damp tide marks and irregular dark patches near the floor.
                for (i in 0..7) {
                    val z = -5.0f + i * 1.32f
                    box(side * 3.79f, 0.34f + (i % 2) * 0.08f, z, 0.012f, 0.18f + (i % 3) * 0.04f, 0.31f, 0.075f, 0.073f, 0.069f)
                }
            }
            // Warped floorboard seams, chipped board ends and scattered masonry.
            for (i in 0..13) {
                val x = -7.1f + i * 1.08f
                box(x, 0.009f, -0.6f, 0.014f, 0.012f, 8.9f, 0.105f, 0.075f, 0.052f)
                box(x + 0.23f, 0.018f, 1.1f + (i % 4) * 0.58f, 0.18f, 0.022f, 0.055f, 0.24f, 0.19f, 0.15f)
            }
            for (i in 0..17) {
                val x = -7.0f + (i * 1.71f % 14.0f)
                val z = -5.0f + (i * 2.13f % 10.0f)
                box(x, 0.025f, z, 0.045f + (i % 3) * 0.018f, 0.025f, 0.035f, 0.26f, 0.24f, 0.20f)
            }
            // Hanging chains under exposed pipes, plus heavy hooks and mounting brackets.
            for (x in listOf(-2.65f, 2.65f)) for (z in listOf(-3.0f, 1.0f, 4.0f)) {
                for (link in 0..6) {
                    val offset = if (link % 2 == 0) 0.025f else -0.025f
                    box(x + offset, 1.88f - link * 0.115f, z, 0.025f, 0.075f, 0.025f, 0.22f, 0.23f, 0.22f)
                }
                box(x, 2.0f, z, 0.13f, 0.045f, 0.11f, 0.30f, 0.24f, 0.16f)
            }
            // Additional aged door hardware: hinges, screw heads and split wood strips.
            for (x in listOf(-4f, 4f)) for (z in listOf(-2f, 2f)) {
                val open = if (x < 0f && z < 0f) bedroomDoorOpen else if (x > 0f && z < 0f) kitchenDoorOpen else if (x < 0f) storageDoorOpen else sideRoomDoorOpen
                if (!open) {
                    for (y in listOf(0.34f, 1.0f, 1.66f)) {
                        box(x + if (x < 0f) 0.07f else -0.07f, y, z + 0.53f, 0.025f, 0.15f, 0.035f, 0.20f, 0.19f, 0.16f)
                        box(x + if (x < 0f) 0.087f else -0.087f, y + 0.035f, z + 0.53f, 0.012f, 0.018f, 0.012f, 0.53f, 0.44f, 0.29f)
                    }
                    box(x + if (x < 0f) 0.071f else -0.071f, 1.25f, z - 0.18f, 0.018f, 0.42f, 0.025f, 0.11f, 0.065f, 0.04f)
                }
            }
            // Broken plaster fragments around the rusty electrical panel.
            for (i in 0..5) {
                box(3.755f, 0.88f + i * 0.105f, 0.48f + (i % 2) * 0.06f, 0.018f, 0.035f, 0.12f, 0.18f, 0.15f, 0.12f)
            }
        }
        // Exit gate: after unlocking, the heavy panel slides aside instead of vanishing.
        val gateX = if (exitDoorOpen) -2.05f else 0f
        if (exitDoorOpen) {
            box(0f,1.05f,-5.57f,1.7f,2.0f,0.06f,0.08f,0.42f,0.18f)
            box(0f,0.08f,-5.48f,1.45f,0.035f,0.035f,0.12f,0.65f,0.25f)
            box(-0.65f,1.25f,-5.48f,0.05f,1.25f,0.035f,0.22f,0.72f,0.32f)
            box(0.65f,1.25f,-5.48f,0.05f,1.25f,0.035f,0.22f,0.72f,0.32f)
        }
        box(gateX,1f,-5.68f,1.8f,1.95f,0.12f,if(keysCount>=3 && hasFuse) 0.12f else 0.24f,if(keysCount>=3 && hasFuse) 0.8f else 0.12f,0.18f)
        // Small status lamps on the gate frame make puzzle progress readable from a distance.
        val gateReady = keysCount >= 3 && hasFuse
        val lampPulse = if ((System.nanoTime()/350000000L)%2L==0L) 1f else 0.45f
        box(-0.78f,1.88f,-5.48f,0.12f,0.10f,0.035f,if(gateReady) 0.08f else lampPulse*0.8f,if(gateReady) 0.9f else 0.06f,0.04f)
        box(0.78f,1.88f,-5.48f,0.12f,0.10f,0.035f,if(gateReady) 0.08f else lampPulse*0.8f,if(gateReady) 0.9f else 0.06f,0.04f)
        // Flashlight illumination is applied in the material shader; no floating floor-bar artifact.
    }

    private fun update(dt: Float) {
        val yaw=Math.toRadians(cameraYaw.toDouble())
        val cy=kotlin.math.cos(yaw).toFloat(); val sy=kotlin.math.sin(yaw).toFloat()
        val moving=kotlin.math.abs(moveX)+kotlin.math.abs(moveZ)>0.08f
        val sprint=moving && stamina>1f && (sprintHeld || kotlin.math.abs(moveX)+kotlin.math.abs(moveZ)>1.25f)
        val speed=if(sprint) 3.25f else 2.3f
        stamina=if(sprint) (stamina-26f*dt).coerceAtLeast(0f) else (stamina+13f*dt).coerceAtMost(100f)
        val noise=if(sprint) 1f else if(moving) 0.45f else 0f
        val dx=(moveX*cy-moveZ*sy)*speed*dt; val dz=(moveZ*cy+moveX*sy)*speed*dt
        if(!hidden && !gameWon) {
            val nx=(px+dx).coerceIn(-7.55f,7.55f); val nz=(pz+dz).coerceIn(-5.72f,5.72f)
            val furnitureBlocked=(nx < -2.0f && nx > -3.55f && nz > -1.25f && nz < 0.45f) || (nx > 1.75f && nx < 3.25f && nz > 1.25f && nz < 2.75f) || (nx > -0.95f && nx < 0.95f && nz > -4.35f && nz < -3.25f)
            // Closed doors stop the player crossing the side-hall wall; open doors connect the rooms.
            val crossesLeftWall = (px > -4f && nx <= -4f) || (px < -4f && nx >= -4f)
            val crossesRightWall = (px < 4f && nx >= 4f) || (px > 4f && nx <= 4f)
            val doorZ = (nz > -2.75f && nz < -1.25f)
            val doorZ2 = (nz > 1.25f && nz < 2.75f)
            val blockedDoor = (crossesLeftWall && ((doorZ && !bedroomDoorOpen) || (doorZ2 && !storageDoorOpen) || (!doorZ && !doorZ2))) ||
                (crossesRightWall && ((doorZ && !kitchenDoorOpen) || (doorZ2 && !sideRoomDoorOpen) || (!doorZ && !doorZ2)))
            val outerFurniture = (nx < -5.25f && nx > -7.2f && nz > -4.8f && nz < -3.0f) || (nx > 5.2f && nx < 6.9f && nz > -4.8f && nz < -3.0f) || (nx > 5.0f && nx < 7.0f && nz > 3.0f && nz < 4.7f)
            if(!furnitureBlocked && !blockedDoor && !outerFurniture) { px=nx; pz=nz }
        }
        if(!hidden && !gameWon) {
            hideDiscoveryTimer = 0f
            val vx=px-mx; val vz=pz-mz; val d=sqrt(vx*vx+vz*vz).coerceAtLeast(0.001f)
            patrolClock+=dt
            openingTimer += dt
            // The monster stays in its own side room during the opening. It only wakes
            // when the player gets close enough or makes a loud sprinting noise.
            val sprintNoise = moving && sprint && d < 8.5f
            if (openingTimer > 7.0f && d < 4.0f) monsterAwakened = true
            if (sprintNoise) monsterAwakened = true
            val walkNoise = monsterAwakened && moving && !sprint && d < 3.4f
            val sightRange = if (visualQuality >= 2) 5.0f else 4.6f
            val spotted = monsterAwakened && d < sightRange && hasClearMonsterSight(mx, mz, px, pz)
            if (spotted || sprintNoise || walkNoise) {
                lastKnownX=px; lastKnownZ=pz
                lastSeenTimer=if(spotted) 2.2f else 0.0f
                alertTimer=if(spotted) 2.8f else if(sprintNoise) 5.0f else 2.5f
                investigateTimer=0f
                searchTargetTimer=0f
                // The monster remembers the direction of the last sound and starts searching nearby corners.
                searchTargetX = (px + if (mx < px) 1.0f else -1.0f).coerceIn(-7.2f, 7.2f)
                searchTargetZ = (pz + if (mz < pz) 0.8f else -0.8f).coerceIn(-5.2f, 5.2f)
            } else {
                lastSeenTimer=(lastSeenTimer-dt).coerceAtLeast(0f)
                alertTimer=(alertTimer-dt).coerceAtLeast(0f)
            }
            if(alertTimer>0f && !hidden && monsterAwakened) {
                monsterState=if(lastSeenTimer>0f) "شافك وبيطاردك!" else "بيجري ناحية آخر صوت سمعه"
                val keyPressure = keysCount * 0.10f
                val chaseSpeed=(if(lastSeenTimer>0f) 1.52f else 1.27f) + keyPressure
                moveMonsterToward(lastKnownX,lastKnownZ,chaseSpeed,dt)
            } else {
                // Search several nearby points around the last known position instead of standing still.
                if(monsterAwakened && investigateTimer<8.0f) {
                    if(searchTargetTimer<=0f) {
                        val phase=searchPhase
                        searchTargetX=(lastKnownX+kotlin.math.cos(phase)*1.55f).coerceIn(-7.0f,7.0f)
                        searchTargetZ=(lastKnownZ+kotlin.math.sin(phase)*1.55f).coerceIn(-5.0f,5.0f)
                        searchPhase+=1.35f
                        searchTargetTimer=1.9f
                    }
                    val sx=searchTargetX-mx; val sz=searchTargetZ-mz
                    val sd=sqrt(sx*sx+sz*sz)
                    if(sd>0.28f) {
                        monsterState="بيفتش حوالين آخر مكان شافك فيه"
                        moveMonsterToward(searchTargetX,searchTargetZ,0.82f + keysCount*0.045f,dt)
                    } else {
                        searchTargetTimer=0f
                        investigateTimer+=dt
                        monsterState="بيفتش في المكان..."
                    }
                    searchTargetTimer-=dt
                } else {
                    // Before awakening, patrol only inside the side room; after awakening,
                    // use a slow perimeter patrol. Timer avoids resetting the target every frame.
                    patrolTargetTimer -= dt
                    if (patrolTargetTimer <= 0f || (kotlin.math.abs(mx-patrolTargetX)<0.45f && kotlin.math.abs(mz-patrolTargetZ)<0.45f)) {
                        if (!monsterAwakened) {
                            val points = arrayOf(floatArrayOf(-6.2f,-4.6f), floatArrayOf(-5.1f,-4.3f), floatArrayOf(-5.3f,-3.1f), floatArrayOf(-6.8f,-3.2f))
                            val point = points[(patrolClock.toInt()/3).mod(points.size)]
                            patrolTargetX = point[0]; patrolTargetZ = point[1]
                        } else {
                            val points = arrayOf(floatArrayOf(-5.5f,-3.7f), floatArrayOf(5.5f,-3.7f), floatArrayOf(5.5f,3.7f), floatArrayOf(-5.5f,3.7f))
                            val point = points[(patrolClock.toInt()/4).mod(points.size)]
                            patrolTargetX = point[0]; patrolTargetZ = point[1]
                        }
                        patrolTargetTimer = 2.8f
                    }
                    monsterState = if (monsterAwakened) "بيفتش ويدور عليك" else "العجوز في الغرفة الجانبية"
                    moveMonsterToward(patrolTargetX,patrolTargetZ,if (monsterAwakened) 0.48f else 0.30f,dt)
                }
            }
            // The monster can push open an interior door when actively chasing or investigating.
            // It only opens a door from the correct side of the partition, preventing random toggling.
            if (alertTimer > 0.15f || investigateTimer < 8.0f) {
                if (kotlin.math.abs(mx + 4f) < 0.48f) {
                    if (mz > -2.8f && mz < -1.2f) bedroomDoorOpen = true
                    if (mz > 1.2f && mz < 2.8f) storageDoorOpen = true
                }
                if (kotlin.math.abs(mx - 4f) < 0.48f) {
                    if (mz > -2.8f && mz < -1.2f) kitchenDoorOpen = true
                    if (mz > 1.2f && mz < 2.8f) sideRoomDoorOpen = true
                }
            }
            val cx=px-mx; val cz=pz-mz
            if(sqrt(cx*cx+cz*cz)<0.72f && !hidden) { gameOver=true; statusText="مسكتك العجوز! اضغط إعادة المحاولة"; monsterState="أمسكت بك" }
                } else if(hidden) {
            monsterState="بيدور عليك"
            // If the monster reaches the hiding area, it lingers and searches before leaving.
            val hx=px-mx; val hz=pz-mz
            if(hx*hx+hz*hz<3.4f) {
                investigateTimer=(investigateTimer+dt).coerceAtMost(6f)
                hideDiscoveryTimer += dt
                mx+=kotlin.math.sin(patrolClock*1.8f)*0.18f*dt
                mz+=kotlin.math.cos(patrolClock*1.6f)*0.18f*dt
                if(hideDiscoveryTimer >= (if (hidingPlace == "خلف الأثاث") 3.5f else 6.0f)) {
                    gameOver=true
                    statusText="العجوز اكتشفت مكان اختبائك! اضغط إعادة المحاولة"
                    monsterState="اكتشفت مخبأك!"
                }
            } else {
                hideDiscoveryTimer = (hideDiscoveryTimer - dt * 1.5f).coerceAtLeast(0f)
                mx+=(lastKnownX-mx)*dt*0.08f
                mz+=(lastKnownZ-mz)*dt*0.08f
            }
        }
        for(i in keys.indices) if(!collected[i]) { val kx=px-keys[i][0]; val kz=pz-keys[i][1]; if(kx*kx+kz*kz<0.55f) collected[i]=true }
        keysCount=collected.count { it }
        if(exitDoorOpen && pz < -5.15f && kotlin.math.abs(px)<1.15f) { gameWon=true; monsterState="نجوت!" }
        val nearHideSpotIndex = hideSpots.indices.firstOrNull { i -> val hx=px-hideSpots[i][0]; val hz=pz-hideSpots[i][1]; hx*hx+hz*hz<1.65f }
        val nearHideSpot = nearHideSpotIndex != null
        val hideLabel = when (nearHideSpotIndex) { 0 -> "تحت السرير"; 1 -> "داخل الدولاب"; 2 -> "خلف الأثاث"; else -> "" }
        val nearCabinet = (px+2.8f)*(px+2.8f)+(pz+0.4f)*(pz+0.4f)<2.2f
        val nearBatteryPack = !batteryPackCollected && (px-1.15f)*(px-1.15f)+(pz+1.85f)*(pz+1.85f)<1.45f
        // Recalculate the monster distance here: dangerDistance is local to the render frame.
        val warningDx = px - mx
        val warningDz = pz - mz
        val warningDistance = sqrt(warningDx * warningDx + warningDz * warningDz)
        val dangerWarning = if (!hidden && !gameWon && !gameOver && warningDistance < 2.8f) "⚠ العجوز قريبة جدًا!" else if (!hidden && !gameWon && !gameOver && warningDistance < 4.2f) "في حركة قريبة..." else ""
        val nearRoomDoor = kotlin.math.abs(kotlin.math.abs(px)-4f)<1.45f && (kotlin.math.abs(pz+2f)<1.45f || kotlin.math.abs(pz-2f)<1.45f)
        val mainStatus = when { gameOver -> "مسكتك العجوز! اضغط إعادة المحاولة"; gameWon -> "نجحت في الهروب!"; hidden -> "مختبئ $hidingPlace — العجوز بتفتش عنك"; nearCabinet && !drawerOpen -> "🗝 درج قريب • اضغط زر التفاعل للبحث"; nearCabinet && drawerOpen && !hasFuse -> "🗝 وجدت قطعة كهربائية! اضغط زر التفاعل لأخذها"; nearBatteryPack -> "🔋 بطارية احتياطية قريبة • اضغط زر التفاعل"; keysCount>=3 && hasFuse && pz < -3.8f -> "🔑 اضغط زر التفاعل لفتح بوابة الهروب"; keysCount>=3 && !hasFuse -> "المفاتيح كاملة! ابحث عن القطعة الكهربائية في الدرج"; keysCount>=3 && hasFuse -> "المفاتيح والقطعة جاهزان — اذهب إلى بوابة الهروب"; nearHideSpot -> "مكان اختباء قريب ($hideLabel) • اضغط اختباء"; nearRoomDoor -> "🔑 باب قريب — اضغط (فتح / تفاعل)"; px < -4.1f -> "غرفة النوم / المخزن • المفاتيح: $keysCount/3"; px > 4.1f -> "المطبخ / الغرفة الداخلية • المفاتيح: $keysCount/3"; else -> "المفاتيح: $keysCount/3 • $monsterState" }
        val batteryLabel = "البطارية: ${flashlightBattery.toInt()}%"
        statusText = listOf(shaderError, mainStatus, dangerWarning, batteryLabel).filter { it.isNotBlank() }.joinToString(" • ")
    }

    fun interact() {
        if (gameOver || gameWon) return
        // Door interaction is proximity-based, so it works with the existing single interact button.
        if (kotlin.math.abs(kotlin.math.abs(px) - 4.0f) < 2.05f && kotlin.math.abs(pz + 2.0f) < 1.85f) {
            if (px < 0f) bedroomDoorOpen = !bedroomDoorOpen else kitchenDoorOpen = !kitchenDoorOpen
            statusText = if (px < 0f) (if (bedroomDoorOpen) "باب غرفة النوم اتفتح" else "باب غرفة النوم اتقفل") else (if (kitchenDoorOpen) "باب المطبخ اتفتح" else "باب المطبخ اتقفل")
            return
        }
        if (kotlin.math.abs(kotlin.math.abs(px) - 4.0f) < 2.05f && kotlin.math.abs(pz - 2.0f) < 1.85f) {
            if (keysCount < 1) { statusText = "الباب مقفول — محتاج مفتاح واحد"; return }
            if (px < 0f) storageDoorOpen = !storageDoorOpen else sideRoomDoorOpen = !sideRoomDoorOpen
            statusText = if (px < 0f) (if (storageDoorOpen) "باب المخزن اتفتح" else "باب المخزن اتقفل") else (if (sideRoomDoorOpen) "باب الغرفة الداخلية اتفتح" else "باب الغرفة الداخلية اتقفل")
            return
        }
        if (kotlin.math.abs(px - 6.0f) < 2.0f && kotlin.math.abs(pz) < 1.8f) {
            if (keysCount < 2) { statusText = "القفل الصدئ محتاج مفتاحين ($keysCount/2)"; return }
            sideRoomDoorOpen = !sideRoomDoorOpen
            statusText = if (sideRoomDoorOpen) "فتحت الباب الداخلي بين الغرف" else "قفلت الباب الداخلي"
            return
        }
        val batteryDistance=(px-1.15f)*(px-1.15f)+(pz+1.85f)*(pz+1.85f)
        if(!batteryPackCollected && batteryDistance<1.45f) {
            batteryPackCollected=true
            flashlightBattery=(flashlightBattery+42f).coerceAtMost(100f)
            statusText="جمعت بطارية احتياطية! شحن الكشاف ${flashlightBattery.toInt()}%"
            return
        }
        val cabinetDistance=(px+2.8f)*(px+2.8f)+(pz+0.4f)*(pz+0.4f)
        if(cabinetDistance<2.2f) {
            if(!drawerOpen) { drawerOpen=true; statusText="فتحت الدرج — ابحث عن الأداة"; return }
            if(!hasFuse) { hasFuse=true; statusText="جمعت القطعة الكهربائية!"; return }
        }
        val nearExit = pz < -3.8f && kotlin.math.abs(px) < 1.6f
        if (!nearExit) { statusText = "اقترب من الباب أو ابحث عن المفاتيح"; return }
        if (keysCount < 3) { statusText = "الباب مقفول — تحتاج 3 مفاتيح ($keysCount/3)"; return }
        if (!hasFuse) { statusText = "لوحة الباب بلا كهرباء — ابحث عن القطعة الكهربائية في الدرج"; return }
        exitDoorOpen = true
        statusText = "انفتح الباب! اركض للخارج بسرعة"
    }

    fun setSprinting(on: Boolean) { sprintHeld = on }
    @Volatile private var sprintHeld = false

    fun toggleHide() {
        if(hidden) { hidden=false; hidingPlace=""; hideDiscoveryTimer=0f; statusText="خرجت من مكان الاختباء"; return }
        val idx=hideSpots.indices.firstOrNull { i -> val dx=px-hideSpots[i][0]; val dz=pz-hideSpots[i][1]; dx*dx+dz*dz<1.65f }
        if(idx != null) {
            hidden=true
            hidingPlace=when(idx) { 0 -> "تحت السرير"; 1 -> "داخل الدولاب"; else -> "خلف الأثاث" }
            statusText="اختبأت $hidingPlace — خليك ساكت!"
        } else statusText="لازم تقرب من السرير أو الدولاب عشان تختبئ"
    }

    fun restartGame() {
        px=0f; pz=3.8f; mx=-6.0f; mz=-4.5f; monsterFacing=0f; monsterAwakened=false; openingTimer=0f; patrolTargetTimer=0f
        for(i in collected.indices) collected[i]=false
        hidden=false; hidingPlace=""; hideDiscoveryTimer=0f; flashlightOn=true; flashlightBattery=100f; stamina=100f; keysCount=0; gameWon=false; exitDoorOpen=false; drawerOpen=false; hasFuse=false; batteryPackCollected=false; bedroomDoorOpen=false; kitchenDoorOpen=false; storageDoorOpen=false; sideRoomDoorOpen=false; gameOver=false; alertTimer=0f; investigateTimer=0f; lastKnownX=0f; lastKnownZ=0f; searchPhase=0f; searchTargetTimer=0f; searchTargetX=0f; searchTargetZ=0f; lastSeenTimer=0f; patrolTargetX=-5.5f; patrolTargetZ=0f; patrolClock=0f; sprintHeld=false
        investigateTimer=8f
        statusText="اجمع المفاتيح والقطعة الكهربائية وابحث عن بطارية احتياطية — الوحش لسه في غرفته الجانبية"
    }

    /** Simple room/door occlusion test so the monster does not visually detect the player through closed partitions. */
    private fun hasClearMonsterSight(ax: Float, az: Float, bx: Float, bz: Float): Boolean {
        val dx = bx - ax
        if (kotlin.math.abs(dx) < 0.0001f) return true
        for (wallX in listOf(-4f, 4f)) {
            val crossed = (ax < wallX && bx >= wallX) || (ax > wallX && bx <= wallX)
            if (!crossed) continue
            val t = (wallX - ax) / dx
            val zAtWall = az + (bz - az) * t
            val lowerDoor = zAtWall > -2.78f && zAtWall < -1.22f
            val upperDoor = zAtWall > 1.22f && zAtWall < 2.78f
            if (!lowerDoor && !upperDoor) return false
            if (wallX < 0f && lowerDoor && !bedroomDoorOpen) return false
            if (wallX < 0f && upperDoor && !storageDoorOpen) return false
            if (wallX > 0f && lowerDoor && !kitchenDoorOpen) return false
            if (wallX > 0f && upperDoor && !sideRoomDoorOpen) return false
        }
        val crossesInternal = (ax < 6f && bx >= 6f) || (ax > 6f && bx <= 6f)
        if (crossesInternal) {
            val t = (6f - ax) / dx
            val zAtWall = az + (bz - az) * t
            if (kotlin.math.abs(zAtWall) < 1.8f && !sideRoomDoorOpen) return false
        }
        return true
    }

    /** Moves the monster in small axis-separated steps so furniture and closed room doors are less likely to trap it. */
    private fun moveMonsterToward(tx: Float, tz: Float, speed: Float, dt: Float) {
        val dx=tx-mx; val dz=tz-mz
        val d=sqrt(dx*dx+dz*dz).coerceAtLeast(0.001f)
        val step=(speed*dt).coerceAtMost(d)
        val nx=(mx+dx/d*step).coerceIn(-7.25f,7.25f)
        val nz=(mz+dz/d*step).coerceIn(-5.35f,5.35f)
        fun blocked(x:Float,z:Float):Boolean {
            val furniture=(x < -2.0f && x > -3.55f && z > -1.25f && z < 0.45f) ||
                (x > 1.75f && x < 3.25f && z > 1.25f && z < 2.75f) ||
                (x > -0.95f && x < 0.95f && z > -4.35f && z < -3.25f)
            val doorBlock=(kotlin.math.abs(x+4f)<0.22f && ((z>-2.75f && z< -1.25f && !bedroomDoorOpen) || (z>1.25f && z<2.75f && !storageDoorOpen))) ||
                (kotlin.math.abs(x-4f)<0.22f && ((z>-2.75f && z< -1.25f && !kitchenDoorOpen) || (z>1.25f && z<2.75f && !sideRoomDoorOpen)))
            return furniture || doorBlock
        }
        // Try the direct path first, then slide along one axis to escape corners.
        if(!blocked(nx,nz)) { mx=nx; mz=nz }
        else if(!blocked(nx,mz)) mx=nx
        else if(!blocked(mx,nz)) mz=nz
        else {
            val sideX=(mx+(-dz/d)*step).coerceIn(-7.25f,7.25f)
            val sideZ=(mz+(dx/d)*step).coerceIn(-5.35f,5.35f)
            if(!blocked(sideX,mz)) mx=sideX else if(!blocked(mx,sideZ)) mz=sideZ
        }
    }

    private fun monsterBox(localX:Float,y:Float,localZ:Float,sx:Float,sy:Float,sz:Float,r:Float,g:Float,b:Float) {
        val c = kotlin.math.cos(monsterFacing); val sn = kotlin.math.sin(monsterFacing)
        val wx = mx + localX*c + localZ*sn
        val wz = mz - localX*sn + localZ*c
        Matrix.setIdentityM(model,0)
        Matrix.translateM(model,0,wx,y,wz)
        Matrix.rotateM(model,0,Math.toDegrees(monsterFacing.toDouble()).toFloat(),0f,1f,0f)
        Matrix.scaleM(model,0,sx,sy,sz)
        currentSx=sx; currentSy=sy; currentSz=sz
        currentSmoothNormals=false
        currentForceUntextured=true
        drawCurrentModel(r,g,b)
        currentForceUntextured=false
    }

    private fun monsterEllipsoid(localX:Float,y:Float,localZ:Float,sx:Float,sy:Float,sz:Float,r:Float,g:Float,b:Float) {
        val c = kotlin.math.cos(monsterFacing); val sn = kotlin.math.sin(monsterFacing)
        val wx = mx + localX*c + localZ*sn
        val wz = mz - localX*sn + localZ*c
        Matrix.setIdentityM(model,0)
        Matrix.translateM(model,0,wx,y,wz)
        Matrix.rotateM(model,0,Math.toDegrees(monsterFacing.toDouble()).toFloat(),0f,1f,0f)
        Matrix.scaleM(model,0,sx,sy,sz)
        currentSx=sx; currentSy=sy; currentSz=sz
        currentSmoothNormals=true
        currentForceUntextured=true
        drawCurrentModel(r,g,b)
        currentForceUntextured=false
    }

    private fun box(x:Float,y:Float,z:Float,sx:Float,sy:Float,sz:Float,r:Float,g:Float,b:Float) {
        Matrix.setIdentityM(model,0); Matrix.translateM(model,0,x,y,z); Matrix.scaleM(model,0,sx,sy,sz)
        currentSx=sx; currentSy=sy; currentSz=sz
        currentSmoothNormals=false
        drawCurrentModel(r,g,b)
    }

    private fun drawCurrentModel(r:Float,g:Float,b:Float) {
        if (shadowPass) {
            // Depth-only pass: skip tiny decoration, big floor slabs and anything out of the light's reach.
            if (!currentForceUntextured) {
                if (currentSx * currentSy * currentSz < 0.03f) return
                if (currentSy < 0.4f && currentSx > 3f && currentSz > 3f) return
            }
            val big = maxOf(currentSx, maxOf(currentSy, currentSz)) * 0.5f
            val ddx = model[12] - fx; val ddy = model[13] - fy; val ddz = model[14] - fz
            val reach = shadowRange + big
            if (ddx*ddx + ddy*ddy + ddz*ddz > reach*reach) return
            if (ddx*fdx + ddy*fdy + ddz*fdz < -(big + 0.3f)) return
            GLES20.glUniformMatrix4fv(shModelHandle, 1, false, model, 0)
            val sb = if (currentSmoothNormals) sphereVertices ?: vertices else vertices
            val sc = if (currentSmoothNormals) sphereVertexCount else 36
            sb.position(0); GLES20.glEnableVertexAttribArray(shPosHandle)
            GLES20.glVertexAttribPointer(shPosHandle,3,GLES20.GL_FLOAT,false,0,sb)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,sc)
            GLES20.glDisableVertexAttribArray(shPosHandle)
            return
        }
        Matrix.multiplyMM(mvp,0,vp,0,model,0)
        val mv = FloatArray(16)
        Matrix.multiplyMM(mv,0,view,0,model,0)
        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(mvpHandle,1,false,mvp,0)
        GLES20.glUniformMatrix4fv(mvHandle,1,false,mv,0)
        GLES20.glUniformMatrix4fv(modelHandle,1,false,model,0)
        GLES20.glUniform3f(dimensionsHandle, currentSx, currentSy, currentSz)
        val fyaw = Math.toRadians(cameraYaw.toDouble())
        GLES20.glUniform3f(flashPosHandle, fx, fy, fz)
        GLES20.glUniform3f(flashDirHandle, fdx, fdy, fdz)
        GLES20.glUniformMatrix4fv(lightVPHandle,1,false,lightVP,0)
        GLES20.glUniform3f(eyePosHandle, eyeX, eyeY, eyeZ)
        GLES20.glUniform3f(shadowFwdHandle, fdx, fdy, fdz)
        GLES20.glUniform1f(shadowFarHandle, shadowFar)
        GLES20.glUniform1f(shadowOnHandle, if (shadowActive) 1f else 0f)
        GLES20.glUniform1f(shadowTexelHandle, 1f / shadowSize)
        val flashActive = flashlightOn && !hidden && flashlightBattery > 0f && !flashlightFlickering
        GLES20.glUniform1i(flashOnHandle, if (flashActive) 1 else 0)
        GLES20.glUniform1f(flashPowerHandle, if (flashlightBattery < 12f) 1.0f else if (flashlightBattery < 28f) 1.25f else 1.55f)
        GLES20.glUniform1f(brightnessHandle, brightness)
        GLES20.glUniform1f(dangerHandle, shaderDanger)
        GLES20.glUniform1i(smoothNormalHandle, if (currentSmoothNormals) 1 else 0)
        GLES20.glUniform4f(colorHandle,r,g,b,1f)
        GLES20.glUniform1f(fogHandle, if (visualQuality >= 2) 1.0f else if (visualQuality == 1) 0.62f else 0.0f)
        GLES20.glUniform1f(timeHandle, (System.nanoTime() / 1.0e9).toFloat())
        GLES20.glUniform2f(viewportHandle, viewportWidth, viewportHeight)
        val material = when {
            r > g * 1.23f && r > b * 1.18f -> 1 // worn timber
            r < 0.17f && b > r * 1.18f -> 2 // dirty stone floor
            else -> 0 // aged plaster
        }
        val hasNM = !currentForceUntextured && material in 0..2 && normalRoughLoaded[material]
        GLES20.glActiveTexture(GLES20.GL_TEXTURE2)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, if (hasNM) normalRoughIds[material] else 0)
        GLES20.glUniform1i(normalMapHandle, 2)
        GLES20.glUniform1f(useNormalMapHandle, if (hasNM) 1f else 0f)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, if (shadowsReady) shadowTex else 0)
        GLES20.glUniform1i(shadowMapHandle, 1)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureIds.getOrElse(material) { 0 })
        GLES20.glUniform1i(textureHandle, 0)
        GLES20.glUniform1i(useTextureHandle, if (!currentForceUntextured && textureIds.getOrElse(material) { 0 } != 0) 1 else 0)
        val drawBuffer = if (currentSmoothNormals) sphereVertices ?: vertices else vertices
        val drawCount = if (currentSmoothNormals) sphereVertexCount else 36
        drawBuffer.position(0); GLES20.glEnableVertexAttribArray(posHandle)
        GLES20.glVertexAttribPointer(posHandle,3,GLES20.GL_FLOAT,false,0,drawBuffer)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,drawCount)
        GLES20.glDisableVertexAttribArray(posHandle)
    }
}
