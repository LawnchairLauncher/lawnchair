#pragma version(1)

#include "../../../../../frameworks/base/libs/rs/scriptc/rs_types.rsh"
#include "../../../../../frameworks/base/libs/rs/scriptc/rs_math.rsh"
#include "../../../../../frameworks/base/libs/rs/scriptc/rs_graphics.rsh"
//#pragma stateVertex(PV)
//#pragma stateFragment(PFTexNearest)
//#pragma stateStore(PSIcons)

#define PI 3.14159f

static int g_SpecialHWWar;


// Constants from Java
int COLUMNS_PER_PAGE_PORTRAIT;
int ROWS_PER_PAGE_PORTRAIT;
int COLUMNS_PER_PAGE_LANDSCAPE;
int ROWS_PER_PAGE_LANDSCAPE;

float gNewPositionX;
int gNewTouchDown;
float gFlingVelocity;
int gIconCount;
int gSelectedIconIndex;
rs_allocation gSelectedIconTexture;
float gZoomTarget;
rs_allocation gHomeButton;
float gTargetPos;

rs_program_fragment gPFTexNearest;
rs_program_fragment gPFTexMip;
rs_program_fragment gPFTexMipAlpha;
rs_program_vertex gPVCurve;
rs_program_store gPS;
rs_mesh gSMCell;

rs_allocation *gIconIDs;
rs_allocation *gLabelIDs;

typedef struct VpConsts_s {
    float4 Position;
    float4 ScaleOffset;
    float2 BendPos;
    float2 ImgSize;
} VpConsts_t;
VpConsts_t *vpConstants;


#pragma rs export_var(COLUMNS_PER_PAGE_PORTRAIT, ROWS_PER_PAGE_PORTRAIT, COLUMNS_PER_PAGE_LANDSCAPE, ROWS_PER_PAGE_LANDSCAPE, gNewPositionX, gNewTouchDown, gFlingVelocity, gIconCount, gSelectedIconIndex, gSelectedIconTexture, gZoomTarget, gHomeButton, gTargetPos, gPFTexNearest, gPFTexMip, gPFTexMipAlpha, gPVCurve, gPS, gSMCell, gIconIDs, gLabelIDs, vpConstants)
#pragma rs export_func(resetHWWar, move, moveTo, setZoom, fling)


void debugAll()
{
    debugPi(1000, COLUMNS_PER_PAGE_PORTRAIT);
    debugPi(1001, ROWS_PER_PAGE_PORTRAIT);
    debugPi(1002, COLUMNS_PER_PAGE_LANDSCAPE);
    debugPi(1003, ROWS_PER_PAGE_LANDSCAPE);

    debugPf(1018, gNewPositionX);
    debugPi(1019, gNewTouchDown);
    debugPf(1020, gFlingVelocity);
    debugPi(1021, gIconCount);
    debugPi(1022, gSelectedIconIndex);
    debugPi(1023, gSelectedIconTexture);
    debugPf(1024, gZoomTarget);
    debugPi(1025, gHomeButton);
    debugPf(1026, gTargetPos);

    debugPi(1027, gPFTexNearest);
    debugPi(1028, gPFTexMip);
    debugPi(1029, gPFTexMipAlpha);
    debugPi(1030, gPVCurve);
    debugPi(1031, gSMCell);

    debugP(1032, gIconIDs);
    debugP(1033, gLabelIDs);
    debugP(1034, vpConstants);
}


// Attraction to center values from page edge to page center.
static float g_AttractionTable[9];
static float g_FrictionTable[9];
static float g_PhysicsTableSize;

static float g_PosPage;
static float g_PosVelocity;
static float g_LastPositionX;
static int g_LastTouchDown;
static float g_DT;
static int g_LastTime;
static int g_PosMax;
static float g_Zoom;
static float g_Animation;
static float g_OldPosPage;
static float g_OldPosVelocity;
static float g_OldZoom;
static float g_MoveToTotalTime;
static float g_MoveToTime;
static float g_MoveToOldPos;

static int g_Cols;
static int g_Rows;

// Drawing constants, should be parameters ======
#define VIEW_ANGLE 1.28700222f

static int g_DrawLastFrame;
static int lastFrame(int draw) {
    //debugPi(99, 13);
    // We draw one extra frame to work around the last frame post bug.
    // We also need to track if we drew the last frame to deal with large DT
    // in the physics.
    int ret = g_DrawLastFrame | draw;
    g_DrawLastFrame = draw;
    return ret;  // should return draw instead.
}

