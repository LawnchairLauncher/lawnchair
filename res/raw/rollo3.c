#pragma version(1)
#pragma stateVertex(PV)
#pragma stateFragment(PFTexLinear)
#pragma stateStore(PSIcons)

#define PI 3.14159f


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
float g_OldPosPage;
float g_OldPosVelocity;
float g_OldZoom;

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


void init() {
    g_AttractionTable[0] = 6.5f;
    g_AttractionTable[1] = 6.5f;
    g_AttractionTable[2] = 7.0f;
    g_AttractionTable[3] = 6.0f;
    g_AttractionTable[4] = -6.0f;
    g_AttractionTable[5] = -7.0f;
    g_AttractionTable[6] = -6.5f;
    g_AttractionTable[7] = -6.5f;
    g_AttractionTable[8] = -6.5f;  // dup 7 to avoid a clamp later
    g_FrictionTable[0] = 3.5f;
    g_FrictionTable[1] = 3.6f;
    g_FrictionTable[2] = 4.0f;
    g_FrictionTable[3] = 5.0f;
    g_FrictionTable[4] = 5.0f;
    g_FrictionTable[5] = 4.0f;
    g_FrictionTable[6] = 3.6f;
    g_FrictionTable[7] = 3.5f;
    g_FrictionTable[8] = 3.5f;  // dup 7 to avoid a clamp later
    g_PhysicsTableSize = 7;

    g_PosVelocity = 0;
    g_PosPage = 0;
    g_LastTouchDown = 0;
    g_LastPositionX = 0;
    g_Zoom = 0;
}

void move() {
    if (g_LastTouchDown) {
        float dx = -(state->newPositionX - g_LastPositionX);
        g_PosVelocity = 0;
        g_PosPage += dx * 4;

        float pmin = -0.25f;
        float pmax = g_PosMax + 0.25f;
        g_PosPage = clampf(g_PosPage, pmin, pmax);
    }
    g_LastTouchDown = state->newTouchDown;
    g_LastPositionX = state->newPositionX;
    //debugF("Move P", g_PosPage);
}

