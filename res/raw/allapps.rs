#pragma version(1)
#pragma stateVertex(PV)
#pragma stateFragment(PFTexNearest)
#pragma stateStore(PSIcons)

#define PI 3.14159f

int g_SpecialHWWar;

// Attraction to center values from page edge to page center.
float g_AttractionTable[9];
float g_FrictionTable[9];
float g_PhysicsTableSize;

float g_PosPage;
float g_PosVelocity;
float g_LastPositionX;
int g_LastTouchDown;
float g_DT;
int g_LastTime;
int g_PosMax;
float g_Zoom;
float g_Animation;
float g_OldPosPage;
float g_OldPosVelocity;
float g_OldZoom;
float g_MoveToTotalTime;
float g_MoveToTime;
float g_MoveToOldPos;

int g_Cols;
int g_Rows;

// Drawing constants, should be parameters ======
#define VIEW_ANGLE 1.28700222f

int g_DrawLastFrame;
int lastFrame(int draw) {
    // We draw one extra frame to work around the last frame post bug.
    // We also need to track if we drew the last frame to deal with large DT
    // in the physics.
    int ret = g_DrawLastFrame | draw;
    g_DrawLastFrame = draw;
    return ret;  // should return draw instead.
}

void updateReadback() {
    if ((g_OldPosPage != g_PosPage) ||
        (g_OldPosVelocity != g_PosVelocity) ||
        (g_OldZoom != g_Zoom)) {

        g_OldPosPage = g_PosPage;
        g_OldPosVelocity = g_PosVelocity;
        g_OldZoom = g_Zoom;

        int i[3];
        i[0] = g_PosPage * (1 << 16);
        i[1] = g_PosVelocity * (1 << 16);
        i[2] = g_OldZoom * (1 << 16);
        sendToClient(&i[0], 1, 12, 1);
    }
}

void setColor(float r, float g, float b, float a) {
    if (g_SpecialHWWar) {
        color(0, 0, 0, 0.001f);
    } else {
        color(r, g, b, a);
    }
}

void init() {
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
    g_SpecialHWWar = 1;
}

void move() {
    if (g_LastTouchDown) {
        float dx = -(state->newPositionX - g_LastPositionX);
        g_PosVelocity = 0;
        g_PosPage += dx * 5.2f;

        float pmin = -0.49f;
        float pmax = g_PosMax + 0.49f;
        g_PosPage = clampf(g_PosPage, pmin, pmax);
    }
    g_LastTouchDown = state->newTouchDown;
    g_LastPositionX = state->newPositionX;
    g_MoveToTime = 0;
    //debugF("Move P", g_PosPage);
}

void moveTo() {
    g_MoveToTime = g_MoveToTotalTime;
    g_PosVelocity = 0;
    g_MoveToOldPos = g_PosPage;

	// debugF("======= moveTo", state->targetPos);
}

void setZoom() {
    g_Zoom = state->zoomTarget;
    g_DrawLastFrame = 1;
    updateReadback();
}

void fling() {
    g_LastTouchDown = 0;
    g_PosVelocity = -state->flingVelocity * 4;
    float av = fabsf(g_PosVelocity);
    float minVel = 3.5f;

    minVel *= 1.f - (fabsf(fracf(g_PosPage + 0.5f) - 0.5f) * 0.45f);

    if (av < minVel && av > 0.2f) {
        if (g_PosVelocity > 0) {
            g_PosVelocity = minVel;
        } else {
            g_PosVelocity = -minVel;
        }
    }

    if (g_PosPage <= 0) {
        g_PosVelocity = maxf(0, g_PosVelocity);
    }
    if (g_PosPage > g_PosMax) {
        g_PosVelocity = minf(0, g_PosVelocity);
    }
}

float
modf(float x, float y)
{
    return x-(y*floorf(x/y));
}


/*
 * Interpolates values in the range 0..1 to a curve that eases in
 * and out.
 */
float
getInterpolation(float input) {
    return (cosf((input + 1) * PI) / 2.0f) + 0.5f;
}