static void updateReadback() {
    //debugPi(99, 12);
    //if ((g_OldPosPage != g_PosPage) ||
        //(g_OldPosVelocity != g_PosVelocity) ||
        //(g_OldZoom != g_Zoom)) {

    //debugPf(40, g_PosPage);
    //debugPf(41, g_PosVelocity);
    //debugPf(42, g_Zoom);
        g_OldPosPage = g_PosPage;
        g_OldPosVelocity = g_PosVelocity;
        g_OldZoom = g_Zoom;

        int i[3];
        i[0] = g_PosPage * (1 << 16);
        i[1] = g_PosVelocity * (1 << 16);
        i[2] = g_OldZoom * (1 << 16);
        sendToClient(&i[0], 1, 12, 1);
    //}
}

static void setColor(float r, float g, float b, float a) {
    //debugPi(99, 11);
    if (g_SpecialHWWar) {
        color(0, 0, 0, 0.001f);
    } else {
        color(r, g, b, a);
    }
}

void init() {
    //debugPi(99, 10);
    g_AttractionTable[0] = 20.0f;
    g_AttractionTable[1] = 20.0f;
    g_AttractionTable[2] = 20.0f;
    g_AttractionTable[3] = 10.0f;
    g_AttractionTable[4] = -10.0f;
    g_AttractionTable[5] = -20.0f;
    g_AttractionTable[6] = -20.0f;
    g_AttractionTable[7] = -20.0f;
    g_AttractionTable[8] = -20.0f;  // dup 7 to avoid a clamp later
    g_FrictionTable[0] = 10.0f;
    g_FrictionTable[1] = 10.0f;
    g_FrictionTable[2] = 11.0f;
    g_FrictionTable[3] = 15.0f;
    g_FrictionTable[4] = 15.0f;
    g_FrictionTable[5] = 11.0f;
    g_FrictionTable[6] = 10.0f;
    g_FrictionTable[7] = 10.0f;
    g_FrictionTable[8] = 10.0f;  // dup 7 to avoid a clamp later
    g_PhysicsTableSize = 7;

    g_PosVelocity = 0;
    g_PosPage = 0;
    g_LastTouchDown = 0;
    g_LastPositionX = 0;
    g_Zoom = 0;
    g_Animation = 1.f;
    g_SpecialHWWar = 1;
    g_MoveToTime = 0;
    g_MoveToOldPos = 0;
    g_MoveToTotalTime = 0.2f; // Duration of scrolling 1 line
}

void resetHWWar() {
    //debugPi(99, 9);
    g_SpecialHWWar = 1;
}

void move() {
    //debugPi(99, 8);
    if (g_LastTouchDown) {
        float dx = -(gNewPositionX - g_LastPositionX);
        g_PosVelocity = 0;
        g_PosPage += dx * 5.2f;

        float pmin = -0.49f;
        float pmax = g_PosMax + 0.49f;
        g_PosPage = clamp(g_PosPage, pmin, pmax);
    }
    g_LastTouchDown = gNewTouchDown;
    g_LastPositionX = gNewPositionX;
    g_MoveToTime = 0;
    //debugF("Move P", g_PosPage);
}

void moveTo() {
    //debugPi(99, 7);
    g_MoveToTime = g_MoveToTotalTime;
    g_PosVelocity = 0;
    g_MoveToOldPos = g_PosPage;

	// debugF("======= moveTo", state->targetPos);
}

void setZoom() {
    //debugPi(99, 6);
    g_Zoom = gZoomTarget;
    g_DrawLastFrame = 1;
    updateReadback();
}

void fling() {
    //debugPi(99, 5);
    g_LastTouchDown = 0;
    g_PosVelocity = -gFlingVelocity * 4;
    float av = fabs(g_PosVelocity);
    float minVel = 3.5f;

    minVel *= 1.f - (fabs(frac(g_PosPage + 0.5f) - 0.5f) * 0.45f);

    if (av < minVel && av > 0.2f) {
        if (g_PosVelocity > 0) {
            g_PosVelocity = minVel;
        } else {
            g_PosVelocity = -minVel;
        }
    }

    if (g_PosPage <= 0) {
        g_PosVelocity = max(0.f, g_PosVelocity);
    }
    if (g_PosPage > g_PosMax) {
        g_PosVelocity = min(0.f, g_PosVelocity);
    }
}

// Interpolates values in the range 0..1 to a curve that eases in
// and out.
static float getInterpolation(float input) {
    //debugPi(99, 4);
    return (cos((input + 1) * PI) * 0.5f) + 0.5f;
}