void fling() {
    g_LastTouchDown = 0;
    g_PosVelocity = -state->flingVelocityX * 2;
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

void touchUp() {
    g_LastTouchDown = 0;
}

int
count_pages(int iconCount)
{
    int iconsPerPage = COLUMNS_PER_PAGE * ROWS_PER_PAGE;
    int pages = iconCount / iconsPerPage;
    if (pages*iconsPerPage != iconCount) {
        pages++;
    }
    return pages;
}

float
modf(float x, float y)
{
    return x-(y*floorf(x/y));
}

void updatePos() {
    if (g_LastTouchDown) {
        return;
    }

    int outOfRange = 0;
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

    if (g_PosPage < -0.5f) {
        accel = g_AttractionTable[0] * g_DT;
        friction = g_FrictionTable[0] * g_DT;
        outOfRange = 1;
    }
    if ((g_PosPage - g_PosMax) > 0.5f) {
        accel = g_AttractionTable[(int)g_PhysicsTableSize] * g_DT;
        friction = g_FrictionTable[(int)g_PhysicsTableSize] * g_DT;
        outOfRange = 1;
    }

    // If our velocity is low OR acceleration is opposing it, apply it.
    if (fabsf(g_PosVelocity) < 1.0f || (g_PosVelocity * accel) < 0) {
        g_PosVelocity += accel;
    }

    if ((friction > fabsf(g_PosVelocity)) &&
        (friction > fabsf(accel)) &&
        !outOfRange) {
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
    } else {
        // Normal physics
        if (g_PosVelocity > 0) {
            g_PosVelocity -= friction;
            g_PosVelocity = maxf(g_PosVelocity, 0);
        } else {
            g_PosVelocity += friction;
            g_PosVelocity = minf(g_PosVelocity, 0);
        }
    }
    g_PosPage += g_PosVelocity * g_DT;

    // Check for out of boundry conditions.
    if (g_PosPage < 0 && g_PosVelocity < 0) {
        g_PosPage = maxf(g_PosPage, -0.49);
        float damp = 1.0 + (g_PosPage * 4);
        damp = clampf(damp, 0.f, 0.9f);
        g_PosVelocity *= damp;
    }
    if (g_PosPage > g_PosMax && g_PosVelocity > 0) {
        g_PosPage = minf(g_PosPage, g_PosMax + 0.49);
        float damp = 1.0 - ((g_PosPage - g_PosMax) * 4);
        damp = clampf(damp, 0.f, 0.9f);
        g_PosVelocity *= damp;
    }
}

int positionStrip(float row, float column, int isTop)
{
    float mat1[16];

    float x = 0.5f * (column - 1.5f);

    float scale = 72.f * 3 / getWidth();
    //float xscale = scale * 4.55 / 1.8f / 2;

    if (isTop) {
        matrixLoadTranslate(mat1, x, 0.8f, 0.f);
        matrixScale(mat1, scale, scale, 1.f);
    } else {
        matrixLoadTranslate(mat1, x, -0.9f, 0.f);
        matrixScale(mat1, scale, -scale, 1.f);
    }
    vpLoadModelMatrix(mat1);

    float soff = row;
    if (isTop) {
        matrixLoadScale(mat1, 1.f, -0.85f, 1.f);
        matrixTranslate(mat1, 0, -(row * 1.4) - 0.97, 0);
    } else {
        matrixLoadScale(mat1, 1.f, 0.85f, 1.f);
        matrixTranslate(mat1, 0, -(row * 1.4) - 0.45, 0);
    }
    vpLoadTextureMatrix(mat1);

    return 0;//- soff * 10.f;
}

void
draw_home_button()
{
    color(1.0f, 1.0f, 1.0f, 1.0f);
    bindTexture(NAMED_PFTexLinear, 0, params->homeButtonId);

    float scale = 2.0f / SCREEN_WIDTH_PX;

    float x = 0.0f;

    float y = -(SCREEN_HEIGHT_PX / (float)SCREEN_WIDTH_PX);
    y += g_Zoom * (scale * params->homeButtonTextureHeight / 2);

    float z = 0.0f;
    drawSprite(x, y, z, params->homeButtonTextureWidth, params->homeButtonTextureHeight);
}

void drawFrontGrid(float rowOffset)
{
    float h = getHeight();
    float w = getWidth();

    int intRowOffset = rowOffset;
    float rowFrac = rowOffset - intRowOffset;
    float colWidth = getWidth() / 4;
    float rowHeight = colWidth + 25.f;
    float yoff = h - ((h - (rowHeight * 4.f)) / 2);

    yoff -= 110;

    int row, col;
    int iconNum = intRowOffset * 4;
    float ymax = yoff;
    float ymin = yoff - (3 * rowHeight) - 70;

    for (row = 0; row < 5; row++) {
        float y = yoff - ((-rowFrac + row) * rowHeight);

        for (col=0; col < 4; col++) {
            if (iconNum >= state->iconCount) {
                return;
            }

            if (iconNum >= 0) {
                float x = colWidth * col - ((128 - colWidth) / 2);

                if ((y >= ymin) && (y <= ymax)) {
                    color(1.f, 1.f, 1.f, 1.f);
                    bindTexture(NAMED_PFTexLinear, 0, loadI32(ALLOC_ICON_IDS, iconNum));
                    drawSpriteScreenspace(x, y, 0, 128, 128);
                }

                float y2 = y - 44;
                float a = 1.f;
                if (y2 < ymin) {
                    a = 1.f - (ymin - y2) * 0.02f;
                }
                if (y > (ymax + 40)) {
                    a = 1.f - (y - (ymax + 40)) * 0.02f;
                }
                a = clampf(a, 0, 1);

                color(1, 1, 1, a);
                bindTexture(NAMED_PFTexLinear, 0, loadI32(ALLOC_LABEL_IDS, iconNum));
                drawSpriteScreenspace(x, y - 44, 0,
                           params->bubbleBitmapWidth, params->bubbleBitmapHeight);
            }
            iconNum++;
        }
    }
}

void drawTop(float rowOffset)
{
    int intRowOffset = rowOffset;

    int row, col;
    int iconNum = 0;
    for (row = 0; row < rowOffset; row++) {
        for (col=0; col < 4; col++) {
            if (iconNum >= state->iconCount) {
                return;
            }

            int ps = positionStrip(rowOffset - row, col, 1);
            bindTexture(NAMED_PFTexLinear, 0, loadI32(ALLOC_ICON_IDS, iconNum));
            drawSimpleMesh(NAMED_SMMesh2);
            iconNum++;
        }
    }
}

void drawBottom(float rowOffset)
{
    float pos = -1.f;
    int intRowOffset = rowOffset;
    pos -= rowOffset - intRowOffset;

    int row, col;
    int iconNum = (intRowOffset + 3) * 4;
    while (1) {
        for (col=0; col < 4; col++) {
            if (iconNum >= state->iconCount) {
                return;
            }
            if (pos > -1) {
                int ps = positionStrip(pos, col, 0);
                bindTexture(NAMED_PFTexLinear, 0, loadI32(ALLOC_ICON_IDS, iconNum));
                drawSimpleMesh(NAMED_SMMesh2);
            }
            iconNum++;
        }
        pos += 1.f;
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
    if (g_DT > 0.2f) {
        // physics may break if DT is large.
        g_DT = 0.2f;
    }

    if (g_Zoom != state->zoomTarget) {
        float dz = (state->zoomTarget - g_Zoom) * g_DT * 5;
        if (dz && (fabsf(dz) < 0.03f)) {
            if (dz > 0) {
                dz = 0.03f;
            } else {
                dz = -0.03f;
            }
        }
        if (fabsf(g_Zoom - state->zoomTarget) < fabsf(dz)) {
            g_Zoom = state->zoomTarget;
        } else {
            g_Zoom += dz;
        }
        updateReadback();
    }

    // Set clear value to dim the background based on the zoom position.
    if ((g_Zoom < 0.001f) && (state->zoomTarget < 0.001f)) {
        pfClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        // When we're zoomed out and not tracking motion events, reset the pos to 0.
        if (!g_LastTouchDown) {
            g_PosPage = 0;
        }
        return lastFrame(0);
    } else if (g_Zoom < 0.85f) {
        pfClearColor(0.0f, 0.0f, 0.0f, g_Zoom);
    } else {
        pfClearColor(0.0f, 0.0f, 0.0f, g_Zoom);
    }

    // icons & labels
    int iconCount = state->iconCount;
    g_PosMax = ((iconCount + 3) / 4) - 4;
    if (g_PosMax < 0) g_PosMax = 0;

    updatePos(0.1f);
    updateReadback();

    //debugF("    draw g_PosPage", g_PosPage);

    // Draw the icons ========================================

    //bindProgramFragment(NAMED_PFColor);
    //positionStrip(1, 0, 0);
    //drawSimpleMesh(NAMED_SMMesh2);
    //positionStrip(1, 1);
    //drawSimpleMesh(NAMED_SMMesh2);
    //positionStrip(1, 2);
    //drawSimpleMesh(NAMED_SMMesh2);
    //positionStrip(1, 3);
    //drawSimpleMesh(NAMED_SMMesh2);

    bindProgramFragment(NAMED_PFTexLinear);


    int lastIcon = iconCount-1;

    int page = g_PosPage;
    float currentPagePosition = g_PosPage - page;

    int iconsPerPage = COLUMNS_PER_PAGE * ROWS_PER_PAGE;
    float scale = (1 / g_Zoom);

    float pageAngle = VIEW_ANGLE * 1.2f;

    float zoomOffset = 40 * (1 - g_Zoom);

    drawTop(g_PosPage);
    drawBottom(g_PosPage);
    drawFrontGrid(g_PosPage);

    {
        float mat1[16];
        matrixLoadIdentity(mat1);
        vpLoadModelMatrix(mat1);
        vpLoadTextureMatrix(mat1);
    }

    if (0) {
        float h = getHeight();

        color(1, 1, 1, 1);
        bindProgramFragment(NAMED_PFColor);
        bindProgramVertex(NAMED_PVOrtho);
        float dy = 145.f;
        float y = h - ((h - (dy * 4.f)) / 2);

        drawLine(0, y, 0,  480, y, 0);
        y -= dy;
        drawLine(0, y, 0,  480, y, 0);
        y -= dy;
        drawLine(0, y, 0,  480, y, 0);
        y -= dy;
        drawLine(0, y, 0,  480, y, 0);
        y -= dy;
        drawLine(0, y, 0,  480, y, 0);
    }


    // Draw the home button ========================================
    //draw_home_button();

    // Bug workaround where the last frame is not always displayed
    // So we keep rendering until the bug is fixed.
    return lastFrame((g_PosVelocity != 0) || fracf(g_PosPage) || g_Zoom != state->zoomTarget);
}