void updatePos() {
    if (g_LastTouchDown) {
        return;
    }

    float tablePosNorm = fracf(g_PosPage + 0.5f);
    float tablePosF = tablePosNorm * g_PhysicsTableSize;
    int tablePosI = tablePosF;
    float tablePosFrac = tablePosF - tablePosI;
    float accel = lerpf(g_AttractionTable[tablePosI],
                        g_AttractionTable[tablePosI + 1],
                        tablePosFrac) * g_DT;
    float friction = lerpf(g_FrictionTable[tablePosI],
                        g_FrictionTable[tablePosI + 1],
                        tablePosFrac) * g_DT;

    if (g_MoveToTime) {
        // New position is old posiition + (total distance) * (interpolated time)
        g_PosPage = g_MoveToOldPos + (state->targetPos - g_MoveToOldPos) * getInterpolation((g_MoveToTotalTime - g_MoveToTime) / g_MoveToTotalTime);
        g_MoveToTime -= g_DT;
        if (g_MoveToTime <= 0) {
            g_MoveToTime = 0;
            g_PosPage = state->targetPos;
        }
        return;
    }

    // If our velocity is low OR acceleration is opposing it, apply it.
    if (fabsf(g_PosVelocity) < 4.0f || (g_PosVelocity * accel) < 0) {
        g_PosVelocity += accel;
    }
    //debugF("g_PosPage", g_PosPage);
    //debugF("  g_PosVelocity", g_PosVelocity);
    //debugF("  friction", friction);
    //debugF("  accel", accel);

    // Normal physics
    if (g_PosVelocity > 0) {
        g_PosVelocity -= friction;
        g_PosVelocity = maxf(g_PosVelocity, 0);
    } else {
        g_PosVelocity += friction;
        g_PosVelocity = minf(g_PosVelocity, 0);
    }

    if ((friction > fabsf(g_PosVelocity)) && (friction > fabsf(accel))) {
        // Special get back to center and overcome friction physics.
        float t = tablePosNorm - 0.5f;
        if (fabsf(t) < (friction * g_DT)) {
            // really close, just snap
            g_PosPage = roundf(g_PosPage);
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
        float damp = 1.0 + (g_PosPage * 4);
        damp = clampf(damp, 0.f, 0.9f);
        g_PosVelocity *= damp;
    }
    if (g_PosPage > g_PosMax && g_PosVelocity > 0) {
        float damp = 1.0 - ((g_PosPage - g_PosMax) * 4);
        damp = clampf(damp, 0.f, 0.9f);
        g_PosVelocity *= damp;
    }

    g_PosPage += g_PosVelocity * g_DT;
    g_PosPage = clampf(g_PosPage, -0.49, g_PosMax + 0.49);
}


void
draw_home_button()
{
    setColor(1.0f, 1.0f, 1.0f, 1.0f);
    bindTexture(NAMED_PFTexNearest, 0, state->homeButtonId);

    float w = getWidth();
    float h = getHeight();

    float x;
    float y;
    if (getWidth() > getHeight()) {
        x = w - (params->homeButtonTextureWidth * (1 - g_Animation)) + 20;
        y = (h - params->homeButtonTextureHeight) * 0.5f;
    } else {
        x = (w - params->homeButtonTextureWidth) / 2;
        y = -g_Animation * params->homeButtonTextureHeight;
        y -= 30; // move the house to the edge of the screen as it doesn't fill the texture.
    }

    drawSpriteScreenspace(x, y, 0, params->homeButtonTextureWidth, params->homeButtonTextureHeight);
}

void drawFrontGrid(float rowOffset, float p)
{
    float h = getHeight();
    float w = getWidth();

    int intRowOffset = rowOffset;
    float rowFrac = rowOffset - intRowOffset;
    float colWidth = 120.f;//getWidth() / 4;
    float rowHeight = colWidth + 25.f;
    float yoff = 0.5f * h + 1.5f * rowHeight;

    int row, col;
    int colCount = 4;
    if (w > h) {
        colCount = 6;
        rowHeight -= 12.f;
        yoff = 0.47f * h + 1.0f * rowHeight;
    }

    int iconNum = (intRowOffset - 5) * colCount;


    bindProgramVertex(NAMED_PVCurve);

    vpConstants->Position.z = p;

    setColor(1.0f, 1.0f, 1.0f, 1.0f);
    for (row = -5; row < 15; row++) {
        float y = yoff - ((-rowFrac + row) * rowHeight);

        for (col=0; col < colCount; col++) {
            if (iconNum >= state->iconCount) {
                return;
            }

            if (iconNum >= 0) {
                float x = colWidth * col + (colWidth / 2);
                vpConstants->Position.x = x + 0.2f;

                if (state->selectedIconIndex == iconNum && !p) {
                    bindProgramFragment(NAMED_PFTexNearest);
                    bindTexture(NAMED_PFTexNearest, 0, state->selectedIconTexture);
                    vpConstants->ImgSize.x = SELECTION_TEXTURE_WIDTH_PX;
                    vpConstants->ImgSize.y = SELECTION_TEXTURE_HEIGHT_PX;
                    vpConstants->Position.y = y - (SELECTION_TEXTURE_HEIGHT_PX - ICON_TEXTURE_HEIGHT_PX) * 0.5f;
                    drawSimpleMesh(NAMED_SMCell);
                }

                bindProgramFragment(NAMED_PFTexMip);
                vpConstants->ImgSize.x = ICON_TEXTURE_WIDTH_PX;
                vpConstants->ImgSize.y = ICON_TEXTURE_HEIGHT_PX;
                vpConstants->Position.y = y - 0.2f;
                bindTexture(NAMED_PFTexMip, 0, loadI32(ALLOC_ICON_IDS, iconNum));
                drawSimpleMesh(NAMED_SMCell);

                bindProgramFragment(NAMED_PFTexMipAlpha);
                vpConstants->ImgSize.x = 120.f;
                vpConstants->ImgSize.y = 64.f;
                vpConstants->Position.y = y - 64.f - 0.2f;
                bindTexture(NAMED_PFTexMipAlpha, 0, loadI32(ALLOC_LABEL_IDS, iconNum));
                drawSimpleMesh(NAMED_SMCell);
            }
            iconNum++;
        }
    }
}


int
main(int launchID)
{
    // Compute dt in seconds.
    int newTime = uptimeMillis();
    g_DT = (newTime - g_LastTime) / 1000.f;
    g_LastTime = newTime;

    if (!g_DrawLastFrame) {
        // If we stopped rendering we cannot use DT.
        // assume 30fps in this case.
        g_DT = 0.033f;
    }
    // physics may break if DT is large.
    g_DT = minf(g_DT, 0.2f);

    if (g_Zoom != state->zoomTarget) {
        float dz = g_DT * 1.7f;
        if (state->zoomTarget < 0.5f) {
            dz = -dz;
        }
        if (fabsf(g_Zoom - state->zoomTarget) < fabsf(dz)) {
            g_Zoom = state->zoomTarget;
        } else {
            g_Zoom += dz;
        }
        updateReadback();
    }
    g_Animation = powf(1-g_Zoom, 3);

    // Set clear value to dim the background based on the zoom position.
    if ((g_Zoom < 0.001f) && (state->zoomTarget < 0.001f) && !g_SpecialHWWar) {
        pfClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        // When we're zoomed out and not tracking motion events, reset the pos to 0.
        if (!g_LastTouchDown) {
            g_PosPage = 0;
        }
        return lastFrame(0);
    } else {
        pfClearColor(0.0f, 0.0f, 0.0f, g_Zoom);
    }

    // icons & labels
    int iconCount = state->iconCount;
    if (getWidth() > getHeight()) {
        g_Cols = 6;
        g_Rows = 3;
    } else {
        g_Cols = 4;
        g_Rows = 4;
    }
    g_PosMax = ((iconCount + (g_Cols-1)) / g_Cols) - g_Rows;
    if (g_PosMax < 0) g_PosMax = 0;

    updatePos();
    updateReadback();

    //debugF("    draw g_PosPage", g_PosPage);

    // Draw the icons ========================================
    drawFrontGrid(g_PosPage, g_Animation);

    bindProgramFragment(NAMED_PFTexNearest);
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
    return lastFrame((g_PosVelocity != 0) || fracf(g_PosPage) || g_Zoom != state->zoomTarget || (g_MoveToTime != 0));
}