static void updatePos() {
    //debugPi(99, 3);
    if (g_LastTouchDown) {
        return;
    }

    float tablePosNorm = frac(g_PosPage + 0.5f);
    float tablePosF = tablePosNorm * g_PhysicsTableSize;
    int tablePosI = tablePosF;
    float tablePosFrac = tablePosF - tablePosI;
    float accel = mix(g_AttractionTable[tablePosI],
                        g_AttractionTable[tablePosI + 1],
                        tablePosFrac) * g_DT;
    float friction = mix(g_FrictionTable[tablePosI],
                        g_FrictionTable[tablePosI + 1],
                        tablePosFrac) * g_DT;

    if (g_MoveToTime) {
        // New position is old posiition + (total distance) * (interpolated time)
        g_PosPage = g_MoveToOldPos + (gTargetPos - g_MoveToOldPos) * getInterpolation((g_MoveToTotalTime - g_MoveToTime) / g_MoveToTotalTime);
        g_MoveToTime -= g_DT;
        if (g_MoveToTime <= 0) {
            g_MoveToTime = 0;
            g_PosPage = gTargetPos;
        }
        return;
    }

    // If our velocity is low OR acceleration is opposing it, apply it.
    if (fabs(g_PosVelocity) < 4.0f || (g_PosVelocity * accel) < 0) {
        g_PosVelocity += accel;
    }
    //debugF("g_PosPage", g_PosPage);
    //debugF("  g_PosVelocity", g_PosVelocity);
    //debugF("  friction", friction);
    //debugF("  accel", accel);

    // Normal physics
    if (g_PosVelocity > 0) {
        g_PosVelocity -= friction;
        g_PosVelocity = max(g_PosVelocity, 0.f);
    } else {
        g_PosVelocity += friction;
        g_PosVelocity = min(g_PosVelocity, 0.f);
    }

    if ((friction > fabs(g_PosVelocity)) && (friction > fabs(accel))) {
        // Special get back to center and overcome friction physics.
        float t = tablePosNorm - 0.5f;
        if (fabs(t) < (friction * g_DT)) {
            // really close, just snap
            g_PosPage = round(g_PosPage);
            g_PosVelocity = 0;
        } else {
            if (t > 0) {
                g_PosVelocity = -friction;
            } else {
                g_PosVelocity = friction;
            }
        }
    }

    // Check for out of boundry conditions.
    if (g_PosPage < 0 && g_PosVelocity < 0) {
        float damp = 1.0f + (g_PosPage * 4);
        damp = clamp(damp, 0.f, 0.9f);
        g_PosVelocity *= damp;
    }
    if (g_PosPage > g_PosMax && g_PosVelocity > 0) {
        float damp = 1.0f - ((g_PosPage - g_PosMax) * 4);
        damp = clamp(damp, 0.f, 0.9f);
        g_PosVelocity *= damp;
    }

    g_PosPage += g_PosVelocity * g_DT;
    g_PosPage = clamp(g_PosPage, -0.49f, g_PosMax + 0.49f);

    //debugPf(300, g_PosPage);
}

static void
draw_home_button()
{
    //debugPi(99, 2);
    setColor(1.0f, 1.0f, 1.0f, 1.0f);
    bindTexture(gPFTexNearest, 0, gHomeButton);

    float w = getWidth();
    float h = getHeight();
    float tw = allocGetDimX(gHomeButton);
    float th = allocGetDimY(gHomeButton);

    float x;
    float y;
    if (getWidth() > getHeight()) {
        x = w - (tw * (1 - g_Animation)) + 20;
        y = (h - th) * 0.5f;
    } else {
        x = (w - tw) / 2;
        y = -g_Animation * th;
        y -= 30; // move the house to the edge of the screen as it doesn't fill the texture.
    }

    drawSpriteScreenspace(x, y, 0, tw, th);
}

static void drawFrontGrid(float rowOffset, float p)
{
    //debugPi(99, 1);
    float h = getHeight();
    float w = getWidth();

    int intRowOffset = rowOffset;
    float rowFrac = rowOffset - intRowOffset;
    float colWidth = 120.f;//getWidth() / 4;
    float rowHeight = colWidth + 25.f;
    float yoff = 0.5f * h + 1.5f * rowHeight;

    //debugPi(199, 1);

    int row, col;
    int colCount = 4;
    if (w > h) {
        colCount = 6;
        rowHeight -= 12.f;
        yoff = 0.47f * h + 1.0f * rowHeight;
    }

    int iconNum = (intRowOffset - 5) * colCount;

    bindProgramVertex(gPVCurve);

    vpConstants->Position.z = p;

    setColor(1.0f, 1.0f, 1.0f, 1.0f);
    for (row = -5; row < 15; row++) {
        float y = yoff - ((-rowFrac + row) * rowHeight);

        for (col=0; col < colCount; col++) {
            if (iconNum >= gIconCount) {
                return;
            }

            if (iconNum >= 0) {
                float x = colWidth * col + (colWidth / 2);
                vpConstants->Position.x = x + 0.2f;

                if (gSelectedIconIndex == iconNum && !p && gSelectedIconTexture) {
                    bindProgramFragment(gPFTexNearest);
                    bindTexture(gPFTexNearest, 0, gSelectedIconTexture);
                    vpConstants->ImgSize.x = allocGetDimX(gSelectedIconTexture);
                    vpConstants->ImgSize.y = allocGetDimY(gSelectedIconTexture);
                    vpConstants->Position.y = y - (allocGetDimY(gSelectedIconTexture) - allocGetDimY(gIconIDs[iconNum])) * 0.5f;
                    drawSimpleMesh(gSMCell);
                }

                bindProgramFragment(gPFTexMip);
                vpConstants->ImgSize.x = allocGetDimX(gIconIDs[iconNum]);
                vpConstants->ImgSize.y = allocGetDimY(gIconIDs[iconNum]);
                vpConstants->Position.y = y - 0.2f;
                bindTexture(gPFTexMip, 0, gIconIDs[iconNum]);
                drawSimpleMesh(gSMCell);

                //debugPf(202, vpConstants->ImgSize.x);
                //debugPf(203, vpConstants->ImgSize.y);
                //debugPf(204, vpConstants->Position.y);
                //debugPi(205, gIconIDs[iconNum]);
                //debugPi(206, gLabelIDs[iconNum]);


                bindProgramFragment(gPFTexMipAlpha);
                vpConstants->ImgSize.x = allocGetDimX(gLabelIDs[iconNum]);
                vpConstants->ImgSize.y = allocGetDimY(gLabelIDs[iconNum]);
                vpConstants->Position.y = y - 64.f - 0.2f;
                bindTexture(gPFTexMipAlpha, 0, gLabelIDs[iconNum]);
                drawSimpleMesh(gSMCell);
            }
            iconNum++;
        }
    }
}


int root()
{
    //debugAll();
    bindProgramStore(gPS);

    // Compute dt in seconds.
    int newTime = uptimeMillis();
    g_DT = (newTime - g_LastTime) * 0.001f;
    g_LastTime = newTime;

    if (!g_DrawLastFrame) {
        // If we stopped rendering we cannot use DT.
        // assume 30fps in this case.
        g_DT = 0.033f;
    }
    // physics may break if DT is large.
    g_DT = min(g_DT, 0.2f);

    if (g_Zoom != gZoomTarget) {
        float dz = g_DT * 1.7f;
        if (gZoomTarget < 0.5f) {
            dz = -dz;
        }
        if (fabs(g_Zoom - gZoomTarget) < fabs(dz)) {
            g_Zoom = gZoomTarget;
        } else {
            g_Zoom += dz;
        }
        updateReadback();
    }
    g_Animation = pow(1.f - g_Zoom, 3.f);

    //debugPf(100, g_Zoom);
    // Set clear value to dim the background based on the zoom position.
    if ((g_Zoom < 0.001f) && (gZoomTarget < 0.001f) && !g_SpecialHWWar) {
        pfClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        // When we're zoomed out and not tracking motion events, reset the pos to 0.
        if (!g_LastTouchDown) {
            g_PosPage = 0;
        }
        return lastFrame(0);
    } else {
        //debugPf(101, g_Zoom);
        pfClearColor(0.0f, 0.0f, 0.0f, g_Zoom);
    }

    // icons & labels
    if (getWidth() > getHeight()) {
        g_Cols = 6;
        g_Rows = 3;
    } else {
        g_Cols = 4;
        g_Rows = 4;
    }
    g_PosMax = ((gIconCount + (g_Cols-1)) / g_Cols) - g_Rows;
    if (g_PosMax < 0) g_PosMax = 0;

    updatePos();
    updateReadback();

    //debugF("    draw g_PosPage", g_PosPage);

    // Draw the icons ========================================
    drawFrontGrid(g_PosPage, g_Animation);

    bindProgramFragment(gPFTexNearest);
    draw_home_button();

    // This is a WAR to do a rendering pass without drawing during init to
    // force the driver to preload and compile its shaders.
    // Without this the first animation does not appear due to the time it
    // takes to init the driver state.
    if (g_SpecialHWWar) {
        g_SpecialHWWar = 0;
        return 1;
    }

    // Bug workaround where the last frame is not always displayed
    // So we keep rendering until the bug is fixed.
    return lastFrame((g_PosVelocity != 0) || frac(g_PosPage) || g_Zoom != gZoomTarget || (g_MoveToTime != 0));
}


